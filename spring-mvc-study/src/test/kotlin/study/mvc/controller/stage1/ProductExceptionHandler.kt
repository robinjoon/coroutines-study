package study.mvc.controller.stage1

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleNotFound(e: ProductNotFoundException): ResponseEntity<ErrorResponse> {
        // TODO(b): 404 상태와 ErrorResponse(code = "PRODUCT_NOT_FOUND", message = 예외 메시지)를 반환하자.
        TODO("404 응답을 만들도록 구현하세요")
    }
}
