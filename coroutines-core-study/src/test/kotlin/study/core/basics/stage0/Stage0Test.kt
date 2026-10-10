package study.core.basics.stage0

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage0Test {
    /**
     * suspend 함수는 실행을 잠시 멈출 수 있는 함수다. 선언만으로 새 코루틴이 생기지는 않는다.
     * 같은 코루틴에서 두 함수를 호출하면 첫 호출이 끝난 뒤 다음 호출로 넘어간다.
     * 공식 문서: https://kotlinlang.org/docs/coroutines-basics.html#suspending-functions
     * 관찰 지점: events의 순서와 두 번의 delay가 더해진 currentTime을 확인하라.
     * 중단할 수 있는 함수인데도 순서가 바뀌지 않은 이유는 무엇일까?
     */
    @Test
    fun suspendDoesNotStartAnotherCoroutine() = runTest {
        val events = mutableListOf<String>()
        suspend fun greet(name: String) {
            events += "$name:start"
            delay(100)
            events += "$name:end"
        }

        greet("first")
        greet("second")

        // TODO(a): events에 기록된 호출 순서에 맞게 기대값을 바꿔 보자.
        assertThat(events).containsExactly("")
        // TODO(a): 두 번의 delay를 지난 가상 시간은 얼마일까?
        assertThat(currentTime).isEqualTo(-1L)
    }

    /**
     * launch는 자식 코루틴을 만들고 Job을 즉시 반환한다. runTest의 기본 dispatcher는 새 작업을 대기열에 넣는다.
     * runCurrent는 현재 가상 시각에 실행할 수 있는 작업을 진행한다.
     * 공식 문서: https://kotlinlang.org/docs/coroutines-basics.html#coroutinescope-launch
     * 관찰 지점: launch 직후, runCurrent 후, join 후의 events를 비교하라.
     * 코루틴을 만들었는데 첫 사건이 아직 기록되지 않은 이유는 무엇일까?
     */
    @Test
    fun launchStartsWhenSchedulerRuns() = runTest {
        val events = mutableListOf("parent")
        val job = launch {
            events += "child:start"
            delay(100)
            events += "child:end"
        }
        val justLaunched = events.toList()
        runCurrent()
        val afterRunCurrent = events.toList()
        job.join()

        // TODO(a): 세 시점에 실제로 실행된 부분을 구분해서 기대값을 채워 보자.
        assertThat(justLaunched).containsExactly("")
        assertThat(afterRunCurrent).containsExactly("")
        assertThat(events).containsExactly("")
    }
}
