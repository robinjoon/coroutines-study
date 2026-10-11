package study.core.sharedstate.stage0

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage0Test {
    /**
     * 값을 읽고 바꾼 뒤 저장하는 과정은 하나의 원자적 연산이 아니다.
     * 이 테스트는 읽기와 쓰기 사이에 yield를 넣어 같은 이전 값을 두 작업이 읽도록 재현한다.
     * 단일 test scheduler의 관찰이며 실제 다중 thread의 일반적인 실행 순서를 증명하지 않는다.
     * [공식 문서](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html#the-problem)
     * 관찰 지점: 두 작업이 읽은 값, 저장한 값, 최종 counter를 확인하라.
     * 증가를 두 번 시도했는데 한 번의 갱신이 사라지는 이유는 무엇일까?
     */
    @Test
    fun splitReadYieldWriteLosesAnUpdate() =
        runTest {
            val events = mutableListOf<String>()
            var counter = 0
            coroutineScope {
                repeat(2) { participant ->
                    launch {
                        val before = counter
                        events += "$participant:read:$before"
                        yield()
                        counter = before + 1
                        events += "$participant:write:$counter"
                    }
                }
            }

            // TODO(a): 명시적 중단 지점이 있는 read → write의 결과를 관찰하자.
            assertThat(counter).isEqualTo(-1)
            assertThat(events).containsExactly("")
        }
}
