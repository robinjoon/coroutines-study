package study.core.cancellation.stage1

import kotlinx.coroutines.CoroutineScope

suspend fun finishWithin(
    timeoutMillis: Long,
    work: suspend CoroutineScope.() -> Unit,
): Boolean {
    // TODO(b): 시간 안에 자식까지 완료하면 true, 이 함수가 건 시간 제한이 만료된 경우만 false를 반환한다.
    // work의 실패·취소·별도 timeout은 그대로 전파한다. withTimeout과 withTimeoutOrNull의 차이는 docs/concepts.md를 참고하자.
    // work는 CoroutineScope를 리시버로 받는 람다다. 리시버가 되는 스코프 안에서 호출하자.
    TODO("시간 제한 안에서 작업과 자식의 완료를 기다리세요")
}

suspend fun loadOrFallback(
    work: suspend () -> String,
    fallback: () -> String,
): String {
    // TODO(b): 일반 Exception에는 fallback을 쓰고 CancellationException은 다시 던진다.
    // work가 값을 반환하면(빈 문자열이어도) 성공이다. fallback 자체의 실패는 호출자에게 전달한다.
    TODO("실패와 취소를 구분하세요")
}
