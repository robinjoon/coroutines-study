package study.mvc.transactions.stage0

import kotlinx.coroutines.delay
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 주문 두 건을 저장한 뒤 결제 실패로 예외를 던지는 서비스. 완성되어 있으니 수정하지 않는다.
 * 메서드가 시작될 때 실제 트랜잭션이 진행 중인지를 transactionActive에 기록한다.
 */
@Service
class OrderService(
    private val jdbcTemplate: JdbcTemplate,
) {
    val transactionActive = CopyOnWriteArrayList<Boolean>()

    @Transactional
    fun placeTwoOrdersBlocking() {
        transactionActive += TransactionSynchronizationManager.isActualTransactionActive()
        insertOrder("keyboard")
        insertOrder("mouse")
        throw IllegalStateException("결제 실패")
    }

    @Transactional
    suspend fun placeTwoOrdersSuspending() {
        transactionActive += TransactionSynchronizationManager.isActualTransactionActive()
        insertOrder("keyboard")
        delay(10) // 결제 API를 기다린다.
        insertOrder("mouse")
        throw IllegalStateException("결제 실패")
    }

    private fun insertOrder(item: String) {
        jdbcTemplate.update("insert into orders(item) values (?)", item)
    }
}
