package study.mvc.lifecycle.stage0

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.Executors

/**
 * runBlocking은 "코루틴 세계"와 "블로킹 세계"를 잇는 다리다. main 함수나 테스트처럼 블로킹 진입점에서만 쓰도록 만들어졌다.
 * 코루틴 안에서 runBlocking을 호출하면, 기다리는 동안 현재 스레드를 통째로 붙잡는다.
 *
 [* - 공식 가이드(runBlocking)](https://kotlinlang.org/docs/coroutines-basics.html)
 * - [runBlocking API 문서](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html)
 */
class Stage0Test {

    /**
     * 스레드가 하나뿐인 디스패처에서 LegacyClient.call을 두 번(A, B) 동시에 실행하고 사건 순서를 관찰하자.
     *
     * 1. 먼저 기본 상태에서 기대값을 관찰한 결과로 고친다.
     * 2. LegacyClient.call의 `runBlocking { delay(100) }` 줄을 주석 처리하고, 바로 아래 `// delay(100)` 줄의 주석을 해제한다.
     *    다시 실행해서 사건 순서가 어떻게 바뀌는지 관찰한다.
     * 3. 관찰이 끝나면 두 줄을 원래대로 되돌리고 기대값도 1번 상태로 되돌린다.
     *
     * 이 예제는 스레드가 하나라서 "느려지는" 것으로 끝났다. 하지만 runBlocking 안의 코루틴이 바로 그 붙잡힌 스레드에서
     * 실행되어야 한다면 영원히 끝나지 않는다(교착 상태). 공용 디스패처의 스레드가 모두 runBlocking에 붙잡혀도 같은 일이 생길 수 있다.
     *
     * 왜 그런 결과가 나왔을까? 코루틴 안에서 블로킹 API를 써야 한다면 runBlocking 대신 무엇을 써야 할까? (blockingio 단원 참고)
     */
    @Test
    fun nestedRunBlockingOccupiesCallerUntilRelease() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val client = LegacyClient(events)

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { singleThread ->
            runBlocking {
                withContext(singleThread) {
                    launch { client.call("A") }
                    launch { client.call("B") }
                }
            }
        }

        // TODO(a): 관찰한 결과로 기대값을 고치자.
        assertThat(events).containsExactly("")
    }
}
