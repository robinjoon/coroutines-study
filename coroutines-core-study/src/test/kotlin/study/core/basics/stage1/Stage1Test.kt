package study.core.basics.stage1

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * 구조적 동시성에서는 부모가 자신이 시작한 자식의 수명을 책임진다.
     * coroutineScope의 본문이 끝나도 자식이 남아 있으면 호출자에게 아직 반환하지 않는다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-basics.html#create-a-coroutine-scope-with-the-coroutinescope-function)
     * 관찰 지점: 부모 본문, 두 자식의 완료, 함수 반환을 events로 비교하라.
     * 본문 종료와 함수 반환 사이에 다른 사건들이 들어오는 이유는 무엇일까?
     *
     * 이 테스트는 결과 순서만 보지 않는다. 가상 시각 0에 자식 코루틴 두 개가 실제로 떠서 기다리고 있는지도 확인한다.
     * 한 코루틴 안에서 delay와 기록을 차례로 이어 붙여 같은 순서를 흉내 내면 통과하지 못한다.
     */
    @Test
    fun parentReturnsAfterChildrenComplete() =
        runTest {
            val events = mutableListOf<String>()
            val caller =
                launch {
                    greetWithChildren(events)
                    events += "caller:returned"
                }

            runCurrent()
            assertThat(events)
                .describedAs("자식이 기다리는 동안 부모 본문은 이미 끝나 있어야 한다")
                .containsExactly("parent:body-end")
            assertThat(caller.waitingChildCoroutines())
                .describedAs("가상 시각 0에 두 자식 코루틴이 각각 기다리고 있어야 한다")
                .isEqualTo(2)

            advanceTimeBy(100)
            runCurrent()
            assertThat(events)
                .describedAs("첫 자식만 완료됐을 때 부모는 아직 반환하지 않아야 한다")
                .containsExactly("parent:body-end", "first:end")
            assertThat(caller.isActive)
                .describedAs("두 번째 자식이 남아 있으므로 호출자는 아직 기다려야 한다")
                .isTrue()

            caller.join()
            assertThat(events).containsExactly("parent:body-end", "first:end", "second:end", "caller:returned")
            assertThat(currentTime).isEqualTo(200L)
        }

    /**
     * launch가 반환한 Job은 작업의 완료와 취소를 다루는 손잡이다.
     * join은 완료를 기다리지만 async의 await처럼 계산 결과를 돌려주지는 않는다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-basics.html#coroutinescope-launch)
     * 관찰 지점: join 전후의 events와 Job의 완료 상태를 확인하라.
     * waitForJob의 반환 타입 Unit을 결과 타입 T를 돌려주는 Deferred<T>.await()와 비교하라.
     * 기다리는 기능과 결과를 받는 기능이 별개인 이유는 무엇일까?
     */
    @Test
    fun joinWaitsButDoesNotProduceValue() =
        runTest {
            val events = mutableListOf("caller:before-join")
            val job =
                launch {
                    delay(100)
                    events += "child:end"
                }

            val result: Unit = waitForJob(job)
            events += "caller:after-join"

            assertThat(result).isEqualTo(Unit)
            assertThat(job.isCompleted).isTrue()
            assertThat(events).containsExactly("caller:before-join", "child:end", "caller:after-join")
            assertThat(currentTime).isEqualTo(100L)
        }

    // 중간 스코프의 Job을 제외하고, 자식이 없는 가장 안쪽 코루틴 수를 센다.
    private fun Job.waitingChildCoroutines(): Int =
        children.sumOf { child -> if (child.children.none()) 1 else child.waitingChildCoroutines() }
}
