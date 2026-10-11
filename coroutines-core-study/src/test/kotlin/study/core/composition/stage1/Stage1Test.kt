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
     * [공식 문서](https://kotlinlang.org/docs/composing-suspending-functions.html#structured-concurrency-with-async)
     * 관찰 지점: 완료 전에 두 시작 사건이 있는지, 결과 합계와 가상 시간, 실패·timeout 후 정리를 확인하라.
     * await를 호출하는 순서와 작업이 시작하는 순서가 다른 이유는 무엇일까?
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
                assertThat(events).contains("first:start", "second:start")
                events += "$name:end"
                return value
            }

            val result = concurrentSum({ useful("first", 10) }, { useful("second", 20) })

            assertThat(result).isEqualTo(30)
            assertThat(currentTime).isEqualTo(100L)
            assertThat(events.take(2)).containsExactlyInAnyOrder("first:start", "second:start")

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
            assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("first failed")
            assertThat(events).containsExactly("second:finally")
            assertThat(currentTime).isEqualTo(110L)

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
            assertThat(timedOut).isNull()
            assertThat(events).containsExactlyInAnyOrder("first:finally", "second:finally")
            assertThat(currentTime).isEqualTo(160L)
        }
}
