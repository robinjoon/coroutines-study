package study.core.sharedstate.stage1

class ProtectedCounter {
    var value = 0
        private set
    var active = 0
        private set
    var maxActive = 0
        private set

    suspend fun increment() {
        // TODO(b): Mutex로 읽기 → yield → 쓰기 전체를 보호한다.
        // 진입·퇴장 때 active를 갱신하고 maxActive에 최대 동시 진입 수를 기록한다.
        // 취소돼도 active 감소와 잠금 해제가 이루어져야 한다.
        TODO("중단 지점을 포함한 증가 연산 전체를 보호하세요")
    }
}
