package study.core.exceptions.stage1

suspend fun supervisedResults(
    first: suspend () -> String,
    second: suspend () -> String,
    onFailure: (Exception) -> Unit,
): List<String> {
    // TODO(b): supervisorScope에서 두 async를 시작하고 각 일반 실패를 onFailure에 보고한다.
    // 성공 결과는 입력 순서로 반환하며 CancellationException은 다시 던진다.
    TODO("형제 작업의 일반 실패를 격리하되 부모 취소는 전파하세요")
}
