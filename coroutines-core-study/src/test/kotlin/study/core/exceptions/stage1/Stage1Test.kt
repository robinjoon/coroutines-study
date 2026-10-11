package study.core.exceptions.stage1

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Stage1Test {
    /**
     * supervisorScope는 한 자식의 실패 때문에 정상 형제 작업까지 취소되는 것을 막는다.
     * 실패를 없애 주지는 않으므로 각 async의 await에서 실패 정책을 직접 적용해야 한다.
     * [공식 문서](https://kotlinlang.org/docs/exception-handling.html#supervision-scope)
     * 관찰 지점: 실패 보고와 정상 결과, 정상 형제의 finally가 모두 남는지 확인하라.
     * 실패를 따로 처리했을 때 정상 형제가 끝까지 실행되는 이유는 무엇일까?
     */
    @Test
    fun supervisorKeepsHealthySiblingRunning() =
        runTest {
            val events = mutableListOf<String>()
            val outcome =
                runCatching {
                    supervisedResults(
                        first = {
                            delay(10)
                            error("first failed")
                        },
                        second = {
                            try {
                                delay(100)
                                events += "second:end"
                                "healthy"
                            } finally {
                                events += "second:finally"
                            }
                        },
                        onFailure = { events += "failure:${it.message}" },
                    )
                }

            assertThat(outcome.exceptionOrNull())
                .describedAs("한 자식의 일반 실패가 정상 형제와 호출자까지 실패시키면 안 된다")
                .isNull()
            val result = outcome.getOrThrow()

            assertThat(result).containsExactly("healthy")
            assertThat(events).containsExactly("failure:first failed", "second:end", "second:finally")
            assertThat(currentTime)
                .describedAs("실패한 자식을 처리하는 동안 정상 형제도 함께 진행되어야 한다")
                .isEqualTo(100L)
            // 실패가 없으면 onFailure는 호출되지 않아야 한다. 호출되면 이 람다가 예외를 던진다.
            assertThat(supervisedResults({ "first" }, { "second" }, { error("unexpected failure") }))
                .containsExactly("first", "second")

            // 두 작업이 모두 성공하되 두 번째가 먼저 끝나는 경우에도 입력 순서를 유지한다.
            val ordered =
                supervisedResults(
                    first = {
                        delay(100)
                        "first"
                    },
                    second = {
                        delay(10)
                        "second"
                    },
                    onFailure = { error("unexpected failure") },
                )
            assertThat(ordered)
                .describedAs("완료 순서와 관계없이 성공 결과는 입력 순서로 반환해야 한다")
                .containsExactly("first", "second")

            events.clear()
            val reversed =
                supervisedResults(
                    first = {
                        delay(100)
                        "healthy-first"
                    },
                    second = {
                        delay(10)
                        error("second failed")
                    },
                    onFailure = { events += "failure:${it.message}" },
                )
            assertThat(reversed).containsExactly("healthy-first")
            assertThat(events).containsExactly("failure:second failed")
        }

    /**
     * supervision은 자식 실패의 전파 방향을 바꾸지만 부모 취소를 무시하지 않는다.
     * 부모가 취소되면 supervisorScope 안의 자식들도 취소되고 finally를 실행한다.
     * [공식 문서](https://kotlinlang.org/docs/exception-handling.html#supervision-scope)
     * 관찰 지점: 부모 취소 후 두 자식의 정리와 일반 실패 보고의 부재를 확인하라.
     * 자식 실패는 격리하면서 부모 취소는 전달하는 이유는 무엇일까?
     */
    @Test
    fun supervisorStillPropagatesParentCancellation() =
        runTest {
            val events = mutableListOf<String>()
            // 테스트 자체를 취소하지 않고 supervisedResults의 호출자(parent)만 취소하려고 감싼다.
            supervisorScope {
                val parent =
                    async {
                        supervisedResults(
                            first = {
                                events += "first:start"
                                try {
                                    delay(1_000)
                                    "first"
                                } finally {
                                    events += "first:finally"
                                }
                            },
                            second = {
                                events += "second:start"
                                try {
                                    delay(1_000)
                                    "second"
                                } finally {
                                    events += "second:finally"
                                }
                            },
                            onFailure = { events += "fallback" },
                        )
                    }
                runCurrent()
                // 구현이 곧바로 실패해 끝났다면(예: TODO 미구현) 그 예외를 여기서 그대로 보여 준다.
                if (parent.isCompleted) parent.await()
                assertThat(events)
                    .describedAs("부모를 취소하기 전에 두 자식이 모두 시작되어 있어야 한다")
                    .containsExactlyInAnyOrder("first:start", "second:start")

                parent.cancelAndJoin()
                val failure = runCatching { parent.await() }.exceptionOrNull()

                assertThat(failure).isInstanceOf(CancellationException::class.java)
                assertThat(parent.isCancelled).isTrue()
                assertThat(events)
                    .describedAs("부모 취소가 두 자식에게 전달되고 각각 finally를 실행해야 한다")
                    .containsExactlyInAnyOrder("first:start", "second:start", "first:finally", "second:finally")
                assertThat(events).doesNotContain("fallback")
                assertThat(currentTime).isEqualTo(0L)
            }
        }
}
