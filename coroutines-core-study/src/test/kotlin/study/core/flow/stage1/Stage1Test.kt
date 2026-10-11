package study.core.flow.stage1

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

            assertThat(result).describedAs("변환한 4의 배수 두 개만 내보내야 한다").containsExactly(4, 8)
            assertThat(events)
                .describedAs("필요한 두 값을 내보낸 뒤에는 upstream 생산을 멈추고 finally를 실행해야 한다")
                .containsExactly("emit:1", "emit:2", "emit:3", "emit:4", "upstream:finally")
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
            assertThat(failure)
                .describedAs("source의 일반 실패를 정상 값으로 바꾸지 않고 전달해야 한다")
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("source failed")
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
            // currentTime은 앞 단계에서 흐른 시간까지 runTest 전체에서 누적된다.
            assertThat(currentTime).isEqualTo(140L)

            // 같은 파이프라인 객체를 두 번 수집해도 각 수집은 새로 시작한다.
            val reusable = selectedNumbers(flowOf(1, 2, 3, 4))
            repeat(2) {
                assertThat(reusable.toList())
                    .describedAs("같은 파이프라인을 다시 수집해도 각 수집의 제한은 새로 시작해야 한다")
                    .containsExactly(4, 8)
            }
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

            val collectionFailure =
                runCatching {
                    upstreamOn(source, upstreamDispatcher).collect { number ->
                        collectorContexts += currentCoroutineContext()[ContinuationInterceptor]
                        events += "collect:$number"
                        collected += number
                    }
                }.exceptionOrNull()
            // 아직 구현하지 않았다면(TODO) 그 예외를 그대로 보여 준다.
            (collectionFailure as? NotImplementedError)?.let { throw it }
            assertThat(collectionFailure)
                .describedAs("upstream을 옮겨도 collector의 context에서 값을 전달해야 한다")
                .isNull()

            assertThat(collected).containsExactly(1, 2)
            assertThat(upstreamDispatcher).isNotSameAs(collectorDispatcher)
            assertThat(upstreamContexts)
                .describedAs("upstream은 전달받은 dispatcher에서 실행되어야 한다")
                .containsExactly(upstreamDispatcher, upstreamDispatcher)
            assertThat(collectorContexts).containsExactly(collectorDispatcher, collectorDispatcher)
            assertThat(events).contains("emit:1", "emit:2", "collect:1", "collect:2")
            // flowOn은 버퍼를 사용할 수 있으므로 emit과 collect의 교대 순서는 단언하지 않는다.
            val failure =
                runCatching {
                    upstreamOn(flow<Int> { error("upstream failed") }, upstreamDispatcher).toList()
                }.exceptionOrNull()
            assertThat(failure)
                .describedAs("upstream의 일반 실패를 수집자에게 전달해야 한다")
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("upstream failed")

            // 생산이 끝나지 않은 흐름에서도 첫 값을 받으면 수집과 upstream 정리가 끝나야 한다.
            // awaitCancellation은 취소될 때까지 끝나지 않고 기다리는 suspend 함수다.
            events.clear()
            val ongoingSource =
                flow {
                    try {
                        emit(1)
                        awaitCancellation()
                    } finally {
                        events += "upstream:finally"
                    }
                }
            val first = withTimeoutOrNull(100) { upstreamOn(ongoingSource, upstreamDispatcher).first() }
            assertThat(first)
                .describedAs("upstream 전체가 끝날 때까지 모으지 않고 첫 값을 전달해야 한다")
                .isEqualTo(1)
            assertThat(events)
                .describedAs("첫 값을 받은 수집자가 끝나면 아직 실행 중인 upstream도 정리되어야 한다")
                .containsExactly("upstream:finally")
        }
}
