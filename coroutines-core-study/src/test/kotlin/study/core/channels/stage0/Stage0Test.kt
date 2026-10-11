package study.core.channels.stage0

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage0Test {
    /**
     * capacity가 0인 rendezvous Channel은 값을 저장할 버퍼가 없다.
     * send는 받을 쪽과 만날 때까지 중단되며 receive가 진행되면 완료할 수 있다.
     * [공식 문서](https://kotlinlang.org/docs/channels.html#channel-basics)
     * 관찰 지점: scheduler 실행 전, receive 전, receive 후의 send 완료 사건을 비교하라.
     * send를 실행했는데 send:end가 바로 기록되지 않은 이유는 무엇일까?
     */
    @Test
    fun sendWaitsForReceiveOnRendezvousChannel() = runTest {
        val events = mutableListOf<String>()
        val channel = Channel<Int>(Channel.RENDEZVOUS)
        val sender = launch {
            events += "send:start"
            channel.send(7)
            events += "send:end"
        }
        val beforeRunCurrent = events.toList()
        runCurrent()
        val beforeReceive = events.toList()
        val completedBeforeReceive = sender.isCompleted
        val received = channel.receive()
        runCurrent()
        sender.join()
        channel.close()

        assertThat(received).isEqualTo(7)
        // TODO(a): send의 시작과 완료를 구분해 세 시점의 기대값을 채워 보자.
        assertThat(beforeRunCurrent).containsExactly("")
        assertThat(beforeReceive).containsExactly("")
        assertThat(completedBeforeReceive).isTrue()
        assertThat(events).containsExactly("")
    }
}
