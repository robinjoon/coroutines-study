package study.mvc.controller.stage1

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * suspend 서비스를 컨트롤러에서 호출하고, 실패를 @RestControllerAdvice로 응답하게 만들어 보자.
 * 이 테스트 코드는 수정하지 않는다. ProductController와 ProductExceptionHandler의 TODO를 구현해서 통과시키자.
 *
 [* - Spring 코루틴 지원](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
 * - [예외 처리](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)
 */
@SpringBootTest(classes = [Stage1Test.Config::class])
@AutoConfigureMockMvc
class Stage1Test {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var productService: ProductService

    /**
     * 컨트롤러가 suspend 서비스를 호출하는 방법은 두 가지를 떠올릴 수 있다.
     * 1. 일반 함수 안에서 runBlocking { productService.find(id) }
     * 2. 핸들러 자체를 suspend 함수로 선언
     *
     * 이 테스트는 요청이 "비동기로 시작되었는지", 그리고 요청 스레드가 서비스 응답을 기다리며 붙잡혀 있지 않은지까지 검사한다.
     * 서비스는 테스트가 응답을 보내 줄 때까지 기다리는데, 테스트는 요청 스레드가 돌아와야 응답을 보낼 수 있다.
     * 1번 방식(suspend 함수 안에서 runBlocking을 쓰는 것도 포함)으로는 왜 통과하지 못할까?
     * runBlocking은 코루틴이 끝날 때까지 호출한 스레드(Tomcat 요청 스레드)를 붙잡는다.
     * 동시 요청이 많아지면 요청 스레드 풀이 금방 바닥난다.
     *
     * 왜 그런 결과가 나왔을까? 요청 스레드가 붙잡히지 않는다는 것은 정확히 무엇을 의미할까?
     */
    @Test
    fun asyncDispatchProducesServiceResponse() {
        val first = mockMvc.perform(get("/products/1"))
            .andExpect(request().asyncStarted())
            .andReturn()
        // 요청 스레드가 돌아온 뒤에 외부 저장소의 응답이 도착한다.
        productService.respond()

        mockMvc.perform(asyncDispatch(first))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":1,"name":"keyboard"}"""))
    }

    /**
     * suspend 핸들러에서 던진 예외는 언제, 어디로 전달될까?
     * 코루틴이 실패하면 그 예외가 비동기 결과가 된다. 두 번째 디스패치에서 MVC가 이 예외를 다시 처리하면서
     * @ExceptionHandler가 동작한다. 그래서 첫 perform에서는 아직 상태 코드를 알 수 없다.
     *
     * 왜 그런 결과가 나왔을까? 첫 번째 perform의 응답 상태 코드는 무엇이었을까?
     */
    @Test
    fun controllerFailureReachesAdviceAfterDispatch() {
        val first = mockMvc.perform(get("/products/999"))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(first))
            .andExpect(status().isNotFound)
            .andExpect(content().json("""{"code":"PRODUCT_NOT_FOUND","message":"product 999 not found"}"""))
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProductController::class, ProductService::class, ProductExceptionHandler::class)
    class Config
}
