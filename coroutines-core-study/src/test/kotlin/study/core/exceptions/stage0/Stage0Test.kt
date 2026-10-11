package study.core.exceptions.stage0

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage0Test {
    /**
     * supervisor의 launch 자식은 실패를 다른 자식에게 전파하지 않는다.
     * 처리하지 않은 launch 예외는 CoroutineExceptionHandler에서 관찰할 수 있으며 handler가 작업을 복구하지는 않는다.
     * 이 테스트는 독립 SupervisorJob을 만든 뒤 finally에서 직접 정리한다.
     * [공식 문서](https://kotlinlang.org/docs/exception-handling.html#exceptions-in-supervised-coroutines)
     * 관찰 지점: 자식의 취소 상태, supervisor의 활성 상태, handler의 events를 확인하라.
     * 실패한 자식과 여전히 활성인 supervisor가 함께 존재하는 이유는 무엇일까?
     */
    @Test
    fun supervisedLaunchReportsUnhandledFailure() =
        runTest {
            val events = mutableListOf<String>()
            val supervisor = SupervisorJob()
            val handler = CoroutineExceptionHandler { _, failure -> events += "handler:${failure.message}" }
            val scope = CoroutineScope(supervisor + StandardTestDispatcher(testScheduler) + handler)
            try {
                val child = scope.launch { error("launch failed") }
                runCurrent()
                child.join()

                assertThat(child.isCancelled).isTrue()
                assertThat(supervisor.isActive).isTrue()
                // TODO(a): handler가 관찰한 사건을 기록에 맞춰 바꾸자.
                assertThat(events).containsExactly("")
            } finally {
                scope.cancel()
                supervisor.join()
            }
        }

    /**
     * supervisor의 async 자식은 실패를 Deferred에 보관하고 await에서 호출자에게 던진다.
     * await 전후의 handler에는 어떤 사건이 기록될까?
     * 일반 부모의 async 자식은 await 전에도 부모를 취소할 수 있으므로 이 결과를 모든 async에 일반화하지 않는다.
     * [공식 문서](https://kotlinlang.org/docs/exception-handling.html#exception-propagation)
     * 관찰 지점: await 전후의 Deferred 상태, 예외, handler의 events를 확인하라.
     * launch와 async가 실패를 관찰하게 하는 창구가 다른 이유는 무엇일까?
     */
    @Test
    fun supervisedAsyncExposesFailureAtAwait() =
        runTest {
            val events = mutableListOf<String>()
            val supervisor = SupervisorJob()
            val handler = CoroutineExceptionHandler { _, failure -> events += "handler:${failure.message}" }
            val scope = CoroutineScope(supervisor + StandardTestDispatcher(testScheduler) + handler)
            try {
                val child = scope.async<String> { error("async failed") }
                runCurrent()
                val beforeAwait = events.toList()
                val failure = runCatching { child.await() }.exceptionOrNull()
                events += "await:${failure?.message}"

                assertThat(child.isCancelled).isTrue()
                assertThat(supervisor.isActive).isTrue()
                assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("async failed")
                // TODO(a): await 전에는 누가 실패를 관찰했고 await 후에는 어떤 사건이 남았을까?
                assertThat(beforeAwait).containsExactly("")
                assertThat(events).containsExactly("")
            } finally {
                scope.cancel()
                supervisor.join()
            }
        }
}
