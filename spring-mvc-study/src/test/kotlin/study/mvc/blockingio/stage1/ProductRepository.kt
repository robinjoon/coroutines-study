package study.mvc.blockingio.stage1

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 블로킹 JDBC DAO. 완성되어 있으니 수정하지 않는다.
 * 어느 스레드에서 호출되었는지 기록한다.
 */
class ProductJdbcDao(
    private val jdbcTemplate: JdbcTemplate,
) {
    val calledThreadNames = CopyOnWriteArrayList<String>()

    /** 테스트가 설정하면, 조회 전에 이 신호가 올 때까지(최대 2초) 기다린다. */
    @Volatile
    var waitBeforeQuery: CountDownLatch? = null

    fun findName(id: Long): String {
        calledThreadNames += Thread.currentThread().name
        waitBeforeQuery?.let { signal ->
            check(signal.await(2, TimeUnit.SECONDS)) { "2초 동안 신호가 오지 않았다. 호출한 스레드가 붙잡혀 있지 않은지 확인하자." }
        }
        return jdbcTemplate.queryForObject("select name from product where id = ?", String::class.java, id)!!
    }
}

/**
 * 코루틴 세계(suspend)에서 블로킹 DAO를 안전하게 호출하는 저장소.
 */
class ProductRepository(
    private val dao: ProductJdbcDao,
    @Qualifier("jdbcDispatcher") private val jdbcDispatcher: CoroutineDispatcher,
) {
    suspend fun findName(id: Long): String {
        // TODO(b): 블로킹 호출(dao.findName)이 jdbcDispatcher의 스레드에서 실행되도록 구현하자.
        TODO("jdbcDispatcher 위에서 dao.findName(id)를 호출하세요")
    }
}

/**
 * 느린 외부 저장소를 흉내 내는 DAO. 완성되어 있으니 수정하지 않는다.
 * 동시에 몇 개의 호출이 진행 중인지(active)와 그 최댓값(maxActive)을 기록한다.
 */
class SlowProductDao(
    private val failingIds: Set<Long> = emptySet(),
) {
    var active = 0
        private set
    var maxActive = 0
        private set

    suspend fun findName(id: Long): String {
        active++
        maxActive = maxOf(maxActive, active)
        try {
            delay(100)
            check(id !in failingIds) { "product $id 조회 실패" }
            return "product-$id"
        } finally {
            active--
        }
    }
}

/**
 * DB 커넥션 풀이 2개뿐인 상황을 가정한다. 동시에 2개를 넘는 호출이 DAO에 들어가지 않게 막는 저장소.
 */
class BulkheadProductRepository(
    private val dao: SlowProductDao,
    private val maxConcurrentCalls: Int,
) {
    suspend fun findName(id: Long): String {
        // TODO(b): 동시에 maxConcurrentCalls 개까지만 dao.findName을 실행하도록 구현하자. 스레드를 블로킹하지 않아야 한다.
        TODO("동시 호출 수를 제한하도록 구현하세요")
    }
}
