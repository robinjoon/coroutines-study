package study.core.flow.stage0

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage0Test {
    /**
     * flow 빌더로 만든 흐름은 보통 collect할 때 생산을 시작하는 cold Flow다.
     * 같은 Flow를 두 번 수집하면 생산 코드도 두 번 실행되며 생성만으로는 값을 만들지 않는다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-flow.html#flows-are-cold)
     * 관찰 지점: 생성 직후의 생산 횟수와 두 번 수집한 뒤의 횟수·events·결과를 비교하라.
     * 동일한 Flow 객체를 수집했는데 생산 코드가 다시 실행되는 이유는 무엇일까?
     */
    @Test
    fun coldFlowRunsForEachCollector() =
        runTest {
            val events = mutableListOf<String>()
            var productions = 0
            val numbers =
                flow {
                    productions++
                    events += "produce:$productions"
                    delay(100)
                    emit(1)
                    emit(2)
                }
            val afterCreation = productions
            val first = numbers.toList()
            val second = numbers.toList()

            assertThat(first).containsExactly(1, 2)
            assertThat(second).containsExactly(1, 2)
            // TODO(a): Flow 생성과 수집이 생산 코드 실행에 어떤 차이를 만드는지 확인하자.
            assertThat(afterCreation).isEqualTo(-1)
            assertThat(productions).isEqualTo(-1)
            assertThat(events).containsExactly("")
            assertThat(currentTime).isEqualTo(200L)
        }
}
