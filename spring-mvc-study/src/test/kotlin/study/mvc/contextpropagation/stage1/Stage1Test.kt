package study.mvc.contextpropagation.stage1

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.concurrent.Executors

/**
 * 코루틴 컨텍스트에 ThreadLocal 값을 실어 보내자.
 * 코루틴 컨텍스트 요소 중에는 "코루틴이 스레드에서 실행을 시작할 때 값을 설치하고, 멈출 때 원래 값으로 되돌리는" 요소가 있다.
 * 일반 ThreadLocal은 `ThreadLocal.asContextElement(value)`, MDC는 `MDCContext`가 이 역할을 한다.
 *
 * 이 테스트 코드는 수정하지 않는다. RequestContextRunner의 TODO를 구현해서 통과시키자.
 *
 * - [공식 가이드(Thread-local data)](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#thread-local-data)
 * - [MDCContext](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-slf4j/kotlinx.coroutines.slf4j/-m-d-c-context/)
 */
class Stage1Test {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "worker") }
    private val worker = executor.asCoroutineDispatcher()

    @AfterEach
    fun tearDown() {
        worker.close()
        MDC.clear()
    }

    /**
     * 요청 A와 B가 하나뿐인 worker 스레드를 번갈아 쓴다. A가 delay로 멈춘 사이 B가 같은 스레드에서 실행된다.
     * worker 스레드에서 MDC.put을 직접 호출하면(끝날 때 finally로 되돌리더라도) A가 재개될 때 B의 값을 보게 된다.
     * 또 worker 스레드에 원래 있던 값("worker-default")이 덮어써진 채로 남는다. 스레드 풀 스레드는 재사용되기 때문이다.
     *
     * MDCContext는 코루틴을 만들 때의 MDC 값을 "스냅샷"으로 담아 둔다. 코루틴 안에서 MDC.put으로 바꾼 값은
     * 다음 재개 때 자동으로 다시 담기지 않는다는 점도 기억하자.
     *
     * 왜 그런 결과가 나왔을까? 스레드 풀을 쓰는 서버에서 MDC를 직접 put하고 지우지 않으면 다른 사용자의 로그에 어떤 값이 찍힐까?
     */
    @Test
    fun mdcSnapshotSurvivesAndRestoresWorkerContext() {
        executor.submit { MDC.put("requestId", "worker-default") }.get()
        val runner = RequestContextRunner(worker)

        // A가 멈춘 사이 B가 시작되고, B가 아직 끝나지 않았을 때 A가 먼저 재개된다.
        val (a, b) =
            runBlocking {
                val a = async { runner.runWithRequestId("req-A") { observe(delayMillis = 50) } }
                val b = async { runner.runWithRequestId("req-B") { observe(delayMillis = 100) } }
                a.await() to b.await()
            }

        assertThat(a).isEqualTo(Observation("worker", "req-A", "req-A"))
        assertThat(b).isEqualTo(Observation("worker", "req-B", "req-B"))
        assertThat(executor.submit<String?> { MDC.get("requestId") }.get()).isEqualTo("worker-default")
        assertThat(MDC.get("requestId")).isNull()
    }

    private data class Observation(
        val thread: String,
        val beforeDelay: String?,
        val afterDelay: String?,
    )

    private suspend fun observe(delayMillis: Long): Observation {
        val before = MDC.get("requestId")
        delay(delayMillis)
        // 코루틴 디버그 모드에서는 스레드 이름 뒤에 " @coroutine#N"이 붙으므로 앞부분만 쓴다.
        return Observation(Thread.currentThread().name.substringBefore(" @"), before, MDC.get("requestId"))
    }
}
