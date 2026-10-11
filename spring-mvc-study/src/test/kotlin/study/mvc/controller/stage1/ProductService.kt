package study.mvc.controller.stage1

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Service

data class ProductResponse(
    val id: Long,
    val name: String,
)

data class ErrorResponse(
    val code: String,
    val message: String,
)

class ProductNotFoundException(
    id: Long,
) : RuntimeException("product $id not found")

/**
 * 외부 저장소에서 상품을 조회하는 suspend 서비스. 완성되어 있으니 수정하지 않는다.
 *
 * 있는 상품은 테스트가 [respond]를 호출해 "외부 저장소의 응답"을 보내 줄 때까지(최대 2초) 기다린다.
 * 테스트는 요청 스레드가 컨트롤러에서 빠져나온 "뒤에" 응답을 보낸다.
 */
@Service
class ProductService {
    private val products = mapOf(1L to "keyboard", 2L to "mouse")
    private val storageResponse = CompletableDeferred<Unit>()

    fun respond() {
        storageResponse.complete(Unit)
    }

    suspend fun find(id: Long): ProductResponse {
        val name = products[id]
        if (name == null) {
            delay(50)
            throw ProductNotFoundException(id)
        }
        withTimeout(2_000) { storageResponse.await() } // 외부 저장소 응답을 기다린다.
        return ProductResponse(id, name)
    }
}
