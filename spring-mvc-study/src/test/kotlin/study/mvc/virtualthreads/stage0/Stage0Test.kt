package study.mvc.virtualthreads.stage0

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.util.concurrent.Executors

/**
 * JDK 21부터 가상 스레드(virtual thread)가 정식 기능이 되었다. Spring Boot는 설정 한 줄로 Tomcat 요청 스레드를 가상 스레드로 바꿀 수 있다.
 * 가상 스레드는 블로킹하는 동안 실제 OS 스레드(carrier)를 내려놓는다. 그래서 기존의 블로킹 JDBC 코드와 ThreadLocal 기반 트랜잭션을 그대로 쓰면서
 * 많은 동시 요청을 받을 수 있다. 코루틴과는 다른 방식으로 같은 문제(기다리는 동안 스레드 낭비)를 푸는 셈이다.
 *
 [* - Spring Boot 가상 스레드](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)
 * - [JEP 444 Virtual Threads](https://openjdk.org/jeps/444)
 */
@SpringBootTest(
    classes = [Stage0Test.Config::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.threads.virtual.enabled=false",
        // "spring.threads.virtual.enabled=true",
    ],
)
class Stage0Test {
    @LocalServerPort
    private var port: Int = 0

    /**
     * 실제 서버를 띄워서 suspend 핸들러가 시작된 요청 스레드와, 코루틴이 직접 고른 디스패처의 스레드가 가상 스레드인지 확인한다.
     *
     * 1. 먼저 기본 상태(가상 스레드 꺼짐)에서 기대값을 관찰한 결과로 고친다.
     * 2. 위 properties의 `// "spring.threads.virtual.enabled=true",` 줄의 주석을 해제한다(뒤에 오는 값이 이긴다). 다시 실행해서 무엇이 바뀌는지 관찰한다.
     * 3. 관찰이 끝나면 주석과 기대값을 1번 상태로 되돌린다.
     *
     * 이 테스트는 thread 이름이 아니라 `Thread.isVirtual()`로 판단한다. 성능 비교는 하지 않는다.
     *
     * 왜 그런 결과가 나왔을까? Boot의 가상 스레드 설정은 코루틴 디스패처까지 바꿀까?
     * 기존 블로킹 JDBC + @Transactional 코드가 대부분인 MVC 서비스라면 코루틴과 가상 스레드 중 무엇이 더 단순한 선택일까?
     */
    @Test
    fun virtualThreadSettingChangesRequestThreadNotExplicitDispatcher() {
        val report =
            RestClient
                .create("http://localhost:$port")
                .get()
                .uri("/thread-report")
                .retrieve()
                .body<ThreadReport>()!!

        // TODO(a): 관찰한 결과로 기대값을 고치자. 그다음 위 설명의 2번을 진행하자.
        assertThat(report.requestThreadVirtual).isTrue()
        assertThat(report.dispatcherThreadVirtual).isTrue()
    }

    data class ThreadReport(
        val requestThreadVirtual: Boolean,
        val dispatcherThreadVirtual: Boolean,
    )

    @RestController
    class ThreadReportController(
        private val platformDispatcher: ExecutorCoroutineDispatcher,
    ) {
        @GetMapping("/thread-report")
        suspend fun report(): ThreadReport {
            val requestThreadVirtual = Thread.currentThread().isVirtual
            val dispatcherThreadVirtual = withContext(platformDispatcher) { Thread.currentThread().isVirtual }
            return ThreadReport(requestThreadVirtual, dispatcherThreadVirtual)
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ThreadReportController::class)
    class Config {
        @Bean(destroyMethod = "close")
        fun platformDispatcher(): ExecutorCoroutineDispatcher =
            Executors.newSingleThreadExecutor { Thread(it, "platform-worker") }.asCoroutineDispatcher()
    }
}
