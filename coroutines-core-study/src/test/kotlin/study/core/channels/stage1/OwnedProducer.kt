package study.core.channels.stage1

import kotlinx.coroutines.channels.ReceiveChannel

suspend fun consumeProduced(
    values: Iterable<Int>,
    events: MutableList<String>,
    consume: suspend (ReceiveChannel<Int>) -> List<Int>,
): List<Int> {
    // TODO(b): 같은 scope에 rendezvous 채널과 생산자를 만들고 consume의 결과를 반환한다.
    // 생산자는 "producer:start", 보낸 뒤 "sent:<값>", finally에서 "producer:finally"를 기록한다.
    // 생산 종료 시 close하고, 소비가 끝나거나 실패하면 생산자를 취소한 뒤 완료를 기다린다.
    TODO("소비자가 끝나면 생산자도 정리되는 채널 소유 관계를 구현하세요")
}
