package study.core.context.stage0

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor

class Stage0Test {
    /**
     * CoroutineContext에는 dispatcher와 Job 같은 실행 정보가 들어 있다.
     * 자식은 부모의 dispatcher를 상속하지만 수명을 나타내는 Job은 별도로 가진다.
     * [공식 문서](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#children-of-a-coroutine)
     * 관찰 지점: dispatcher 객체의 동일성, Job의 차이, 부모의 children 목록을 확인하라.
     * 같은 dispatcher로 실행되는데 서로 다른 Job을 갖는 이유는 무엇일까?
     */
    @Test
    fun childrenInheritDispatcherButHaveOwnJob() =
        runTest {
            val parentJob = coroutineContext[Job]!!
            val parentDispatcher = coroutineContext[ContinuationInterceptor]
            val events = mutableListOf<String>()
            var childJob: Job? = null
            var childDispatcher: ContinuationInterceptor? = null
            val child =
                launch {
                    childJob = coroutineContext[Job]
                    childDispatcher = coroutineContext[ContinuationInterceptor]
                    events += "child:started"
                }
            val belongsToParent = parentJob.children.toList().contains(child)
            runCurrent()
            child.join()

            assertThat(events).containsExactly("child:started")
            // TODO(a): 상속되는 요소와 새로 생기는 요소를 구분해서 기대값을 바꾸자.
            assertThat(childDispatcher).isNotSameAs(parentDispatcher)
            assertThat(childJob).isSameAs(parentJob)
            assertThat(belongsToParent).isFalse()
        }
}
