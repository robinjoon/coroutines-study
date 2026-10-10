package study.mvc.aggregation.stage1

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 외부 API 여러 개를 병렬로 호출하고, 실패와 시간 초과를 정책대로 처리하자.
 * 이 테스트 코드는 수정하지 않는다. ProductSummaryService의 TODO를 구현해서 통과시키자.
 *
 * - 공식 가이드(async로 동시 실행): https://kotlinlang.org/docs/composing-suspending-functions.html#concurrent-using-async
 * - 공식 가이드(구조화된 동시성과 async): https://kotlinlang.org/docs/composing-suspending-functions.html#structured-concurrency-with-async
 * - 공식 가이드(timeout): https://kotlinlang.org/docs/cancellation-and-timeouts.html#timeout
 * - 공식 가이드(예외 전파): https://kotlinlang.org/docs/exception-handling.html
 */
class Stage1Test {

    /**
     * `coroutineScope { async { ... } }`로 두 호출을 동시에 시작할 수 있다.
     * 구조화된 동시성 덕분에 자식 하나가 실패하면 형제 자식도 취소되고, 예외는 부모로 전파된다.
     * 그렇다면 "재고 실패만은 삼키고 싶을" 때는 예외를 어디서 잡아야 할까?
     * `await()`를 try-catch로 감싸는 것만으로는 부족하다. 왜 그럴까?
     *
     * 왜 그런 결과가 나왔을까? 가격 실패 시 재고 조회가 취소되지 않고 끝까지 진행된다면 서버 자원에 어떤 낭비가 생길까?
     */
    @Test
    fun parallelAggregationAppliesFailurePolicy() {
        // 1. 둘 다 성공하면 두 호출이 동시에 진행된다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(FakePriceClient(events), FakeStockClient(events))

            assertThat(service.summary(1)).isEqualTo(ProductSummary(1, 10_000, 7))
            assertThat(currentTime).isEqualTo(100)
            assertThat(events).contains("price:start", "stock:start", "price:end", "stock:end")
        }

        // 2. 선택 정보인 재고가 실패하면 부분 응답을 만든다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(
                FakePriceClient(events),
                FakeStockClient(events, delayMillis = 50, fail = true),
            )

            assertThat(service.summary(1)).isEqualTo(ProductSummary(1, 10_000, null))
            assertThat(currentTime).isEqualTo(100)
            assertThat(events).contains("stock:failed", "price:end")
        }

        // 3. 필수 정보인 가격이 실패하면 전체가 실패하고, 진행 중인 재고 조회는 취소된다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(
                FakePriceClient(events, delayMillis = 50, fail = true),
                FakeStockClient(events),
            )

            val failure = runCatching { service.summary(1) }.exceptionOrNull()

            assertThat(failure).isInstanceOf(PriceUnavailableException::class.java)
            assertThat(currentTime).isEqualTo(50)
            assertThat(events).contains("price:failed", "stock:cancelled").doesNotContain("stock:end")
        }
    }

    /**
     * 시간 예산은 두 종류다.
     * - 선택 정보(재고)에만 걸리는 짧은 예산: 넘기면 포기하고 부분 응답한다(`withTimeoutOrNull`).
     * - 요청 전체에 걸리는 예산: 넘기면 요청 전체가 실패한다(`withTimeout`).
     * 시간 초과로 취소된 호출은 CancellationException으로 끝난다. 이 예외를 일반 실패처럼 삼키면 안 되는 이유를 생각해 보자.
     *
     * `withTimeout`이 던지는 TimeoutCancellationException을 잡을 때는 "누구의" 시간 초과인지도 구분해야 한다. 바깥에서 건 timeout까지 잡아 버리지 않도록 주의하자.
     *
     * 여기의 가짜 클라이언트는 delay로 기다리므로 취소에 바로 반응한다. 실제 블로킹 HTTP 클라이언트나 JDBC는 취소돼도 바로 멈추지 않을 수 있으니,
     * 실제 연동에서는 클라이언트 자체의 connect/read timeout도 함께 설정해야 한다(blockingio 단원 참고).
     *
     * 왜 그런 결과가 나왔을까? 사용자가 요청을 취소(바깥 Job 취소)했는데 서비스가 "재고 없음" 같은 부분 응답을 만들어 버리면 어떤 문제가 생길까?
     */
    @Test
    fun deadlinesAndParentCancellationStopOutstandingCalls() {
        // 0. 예산 안에서는 summary와 같은 실패 정책을 따른다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(FakePriceClient(events), FakeStockClient(events, delayMillis = 50))
            assertThat(service.summaryWithDeadline(1)).isEqualTo(ProductSummary(1, 10_000, 7))
            assertThat(currentTime).isEqualTo(100)
        }
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(FakePriceClient(events), FakeStockClient(events, delayMillis = 50, fail = true))
            assertThat(service.summaryWithDeadline(1)).isEqualTo(ProductSummary(1, 10_000, null))
        }
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(FakePriceClient(events, delayMillis = 50, fail = true), FakeStockClient(events))
            val failure = runCatching { service.summaryWithDeadline(1) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(PriceUnavailableException::class.java)
            assertThat(events).contains("stock:cancelled")
        }

        // 1. 재고가 예산(100ms)을 넘기면 재고만 포기한다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(
                FakePriceClient(events, delayMillis = 150),
                FakeStockClient(events, delayMillis = 200),
            )

            assertThat(service.summaryWithDeadline(1)).isEqualTo(ProductSummary(1, 10_000, null))
            assertThat(currentTime).isEqualTo(150)
            assertThat(events).contains("stock:cancelled", "price:end")
        }

        // 2. 요청 전체가 예산(300ms)을 넘기면 SummaryTimeoutException으로 실패하고 남은 호출은 취소된다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(
                FakePriceClient(events, delayMillis = 500),
                FakeStockClient(events, delayMillis = 50),
            )

            val failure = runCatching { service.summaryWithDeadline(1) }.exceptionOrNull()

            assertThat(failure).isInstanceOf(SummaryTimeoutException::class.java)
            assertThat(currentTime).isEqualTo(300)
            assertThat(events).contains("stock:end", "price:cancelled")
        }

        // 3. 바깥에서 취소하면 모든 호출이 취소되고, 부분 응답을 만들지 않는다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(FakePriceClient(events), FakeStockClient(events))
            var result: ProductSummary? = null

            val request = launch { result = service.summaryWithDeadline(1) }
            advanceTimeBy(50)
            runCurrent()
            request.cancelAndJoin()

            assertThat(result).isNull()
            assertThat(events).contains("price:cancelled", "stock:cancelled")
        }

        // 4. 호출한 쪽이 건 더 짧은 timeout은 호출한 쪽의 것이다. 서비스의 시간 초과(SummaryTimeoutException)로 바꾸면 안 된다.
        runTest {
            val events = mutableListOf<String>()
            val service = ProductSummaryService(FakePriceClient(events), FakeStockClient(events))

            val failure = runCatching { withTimeout(50) { service.summaryWithDeadline(1) } }.exceptionOrNull()

            assertThat(failure).isInstanceOf(TimeoutCancellationException::class.java)
            assertThat(currentTime).isEqualTo(50)
            assertThat(events).contains("price:cancelled", "stock:cancelled")
        }
    }
}
