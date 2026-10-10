package study.core.cancellation.stage2

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

class Stage2Test {
    /**
     * 취소된 코루틴의 finally도 실행되지만 그 안의 suspend 작업은 취소를 다시 확인한다.
     * NonCancellable을 제한된 정리 구간에 사용하면 취소 요청 뒤에도 그 구간을 완료할 수 있다.
     * 정리가 무한히 기다리지 않도록 이 예제는 50 ms의 유한한 작업만 사용한다.
     * 공식 문서: https://kotlinlang.org/docs/coroutines-cancellation.html#run-non-cancellable-block
     * 관찰 지점: cleanup:end 기록 여부와 cancelAndJoin 후의 currentTime을 비교하라.
     * 기대값을 맞춘 뒤 `cleanupContext = NonCancellable` 줄의 주석을 해제하고 다시 실행해 무엇이 달라지는지 확인하라.
     * 복원 방법: 해당 줄을 다시 주석 처리하고 기본 상태의 기대값으로 돌린다.
     * 같은 finally 안인데도 suspend 정리의 완료 여부가 달라지는 이유는 무엇일까?
     */
    @Test
    fun suspendingCleanupNeedsExplicitPolicy() = runTest {
        val events = mutableListOf<String>()
        val job = launch {
            try {
                events += "work:start"
                delay(1_000)
            } finally {
                events += "cleanup:start"
                var cleanupContext: CoroutineContext = coroutineContext
                // cleanupContext = NonCancellable
                withContext(cleanupContext) {
                    delay(50)
                    events += "cleanup:end"
                }
            }
        }
        runCurrent()
        job.cancelAndJoin()

        // TODO(a): 먼저 주석을 유지한 상태의 사건과 가상 시간에 맞게 기대값을 바꾸자.
        assertThat(events).containsExactly("")
        assertThat(currentTime).isEqualTo(-1L)
        assertThat(job.isCancelled).isTrue()
        assertThat(job.isCompleted).isTrue()
    }
}
