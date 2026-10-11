package study.core.context.stage1

import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
    fun withContextMovesOnlySelectedBlock() = runTest {
        val callerThread = Thread.currentThread().threadId()
        val callerDispatcher = coroutineContext[ContinuationInterceptor]
        val events = mutableListOf("caller:before")
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
            val workerThread = onDispatcher(worker) {
                val firstThread = Thread.currentThread().threadId()
                assertThat(currentCoroutineContext()[ContinuationInterceptor]).isSameAs(worker)
                yield()
                assertThat(Thread.currentThread().threadId()).isEqualTo(firstThread)
                firstThread
            }
            events += "caller:after"

            assertThat(workerThread).isNotEqualTo(callerThread)
            assertThat(Thread.currentThread().threadId()).isEqualTo(callerThread)
            assertThat(currentCoroutineContext()[ContinuationInterceptor]).isSameAs(callerDispatcher)

            assertThat(events).containsExactly("caller:before", "caller:after")

            val callerContinued = CompletableDeferred<Unit>()
            val sibling = launch {
                events += "caller:continued"
                callerContinued.complete(Unit)
            }
            // worker에서 기다려도 호출자의 다른 작업은 진행돼야 한다. 실제 thread 대기는 5초로 제한한다.
            onDispatcher(worker) { withTimeout(5_000) { callerContinued.await() } }
            sibling.join()
            assertThat(events).contains("caller:continued")

            val failure = runCatching { onDispatcher(worker) { error("worker failed") } }.exceptionOrNull()
            assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("worker failed")
            assertThat(Thread.currentThread().threadId()).isEqualTo(callerThread)
            assertThat(currentCoroutineContext()[ContinuationInterceptor]).isSameAs(callerDispatcher)
        }
    }
}
