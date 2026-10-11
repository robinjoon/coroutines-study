package study.core.context.stage1

import kotlinx.coroutines.CoroutineDispatcher

suspend fun <T> onDispatcher(
    dispatcher: CoroutineDispatcher,
    work: suspend () -> T,
): T {
    // TODO(b): 지정한 dispatcher에서 work를 실행하고 결과를 반환한다.
    TODO("선택한 블록만 다른 dispatcher에서 실행하세요")
}
