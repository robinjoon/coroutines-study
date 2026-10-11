package study.core.composition.stage1

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * async는 결과를 가진 자식 코루틴을 만들고 await는 그 결과를 기다린다.
     * 두 async를 먼저 만들면 독립적인 두 작업의 대기 시간을 겹칠 수 있다.
     * 같은 scope에 속한 자식은 다른 자식의 실패나 부모 취소 때 함께 정리된다.
     * withTimeoutOrNull은 자기가 건 시간 제한이 만료되면 블록을 취소하고 null을 반환한다. 자세한 동작은 cancellation 단원에서 다룬다.
     * [공식 문서](https://kotlinlang.org/docs/composing-suspending-functions.html#structured-concurrency-with-async)
     * 관찰 지점: 완료 전에 두 시작 사건이 있는지, 결과 합계와 가상 시간, 실패·timeout 후 정리를 확인하라.
     * await 전에 두 작업을 실행 대기열에 올리면 가상 시간은 어떻게 달라질까?
     */
    @Test
    fun asyncStartsBothTasksBeforeAwaiting() =
        runTest {
            val events = mutableListOf<String>()

            suspend fun useful(
                name: String,
                value: Int,
            ): Int {
                events += "$name:start"
                delay(100)
                assertThat(events)
                    .describedAs("두 작업이 모두 시작된 뒤에야 첫 delay가 끝나야 한다")
                    .contains("first:start", "second:start")
                events += "$name:end"
                return value
            }

            val result = concurrentSum({ useful("first", 10) }, { useful("second", 20) })

            assertThat(result).describedAs("두 작업의 결과를 합쳐야 한다").isEqualTo(30)
            assertThat(currentTime).describedAs("독립적인 두 작업의 대기 시간이 겹쳐야 한다").isEqualTo(100L)
            assertThat(events.take(2))
                .describedAs("어느 작업이 먼저 시작하든 두 작업 모두 완료 전에 시작돼야 한다")
                .containsExactlyInAnyOrder("first:start", "second:start")

            events.clear()
            val failure =
                runCatching {
                    concurrentSum(
                        {
                            delay(10)
                            error("first failed")
                        },
                        {
                            try {
                                delay(1_000)
                                20
                            } finally {
                                events += "second:finally"
                            }
                        },
                    )
                }.exceptionOrNull()
            assertThat(failure)
                .describedAs("작업 실패는 호출자에게 전달돼야 한다")
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("first failed")
            assertThat(events)
                .describedAs("first가 실패하면 같은 scope의 second도 취소되어 finally가 실행돼야 한다")
                .containsExactly("second:finally")
            // currentTime은 runTest 전체에서 누적된다. 앞 단계에서 흐른 시간 + 이번 단계의 시간이다.
            assertThat(currentTime).describedAs("형제 작업의 원래 완료 시각까지 기다리지 않아야 한다").isEqualTo(110L)

            events.clear()
            val timedOut =
                withTimeoutOrNull(50) {
                    concurrentSum(
                        {
                            try {
                                delay(1_000)
                                10
                            } finally {
                                events += "first:finally"
                            }
                        },
                        {
                            try {
                                delay(1_000)
                                20
                            } finally {
                                events += "second:finally"
                            }
                        },
                    )
                }
            assertThat(timedOut).describedAs("시간 제한이 만료되면 정상 결과를 반환하지 않아야 한다").isNull()
            assertThat(events)
                .describedAs("부모가 취소되면 두 자식의 finally가 모두 실행돼야 한다")
                .containsExactlyInAnyOrder("first:finally", "second:finally")
            // currentTime은 앞 단계의 시간에 이번 timeout의 시간을 더한 값이다.
            assertThat(currentTime).describedAs("부모의 시간 예산 안에서 두 자식을 정리해야 한다").isEqualTo(160L)
        }
}
