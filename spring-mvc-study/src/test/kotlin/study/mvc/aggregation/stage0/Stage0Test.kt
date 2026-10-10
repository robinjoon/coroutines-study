package study.mvc.aggregation.stage0

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 상품 요약 API는 가격 API와 재고 API를 호출해서 결과를 합친다.
 * 이 단원은 공식 가이드의 "Composing suspending functions"를 백엔드 시나리오에 적용한다.
 *
 * - 공식 가이드(순차 실행이 기본): https://kotlinlang.org/docs/composing-suspending-functions.html#sequential-by-default
 * - runTest와 가상 시간: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
 */
class Stage0Test {

    /**
     * suspend 함수를 차례로 호출하면 코루틴 안에서도 "순서대로" 실행된다. 코루틴이라고 저절로 병렬이 되지는 않는다.
     * runTest의 `currentTime`은 가상 시계다. delay(100)은 실제로 기다리지 않고 가상 시계만 100ms 앞으로 보낸다.
     *
     * 왜 그런 결과가 나왔을까? 가격과 재고는 서로 의존하지 않는데, 시간을 줄이려면 무엇이 필요할까?
     */
    @Test
    fun sequentialAggregationAccumulatesDelay() = runTest {
        val events = mutableListOf<String>()
        val service = SequentialProductSummaryService(FakePriceClient(events), FakeStockClient(events))

        val summary = service.summary(1)

        assertThat(summary).isEqualTo(ProductSummary(id = 1, price = 10_000, stock = 7))
        // TODO(a): 호출 순서와 가상 경과 시간을 관찰하고 기대값을 고치자.
        assertThat(events).containsExactly("")
        assertThat(currentTime).isEqualTo(-1)
    }
}

data class ProductSummary(val id: Long, val price: Int, val stock: Int?)

class FakePriceClient(private val events: MutableList<String>) {
    suspend fun price(id: Long): Int {
        events += "price:start"
        delay(100)
        events += "price:end"
        return 10_000
    }
}

class FakeStockClient(private val events: MutableList<String>) {
    suspend fun stock(id: Long): Int {
        events += "stock:start"
        delay(100)
        events += "stock:end"
        return 7
    }
}

class SequentialProductSummaryService(
    private val priceClient: FakePriceClient,
    private val stockClient: FakeStockClient,
) {
    suspend fun summary(id: Long): ProductSummary {
        val price = priceClient.price(id)
        val stock = stockClient.stock(id)
        return ProductSummary(id, price, stock)
    }
}
