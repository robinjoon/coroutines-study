package study.mvc.contextpropagation.stage1

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 요청 ID를 MDC에 담아 worker 디스패처에서 작업을 실행한다.
 */
class RequestContextRunner(
    private val worker: CoroutineDispatcher,
) {
    suspend fun <T> runWithRequestId(
        requestId: String,
        block: suspend () -> T,
    ): T {
        // TODO(b): block을 worker 디스패처에서 실행하자. block 안에서는 중단(delay) 전후 모두 MDC.get("requestId") == requestId 여야 한다.
        //  작업이 끝난 뒤 worker 스레드의 MDC는 원래 값으로 돌아가야 한다.
        //  힌트: kotlinx-coroutines-slf4j 의 MDCContext
        TODO("요청 ID를 전파하도록 구현하세요")
    }
}
