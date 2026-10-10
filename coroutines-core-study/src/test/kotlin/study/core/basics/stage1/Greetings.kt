package study.core.basics.stage1

import kotlinx.coroutines.Job

suspend fun greetWithChildren(events: MutableList<String>) {
    // TODO(b): 같은 scope의 두 자식이 각각 100 ms, 200 ms 뒤 완료하도록 만들자.
    // 부모 본문에서는 "parent:body-end", 자식에서는 "first:end", "second:end"를 기록한다.
    TODO("자식이 모두 끝난 뒤 반환하는 인사 함수를 구현하세요")
}

suspend fun waitForJob(job: Job) {
    // TODO(b): Job 완료를 기다리되 결과값을 만들지 않는다.
    TODO("Job 완료를 기다리세요")
}
