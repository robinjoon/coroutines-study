package study.mvc.lifecycle.stage1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.springframework.beans.factory.DisposableBean
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/**
 * 요청이 끝난 뒤에도 계속되어야 하는 작업(예: 알림 발송)을 실행하는 애플리케이션 수명의 코루틴 스코프.
 * 스프링 빈으로 등록되고, 애플리케이션 컨텍스트가 닫힐 때 함께 정리되어야 한다.
 */
class ApplicationCoroutineScope : CoroutineScope, DisposableBean {

    /** 처리되지 않은 예외로 실패한 작업의 예외를 기록한다. */
    val failures = CopyOnWriteArrayList<Throwable>()

    override val coroutineContext: CoroutineContext
        // TODO(b): 다음 조건을 만족하는 컨텍스트를 만들자. 매번 새로 만들지 말고 한 번 만든 컨텍스트를 재사용해야 한다.
        //  - 작업 하나가 실패해도 다른 작업은 계속 실행된다.
        //  - 처리되지 않은 예외는 failures에 기록된다.
        //  - 블로킹하지 않는 작업이므로 Dispatchers.Default에서 실행한다.
        get() = TODO("애플리케이션 스코프의 컨텍스트를 구현하세요")

    override fun destroy() {
        // TODO(b): 컨텍스트가 닫힐 때 이 스코프에서 실행 중인 모든 작업을 취소하자.
        TODO("스코프의 작업을 취소하도록 구현하세요")
    }
}

/**
 * 알림을 비동기로 보낸다. 완성되어 있으니 수정하지 않는다.
 */
class NotificationSender(private val scope: ApplicationCoroutineScope) {
    fun sendLater(action: suspend () -> Unit): Job = scope.launch { action() }
}
