package study.core.context.stage1

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import kotlin.coroutines.ContinuationInterceptor

class Stage1Test {
    /**
     * withContext는 지정한 블록의 실행 context를 바꾸고 블록이 끝나면 호출자의 context로 돌아온다.
     * dispatcher 변경이 실제 thread 이동인지 확인하려고 이 테스트는 전용 단일 thread 실행기를 만든다.
     * 테스트가 만든 실행기는 성공이나 실패와 관계없이 use에서 닫는다.
     * [공식 문서](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#jumping-between-threads)
     * 관찰 지점: 블록 안팎의 thread ID와 dispatcher 객체, 실패 후 복귀와 대기 중 호출자의 진행을 확인하라.
     * 선택한 블록이 끝난 뒤 호출자의 thread에서 다시 실행되는 이유는 무엇일까?
     */
    @Test
    fun withContextMovesOnlySelectedBlock() =
        runTest {
            val callerThread = Thread.currentThread().threadId()
            val callerDispatcher = coroutineContext[ContinuationInterceptor]
            val events = mutableListOf("caller:before")
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
                // 1) 블록 안은 worker에서 실행되고, 끝나면 호출자로 복귀한다.
                val workerThread =
                    onDispatcher(worker) {
                        val firstThread = Thread.currentThread().threadId()
                        assertThat(currentCoroutineContext()[ContinuationInterceptor]).isSameAs(worker)
                        yield()
                        assertThat(Thread.currentThread().threadId())
                            .describedAs("단일 worker에서 시작하고 yield 뒤에도 같은 worker 스레드여야 한다")
                            .isEqualTo(firstThread)
                        firstThread
                    }
                events += "caller:after"

                assertThat(workerThread).isNotEqualTo(callerThread)
                assertThat(Thread.currentThread().threadId()).isEqualTo(callerThread)
                assertThat(currentCoroutineContext()[ContinuationInterceptor]).isSameAs(callerDispatcher)

                assertThat(events).containsExactly("caller:before", "caller:after")

                // 2) worker에서 기다리는 동안 호출자는 다른 코루틴을 실행할 수 있다.
                // CompletableDeferred는 코루틴 없이 complete할 수 있는 Deferred이며, 여기서는 sibling의 실행 신호다.
                val callerContinued = CompletableDeferred<Unit>()
                val sibling =
                    launch {
                        events += "caller:continued"
                        callerContinued.complete(Unit)
                    }
                // worker에서 신호를 기다리는 실제 시간은 5초로 제한한다.
                val progressed =
                    onDispatcher(worker) {
                        withTimeoutOrNull(5_000) {
                            callerContinued.await()
                            true
                        }
                    }
                assertThat(progressed)
                    .describedAs("worker가 기다리는 동안 호출자의 다른 코루틴도 진행돼야 한다. 호출자 스레드를 막고 있지 않은지 확인하자")
                    .isTrue()
                sibling.join()
                assertThat(events).contains("caller:continued")

                // 3) 블록 안의 실패도 호출자에게 전달되고 호출자 context로 복귀한다.
                val failure = runCatching { onDispatcher(worker) { error("worker failed") } }.exceptionOrNull()
                assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("worker failed")
                assertThat(Thread.currentThread().threadId()).isEqualTo(callerThread)
                assertThat(currentCoroutineContext()[ContinuationInterceptor]).isSameAs(callerDispatcher)
            }
        }
}
