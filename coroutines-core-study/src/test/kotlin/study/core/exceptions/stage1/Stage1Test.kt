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
    fun supervisorKeepsHealthySiblingRunning() = runTest {
        val events = mutableListOf<String>()
        val result = supervisedResults(
            first = { delay(10); error("first failed") },
            second = {
                try { delay(100); events += "second:end"; "healthy" }
                finally { events += "second:finally" }
            },
            onFailure = { events += "failure:${it.message}" },
        )

        assertThat(result).containsExactly("healthy")
        assertThat(events).containsExactly("failure:first failed", "second:end", "second:finally")
        assertThat(currentTime).isEqualTo(100L)
        assertThat(supervisedResults({ "first" }, { "second" }, { error("unexpected failure") }))
            .containsExactly("first", "second")

        events.clear()
        val reversed = supervisedResults(
            first = { delay(100); "healthy-first" },
            second = { delay(10); error("second failed") },
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
    fun supervisorStillPropagatesParentCancellation() = runTest {
        val events = mutableListOf<String>()
        supervisorScope {
            val parent = async {
                supervisedResults(
                    first = {
                        events += "first:start"
                        try { delay(1_000); "first" } finally { events += "first:finally" }
                    },
                    second = {
                        events += "second:start"
                        try { delay(1_000); "second" } finally { events += "second:finally" }
                    },
                    onFailure = { events += "fallback" },
                )
            }
            runCurrent()
            // 미구현 실패를 await로 직접 전달해 teardown 예외로 숨기지 않는다.
            if (parent.isCompleted) parent.await()
            assertThat(events).containsExactlyInAnyOrder("first:start", "second:start")

            parent.cancelAndJoin()
            val failure = runCatching { parent.await() }.exceptionOrNull()

            assertThat(failure).isInstanceOf(CancellationException::class.java)
            assertThat(parent.isCancelled).isTrue()
            assertThat(events).containsExactlyInAnyOrder("first:start", "second:start", "first:finally", "second:finally")
            assertThat(events).doesNotContain("fallback")
            assertThat(currentTime).isEqualTo(0L)
        }
    }
}
