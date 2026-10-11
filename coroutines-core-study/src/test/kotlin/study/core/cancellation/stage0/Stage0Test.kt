package study.core.cancellation.stage0

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage0Test {
    /**
     * cancel은 취소를 요청하고 join은 실제 종료를 기다린다. 요청 직후에는 finally가 아직 실행되지 않았을 수 있다.
     * delay는 취소를 확인하는 중단 지점이다. 이런 지점이나 ensureActive가 없는 CPU 작업은 취소에 바로 반응하지 않는다.
     * 이 테스트는 delay에서 멈춰 있는 코루틴만 다룬다.
     * cancelledBeforeJoin은 취소 요청, completedBeforeJoin은 finally까지 끝났는지 나타내며 eventsBeforeJoin은 join 전의 복사본이다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-cancellation.html#cancellation-is-cooperative)
     * 관찰 지점: cancel 직후의 Job 상태와 events를 join 후와 비교하라.
     * 취소됨과 완료됨이 같은 순간을 뜻하지 않는 이유는 무엇일까?
     */
    @Test
    fun cancelRequestAndCompletionAreDifferent() =
        runTest {
            val events = mutableListOf<String>()
            val job =
                launch {
                    try {
                        events += "started"
                        delay(1_000)
                        events += "finished"
                    } finally {
                        events += "finally"
                    }
                }
            runCurrent()
            job.cancel()
            val cancelledBeforeJoin = job.isCancelled
            val completedBeforeJoin = job.isCompleted
            val eventsBeforeJoin = events.toList()
            job.join()

            // TODO(a): 취소 요청과 종료 확인의 차이를 기대값에 반영하자.
            assertThat(cancelledBeforeJoin).isFalse()
            assertThat(completedBeforeJoin).isTrue()
            assertThat(eventsBeforeJoin).containsExactly("")
            assertThat(job.isCompleted).isTrue()
            assertThat(events).containsExactly("")
        }
}
