package study.mvc.lifecycle.stage1

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 요청과 상관없이 오래 사는 작업은 어디서 실행해야 할까?
 *
 * - `GlobalScope.launch`: 아무도 이 작업을 소유하지 않는다. 스프링 컨텍스트가 닫혀도 자동으로 취소되지 않고, 실패는 기본 예외 처리기(로그)로만 흘러가서 애플리케이션이 정한 방식으로 수집·처리하기 어렵다.
 * - 요청 처리 코루틴 안의 `launch`: 요청이 끝날 때까지 응답이 기다리거나, 요청이 취소되면 함께 취소된다.
 * - 애플리케이션 수명의 스코프 빈: 실패를 격리하고 기록하며, 컨텍스트가 닫힐 때 함께 정리된다.
 *
 * 이 테스트 코드는 수정하지 않는다. ApplicationCoroutineScope의 TODO를 구현해서 통과시키자.
 *
 * - [공식 가이드(Coroutine scope)](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#coroutine-scope)
 * - [공식 가이드(Supervision)](https://kotlinlang.org/docs/exception-handling.html#supervision)
 * - [공식 가이드(CoroutineExceptionHandler)](https://kotlinlang.org/docs/exception-handling.html#coroutineexceptionhandler)
 */
class Stage1Test {
    /**
     * 알림 두 개를 보낸다. 하나는 곧바로 실패하고, 하나는 오래 걸린다.
     * - 실패는 failures에 한 번 기록되어야 한다.
     * - 실패한 작업 때문에 다른 작업이 취소되면 안 된다.
     * - 컨텍스트가 닫히면 남은 작업은 취소되고 finally 블록이 실행되어야 한다.
     *
     * 이 스코프는 메시지 큐가 아니다. 서버 프로세스가 죽으면 진행 중이던 작업은 사라진다.
     * "반드시" 보내야 하는 메시지라면 DB나 메시지 큐에 먼저 저장해야 한다.
     *
     * 왜 그런 결과가 나왔을까? SupervisorJob 대신 Job()을 쓰면 오래 걸리는 알림은 어떻게 될까?
     */
    @Test
    fun applicationScopeSupervisesAndClosesOwnedJobs() {
        val context = AnnotationConfigApplicationContext(Config::class.java)
        val events = CopyOnWriteArrayList<String>()
        try {
            val scope = context.getBean(ApplicationCoroutineScope::class.java)
            val sender = context.getBean(NotificationSender::class.java)
            val longRunningStarted = CompletableDeferred<Unit>()

            val failing = sender.sendLater { throw IllegalStateException("메일 서버 오류") }
            val longRunning =
                sender.sendLater {
                    try {
                        longRunningStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        events += "long-running finally"
                    }
                }
            runBlocking {
                withTimeout(5_000) {
                    failing.join()
                    longRunningStarted.await()
                }
            }

            assertThat(scope.failures).singleElement().isInstanceOf(IllegalStateException::class.java)
            assertThat(longRunning.isActive).isTrue()

            context.close()
            runBlocking { withTimeout(5_000) { longRunning.join() } }

            assertThat(longRunning.isCancelled).isTrue()
            assertThat(events).containsExactly("long-running finally")
        } finally {
            context.close()
        }
    }

    @Configuration
    class Config {
        @Bean
        fun applicationCoroutineScope() = ApplicationCoroutineScope()

        @Bean
        fun notificationSender(scope: ApplicationCoroutineScope) = NotificationSender(scope)
    }
}
