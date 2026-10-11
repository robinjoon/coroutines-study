package study.core.basics.stage1

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
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
     */
    @Test
    fun parentReturnsAfterChildrenComplete() = runTest {
        val events = mutableListOf<String>()

        greetWithChildren(events)
        events += "caller:returned"

        assertThat(events).containsExactly("parent:body-end", "first:end", "second:end", "caller:returned")
        assertThat(currentTime).isEqualTo(200L)
    }

    /**
     * launch가 반환한 Job은 작업의 완료와 취소를 다루는 손잡이다.
     * join은 완료를 기다리지만 async의 await처럼 계산 결과를 돌려주지는 않는다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-basics.html#coroutinescope-launch)
     * 관찰 지점: join 전후의 events, 반환값 Unit, Job의 완료 상태를 확인하라.
     * 기다리는 기능과 결과를 받는 기능이 별개인 이유는 무엇일까?
     */
    @Test
    fun joinWaitsButDoesNotProduceValue() = runTest {
        val events = mutableListOf("caller:before-join")
        val job = launch {
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
}
