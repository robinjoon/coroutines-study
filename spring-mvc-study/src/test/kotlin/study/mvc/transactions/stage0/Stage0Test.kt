package study.mvc.transactions.stage0

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * `@Transactional`은 suspend 함수에도 똑같이 동작할까?
 *
 * JDBC 트랜잭션은 **스레드**에 묶인다. Spring의 JDBC 트랜잭션 매니저는 커넥션과 트랜잭션 상태를 ThreadLocal에 저장한다.
 * 코루틴은 중단 후 다른 스레드에서 이어질 수 있다. 그렇다면 suspend 함수의 트랜잭션은 어떻게 될까?
 *
 * 이 테스트 클래스에는 @Transactional을 붙이지 않았다. 롤백 여부는 테스트 트랜잭션의 영향 없이 DB에 남은 행 수로 확인한다.
 *
 [* - 선언적 트랜잭션](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)
 * - [코루틴과 트랜잭션](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html#transactions)
 * - [Spring 7.0.9 TransactionAspectSupport 소스](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionAspectSupport.java)
 */
@SpringBootTest(
    classes = [Stage0Test.Config::class],
    properties = ["spring.datasource.url=jdbc:h2:mem:transactions-stage0;DB_CLOSE_DELAY=-1"],
)
class Stage0Test {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("create table if not exists orders(id bigint auto_increment primary key, item varchar(50))")
        jdbcTemplate.execute("delete from orders")
        orderService.transactionActive.clear()
    }

    /**
     * 먼저 익숙한 동기(블로킹) 코드로 기준선을 확인하자.
     * 메서드 안에서 트랜잭션이 진행 중이었는가? 예외가 난 뒤 orders 테이블에는 몇 건이 남았는가?
     *
     * 왜 그런 결과가 나왔을까?
     */
    @Test
    fun blockingTransactionalBaselineRollsBack() {
        assertThatThrownBy { orderService.placeTwoOrdersBlocking() }
            .isInstanceOf(IllegalStateException::class.java)

        // TODO(a): 관찰한 결과로 기대값을 고치자.
        assertThat(orderService.transactionActive).containsExactly(false)
        assertThat(orderCount()).isEqualTo(-1)
    }

    /**
     * 똑같은 코드를 suspend 함수로 바꿨다. 트랜잭션 매니저는 그대로 JDBC용이다.
     *
     * Spring Boot 4.1.1 / Spring Framework 7.0.9에서 확인한 사실:
     * Spring AOP는 suspend 메서드를 호출할 때 코루틴을 Reactor `Mono`로 감싸서 넘긴다. JDBC용(Platform) 트랜잭션 매니저는 이 Mono를
     * "그냥 반환값"으로 다룬다. 그래서 트랜잭션의 경계가 실제 본문 실행 시점과 어긋난다. 반응형(Reactive) 트랜잭션 매니저일 때만 코루틴 전체를 감싼다.
     * 소스 코드의 `invokeWithinTransaction`에서 `ReactiveTransactionManager` 분기를 찾아 읽어 보자.
     *
     * 왜 그런 결과가 나왔을까? 컴파일 에러도, 런타임 에러도 없이 이런 일이 일어난다는 점이 왜 위험할까?
     * (주의: 이 결과는 위 버전 조합에서 확인한 것이다. 다른 버전이나 트랜잭션 매니저에서는 다를 수 있다.)
     */
    @Test
    fun suspendTransactionalDoesNotCoverJdbcWork() {
        assertThat(transactionManager.javaClass.simpleName).isEqualTo("JdbcTransactionManager")

        assertThatThrownBy { runBlocking { orderService.placeTwoOrdersSuspending() } }
            .isInstanceOf(IllegalStateException::class.java)

        // TODO(a): 관찰한 결과로 기대값을 고치자. 첫 번째 테스트와 같을 거라고 예상했는가?
        assertThat(orderService.transactionActive).containsExactly(true)
        assertThat(orderCount()).isEqualTo(-1)
    }

    private fun orderCount(): Int = jdbcTemplate.queryForObject("select count(*) from orders", Int::class.java)!!

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(OrderService::class)
    class Config
}
