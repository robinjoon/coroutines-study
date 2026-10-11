package study.core.channels.stage1

import kotlinx.coroutines.channels.ReceiveChannel

suspend fun consumeProduced(
    values: Iterable<Int>,
    events: MutableList<String>,
    consume: suspend (ReceiveChannel<Int>) -> List<Int>,
): List<Int> {
    // TODO(b): 이 함수가 채널과 생산자의 주인이다. 스코프 안에 rendezvous 채널과 생산자를 만들고 consume(채널)의 결과를 반환한다.
    // - 기록: 시작할 때 "producer:start", 값을 보낸 뒤 "sent:<값>", 정상·취소 모두 finally에서 "producer:finally".
    // - 정상 수집: 값을 다 보내면 채널을 close해 소비자에게 더 이상 값이 없음을 알린다.
    // - 조기 종료·소비 실패·timeout: 생산자를 취소하고 완료를 기다리며, 채널도 닫거나 취소한다.
    // close()는 이미 중단된 send를 취소하지 않으므로 소비가 먼저 끝나면 생산자 Job도 취소해야 한다.
    // 소비자의 실패와 취소 신호는 정상 결과로 바꾸지 말고 호출자에게 전파한다.
    TODO("소비자가 끝나면 생산자도 정리되는 채널 소유 관계를 구현하세요")
}
