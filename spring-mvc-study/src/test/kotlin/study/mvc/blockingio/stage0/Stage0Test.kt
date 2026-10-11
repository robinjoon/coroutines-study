package study.mvc.blockingio.stage0

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.Executors

/**
 * `suspend`를 붙이면 블로킹 코드가 논블로킹이 될까?
 *
 * JDBC 드라이버는 결과가 올 때까지 호출한 스레드를 붙잡고 기다린다(블로킹).
 * 함수에 `suspend` 키워드를 붙여도 내부의 블로킹 호출은 그대로다. 코루틴은 `delay` 같은 "중단 지점"에서만 스레드를 양보한다.
 *
 [* - 공식 가이드(중단 함수)](https://kotlinlang.org/docs/coroutines-basics.html)
 * - [Dispatchers.IO](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html)
 */
class Stage0Test {

    /**
     * JDBC 조회를 흉내 내는 두 저장소가 있다.
     * - [BlockingProductRepository]: suspend 함수지만 내부에서 `Thread.sleep`으로 스레드를 붙잡는다(JDBC와 같다).
     * - [SuspendingProductRepository]: `delay`로 기다리는 동안 스레드를 양보한다.
     *
     * 두 저장소를 스레드가 하나뿐인 디스패처에서 동시에 두 번(A, B) 호출하고 사건 순서를 관찰하자.
     *
     * 왜 그런 결과가 나왔을까? 스레드가 200개인 Tomcat에서 모든 요청이 BlockingProductRepository를 호출하면 무슨 일이 생길까?
     */
    @Test
    fun suspendRepositoryStillOccupiesCaller() {
        val blockingEvents = runOnSingleThread { events, name -> BlockingProductRepository(events).findName(name) }
        val suspendingEvents = runOnSingleThread { events, name -> SuspendingProductRepository(events).findName(name) }

        // TODO(a): 두 사건 순서를 관찰하고 기대값을 고치자.
        assertThat(blockingEvents).containsExactly("")
        assertThat(suspendingEvents).containsExactly("")
    }

    private fun runOnSingleThread(call: suspend (MutableList<String>, String) -> Unit): List<String> {
        val events = Collections.synchronizedList(mutableListOf<String>())
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { singleThread ->
            runBlocking {
                withContext(singleThread) {
                    launch { call(events, "A") }
                    launch { call(events, "B") }
                }
            }
        }
        return events.toList()
    }
}

class BlockingProductRepository(private val events: MutableList<String>) {
    suspend fun findName(name: String) {
        events += "$name 조회 시작"
        Thread.sleep(100) // JDBC 호출처럼 스레드를 붙잡고 기다린다.
        events += "$name 조회 끝"
    }
}

class SuspendingProductRepository(private val events: MutableList<String>) {
    suspend fun findName(name: String) {
        events += "$name 조회 시작"
        delay(100) // 기다리는 동안 스레드를 양보한다.
        events += "$name 조회 끝"
    }
}
