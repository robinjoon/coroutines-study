package study.mvc.controller.stage0

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * Spring MVC에서 suspend 컨트롤러는 어떻게 실행될까?
 *
 * Spring은 suspend 핸들러를 호출할 때 코루틴을 Reactor `Mono`로 감싼다.
 * MVC는 `Mono` 같은 비동기 반환값을 받으면 Servlet 비동기 처리(asynchronous request processing)를 시작한다.
 * 요청 스레드는 결과를 기다리지 않고 컨테이너로 돌아가고, 코루틴이 끝나면 요청이 한 번 더 디스패치되어 응답이 쓰인다.
 *
 [* - Spring 코루틴 지원](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
 * - [MVC 비동기 요청](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
 * - [MockMvc 비동기 테스트](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html)
 */
@SpringBootTest(classes = [Stage0Test.Config::class])
@AutoConfigureMockMvc
class Stage0Test {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var recorder: ThreadRecorder

    /**
     * MockMvc의 첫 번째 perform은 "비동기 처리가 시작된 시점"까지만 실행한다.
     * 최종 응답은 asyncDispatch로 두 번째 디스패치를 직접 실행해야 볼 수 있다.
     *
     * 관찰 지점
     * 1. 첫 perform 직후 요청은 비동기 상태인가? 응답 본문에는 무엇이 들어 있는가?
     * 2. asyncDispatch 이후 응답 본문은 어떻게 바뀌는가?
     * 3. ProductController에서 delay 전후의 스레드는 같은가?
     *    suspend 핸들러는 `Dispatchers.Unconfined`로 시작한다. Unconfined 코루틴은 중단된 뒤 "자신을 깨운 쪽"의 스레드에서 이어진다.
     *    그 스레드가 어떤 스레드인지 디버거나 로그로 확인해 보자.
     *
     * 왜 그런 결과가 나왔을까? 재개된 스레드에서 오래 걸리는 블로킹 작업을 하면 어떤 일이 생길까?
     */
    @Test
    fun suspendEndpointStartsServletAsync() {
        val first = mockMvc.perform(get("/products/1")).andReturn()

        // TODO(a): 첫 번째 디스패치 직후의 상태를 관찰하고 기대값을 고치자.
        assertThat(first.request.isAsyncStarted).isFalse()
        assertThat(first.response.contentAsString).isEqualTo("""{"id":1,"name":"keyboard"}""")

        val dispatched = mockMvc.perform(asyncDispatch(first)).andReturn()

        // TODO(a): asyncDispatch 이후의 응답 본문을 관찰하고 기대값을 고치자.
        assertThat(dispatched.response.contentAsString).isEqualTo("")

        // TODO(a): delay 전후의 스레드가 같은지 관찰하고 기대값을 고치자.
        assertThat(recorder.resumedThread).isSameAs(recorder.requestThread)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProductController::class)
    class Config {
        @Bean
        fun threadRecorder() = ThreadRecorder()
    }
}
