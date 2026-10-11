package study.mvc.transactions.stage1

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * stage0에서 JDBC 트랜잭션과 suspend 함수가 잘 맞지 않는다는 것을 확인했다.
 * 그렇다면 코루틴 기반 서비스에서 JDBC 트랜잭션은 어떻게 다뤄야 할까?
 *
 * 안전한 방법 중 하나는 경계를 나누는 것이다.
 * - 트랜잭션 안의 작업은 suspend가 아닌 평범한 블로킹 메서드로 만든다. 그러면 처음부터 끝까지 한 스레드에서 실행된다.
 * - 그 메서드 전체를 블로킹 전용 디스패처(withContext)에서 호출한다.
 * - 외부 API처럼 오래 기다리는 suspend 호출은 트랜잭션 밖에서 한다. 트랜잭션을 오래 열어 두면 커넥션을 오래 붙잡기 때문이다.
 *
 * 이 테스트 코드는 수정하지 않는다. OrderFacade의 TODO를 구현해서 통과시키자.
 *
 * - [withContext](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
 * - [트랜잭션 전파와 경계](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
 */
@SpringBootTest(
    classes = [Stage1Test.Config::class],
    properties = ["spring.datasource.url=jdbc:h2:mem:transactions-stage1;DB_CLOSE_DELAY=-1"],
)
class Stage1Test {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var orderFacade: OrderFacade

    @Autowired
    private lateinit var paymentClient: PaymentClient

    @Autowired
    private lateinit var orderTxService: OrderTxService

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "create table if not exists orders(id bigint auto_increment primary key, item varchar(50), payment_id varchar(50))",
        )
        jdbcTemplate.execute("create table if not exists order_history(id bigint auto_increment primary key, item varchar(50))")
        jdbcTemplate.execute("delete from orders")
        jdbcTemplate.execute("delete from order_history")
        paymentClient.transactionActiveDuringCall.clear()
        orderTxService.observations.clear()
        orderTxService.beforeSave = null
    }

    /**
     * 성공하면 주문과 이력이 함께 저장되고, 이력 저장이 실패하면 주문까지 함께 롤백되어야 한다.
     * 결제 승인은 트랜잭션 밖에서, 주문 저장은 tx- 로 시작하는 스레드의 트랜잭션 안에서 실행되었는지도 확인한다.
     *
     * 왜 그런 결과가 나왔을까? 결제 승인을 트랜잭션 안으로 옮기면, 동시 요청이 많을 때 커넥션 풀에 어떤 일이 생길까?
     */
    @Test
    fun blockingTransactionStaysInsideIoBoundary() {
        // Tomcat 요청 스레드를 흉내 낸 전용 스레드(request-thread)에서 주문을 호출한다.
        // 저장은 "그 스레드에서 실행되는 다른 코루틴"이 신호를 보내야 진행된다.
        // 저장하는 동안 호출한 스레드가 붙잡혀 있으면(runBlocking 등) 그 코루틴이 실행되지 못한다.
        val saveStarted = CompletableDeferred<Unit>()
        val callerThreadIsFree = CountDownLatch(1)
        orderTxService.beforeSave = {
            saveStarted.complete(Unit)
            check(callerThreadIsFree.await(2, TimeUnit.SECONDS)) { "2초 동안 신호가 오지 않았다. 호출한 스레드가 붙잡혀 있지 않은지 확인하자." }
        }

        onRequestThread {
            val placing = async { orderFacade.place("keyboard") }
            launch {
                saveStarted.await()
                callerThreadIsFree.countDown()
            }
            placing.await()
        }
        orderTxService.beforeSave = null

        assertThat(count("orders")).isEqualTo(1)
        assertThat(count("order_history")).isEqualTo(1)
        assertThat(paymentClient.transactionActiveDuringCall).containsExactly(false)
        assertThat(orderTxService.observations).singleElement().asString().matches("active=true thread=tx-.*")

        assertThatThrownBy { runBlocking { orderFacade.place("mouse", failHistory = true) } }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(count("orders")).isEqualTo(1)
        assertThat(count("order_history")).isEqualTo(1)
    }

    private fun <T> onRequestThread(block: suspend CoroutineScope.() -> T): T =
        Executors.newSingleThreadExecutor { Thread(it, "request-thread") }.asCoroutineDispatcher().use { requestThread ->
            runBlocking { withContext(requestThread, block) }
        }

    private fun count(table: String): Int = jdbcTemplate.queryForObject("select count(*) from $table", Int::class.java)!!

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(OrderTxService::class)
    class Config {
        @Bean(destroyMethod = "close")
        fun txDispatcher(): ExecutorCoroutineDispatcher =
            Executors.newFixedThreadPool(2) { Thread(it, "tx-worker") }.asCoroutineDispatcher()

        @Bean
        fun paymentClient() = PaymentClient()

        @Bean
        fun orderFacade(
            paymentClient: PaymentClient,
            orderTxService: OrderTxService,
            txDispatcher: ExecutorCoroutineDispatcher,
        ) = OrderFacade(paymentClient, orderTxService, txDispatcher)
    }
}
