package study.core.sharedstate.stage1

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * Mutex는 임계구역에 한 번에 하나의 코루틴만 들어가게 한다.
     * 읽기와 쓰기 사이에 중단이 있어도 전체 구간을 보호하면 갱신이 사라지지 않는다.
     * 잠금은 취소나 실패 뒤에도 해제되어야 하며 임계구역에서 다른 잠금 대기자를 기다리면 안 된다.
     * [공식 문서](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html#mutual-exclusion)
     * 관찰 지점: 100번 증가의 결과, 동시 진입 상한, 중간 취소 뒤의 재진입을 확인하라.
     * 읽기와 쓰기 각각에만 잠금을 걸면 같은 보호 효과를 얻을 수 있을까?
     */
    @Test
    fun mutexProtectsWholeReadModifyWrite() = runTest {
        val counter = ProtectedCounter()
        coroutineScope {
            repeat(100) { launch { counter.increment() } }
        }

        assertThat(counter.value).isEqualTo(100)
        assertThat(counter.maxActive).isEqualTo(1)
        assertThat(counter.active).isEqualTo(0)

        val cancelledCounter = ProtectedCounter()
        val child = launch(start = CoroutineStart.UNDISPATCHED) { cancelledCounter.increment() }
        assertThat(cancelledCounter.active).isEqualTo(1)
        child.cancelAndJoin()
        assertThat(cancelledCounter.active).isEqualTo(0)
        assertThat(cancelledCounter.value).isEqualTo(0)

        cancelledCounter.increment()
        assertThat(cancelledCounter.value).isEqualTo(1)
        assertThat(cancelledCounter.maxActive).isEqualTo(1)
    }
}
