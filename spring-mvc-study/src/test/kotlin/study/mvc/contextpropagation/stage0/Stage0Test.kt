package study.mvc.contextpropagation.stage0

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.concurrent.Executors

/**
 * 로그에 요청 ID를 남기려고 MDC(Mapped Diagnostic Context)를 자주 쓴다.
 * MDC는 내부적으로 ThreadLocal이다. 값은 "스레드"에 저장된다.
 * 코루틴이 다른 스레드로 옮겨 가면 그 값은 어떻게 될까?
 *
 [* - 공식 가이드(Thread-local data)](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#thread-local-data)
 * - [SLF4J MDC](https://www.slf4j.org/manual.html#mdc)
 */
class Stage0Test {
    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    /**
     * 요청 스레드에서 MDC에 requestId를 넣은 뒤, 같은 스레드에서 실행되는 코루틴과
     * 다른 스레드(worker)로 옮겨 간 코루틴에서 각각 값을 읽어 보자.
     *
     * 왜 그런 결과가 나왔을까? suspend 컨트롤러는 delay 이후 다른 스레드에서 재개된다(controller.stage0 참고).
     * 그 뒤에 남긴 로그에는 requestId가 찍힐까?
     */
    @Test
    fun plainThreadLocalIsAbsentOnFreshWorker() {
        MDC.put("requestId", "req-1")

        val sameThread = runBlocking { MDC.get("requestId") }
        val otherThread =
            Executors.newSingleThreadExecutor { Thread(it, "worker") }.asCoroutineDispatcher().use { worker ->
                runBlocking { withContext(worker) { MDC.get("requestId") } }
            }

        // TODO(a): 관찰한 결과로 기대값을 고치자.
        assertThat(sameThread).isNull()
        assertThat(otherThread).isEqualTo("req-1")
    }
}
