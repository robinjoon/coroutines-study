package study.mvc.transactions.stage1

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 결제 승인 API(외부 호출). 완성되어 있으니 수정하지 않는다.
 * 호출될 때 트랜잭션이 진행 중이었는지 기록한다.
 */
class PaymentClient {
    val transactionActiveDuringCall = CopyOnWriteArrayList<Boolean>()

    suspend fun approve(item: String): String {
        transactionActiveDuringCall += TransactionSynchronizationManager.isActualTransactionActive()
        delay(50) // 외부 API 응답을 기다린다.
        return "payment-$item"
    }
}

/**
 * 주문과 주문 이력을 하나의 JDBC 트랜잭션으로 저장한다. suspend가 아닌 평범한 블로킹 메서드다.
 * 완성되어 있으니 수정하지 않는다.
 */
@Service
class OrderTxService(
    private val jdbcTemplate: JdbcTemplate,
) {
    val observations = CopyOnWriteArrayList<String>()

    /** 테스트가 설정하면 저장을 시작하기 직전에 호출된다. */
    @Volatile
    var beforeSave: (() -> Unit)? = null

    @Transactional
    fun saveOrderWithHistory(
        item: String,
        paymentId: String,
        failHistory: Boolean,
    ) {
        beforeSave?.invoke()
        observations += "active=${TransactionSynchronizationManager.isActualTransactionActive()} thread=${Thread.currentThread().name}"
        jdbcTemplate.update("insert into orders(item, payment_id) values (?, ?)", item, paymentId)
        if (failHistory) {
            throw IllegalStateException("이력 저장 실패")
        }
        jdbcTemplate.update("insert into order_history(item) values (?)", item)
    }
}

/**
 * 주문 유스케이스. 외부 결제 승인(suspend)과 DB 저장(블로킹 트랜잭션)을 조합한다.
 */
class OrderFacade(
    private val paymentClient: PaymentClient,
    private val orderTxService: OrderTxService,
    private val txDispatcher: CoroutineDispatcher,
) {
    suspend fun place(
        item: String,
        failHistory: Boolean = false,
    ) {
        // TODO(b): 1. 결제 승인(paymentClient.approve)은 트랜잭션 밖에서 기다린다.
        //          2. 주문 저장(orderTxService.saveOrderWithHistory)은 txDispatcher 스레드에서, 하나의 블로킹 트랜잭션으로 시작부터 끝까지 실행한다.
        TODO("결제 승인과 주문 저장을 조합하도록 구현하세요")
    }
}
