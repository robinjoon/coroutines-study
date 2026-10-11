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
    fun ownedProducerClosesAndCancelsWithConsumer() =
        runTest {
            val events = mutableListOf<String>()
            var channel: ReceiveChannel<Int>? = null
            // 1. 정상 수집: close가 빠지면 소비가 끝나지 않으므로 가상 시간으로 기다림을 제한한다.
            val all =
                withTimeoutOrNull(100) {
                    consumeProduced(listOf(1, 2, 3), events) { input ->
                        channel = input
                        input.toList()
                    }
                }
            assertThat(all)
                .describedAs("정상 생산이 끝나면 채널을 닫아 소비와 consumeProduced가 반환해야 한다")
                .containsExactly(1, 2, 3)
            // tryReceive는 기다리지 않고 지금 채널 상태만 확인한다. 닫힌 채널이면 isClosed가 true다.
            assertThat(channel!!.tryReceive().isClosed)
                .describedAs("값을 다 보낸 생산자는 채널을 닫아야 한다")
                .isTrue()
            assertThat(events).containsExactly("producer:start", "sent:1", "sent:2", "sent:3", "producer:finally")

            events.clear()
            // 2. 조기 종료: 전송을 기다리는 생산자를 남겨 두면 이 호출은 반환하지 못한다.
            val firstOnly =
                withTimeoutOrNull(100) {
                    consumeProduced(listOf(1, 2, 3), events) { input ->
                        channel = input
                        listOf(input.receive())
                    }
                }
            assertThat(firstOnly)
                .describedAs("소비자가 먼저 끝나면 전송 대기 중인 생산자를 정리하고 반환해야 한다")
                .containsExactly(1)
            assertThat(channel!!.tryReceive().isClosed)
                .describedAs("소비가 끝난 뒤에는 채널도 닫히거나 취소되어야 한다")
                .isTrue()
            assertThat(events)
                .describedAs("소비가 끝나면 나머지 값을 보내지 않고 생산자를 정리해야 한다")
                .contains("producer:start", "producer:finally")
                .doesNotContain("sent:2", "sent:3")

            events.clear()
            // 3. 소비 실패: 예외를 호출자에게 전달하고 생산자를 정리한다.
            val failure =
                runCatching {
                    consumeProduced(listOf(1, 2, 3), events) { input ->
                        input.receive()
                        error("consumer failed")
                    }
                }.exceptionOrNull()
            assertThat(failure)
                .describedAs("소비자의 실패를 정상 결과로 바꾸지 않고 호출자에게 전달해야 한다")
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("consumer failed")
            assertThat(events).contains("producer:finally")

            events.clear()
            // 4. 부모 timeout: 소비 중 부모가 취소되어도 생산자의 finally가 실행된다.
            val timedOut =
                withTimeoutOrNull(100) {
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
