package study.core.channels.stage1

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * Channel을 만든 쪽은 생산자의 수명과 채널의 종료를 함께 책임져야 한다.
     * 정상 생산이 끝나면 close로 더 이상 값이 없음을 알리고 소비자가 일찍 끝나면 생산자를 취소한다.
     * 소비자가 실패하거나 부모가 취소될 때도 같은 정리 계약이 필요하다.
     * [공식 문서](https://kotlinlang.org/docs/channels.html#closing-and-iteration-over-channels)
     * 관찰 지점: 정상 수집의 끝, 조기 종료·소비 실패·timeout 뒤 생산자 finally를 확인하라.
     * 소비가 끝난 뒤 보내는 생산자를 남겨 두면 어떤 문제가 생길까?
     */
    @Test
    fun ownedProducerClosesAndCancelsWithConsumer() = runTest {
        val events = mutableListOf<String>()
        var channel: ReceiveChannel<Int>? = null
        val all = consumeProduced(listOf(1, 2, 3), events) { input ->
            channel = input
            input.toList()
        }
        assertThat(all).containsExactly(1, 2, 3)
        assertThat(channel!!.receiveCatching().isClosed).isTrue()
        assertThat(events).containsExactly("producer:start", "sent:1", "sent:2", "sent:3", "producer:finally")

        events.clear()
        val firstOnly = consumeProduced(listOf(1, 2, 3), events) { input ->
            listOf(input.receive())
        }
        assertThat(firstOnly).containsExactly(1)
        assertThat(events).contains("producer:start", "producer:finally").doesNotContain("sent:2", "sent:3")

        events.clear()
        val failure = runCatching {
            consumeProduced(listOf(1, 2, 3), events) { input ->
                input.receive()
                error("consumer failed")
            }
        }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("consumer failed")
        assertThat(events).contains("producer:finally")

        events.clear()
        val timedOut = withTimeoutOrNull(100) {
            consumeProduced(listOf(1, 2, 3), events) { input ->
                input.receive()
                delay(1_000)
                input.toList()
            }
        }
        assertThat(timedOut).isNull()
        assertThat(events).contains("producer:finally")
    }
}
