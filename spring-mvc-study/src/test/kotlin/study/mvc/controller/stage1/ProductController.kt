package study.mvc.controller.stage1

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ProductController(private val productService: ProductService) {

    /**
     * ProductService.find는 suspend 함수라서 일반 함수에서는 바로 호출할 수 없다.
     * runBlocking으로 감싸면 컴파일은 되지만 요청 스레드가 응답이 올 때까지 붙잡힌다.
     */
    @GetMapping("/products/{id}")
    fun find(@PathVariable id: Long): ProductResponse {
        // TODO(b): runBlocking 없이 productService.find(id)를 호출해 결과를 반환하도록 바꾸자. 함수 선언부도 바꿀 수 있다.
        TODO("productService.find(id)를 호출하도록 구현하세요")
    }
}
