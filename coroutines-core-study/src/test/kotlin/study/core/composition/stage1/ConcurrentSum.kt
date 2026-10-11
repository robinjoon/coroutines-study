package study.core.composition.stage1

suspend fun concurrentSum(
    first: suspend () -> Int,
    second: suspend () -> Int,
): Int {
    // TODO(b): 두 작업을 각각 async로 시작한 뒤 await한 결과를 더한다.
    // async도 launch처럼 스코프 안에서만 부를 수 있다. basics stage1에서 suspend 함수 안에 스코프를 연 방법을 떠올려 보자.
    TODO("독립적인 두 작업을 동시에 시작하고 결과를 합치세요")
}
