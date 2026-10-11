# 코루틴 학습 테스트: Spring MVC 백엔드 편

Kotlin 코루틴을 **Spring Boot MVC 백엔드**에서 제대로 쓰기 위한 학습 프로젝트입니다.
[공식 코루틴 가이드](https://kotlinlang.org/docs/coroutines-guide.html)의 개념을 테스트로 익힌 뒤(1부), 백엔드 실무에서 만나는 함정과 패턴(2부)으로 넘어갑니다.
형식은 [우아한테크코스 java-jdbc study](https://github.com/woowacourse/java-jdbc/tree/main/study)를 참고했습니다.

정답 코드는 없습니다. 테스트가 알려 주는 결과를 관찰하고, 직접 구현하고, "왜 그럴까?"에 스스로 답하는 것이 학습입니다.

## 준비

- JDK 25 (Gradle toolchain이 사용합니다)
- IntelliJ IDEA 권장 — 테스트 메서드 옆 ▶ 버튼으로 하나씩 실행하는 것이 가장 편합니다.
- Gradle은 설치하지 않아도 됩니다. `./gradlew`를 사용합니다.

환경 확인:

```bash
./gradlew test --tests '*SmokeTest'
```

## 학습 방법

> **코루틴이 처음이라면 [`docs/concepts.md`](docs/concepts.md)를 먼저 읽어 주세요.**
> 테스트 주석에 단원보다 먼저 나오는 용어(suspend 람다, 코루틴 빌더, 스코프, Job, 디스패처, runTest의 가상 시간)를 짧게 정리해 두었습니다.

각 테스트 파일은 `stage0`, `stage1`, … 순서로 진행합니다. 테스트 위의 주석에는 개념 설명, 공식 문서 링크, 관찰할 지점, 그리고 마지막 질문이 있습니다.

| 유형 | 표시 | 할 일 |
| --- | --- | --- |
| **a. 관찰 후 기대값 수정** | `// TODO(a)` | 일부러 틀리게 적힌 기대값(`containsExactly("")`, `isEqualTo(-1)` 등)을 실행 결과를 관찰해서 고칩니다. 고치기 전에 결과를 **먼저 예상**해 보세요. |
| **b. 구현** | `// TODO(b)` + `TODO("...")` | 같은 패키지의 구현 파일을 채워 테스트를 통과시킵니다. **테스트 코드는 고치지 않습니다.** |
| **c. 주석 해제 관찰** | 테스트 주석의 1·2·3 단계 | 기대값을 고친 뒤, 지정된 줄의 주석을 해제하고 다시 실행해서 무엇이 바뀌는지 관찰합니다. 관찰 후 원래대로 되돌립니다. |

권장 루틴:

1. 테스트 주석과 링크된 공식 문서를 읽는다.
2. 결과를 예상하고 적어 본다.
3. 실행해서 예상과 비교하고, 기대값을 고치거나 구현한다.
4. 주석 마지막의 질문에 2~3문장으로 답을 적어 본다(학습 노트 추천).

## 진행 순서

### 1부. 코루틴 기본기 (`coroutines-core-study`, Spring 없음)

공식 가이드 목차 대신 개념이 쌓이는 순서로 배치했습니다. 각 테스트 주석에 가이드의 해당 절 링크가 있습니다.
주석에서 처음 보는 용어가 나오면 [`docs/concepts.md`](docs/concepts.md)에서 찾아보세요.

| 단원 | stage | 주제 | 테스트 |
| --- | --- | --- | --- |
| basics | 0 | suspend 함수 호출은 순차적이다, launch는 스케줄러가 돌 때 시작한다 | `suspendDoesNotStartAnotherCoroutine` (a), `launchStartsWhenSchedulerRuns` (a) |
|  | 1 | 구조화된 동시성: 부모는 자식을 기다린다, Job.join | `parentReturnsAfterChildrenComplete` (b), `joinWaitsButDoesNotProduceValue` (b) |
| composition | 0 | 순차 호출의 비용 | `sequentialCallsAccumulateVirtualDelay` (a) |
|  | 1 | async로 동시 실행, 실패·timeout 시 형제 정리 | `asyncStartsBothTasksBeforeAwaiting` (b) |
| cancellation | 0 | 취소 요청과 완료는 다르다, 협력적 취소 | `cancelRequestAndCompletionAreDifferent` (a) |
|  | 1 | timeout과 자식 정리, 취소를 fallback으로 삼키지 않기 | `timeoutStopsChildrenAndRunsFinally` (b), `cancellationDoesNotBecomeFallback` (b) |
|  | 2 | 취소 후 suspend 정리와 NonCancellable | `suspendingCleanupNeedsExplicitPolicy` (c) |
| context | 0 | dispatcher는 상속되고 Job은 새로 생긴다 | `childrenInheritDispatcherButHaveOwnJob` (a) |
|  | 1 | withContext로 일부 블록만 다른 스레드에서 | `withContextMovesOnlySelectedBlock` (b) |
| exceptions | 0 | supervisor에서 launch와 async의 실패 관찰 창구 | `supervisedLaunchReportsUnhandledFailure` (a), `supervisedAsyncExposesFailureAtAwait` (a) |
|  | 1 | supervisorScope로 실패 격리, 부모 취소는 전파 | `supervisorKeepsHealthySiblingRunning` (b), `supervisorStillPropagatesParentCancellation` (b) |
| flow | 0 | cold Flow는 수집할 때마다 실행된다 | `coldFlowRunsForEachCollector` (a) |
|  | 1 | map/filter/take 파이프라인, flowOn | `pipelineProducesOnlyRequestedItems` (b), `flowOnChangesUpstreamNotCollector` (b) |
| channels | 0 | rendezvous 채널에서 send는 receive를 기다린다 | `sendWaitsForReceiveOnRendezvousChannel` (a) |
|  | 1 | 생산자 소유권: close와 취소 | `ownedProducerClosesAndCancelsWithConsumer` (b) |
| sharedstate | 0 | 읽기-쓰기 사이 중단으로 갱신이 사라진다 | `splitReadYieldWriteLosesAnUpdate` (a) |
|  | 1 | Mutex로 임계구역 전체 보호 | `mutexProtectsWholeReadModifyWrite` (b) |

### 2부. Spring MVC 백엔드 (`spring-mvc-study`)

| 단원 | stage | 주제 | 테스트 |
| --- | --- | --- | --- |
| controller | 0 | suspend 핸들러와 Servlet 비동기 처리, 재개 스레드 | `suspendEndpointStartsServletAsync` (a) |
| | 1 | runBlocking 없이 suspend 서비스 호출, 비동기 실패와 `@RestControllerAdvice` | `asyncDispatchProducesServiceResponse` (b), `controllerFailureReachesAdviceAfterDispatch` (b) |
| blockingio | 0 | `suspend`를 붙여도 JDBC는 블로킹이다 | `suspendRepositoryStillOccupiesCaller` (a) |
| | 1 | 블로킹 호출 격리(withContext), DB 동시 호출 상한(Semaphore) | `jdbcAdapterUsesInjectedBlockingDispatcher` (b), `databaseBulkheadCapsActiveCalls` (b) |
| aggregation | 0 | 외부 API 순차 호출 비용 | `sequentialAggregationAccumulatesDelay` (a) |
| | 1 | 병렬 조합, 필수/선택 부분 실패, 시간 예산과 취소 | `parallelAggregationAppliesFailurePolicy` (b), `deadlinesAndParentCancellationStopOutstandingCalls` (b) |
| transactions | 0 | `@Transactional` 기준선, suspend + JDBC 트랜잭션의 함정 | `blockingTransactionalBaselineRollsBack` (a), `suspendTransactionalDoesNotCoverJdbcWork` (a) |
| | 1 | 블로킹 트랜잭션을 IO 경계 안에 가두기 | `blockingTransactionStaysInsideIoBoundary` (b) |
| contextpropagation | 0 | 스레드가 바뀌면 MDC(ThreadLocal)가 사라진다 | `plainThreadLocalIsAbsentOnFreshWorker` (a) |
| | 1 | MDCContext로 요청 ID 전파·복원 | `mdcSnapshotSurvivesAndRestoresWorkerContext` (b) |
| lifecycle | 0 | 코루틴 안의 runBlocking이 스레드를 붙잡는다 | `nestedRunBlockingOccupiesCallerUntilRelease` (c) |
| | 1 | 애플리케이션 스코프 빈: 실패 격리, 종료 시 정리 | `applicationScopeSupervisesAndClosesOwnedJobs` (b) |
| virtualthreads | 0 | 가상 스레드 설정이 바꾸는 것과 바꾸지 않는 것 | `virtualThreadSettingChangesRequestThreadNotExplicitDispatcher` (c) |

## 실행 명령

```bash
# 테스트 클래스 하나
./gradlew :coroutines-core-study:test --tests 'study.core.basics.stage0.Stage0Test'
./gradlew :spring-mvc-study:test --tests 'study.mvc.controller.stage0.Stage0Test'

# 테스트 메서드 하나
./gradlew :spring-mvc-study:test --tests 'study.mvc.transactions.stage0.Stage0Test.suspendTransactionalDoesNotCoverJdbcWork'

# 모듈 전체
./gradlew :coroutines-core-study:test
```

서로 다른 패키지에 같은 이름의 `Stage0Test`가 있으므로 패키지까지 적어 주세요.

## 도구 안내: 코루틴 테스트

이 프로젝트의 테스트는 **실제 시간을 재지 않습니다.** 대신 다음 도구로 결과를 매번 똑같이 재현합니다.

- **`runTest { }`**: 코루틴 테스트를 실행합니다. 안에서 `delay(1000)`을 호출해도 실제로 1초를 기다리지 않고, **가상 시계**만 1000ms 앞으로 갑니다.
- **`currentTime`**: 지금까지 흐른 가상 시간(ms)입니다. "순차면 200ms, 병렬이면 100ms" 같은 비교를 정확하게 할 수 있습니다.
- **`runCurrent()`**: 지금 시각에 실행할 수 있는 코루틴을 모두 실행합니다.
- **`advanceTimeBy(ms)`**: 가상 시계를 ms만큼 앞으로 보냅니다. 그 시각에 깨어날 작업까지 확실히 실행하려면 이어서 `runCurrent()`를 호출합니다.
- **`advanceUntilIdle()`**: 더 실행할 것이 없을 때까지 가상 시간을 진행합니다.
- **`events` 리스트**: 코루틴이 시작/종료/취소될 때 문자열을 기록해 두고, 실행 순서를 `containsExactly(...)`로 검증합니다.

가상 시간은 `Thread.sleep`, JDBC, 실제 네트워크, 직접 만든 스레드풀에는 적용되지 않습니다. 그런 테스트는 실제 스레드와 짧은 실제 지연을 사용합니다.

- [kotlinx-coroutines-test 문서](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- MVC 테스트에서는 `MockMvc`의 첫 `perform`이 비동기 처리 시작까지만 실행합니다. 최종 응답은 `asyncDispatch`로 확인합니다([문서](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html)).

## 처음 실행했을 때 실패하는 테스트

아래 테스트는 **처음부터 실패하는 것이 정상**입니다. 학습을 마치면 모두 통과합니다.
실패 원인은 `AssertionError`(기대값이 다름) 또는 `NotImplementedError`(`TODO()` 미구현)뿐입니다. 2부에서는 미구현 예외가 비동기 처리 중 `ServletException`으로 감싸져 보일 수 있습니다.
그 밖의 예외(컴파일 오류, 스프링 컨텍스트 기동 실패, 타임아웃)가 난다면 테스트 외의 코드를 잘못 건드렸을 가능성이 큽니다.

- 1부: 23개 테스트 전체 (`SmokeTest` 제외)
- 2부: 17개 테스트 전체 (`SmokeTest` 제외)

## 버전

| 항목 | 버전 |
| --- | --- |
| JDK (toolchain) | 25 |
| Spring Boot | 4.1.1 (Spring Framework 7.0.9) |
| Kotlin | 2.3.21 |
| kotlinx-coroutines | 1.10.2 |
| Gradle | 9.3.0 |

버전에 따라 동작이 달라질 수 있는 내용(트랜잭션, 스레드)은 테스트 주석에 "어느 버전에서 확인했는지"를 적어 두었습니다. 위 조합에서 실제로 실행해 확인한 결과입니다.

## 다루지 않는 것

WebFlux 서버, R2DBC(반응형 트랜잭션), 성능 벤치마크는 범위 밖입니다. 2부를 마쳤다면 다음을 더 찾아보세요.

- Spring의 `ReactiveTransactionManager` + `TransactionalOperator.executeAndAwait`(R2DBC에서의 코루틴 트랜잭션)
- Micrometer context-propagation과 Spring Framework 7의 `PropagationContextElement`
- `Flow`를 응답으로 스트리밍하기(SSE)
- 코루틴과 가상 스레드를 실제 부하로 비교하기

## 이 프로젝트가 만들어진 과정

커리큘럼은 Claude와 Codex(gpt-6.1-sol)가 협의해서 정했습니다. 협의 기록은 [`docs/discussion`](docs/discussion), 설계 문서는 [`docs/superpowers/specs`](docs/superpowers/specs)에 있습니다.
