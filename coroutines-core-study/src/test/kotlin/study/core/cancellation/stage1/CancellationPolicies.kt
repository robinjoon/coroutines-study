package study.core.cancellation.stage1

import kotlinx.coroutines.CoroutineScope

suspend fun finishWithin(
    timeoutMillis: Long,
    work: suspend CoroutineScope.() -> Unit,
): Boolean {
    // TODO(b): 시간 안에 자식까지 완료하면 true, timeout이면 false를 반환한다. 일반 실패는 전파한다.
    TODO("시간 예산 안에서 작업과 자식의 완료를 기다리세요")
}

suspend fun loadOrFallback(
    work: suspend () -> String,
    fallback: () -> String,
): String {
    // TODO(b): 일반 Exception에는 fallback을 쓰고 CancellationException은 다시 던진다.
    TODO("실패와 취소를 구분하세요")
}
