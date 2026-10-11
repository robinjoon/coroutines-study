package study.core.composition.stage0

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage0Test {
    /**
     * suspend 함수 호출은 기본적으로 순차 실행된다. 각 호출이 100 ms씩 기다리면 두 대기 시간이 더해진다.
     * runTest는 실제로 잠들지 않고 가상 시각을 진행하므로 이 비용을 빠르게 관찰할 수 있다.
     * [공식 문서](https://kotlinlang.org/docs/composing-suspending-functions.html#sequential-by-default)
     * 관찰 지점: 두 작업의 시작과 끝, 결과 합계, currentTime을 확인하라.
     * suspend를 두 번 호출했는데 시간이 겹치지 않은 이유는 무엇일까?
     */
    @Test
    fun sequentialCallsAccumulateVirtualDelay() =
        runTest {
            val events = mutableListOf<String>()

            suspend fun useful(
                name: String,
                value: Int,
            ): Int {
                events += "$name:start"
                delay(100)
                events += "$name:end"
                return value
            }
            val result = useful("first", 10) + useful("second", 20)

            assertThat(result).isEqualTo(30)
            // TODO(a): 순차 호출의 사건 순서와 가상 경과 시간에 맞게 고쳐 보자.
            assertThat(events).containsExactly("")
            assertThat(currentTime).isEqualTo(-1L)
        }
}
