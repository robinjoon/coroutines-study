package study.core.cancellation.stage1

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * withTimeout은 시간 예산을 넘으면 내부 작업과 그 자식을 취소한다.
     * 취소 중에도 finally는 실행되므로 자식의 정리를 확인할 수 있다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-cancellation.html#timeout)
     * 관찰 지점: 100 ms 예산과 1,000 ms 작업을 비교하고 반환 전에 finally가 끝났는지 확인하라.
     * 시간 예산이 끝났을 때 자식 작업도 정리되는 이유는 무엇일까?
     */
    @Test
    fun timeoutStopsChildrenAndRunsFinally() = runTest {
        val events = mutableListOf<String>()
        val completed = finishWithin(100) {
            launch {
                try {
                    events += "child:start"
                    delay(1_000)
                    events += "child:end"
                } finally {
                    events += "child:finally"
                }
            }
        }

        assertThat(completed).isFalse()
        assertThat(currentTime).isEqualTo(100L)
        assertThat(events).containsExactly("child:start", "child:finally")
        assertThat(finishWithin(100) { delay(10) }).isTrue()
        assertThat(currentTime).isEqualTo(110L)

        val failure = runCatching { finishWithin(100) { error("work failed") } }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("work failed")
    }

    /**
     * CancellationException은 일반적인 서비스 실패가 아니라 작업을 중단하라는 신호다.
     * 일반 실패에는 대체값을 줄 수 있지만 취소 신호는 다시 던져 호출자에게 전달해야 한다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-cancellation.html#cancellation-is-cooperative)
     * 관찰 지점: 성공·일반 실패·직접 취소·timeout에서 fallback 호출 여부와 정리를 확인하라.
     * 취소까지 대체값으로 바꾸면 호출자는 어떤 사실을 놓칠까?
     */
    @Test
    fun cancellationDoesNotBecomeFallback() = runTest {
        val events = mutableListOf<String>()
        val fallback = { events += "fallback"; "cached" }
        assertThat(loadOrFallback({ "fresh" }, fallback)).isEqualTo("fresh")
        assertThat(events).isEmpty()
        assertThat(loadOrFallback({ error("unavailable") }, fallback)).isEqualTo("cached")
        assertThat(events).containsExactly("fallback")

        events.clear()
        val cancellation = CancellationException("stop")
        val failure = runCatching { loadOrFallback({ throw cancellation }, fallback) }.exceptionOrNull()
        assertThat(failure).isSameAs(cancellation)
        assertThat(events).isEmpty()

        val result = withTimeoutOrNull(100) {
            loadOrFallback(
                { try { delay(1_000); "late" } finally { events += "finally" } },
                fallback,
            )
        }
        assertThat(result).isNull()
        assertThat(events).containsExactly("finally")
        assertThat(currentTime).isEqualTo(100L)
    }
}
