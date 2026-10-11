package study.core.flow.stage1

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor

class Stage1Test {
    /**
     * map은 값을 바꾸고 filter는 조건에 맞는 값을 골라낸다.
     * take는 필요한 개수를 받으면 upstream 생산을 중단하며 생산 코드의 finally도 실행된다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-flow.html#size-limiting-operators)
     * 관찰 지점: 두 배로 바꾼 4의 배수 두 개, 생산한 원본 값, 정상·실패·timeout 시 정리를 확인하라.
     * 원본 열 개를 준비했는데 일부만 생산하고 finally로 넘어가는 이유는 무엇일까?
     */
    @Test
    fun pipelineProducesOnlyRequestedItems() =
        runTest {
            val events = mutableListOf<String>()
            val source =
                flow {
                    try {
                        for (number in 1..10) {
                            delay(10)
                            events += "emit:$number"
                            emit(number)
                        }
                    } finally {
                        events += "upstream:finally"
                    }
                }

            val result = selectedNumbers(source).toList()

            assertThat(result).containsExactly(4, 8)
            assertThat(events).containsExactly("emit:1", "emit:2", "emit:3", "emit:4", "upstream:finally")
            assertThat(currentTime).isEqualTo(40L)

            events.clear()
            val failedSource =
                flow<Int> {
                    try {
                        error("source failed")
                    } finally {
                        events += "failed:finally"
                    }
                }
            val failure = runCatching { selectedNumbers(failedSource).toList() }.exceptionOrNull()
            assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("source failed")
            assertThat(events).containsExactly("failed:finally")

            events.clear()
            val slowSource =
                flow {
                    try {
                        delay(1_000)
                        emit(2)
                    } finally {
                        events += "cancelled:finally"
                    }
                }
            val timedOut = withTimeoutOrNull(100) { selectedNumbers(slowSource).toList() }
            assertThat(timedOut).isNull()
            assertThat(events).containsExactly("cancelled:finally")
            assertThat(currentTime).isEqualTo(140L)
        }

    /**
     * flowOn은 자신보다 앞에 있는 upstream의 실행 context를 바꾼다.
     * collect하는 쪽은 호출자의 context를 유지한다.
     * 여기서는 같은 가상 시간 scheduler를 쓰는 서로 다른 dispatcher 객체로 경계를 확인한다.
     * [공식 문서](https://kotlinlang.org/docs/coroutines-flow.html#flowon-operator)
     * 관찰 지점: emit과 collect에서 관찰한 dispatcher 객체를 비교하라. 이 실습은 실제 thread 이동을 증명하지 않는다.
     * upstream을 옮겼는데 collector의 dispatcher는 그대로인 이유는 무엇일까?
     */
    @Test
    fun flowOnChangesUpstreamNotCollector() =
        runTest {
            val upstreamDispatcher = StandardTestDispatcher(testScheduler, name = "upstream")
            val collectorDispatcher = currentCoroutineContext()[ContinuationInterceptor]
            val upstreamContexts = mutableListOf<ContinuationInterceptor?>()
            val collectorContexts = mutableListOf<ContinuationInterceptor?>()
            val events = mutableListOf<String>()
            val source =
                flow {
                    for (number in 1..2) {
                        upstreamContexts += currentCoroutineContext()[ContinuationInterceptor]
                        events += "emit:$number"
                        emit(number)
                    }
                }
            val collected = mutableListOf<Int>()

            upstreamOn(source, upstreamDispatcher).collect { number ->
                collectorContexts += currentCoroutineContext()[ContinuationInterceptor]
                events += "collect:$number"
                collected += number
            }

            assertThat(collected).containsExactly(1, 2)
            assertThat(upstreamDispatcher).isNotSameAs(collectorDispatcher)
            assertThat(upstreamContexts).containsExactly(upstreamDispatcher, upstreamDispatcher)
            assertThat(collectorContexts).containsExactly(collectorDispatcher, collectorDispatcher)
            assertThat(events).contains("emit:1", "emit:2", "collect:1", "collect:2")
            // flowOn은 버퍼를 사용할 수 있으므로 emit과 collect의 교대 순서는 단언하지 않는다.
            val failure =
                runCatching {
                    upstreamOn(flow<Int> { error("upstream failed") }, upstreamDispatcher).toList()
                }.exceptionOrNull()
            assertThat(failure).isInstanceOf(IllegalStateException::class.java).hasMessage("upstream failed")
        }
}
