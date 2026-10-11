package study.core.cancellation.stage1

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * finishWithin은 자식까지 시간 안에 완료하면 true, 이 함수가 건 시간 제한이 만료된 경우만 false를 반환한다.
     * work에서 발생한 실패·취소·별도 timeout은 그대로 전파한다.
     * withTimeout은 시간 예산을 넘으면 내부 작업과 그 자식을 취소한다.
     * 취소 중에도 finally는 실행되므로 자식의 정리를 확인할 수 있다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-cancellation.html#timeout)
     * 관찰 지점: 100 ms 예산과 1,000 ms 작업을 비교하고 반환 전에 finally가 끝났는지 확인하라.
     * 시간 예산이 끝났을 때 자식 작업도 정리되는 이유는 무엇일까?
     */
    @Test
    fun timeoutStopsChildrenAndRunsFinally() =
        runTest {
            val events = mutableListOf<String>()
            // 1) 이 함수가 건 시간 제한이 만료되면 자식의 finally까지 실행하고 false를 반환한다.
            val completed =
                finishWithin(100) {
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

            assertThat(completed).describedAs("이 함수가 건 시간 제한이 만료되면 false를 반환해야 한다").isFalse()
            assertThat(currentTime).describedAs("시간 제한이 만료되면 자식의 원래 완료 시각까지 기다리지 않는다").isEqualTo(100L)
            assertThat(events)
                .describedAs("반환 전에 자식이 취소되고 finally가 실행돼야 한다")
                .containsExactly("child:start", "child:finally")

            // 2) 시간 안에 완료되면 true를 반환하고, 일반 실패는 전파한다.
            assertThat(finishWithin(100) { delay(10) }).describedAs("시간 안에 끝난 작업은 성공이다").isTrue()
            // currentTime은 runTest 전체에서 누적된다. 앞 단계에서 흐른 시간 + 이번 단계의 시간이다.
            assertThat(currentTime).isEqualTo(110L)

            val failure = runCatching { finishWithin(100) { error("work failed") } }.exceptionOrNull()
            assertThat(failure)
                .describedAs("작업의 일반 실패는 이 함수의 시간 초과로 바꾸지 않아야 한다")
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("work failed")

            // 3) 작업의 취소 신호는 이 함수가 건 시간 제한의 만료와 다르다.
            val cancellation = CancellationException("work cancelled")
            val cancelled = runCatching { finishWithin(100) { throw cancellation } }.exceptionOrNull()
            assertThat(cancelled)
                .describedAs("work의 CancellationException은 false로 바꾸지 말고 호출자에게 전파해야 한다")
                .isInstanceOf(CancellationException::class.java)
                .hasMessage("work cancelled")

            // 4) 요청 전체 제한 안에서 DB 호출에 더 짧은 제한을 둔 경우를 생각하자.
            // 안쪽 timeout을 이 함수의 시간 초과로 오해하면 안 된다.
            // TimeoutCancellationException을 모두 잡으면 둘을 구분할 수 없다. docs/concepts.md의 withTimeoutOrNull을 참고하자.
            val nestedTimeout =
                runCatching {
                    finishWithin(100) {
                        withTimeout(10) { delay(1_000) }
                    }
                }.exceptionOrNull()
            assertThat(nestedTimeout)
                .describedAs("work의 별도 timeout은 이 함수의 false로 바꾸지 말고 전파해야 한다")
                .isInstanceOf(TimeoutCancellationException::class.java)

            // 5) 호출자가 지정한 다른 시간 제한도 적용해야 한다.
            assertThat(finishWithin(20) { delay(50) })
                .describedAs("호출자가 지정한 시간 예산을 사용해야 한다")
                .isFalse()
        }

    /**
     * CancellationException은 일반적인 서비스 실패가 아니라 작업을 중단하라는 신호다.
     * 일반 실패에는 대체값을 줄 수 있지만 취소 신호는 다시 던져 호출자에게 전달해야 한다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-cancellation.html#cancellation-is-cooperative)
     * 관찰 지점: 성공·일반 실패·직접 취소·timeout에서 fallback 호출 여부와 정리를 확인하라.
     * 취소까지 대체값으로 바꾸면 호출자는 어떤 사실을 놓칠까?
     */
    @Test
    fun cancellationDoesNotBecomeFallback() =
        runTest {
            val events = mutableListOf<String>()
            val fallback = {
                events += "fallback"
                "cached"
            }
            assertThat(loadOrFallback({ "fresh" }, fallback)).isEqualTo("fresh")
            assertThat(events).isEmpty()
            assertThat(loadOrFallback({ error("unavailable") }, fallback)).isEqualTo("cached")
            assertThat(events).containsExactly("fallback")

            events.clear()
            val cancellation = CancellationException("stop")
            val failure = runCatching { loadOrFallback({ throw cancellation }, fallback) }.exceptionOrNull()
            assertThat(failure)
                .describedAs("CancellationException은 fallback으로 바꾸지 말고 호출자에게 전파해야 한다")
                .isInstanceOf(CancellationException::class.java)
                .hasMessage("stop")
            assertThat(events).isEmpty()

            val result =
                withTimeoutOrNull(100) {
                    loadOrFallback(
                        {
                            try {
                                delay(1_000)
                                "late"
                            } finally {
                                events += "finally"
                            }
                        },
                        fallback,
                    )
                }
            assertThat(result).isNull()
            assertThat(events).containsExactly("finally")
            assertThat(currentTime).isEqualTo(100L)
        }
}
