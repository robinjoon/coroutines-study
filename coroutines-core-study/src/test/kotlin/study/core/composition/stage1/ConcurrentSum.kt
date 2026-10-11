package study.core.composition.stage1

suspend fun concurrentSum(
    first: suspend () -> Int,
    second: suspend () -> Int,
): Int {
    // TODO(b): 같은 scope에서 두 async를 시작한 뒤 await한 결과를 더한다.
    TODO("독립적인 두 작업을 동시에 시작하고 결과를 합치세요")
}
