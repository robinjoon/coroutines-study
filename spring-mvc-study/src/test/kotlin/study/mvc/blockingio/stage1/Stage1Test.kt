package study.mvc.blockingio.stage1

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 블로킹 JDBC를 코루틴에서 호출할 때는 "어느 스레드에서 블로킹할지"와 "동시에 몇 개까지 블로킹할지"를 정해야 한다.
 *
 * - [withContext](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)
 * - [Dispatchers.IO / limitedParallelism](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html)
 * - [Semaphore](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-semaphore/)
 */
@SpringBootTest(
    classes = [Stage1Test.Config::class],
    properties = ["spring.datasource.url=jdbc:h2:mem:blockingio-stage1;DB_CLOSE_DELAY=-1"],
)
class Stage1Test {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dao: ProductJdbcDao

    @Autowired
    private lateinit var repository: ProductRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("create table if not exists product(id bigint primary key, name varchar(50))")
        jdbcTemplate.execute("merge into product key(id) values (1, 'keyboard')")
        dao.calledThreadNames.clear()
        dao.waitBeforeQuery = null
    }

    /**
     * 블로킹 호출은 블로킹해도 괜찮은 스레드로 옮겨서 실행한다.
     * 실무에서는 보통 `Dispatchers.IO`나 `Dispatchers.IO.limitedParallelism(커넥션 풀 크기)`를 쓴다.
     * 이 테스트는 어느 스레드에서 실행됐는지 확인하기 위해 이름이 `jdbc-`로 시작하는 전용 디스패처를 주입한다.
     *
     * 주의: 다른 디스패처로 옮겨도 JDBC는 여전히 블로킹이다. 블로킹을 "없애는" 것이 아니라 "격리"하는 것이다.
     * 또 코루틴을 취소하거나 withTimeout을 걸어도 이미 실행 중인 JDBC 호출이 멈춘다는 보장은 없다(취소는 협력적이다).
     * 실행 중인 쿼리를 끊으려면 JDBC query timeout, 커넥션 획득 timeout, 커넥션 풀 크기를 따로 설정해야 한다.
     * 공식 가이드: [취소는 협력적이다](https://kotlinlang.org/docs/cancellation-and-timeouts.html#cancellation-is-cooperative)
     *
     * runBlocking으로 감싸거나 DAO를 그냥 호출하면 호출한 스레드가 조회가 끝날 때까지 붙잡힌다. 이 테스트는 그런 구현을 통과시키지 않는다.
     *
     * 왜 그런 결과가 나왔을까? suspend 컨트롤러가 재개된 스레드(controller.stage0 참고)에서 바로 JDBC를 호출하면 어떤 문제가 생길까?
     */
    @Test
    fun jdbcAdapterUsesInjectedBlockingDispatcher() {
        // Tomcat 요청 스레드를 흉내 낸 전용 스레드(request-thread)에서 저장소를 호출한다.
        // DAO는 "그 스레드에서 실행되는 다른 코루틴"이 신호를 보내야 조회를 진행한다.
        // 호출한 스레드가 JDBC 때문에 붙잡혀 있으면 그 코루틴이 실행되지 못한다.
        val callerThreadIsFree = CountDownLatch(1)
        dao.waitBeforeQuery = callerThreadIsFree

        val name =
            onRequestThread {
                val lookup = async { repository.findName(1) }
                launch { callerThreadIsFree.countDown() }
                lookup.await()
            }

        assertThat(name).isEqualTo("keyboard")
        assertThat(dao.calledThreadNames).hasSize(1).allMatch { it.startsWith("jdbc-") }
    }

    /**
     * DB 커넥션 풀이 2개라면, 동시에 5개 요청이 와도 DB 작업은 2개씩만 진행되어야 한다.
     * 스레드를 블로킹하는 `java.util.concurrent.Semaphore` 대신 코루틴을 "중단"시키는 방법을 찾아보자.
     *
     * runTest는 가상 시간을 쓴다. delay(100)은 실제로 기다리지 않고 가상 시계만 100ms 앞으로 보낸다.
     * 5개 요청을 2개씩 처리하면 가상 시간은 얼마가 걸릴까?
     *
     * 왜 그런 결과가 나왔을까? `limitedParallelism(2)` 디스패처로도 같은 결과가 나올까? (힌트: delay 중인 코루틴은 스레드를 점유하지 않는다)
     */
    @Test
    fun databaseBulkheadCapsActiveCalls() =
        runTest {
            val slowDao = SlowProductDao()
            val bulkhead = BulkheadProductRepository(slowDao, maxConcurrentCalls = 2)

            val names = (1L..5L).map { id -> async { bulkhead.findName(id) } }.awaitAll()

            assertThat(names).containsExactly("product-1", "product-2", "product-3", "product-4", "product-5")
            assertThat(slowDao.maxActive).isEqualTo(2)
            assertThat(currentTime).isEqualTo(300)

            // 실패한 호출도 자리를 반납해야 한다. 반납하지 않으면 실패가 두 번 난 뒤로는 아무도 DB에 들어가지 못한다.
            val flakyBulkhead = BulkheadProductRepository(SlowProductDao(failingIds = setOf(1L, 2L)), maxConcurrentCalls = 2)
            assertThat(runCatching { flakyBulkhead.findName(1) }.isFailure).isTrue()
            assertThat(runCatching { flakyBulkhead.findName(2) }.isFailure).isTrue()
            assertThat(withTimeout(1_000) { flakyBulkhead.findName(3) }).isEqualTo("product-3")

            // 취소된 호출도 자리를 반납해야 한다.
            val cancelledBulkhead = BulkheadProductRepository(SlowProductDao(), maxConcurrentCalls = 2)
            val inFlight = (1L..2L).map { id -> launch { cancelledBulkhead.findName(id) } }
            runCurrent()
            inFlight.forEach { it.cancelAndJoin() }
            assertThat(withTimeout(1_000) { cancelledBulkhead.findName(3) }).isEqualTo("product-3")
        }

    private fun <T> onRequestThread(block: suspend CoroutineScope.() -> T): T =
        Executors.newSingleThreadExecutor { Thread(it, "request-thread") }.asCoroutineDispatcher().use { requestThread ->
            runBlocking { withContext(requestThread, block) }
        }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    class Config {
        @Bean(destroyMethod = "close")
        fun jdbcDispatcher(): ExecutorCoroutineDispatcher {
            val sequence = AtomicInteger()
            return Executors
                .newFixedThreadPool(2) { Thread(it, "jdbc-${sequence.incrementAndGet()}") }
                .asCoroutineDispatcher()
        }

        @Bean
        fun productJdbcDao(jdbcTemplate: JdbcTemplate) = ProductJdbcDao(jdbcTemplate)

        @Bean
        fun productRepository(
            dao: ProductJdbcDao,
            @Qualifier("jdbcDispatcher") jdbcDispatcher: CoroutineDispatcher,
        ) = ProductRepository(dao, jdbcDispatcher)
    }
}
