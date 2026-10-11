package study.core.sharedstate.stage1

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * Mutex는 임계구역에 한 번에 하나의 코루틴만 들어가게 한다.
     * 읽기와 쓰기 사이에 중단이 있어도 전체 구간을 보호하면 갱신이 사라지지 않는다.
     * 잠금은 취소나 실패 뒤에도 해제되어야 한다. Mutex는 재진입을 허용하지 않는다.
     * 잠금을 쥔 채 같은 잠금을 다시 얻거나 그 잠금을 기다리는 코루틴을 기다리면 서로를 기다리며 멈춘다.
     * [공식 문서](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html#mutual-exclusion)
     * 관찰 지점: 100번 증가의 결과, 동시 진입 상한, 중간 취소 뒤의 재진입을 확인하라.
     * 읽기와 쓰기 각각에만 잠금을 걸면 같은 보호 효과를 얻을 수 있을까?
     */
    @Test
    fun mutexProtectsWholeReadModifyWrite() =
        runTest {
            val counter = ProtectedCounter()
            coroutineScope {
                repeat(100) { launch { counter.increment() } }
            }

            assertThat(counter.value)
                .describedAs("읽기부터 쓰기까지 보호해 모든 증가가 반영되어야 한다")
                .isEqualTo(100)
            assertThat(counter.maxActive).describedAs("잠금 안에 동시에 들어간 코루틴 수의 최댓값").isEqualTo(1)
            assertThat(counter.active).describedAs("증가가 모두 끝나면 잠금 안에 남은 코루틴이 없어야 한다").isEqualTo(0)

            // 잠금 대기자가 즉시 재개되어도 이전 증가의 쓰기가 끝난 값을 읽어야 한다.
            val boundaryCounter = ProtectedCounter()
            // UNDISPATCHED: 대기열을 거치지 않고 시작해 첫 중단 지점까지 실행한다.
            val first = launch(start = CoroutineStart.UNDISPATCHED) { boundaryCounter.increment() }
            // UnconfinedTestDispatcher: 잠금 대기에서 깨어나면 대기열을 거치지 않고 바로 이어서 실행한다.
            val second = launch(UnconfinedTestDispatcher(testScheduler)) { boundaryCounter.increment() }
            first.join()
            second.join()
            assertThat(boundaryCounter.value)
                .describedAs("다음 잠금 대기자는 이전 증가의 쓰기가 끝난 값을 읽어야 한다")
                .isEqualTo(2)

            val cancelledCounter = ProtectedCounter()
            // UNDISPATCHED로 첫 yield까지 실행해 잠금 안에 들어간 자식을 바로 관찰한다.
            val child = launch(start = CoroutineStart.UNDISPATCHED) { cancelledCounter.increment() }
            assertThat(cancelledCounter.active).describedAs("yield에서 멈춘 자식이 잠금 안에 들어가 있어야 한다").isEqualTo(1)
            child.cancelAndJoin()
            assertThat(cancelledCounter.active).describedAs("취소된 뒤에도 finally에서 active가 줄어야 한다").isEqualTo(0)
            assertThat(cancelledCounter.value).isEqualTo(0)

            // 잠금을 반납하지 않은 구현도 가상 시간 안에 설명이 붙은 단언으로 실패하게 한다.
            val reentered =
                withTimeoutOrNull(100) {
                    cancelledCounter.increment()
                    true
                }
            assertThat(reentered)
                .describedAs("취소된 작업이 잠금을 반납해 다음 증가가 진행되어야 한다")
                .isEqualTo(true)
            assertThat(cancelledCounter.value).isEqualTo(1)
            assertThat(cancelledCounter.maxActive).describedAs("재진입해도 잠금 안에는 한 코루틴만 들어가야 한다").isEqualTo(1)
        }
}
