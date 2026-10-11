package study.mvc.controller.stage0

import kotlinx.coroutines.delay
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

data class ProductResponse(
    val id: Long,
    val name: String,
)

/**
 * suspend 핸들러가 어느 스레드에서 시작하고, 중단(delay) 이후 어느 스레드에서 이어지는지 기록한다.
 * 이 클래스는 완성되어 있다. 수정하지 말고 관찰만 하자.
 */
class ThreadRecorder {
    @Volatile
    var requestThread: Thread? = null

    @Volatile
    var resumedThread: Thread? = null
}

@RestController
class ProductController(
    private val recorder: ThreadRecorder,
) {
    @GetMapping("/products/{id}")
    suspend fun find(
        @PathVariable id: Long,
    ): ProductResponse {
        recorder.requestThread = Thread.currentThread()
        delay(100) // 외부 저장소를 기다리는 동안 코루틴이 중단된다.
        recorder.resumedThread = Thread.currentThread()
        return ProductResponse(id, "keyboard")
    }
}
