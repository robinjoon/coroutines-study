# Kotlin 코루틴 + Spring MVC 학습 프로젝트 제안

**→ 핵심 제안: 순수 코루틴과 MVC 실습을 두 모듈로 분리하고, JDBC 트랜잭션 경계와 취소 전파를 결정적으로 관찰하는 학습 테스트를 중심에 둔다.** 정답 구현은 제공하지 않는다. 아래 테스트 계약은 콘텐츠 설계자를 위한 명세이며, 학습자용 주석에는 완성된 기대값이나 구현 절차를 그대로 옮기지 않는다.

**→ 기준일: 2026-10-10.** JDK 25, Boot 4.1.1, Kotlin 2.4.21, kotlinx-coroutines 1.11.0, Gradle 9.8.1을 요청된 후보로 사용한다. **최신 버전끼리의 호환성은 별개이며, Gradle 9.8.1은 Kotlin 2.4.21의 공식 완전 지원 범위 밖이다.** 상세한 근거와 대안은 4절에 적는다.

## 1. 패키지, stage, 테스트 설계

### 공통 학습 계약

**→ 구조:** `coroutines-core-study/src/test/kotlin/study/core/<topic>/stageN/StageNTest.kt`, `spring-mvc-study/src/test/kotlin/study/mvc/<topic>/stageN/StageNTest.kt`를 제안한다. 실제 패키지에는 숫자 접두사를 넣지 않고, 아래 번호는 학습 순서만 나타낸다. 각 주제는 `stage0` 관찰, `stage1` 구현, `stage2` 실패 경계 탐구로 구성하되 과제 유형은 테스트마다 지정한다.

**→ 과제 유형:** **a**는 `hasSize(-1)` 또는 `containsExactly("")` 같은 의도적으로 틀린 기대값을 관찰 후 고친다. **b**는 컴파일되는 `TODO()`나 빈 구현을 채운다. **c**는 지정된 한 줄의 주석을 해제하고 전후 차이를 비교한다. b의 고정 검증 조건과 fixture는 수정하지 않고, a만 기대값 수정이 과제임을 명시한다.

**→ 주석 형식:** 각 테스트 바로 위에 개념 2~4문장, 해당 공식 문서의 절 링크, 관찰 지점, 마지막에 **“왜 그런 결과가 나왔을까?”**를 넣는다. c에는 변경할 줄과 복원 방법도 적는다. 컴파일 오류를 관찰하는 코드는 기본 실행에서는 주석으로 유지한다.

**→ 참고 양식 확인:** [우테코 study](https://github.com/woowacourse/java-jdbc/tree/main/study)의 `connectionpool/stage0/Stage0Test.java`에서 개념 설명, 공식 링크, 주석 해제 형식을, `aop/stage0/Stage0Test.java`와 `TransactionHandler.java`에서 미완성 구현 형식을 확인했다. 질문 문구와 a/b/c 구분은 이번 프로젝트의 교육 규칙으로 추가한다.

**→ 검증 도구를 먼저 제공한다.** `runTest`와 사건 기록용 fixture 사용법은 처음에 짧게 안내한다. 이 도구의 구현을 첫 과제로 삼으면 코루틴 개념 대신 테스트 프레임워크를 배우게 된다. fixture는 동기화와 관찰만 담당하고 학습 대상 로직은 담지 않는다.

### 1부: Spring 없는 순수 코루틴

**→ 공식 가이드 순서:** 현재 [목차](https://kotlinlang.org/docs/coroutines-guide.html)는 basics → cancellation/timeouts → flows → coroutines/channels 입문 튜토리얼 → composing → context/dispatchers → channels → exceptions → shared state 순서다. 요청의 괄호 안 나열 순서와 다르므로 **현재 목차를 기준으로 고정**한다. 튜토리얼은 짧은 연결 단원으로 두며 experimental select와 IDE 디버깅은 선택 읽기로 남긴다.

#### 01. `basics` [공식 문서](https://kotlinlang.org/docs/coroutines-basics.html)

**→ `stage0/Stage0Test`: 실행과 중단 관찰.**

`suspendDoesNotStartAnotherCoroutine` (**a**): suspend 선언과 동시 실행을 구분한다. 동일 test dispatcher에서 순차 호출의 사건 목록과 호출 수를 기록해 검증한다.

`launchStartsWhenSchedulerRuns` (**a**): `launch`와 scheduler의 관계를 관찰한다. `StandardTestDispatcher`에서 생성 직후, `runCurrent()` 후의 사건 목록을 따로 검증한다.

**→ `stage1/Stage1Test`: 수명과 대기 구현.**

`parentReturnsAfterChildrenComplete` (**b**): 구조적 동시성을 익힌다. 두 자식을 gate로 멈추고 부모 완료 여부를 관찰한 뒤 gate 해제 후 최종 사건을 검증한다.

`joinWaitsButDoesNotProduceValue` (**b**): `Job` 완료 대기와 반환값 획득을 구분한다. 자식 완료 표식과 부모가 읽은 상태를 검증한다.

**→ `stage2/Stage2Test`: 스레드를 점유하는 경계.**

`runBlockingBridgeBlocksCaller` (**c**): 동기 진입점에서 `runBlocking`의 비용을 관찰한다. 전용 실행기와 시작/해제 latch로 호출자가 아직 반환하지 않았음을 검증하고, 해제 후 종료를 보장한다. 실제 시간 기반 속도 비교는 하지 않는다.

#### 02. `cancellation` [공식 문서](https://kotlinlang.org/docs/coroutines-cancellation.html)

**→ `stage0/Stage0Test`: 취소 상태 관찰.**

`cancelNeedsCompletionBeforeCleanupAssertion` (**a**): 취소 요청과 종료를 구분한다. `cancel()` 직후와 `join()` 후 `finally` 표식 및 `isCompleted`를 비교한다.

**→ `stage1/Stage1Test`: 협력적 취소 구현.**

`boundedCpuLoopChecksCancellation` (**b**): CPU 작업의 취소 확인 지점을 채운다. 이미 취소된 Job으로 유한 루프를 시작하고 완료 작업 수와 취소 예외를 검증한다. 무한 busy loop를 만들어 실습하지 않는다.

`timeoutStopsChildrenAndRunsFinally` (**b**): timeout의 자식 취소를 익힌다. 100 ms 예산과 1,000 ms 가상 지연을 사용하고, timeout 타입, 자식 종료, 정리 표식을 검증한다.

**→ `stage2/Stage2Test`: 예외와 정리 경계.**

`catchingCancellationChangesObservedOutcome` (**c**): 취소 예외를 일반 실패로 삼키는 줄을 켜고 fallback 사건 유무를 비교한다. 취소된 Job이 다시 정상 활성 상태가 되는 것으로 설명하지 않는다.

`suspendingCleanupNeedsExplicitPolicy` (**c**): 정리 중 suspend 호출을 비교한다. `NonCancellable` 적용 전후의 정리 사건을 관찰하되 정리 작업 자체의 상한도 둔다.

#### 03. `flow` [공식 문서](https://kotlinlang.org/docs/coroutines-flow.html)

**→ `stage0/Stage0Test`: cold stream 관찰.**

`coldFlowRunsForEachCollector` (**a**): 선언과 실행을 구분한다. 생성 직후와 두 번 수집한 뒤의 생산자 실행 횟수를 검증한다.

**→ `stage1/Stage1Test`: 변환과 수집 구현.**

`pipelineProducesOnlyRequestedItems` (**b**): `map`, `filter`, `take` 파이프라인을 채운다. 고정 입력의 수집 결과와 upstream 정리 표식을 검증한다.

**→ `stage2/Stage2Test`: 느린 소비자와 오류 경계.**

`bufferChangesProducerProgress` (**c**): buffering을 켜고 consumer gate가 닫힌 동안 생산자가 어디까지 진행했는지 비교한다. 고정 capacity와 단계별 gate를 사용하고 OS 스케줄링 순서를 단언하지 않는다.

`catchDoesNotHandleCollectorFailure` (**a**): upstream과 downstream 실패를 구분한다. 실패를 발생시키는 위치를 따로 정하고 `catch` 표식과 최종 예외를 검증한다.

**→ 범위 제한:** `StateFlow`와 `SharedFlow`는 cold/hot 차이를 설명하는 선택 읽기로 둔다. `flowOn`은 context 단원에서 되돌아와 실습한다. 최신 Flow API를 전부 소개하려 하지 않는다.

#### 04. `channelintro` 연결 단원 [공식 튜토리얼](https://kotlinlang.org/docs/coroutines-and-channels.html)

**→ `stage0/Stage0Test`:** `suspendingPipelineCanOverlapWork` (**a**)는 고정된 두 작업의 시작 gate로 대기 중 다른 작업이 진행하는지 관찰한다.

**→ `stage1/Stage1Test`:** `pipelineHasAnOwnedLifetime` (**b**)는 생산/소비 작업을 부모 scope 안에 연결하고 반환 시 모든 자식이 끝났는지 검증한다.

**→ `stage2/Stage2Test`:** `pipelineCancellationStopsBothEnds` (**c**)는 부모 취소 줄을 켜고 양쪽 정리 표식을 검증한다. channel 상세 정책은 07에서 다룬다.

#### 05. `composition` [공식 문서](https://kotlinlang.org/docs/composing-suspending-functions.html)

**→ `stage0/Stage0Test`: 순차 호출 관찰.**

`sequentialSuspendCallsAccumulateVirtualDelay` (**a**): 두 100 ms 작업의 가상 경과 시간과 사건을 관찰한다. 실시간 stopwatch를 사용하지 않는다.

**→ `stage1/Stage1Test`: 동시 결과 조합 구현.**

`asyncStartsBothTasksBeforeAwaitingResults` (**b**): 두 작업을 시작한 후 결과를 합친다. 두 시작 gate가 모두 열렸는지와 가상 경과 시간, 결과 조합을 검증한다.

**→ `stage2/Stage2Test`: 시작 정책과 실패 전파.**

`lazyAsyncCanBecomeSequential` (**c**): `CoroutineStart.LAZY` 줄을 켜고 시작 시점과 총 가상 시간을 비교한다. 서로 기다리는 교착 예제는 기본 테스트에 넣지 않는다.

`failedChildCancelsSibling` (**a**): `coroutineScope`의 실패 경계를 관찰한다. sibling 시작을 확인한 뒤 실패 gate를 열고 sibling의 `finally`와 최종 예외를 검증한다.

#### 06. `context` [공식 문서](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)

**→ `stage0/Stage0Test`: context 상속 관찰.**

`childrenInheritDispatcherAndGetTheirOwnJob` (**a**): dispatcher 상속과 Job 관계를 구분한다. dispatcher 객체 동일성과 부모/자식 Job 연결을 검증한다.

**→ `stage1/Stage1Test`: 실행 위치와 값 전파 구현.**

`withContextMovesOnlyTheSelectedBlock` (**b**): 전용 dispatcher로 일부 작업만 이동한다. 자신이 만든 실행기의 thread ID와 원래 dispatcher 복귀를 검증한다.

`threadLocalElementRestoresPreviousValue` (**b**): `asContextElement` 적용 지점을 채운다. 전용 단일 스레드의 기존 값, 코루틴 안의 값, 종료 후 원래 값을 검증한다.

**→ `stage2/Stage2Test`: Flow context 경계.**

`flowOnChangesUpstreamNotCollectorContext` (**c**): upstream에 `flowOn`을 적용하고 map/collect에서 dispatcher 객체와 명시적 표식을 비교한다. suspend 이후 스레드가 반드시 바뀐다고 단언하지 않는다.

#### 07. `channels` [공식 문서](https://kotlinlang.org/docs/channels.html)

**→ `stage0/Stage0Test`: rendezvous 관찰.**

`sendWaitsForReceiveOnRendezvousChannel` (**a**): capacity 0의 의미를 관찰한다. send 시작/완료 표식과 receive gate 전후 상태를 검증한다.

**→ `stage1/Stage1Test`: 소유권과 종료 구현.**

`producerClosesChannelAfterLastItem` (**b**): 유한 생산자가 close 책임을 갖는다. 수집 데이터, 소비 루프 종료, 잔존 자식 부재를 검증한다.

**→ `stage2/Stage2Test`: capacity와 취소.**

`bufferCapacityControlsSendSuspension` (**c**): capacity 0과 1을 비교한다. consumer 시작 전 완료된 send 수를 scheduler checkpoint로 검증한다.

`cancelledConsumerDoesNotLeaveProducerBehind` (**b**): 소비 취소 시 생산자 종료 정책을 채운다. 양쪽 Job 종료와 정리 표식을 검증한다. 오래된 actor 빌더는 필수 과제에 넣지 않는다.

#### 08. `exceptions` [공식 문서](https://kotlinlang.org/docs/exception-handling.html)

**→ `stage0/Stage0Test`: launch와 async 예외 관찰.**

`supervisedLaunchReportsUnhandledFailure` (**a**): handler의 통지 역할을 관찰한다. 명시적 독립 SupervisorJob scope에서 handler 전달값을 gate로 받아 검증한다. 테스트 scope에 미처리 예외를 흘리지 않고 scope를 정리한다.

`supervisedAsyncExposesFailureAtAwait` (**a**): Deferred 실패 관찰을 익힌다. 동일한 독립 supervisor fixture에서 `await` 예외와 handler 호출 기록을 검증한다. 일반 자식 async의 부모 취소는 별도 사례임을 주석에 명시한다.

**→ `stage1/Stage1Test`: 부분 실패 정책 구현.**

`supervisorKeepsHealthySiblingRunning` (**b**): 실패한 `async`를 개별 처리하면서 정상 sibling을 유지한다. 결과별 성공/실패 상태, 정상 작업 완료, 부모 완료를 검증한다.

**→ `stage2/Stage2Test`: supervision의 방향.**

`supervisorStillPropagatesParentCancellation` (**c**): 부모 취소 줄을 켜고 supervisor의 모든 자식 종료를 확인한다. supervisor가 취소를 막는다고 설명하지 않는다.

#### 09. `sharedstate` [공식 문서](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html)

**→ `stage0/Stage0Test`: lost update 관찰.**

`splitReadWriteCanLoseAnUpdate` (**a**): 두 작업이 같은 값을 읽은 뒤 쓰도록 두 gate로 강제한다. 작은 고정 작업 수의 최종 값과 읽은 값을 검증한다. 실제 병렬 실행에서 “언젠가 틀릴 것”에 기대지 않는다.

**→ `stage1/Stage1Test`: 공유 상태 보호 구현.**

`mutexProtectsWholeReadModifyWrite` (**b**): 임계구역 전체를 보호한다. 정해진 작업 수의 최종 값과 임계구역 최대 동시 진입 수를 검증한다. 임계구역 안에서 두 참가자를 기다리는 barrier는 넣지 않는다.

**→ `stage2/Stage2Test`: 전략 비교.**

`volatileDoesNotMakeIncrementAtomic` (**c**): volatile 줄을 켜고 동일한 read/write interleaving을 재현한다.

`atomicAndConfinementMeetSameCounterContract` (**b**): atomic 또는 단일 dispatcher confinement으로 같은 계약을 만족시킨다. 실제 다중 스레드 보완 테스트는 정확한 최종 count만 단언하고 성능 순위는 단언하지 않는다.

### 2부: Spring Boot MVC 백엔드 시나리오

**→ 공통 시나리오:** 상품 요약 API가 가격/재고 정보를 조합하고, JDBC로 조회 이력을 저장하며, 요청 ID를 로그에 남긴다. 요청마다 결과가 필요한 작업과 애플리케이션 소유 작업의 수명을 구분한다. 핵심 서비스 검증은 suspend fake client로, MVC/JDBC 연결 검증은 실제 Spring context로 진행한다.

**→ 범위:** WebFlux 서버와 R2DBC 실습은 제외한다. 필수 외부 API 과제는 suspend 인터페이스 + fake, 블로킹 HTTP adapter는 명시적 IO 경계로 진행한다. 실제 non-blocking client 비교는 JDK `HttpClient.sendAsync` 같은 adapter로 확장할 수 있으며 **MVC의 내부 Reactor bridge와 WebFlux 서버는 구분**한다.

#### 01. `controller`: suspend 핸들러와 Servlet async

**→ `stage0/Stage0Test`:** `suspendEndpointStartsServletAsync` (**a**)는 suspend handler 안에 명시적 gate를 두고 `asyncStarted`와 미완성 응답을 관찰한다. gate 해제 후 `asyncDispatch`의 최종 JSON을 검증한다.

**→ `stage1/Stage1Test`:** `suspendControllerUsesServiceWithoutRunBlocking` (**b**)는 서비스 호출 TODO를 채운다. 요청 파라미터 전달, suspend service 실행, dispatch 이후 HTTP 본문을 검증한다.

**→ `stage2/Stage2Test`:** `controllerFailureReachesAdviceAfterAsyncDispatch` (**b**)는 도메인 예외를 `@RestControllerAdvice`에서 처리한다. async 시작 → 실패 gate 해제 → dispatch → 상태/오류 코드 순으로 검증한다. 테스트용 오류 응답 정책은 명시하고 프레임워크 기본값을 외우게 하지 않는다.

근거: [Spring 코루틴 지원](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html), [MVC async 처리](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html), [MockMvc async 검증](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html).

#### 02. `blockingio`: JDBC 실행 경계와 자원 상한

**→ `stage0/Stage0Test`:** `suspendRepositoryStillCallsBlockingJdbc` (**a**)는 동기 JDBC adapter를 gate로 멈추고 점유 중인 thread와 호출 완료 여부를 관찰한다. `suspend` 표시만 바꾸어 JDBC가 비동기화되지 않음을 설명한다.

**→ `stage1/Stage1Test`:** `jdbcAdapterUsesInjectedBlockingDispatcher` (**b**)는 blocking dispatcher 경계를 채운다. 전용 실행기에서 query가 호출됐는지와 반환 데이터를 검증한다. 가상 시간 단위 테스트에서는 dispatcher를 test dispatcher로 교체한다.

**→ `stage2/Stage2Test`:** `databaseBulkheadCapsActiveCalls` (**b**)는 DB 작업 상한 2를 둔다. 5개 요청을 gate가 있는 adapter로 보내 활성 호출 최대 2와 최종 완료를 검증한다. suspend 작업 전체의 상한은 `Semaphore`, 비중단 블로킹 구간의 실행 병렬도는 dispatcher 정책으로 구분한다.

`requestTimeoutDoesNotGuaranteeJdbcStopped` (**c**)는 요청 timeout 줄을 켜고 동기 adapter가 여전히 실행 중인지 관찰한다. 실제 JDBC query/connection timeout과 테스트 종료용 gate를 별도로 둔다.

근거: [Dispatchers.IO](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html). IO는 blocking offload이며 DB 풀을 늘리거나 JDBC를 non-blocking으로 바꾸지 않는다.

#### 03. `transactions`: JDBC 트랜잭션 경계

**→ `stage0/Stage0Test`:** `blockingTransactionalBaselineRollsBack` (**a**)는 일반 동기 `@Transactional` bean에 두 번 쓰고 두 번째 작업에서 runtime 예외를 발생시킨다. **테스트 자체에는 `@Transactional`을 붙이지 않고**, 완료 후 별도 connection으로 잔존 행을 검증한다.

**→ `stage1/Stage1Test`:** `suspendTransactionalBoundaryIsCharacterized` (**c**)는 실제 AOP proxy를 통한 suspend 메서드에 강제 중단 줄을 켠다. 시작/중단 후/dispatcher 이동 후의 `isActualTransactionActive`, connection 식별자, commit/rollback 사건, 외부 조회 행을 기록한다. **확신 없음: 요청 버전 조합에서 AOP 경로별 최초 실행 위치와 정확한 commit 시점은 실행 확인 전 고정 기대값으로 쓰지 않는다.** 무중단/강제 중단/강제 thread 이동 세 경우를 구분한다.

**→ `stage2/Stage2Test`:** `blockingTransactionRunsEntirelyInsideIoBoundary` (**b**)는 suspend orchestration과 별도 bean의 **비-suspend 트랜잭션 작업**을 분리한다. 전체 동기 트랜잭션을 blocking dispatcher 안에서 시작/종료시키고, 외부 API 대기는 밖에서 수행하도록 과제를 낸다. 성공 시 두 행 반영, 실패 시 두 행 롤백, 트랜잭션 종료 후 외부 connection 조회를 검증한다.

근거: [트랜잭션 구현 설명](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html), [7.0.9 TransactionAspectSupport 소스](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionAspectSupport.java). 소스상 reactive manager 분기와 imperative manager 분기가 다르다. **ThreadLocal 트랜잭션을 MDC처럼 복사하는 해결책은 제시하지 않는다.** reactive `executeAndAwait`는 R2DBC용 비교 설명에만 등장시키며 JDBC 해결책으로 쓰지 않는다.

#### 04. `contextpropagation`: 요청 ID, ThreadLocal, MDC

**→ `stage0/Stage0Test`:** `plainThreadLocalIsAbsentOnFreshWorker` (**a**)는 요청 thread의 값을, 값이 없는 별도 단일 worker로 옮긴다. 이동 전/후 값과 worker의 원래 값을 검증한다. 단순 `delay`만으로 유실을 보장하려 하지 않는다.

**→ `stage1/Stage1Test`:** `mdcSnapshotSurvivesSuspensionAndIsRestored` (**b**)는 캡처한 요청 ID를 전파한다. 두 요청의 서로 다른 ID, 중단 전후 로그, 작업 종료 후 worker MDC 복원을 검증한다.

**→ `stage2/Stage2Test`:** `mdcMutationNeedsANewSnapshot` (**c**)는 코루틴 내부의 `MDC.put` 줄을 켜고 재개 후 값을 관찰한다. MDCContext는 snapshot이며 내부 변경을 자동으로 다시 캡처하지 않음을 묻는다.

`frameworkPropagationRequiresRegisteredAccessorAndConfiguration` (**c**, 확장)는 Framework 7의 전파 설정을 켜고 등록된 accessor 값과 미등록 사용자 ThreadLocal을 비교한다. hook은 JVM 전역이므로 독립 test JVM에서 실행한다.

근거: [MDCContext](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-slf4j/kotlinx.coroutines.slf4j/-m-d-c-context/), [PropagationContextElement, since 7.0](https://docs.spring.io/spring-framework/docs/7.1.0-M2/javadoc-api/org/springframework/core/PropagationContextElement.html). MDC, Micrometer observation, security context, request attributes는 각각 별도 전파 대상이다.

#### 05. `aggregation`: 병렬 외부 호출, timeout, 부분 실패

**→ `stage0/Stage0Test`:** `sequentialAggregationWaitsForEachDependency` (**a**)는 가격/재고 각 100 ms fake의 호출 순서와 총 가상 시간을 관찰한다.

**→ `stage1/Stage1Test`:** `parallelAggregationStartsBothDependencies` (**b**)는 병렬 조합 TODO를 채운다. 두 호출이 모두 시작되기 전에는 gate가 열리지 않게 만들고, 결과 조합과 가상 시간을 검증한다.

**→ `stage2/Stage2Test`:** `optionalDependencyFailureKeepsRequiredResult` (**b**)는 필수 가격/선택 재고의 정책을 구현한다. 가격 성공 + 재고 실패에서 명시적 부분 응답을, 가격 실패에서 sibling 취소와 오류 응답을 검증한다. supervisor만 넣고 await 예외를 방치하는 구현은 계약을 통과하지 못하게 한다.

`overallDeadlineCancelsEveryOutstandingCall` (**b**)는 전체 예산 300 ms, 선택 재고 예산 100 ms를 구분한다. 선택 timeout, 전체 timeout, 성공, 외부 취소 네 경우의 호출 종료와 응답 정책을 검증한다. **CancellationException은 부분 실패 값으로 변환하지 않는다.** HTTP adapter timeout은 이 가상 시간 계약과 별도다.

근거: [조합](https://kotlinlang.org/docs/composing-suspending-functions.html), [취소](https://kotlinlang.org/docs/coroutines-cancellation.html), [supervision](https://kotlinlang.org/docs/exception-handling.html). 이 단원은 1부 지식을 백엔드 오류 정책으로 연결하는 핵심 종합 과제다.

#### 06. `lifecycle`: runBlocking 남용과 application scope

**→ `stage0/Stage0Test`:** `nestedRunBlockingOccupiesCallerUntilRelease` (**c**)는 서비스에 넣은 runBlocking 줄을 켠다. 실제 전용 caller 실행기와 latch로 점유 상태를 확인하고 해제 후 종료한다. runTest의 scheduler 안에서 교착을 직접 만들지 않는다.

**→ `stage1/Stage1Test`:** `applicationScopeIsCancelledWhenContextCloses` (**b**)는 애플리케이션 소유 scope bean의 수명 관리를 채운다. 시작된 작업을 확인하고 context를 닫은 후 작업 종료, finally 실행, 사용자가 소유한 실행기 종료를 검증한다. 공용 Dispatchers.IO는 닫지 않는다.

**→ `stage2/Stage2Test`:** `applicationTaskFailureIsObservedWithoutCancellingSibling` (**b**)는 SupervisorJob과 오류 관측 계약을 채운다. 실패 사건 한 번, 정상 sibling 완료, context 종료 시 모두 종료를 검증한다.

`applicationTaskOutlivesRequestButHasNoDurabilityGuarantee` (**a**)는 request parent를 취소한 뒤 명시적 application scope 작업의 수명을 관찰한다. 종료할 때 반드시 정리한다. 이 동작을 저장 보장이나 재시도 보장으로 설명하지 않는다.

근거: [CoroutineScope API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/). 요청 응답에 필요한 작업은 request scope, 종료 정책이 있는 앱 작업은 application scope로 구분한다. 주기 작업/알림의 운영 보장은 별도 queue나 outbox의 문제다.

#### 07. `testing`: 테스트 경계 자체를 학습

**→ `stage0/Stage0Test`:** `runTestControlsOnlySharedTestScheduler` (**a**)는 공유 scheduler의 가상 시간을 관찰한다. 한 fixture의 dispatcher만 바꾸는 c 변형에서는 실제 dispatcher의 지연이 가상 시간에 의해 자동 처리되지 않는 이유를 설명하고 gate로 종료시킨다.

**→ `stage1/Stage1Test`:** `mockMvcAsyncDispatchProducesFinalHttpResponse` (**b**)는 초기 request와 async dispatch를 이어 준다. handler 진입 → test scheduler의 `runCurrent`/gate 해제 → async result 완료 → dispatch 후 본문을 검증한다. scheduler 진행 전에 MockMvc의 동기 대기 matcher를 호출하면 막힐 수 있다.

**→ `stage2/Stage2Test`:** `realServerCompletionDoesNotDependOnTestVirtualClock` (**c**)는 RANDOM_PORT smoke test를 켠다. 실제 HTTP 성공/실패 본문과 종료 후 잔존 작업 부재만 검증한다. **확신 없음: 연결 끊김과 Servlet async timeout이 요청 코루틴을 취소하는 정확한 시점은 컨테이너/버전별 실증이 필요**하므로 MockMvc만으로 보장하지 않는다.

근거: [runTest](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/), [MockMvc async](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html).

#### 08. `virtualthreads`: JDK 25 비교 실습

**→ `stage0/Stage0Test`:** `platformAndVirtualRequestsReturnSameBusinessResult` (**a**)는 일반 동기 MVC + JDBC를 두 설정으로 실행한다. 실제 서버에서 `Thread.isVirtual()` 진단 표식과 동일한 업무 결과를 검증한다. thread 이름 문자열로 virtual 여부를 판단하지 않는다.

**→ `stage1/Stage1Test`:** `virtualThreadBlockingTransactionKeepsLogicalThreadContext` (**b**)는 비-suspend 동기 트랜잭션 과제를 채운다. 가상 thread 내부의 transaction 활성 상태와 실패 후 롤백을 검증한다. carrier가 바뀌어도 동일한 Java thread의 ThreadLocal이라는 점을 묻는다.

**→ `stage2/Stage2Test`:** `bootVirtualThreadSettingDoesNotReplaceCoroutineDispatcher` (**c**)는 `spring.threads.virtual.enabled=true`를 켠다. 코루틴에서 **명시적으로 선택한 별도 platform-thread 실행기**로 이동한 구간의 `isVirtual`과 context를 관찰한다. Boot 설정이 모든 coroutine dispatcher를 바꾸는 것으로 설명하지 않는다.

근거: [Boot virtual threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html), [Boot task execution](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html). **기능 비교가 목적이며, 코루틴과 가상 스레드의 성능 우열을 합격 조건으로 삼지 않는다.**

## 2. Spring MVC 실무 주제와 함정의 우선순위

**1 → P0: suspend 컨트롤러의 실행 원리.** suspend는 compiler의 continuation 변환을 이용하고 Spring은 suspend 호출을 Publisher로 적응시켜 MVC async 처리로 연결한다. **suspend 선언만으로 호출 전체가 IO dispatcher나 별도 worker로 이동하지 않는다.** [7.0.9 CoroutinesUtils](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-core/src/main/java/org/springframework/core/CoroutinesUtils.java)는 기본 경로에서 Unconfined를 사용한다. 초기 실행/재개 thread를 특정 이름으로 고정하는 것은 공개 계약이 아니다.

**2 → P0: blocking JDBC와 IO, 동시성 상한.** IO dispatcher에서 JDBC를 실행해도 실행 중 thread와 DB connection을 점유한다. IO와 Default는 thread를 공유할 수 있어 `withContext(IO)`가 항상 thread ID를 바꾼다는 기대는 틀리다. DB pool, 호출 동시성, client timeout을 함께 설계한다. `limitedParallelism`은 suspend 작업의 전체 개수를 제한하는 semaphore와 같지 않으며, IO view의 elasticity도 따로 설명한다. [공식 IO API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html)

**3 → P0: @Transactional + suspend를 JDBC에 일반화하지 않는다.** imperative manager는 thread-bound이고 reactive manager는 Reactor context 기반이다. Framework 6/7의 suspend/AOP 지원 문구가 **JDBC 트랜잭션의 suspend 전후 보장**을 뜻하지 않는다. 실제 manager 이름, proxy 경유, 강제 중단, thread 이동, 외부 connection의 DB 결과를 확인한다. self invocation은 별도 함정이다. [공식 트랜잭션 설명](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

**4 → P0: request scope, timeout, 부분 실패.** 필수 실패는 전체 실패, 선택 실패는 명시적 부분 응답이라는 정책을 먼저 정한다. 전체 deadline과 의존 API별 timeout을 구분하고 취소는 재전파한다. 서버 async timeout, client read timeout, coroutine timeout은 각각 다른 시계와 경계다. 사용자 연결 종료가 곧바로 DB 작업 종료를 뜻하지 않는다. [Servlet async와 disconnect](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)

**5 → P1: ThreadLocal/MDC와 버전 변화.** 직접 thread 이동에서는 MDCContext/asContextElement 같은 명시적 전파를 먼저 학습한다. Framework **7.0부터 PropagationContextElement가 추가**됐으며 automatic propagation에는 Micrometer 라이브러리, 등록 accessor와 Reactor hook 설정이 관련된다. **확신 없음: Boot 4.1.1의 기본 자동설정만으로 이번 MDC fixture까지 전파되는 범위.** Boot property와 의존성을 고정하고 integration test로 확인한다. Framework 6에는 동일 클래스가 없으며, 6.x도 Reactor/Micrometer 전파 기능의 세부 수준은 minor 버전별로 다를 수 있다. [7 API](https://docs.spring.io/spring-framework/docs/7.1.0-M2/javadoc-api/org/springframework/core/PropagationContextElement.html), [Spring 코루틴 context 설명](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html#coroutines.context-propagation)

**6 → P1: runBlocking의 사용 경계.** 동기 main/제한된 bridge에서 배우되 Spring suspend controller/service 내부에서는 호출자 blocking과 취소 연결 손실의 위험을 관찰한다. 특히 단일 실행기에서 runBlocking 내부가 같은 실행기를 기다리는 예제는 학습 JVM을 멈출 수 있으므로 gate로 통제한 관찰 과제로 대체한다. [runBlocking API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html)

**7 → P1: application scope 빈의 수명과 관측.** SupervisorJob은 실패 격리만 한다. 실패 보고, active job 상한, context close에서 cancel/종료 대기 정책, 소유 executor 정리는 추가 책임이다. GlobalScope나 요청마다 만든 독립 scope를 정답 패턴으로 쓰지 않는다. 앱 scope는 프로세스가 죽으면 유실되므로 금전 처리나 반드시 보낼 메시지의 저장 보장 과제로 확대하지 않는다.

**8 → P1: runTest와 MVC integration의 분리.** 단위 테스트는 가상 시간, MockMvc는 async 결과와 최종 HTTP 응답, 실제 서버는 Servlet/네트워크 생명주기를 검증한다. 이 세 도구가 검증하는 계약을 주석에 적는다. Boot 3의 test annotation import와 Boot 4의 모듈화된 test starter/import를 그대로 섞지 않는다. [Boot 4 migration](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

**9 → P2: 가상 스레드는 Spring MVC 사용자에게 현실적인 대안이다.** 기존 동기 JDBC 트랜잭션을 유지하는 모델과 코루틴의 구조적 동시성 모델을 비교한다. Boot **3.2+**의 virtual-thread 설정은 Java **21+**가 전제이고 Boot 4에서도 제공된다. JDK 25에 JDK 21의 synchronized pinning 설명을 그대로 적용하면 안 된다. JDK 24의 JEP 491 변화는 비교 읽기로 두며 native/foreign 호출 등 남은 제약과 driver별 영향은 별도 확인한다. [Boot 3.2 공식 가이드](https://docs.spring.io/spring-boot/docs/3.2.0-RC2/reference/pdf/spring-boot-reference.pdf), [JEP 491](https://openjdk.org/jeps/491)

**→ 버전별 검증 범위:** 기본 지원 여부(MVC suspend, core+reactor 필요)는 [Framework 6.2 문서](https://docs.spring.io/spring-framework/reference/6.2/languages/kotlin/coroutines.html)와 7 문서에서 확인할 수 있지만, **내부 dispatcher, proxy의 suspend 호출 경로, cancellation 연결, 자동 context 전파는 정확한 patch와 classpath를 기록해야 한다.** Boot 4.1.1 BOM은 Framework 7.0.9다. 7.1 preview 문서의 예제를 신규 API라고 가정하지 말고 `Since`와 실행 대상 jar를 대조한다. Boot 3 비교는 3.5.x 같은 구체적 버전이 결정될 때 별도 소규모 matrix로 수행하고, 현재 설계에서 두 major를 동시에 지원한다고 약속하지 않는다.

## 3. flaky 방지 원칙

**1 → 가상 시간은 공유 scheduler에서만 통제한다.** 서비스의 dispatcher와 scope를 주입하고 모든 TestDispatcher는 같은 TestCoroutineScheduler를 사용한다. `runCurrent`, `advanceTimeBy`, `advanceUntilIdle`의 역할을 구분한다. **advanceTimeBy의 도착 시각 작업은 runCurrent로 명시적으로 실행**해 경계 단언을 모호하게 하지 않는다. [test scheduler API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/kotlinx.coroutines.test/-test-coroutine-scheduler/advance-time-by.html)

**2 → IO, Thread.sleep, DB, 네트워크에는 가상 시간을 기대하지 않는다.** 실제 실행 테스트는 별도 태그와 bounded wait를 사용한다. 순수 테스트의 timeout fixture는 100/300/1,000 ms 가상 시간을 사용하고, 실제 테스트의 대기 상한은 예를 들어 **5초**, 전체 테스트 상한은 **10초**로 분리한다. CI가 느리면 전체 상한 조정은 허용하되 성능 조건으로 만들지 않는다. non-cooperative 블로킹은 interrupt만 믿지 않고 해제 gate와 finally 정리를 둔다.

**3 → 순서는 사건의 선후 관계로 검증한다.** CompletableDeferred, Channel, latch로 필요한 진입/완료 시점을 고정한다. 서로 독립적인 두 자식의 println 순서나 `yield` 한 번으로 보장되지 않는 interleaving은 단언하지 않는다. 다중 thread 사건 기록은 thread-safe 수집기를 사용한다.

**4 → thread 이름은 자신이 만든 실행기에만 한정한다.** coroutine debug 옵션은 이름에 suffix를 붙일 수 있다. 공용 `DefaultDispatcher-worker-N`, Tomcat 요청 thread 이름, “delay 후 다른 thread”를 정답으로 삼지 않는다. 전용 thread의 ID, dispatcher 객체, CoroutineName, `Thread.isVirtual()` 등 목적에 맞는 표식을 선택한다.

**5 → 무한 수명과 미처리 예외를 테스트 fixture가 소유한다.** collector, channel producer, application scope, executor, MDC, hook은 테스트 종료 시 복원한다. 영구 수집은 backgroundScope 또는 명시적 cancel/join을 사용한다. 일반 exception 테스트를 runTest 자식에서 무처리 launch로 터뜨려 teardown에서 실패하게 하지 않는다.

**6 → JDBC rollback은 테스트 바깥 connection으로 본다.** 테스트 자체의 transaction, 동일 connection 캐시, self invocation이 실패를 가릴 수 있다. DB는 테스트별 고유한 in-memory H2 이름을 쓰고 파일 DB는 만들지 않는다. H2가 운영 DB의 query 취소, driver interrupt, pool 고갈까지 대표한다고 주장하지 않는다.

**7 → MockMvc의 동기 대기와 가상 scheduler를 교착시키지 않는다.** request 수행 후 scheduler/gate를 진행시키고, async 결과 완료를 확인한 뒤 asyncDispatch한다. MockMvc의 대기 상한은 실제 시간이다. 별도의 실제 서버 smoke test로 Servlet 동작을 보완하며 외부 인터넷 호출은 사용하지 않는다.

**8 → 성능은 학습 결과의 합격 조건이 아니다.** 순차/병렬 가상 시간과 활성 작업 수는 단언할 수 있다. 실제 처리량, CPU 사용량, 코루틴 대 가상 스레드 속도 순위는 선택 실험 기록으로만 남긴다.

## 4. 빌드 구성 제안

### 모듈 선택

**→ 권고: 루트 + 두 모듈만 사용한다.** `coroutines-core-study`는 Kotlin/JUnit/coroutines만, `spring-mvc-study`는 MVC/JDBC와 integration test만 갖는다. 1부 classpath에 Spring이 섞이지 않고 context 기동 비용도 없다. 2부가 1부 미완성 구현에 의존하지 않도록 모듈 간 코드 의존은 두지 않는다. 공통 Gradle convention plugin이나 별도 fixture 모듈은 초기에는 만들지 않는다.

**→ 단일 모듈의 장점도 있다.** 설정과 IDE 탐색은 간단하지만 1부가 Spring 없는 환경이라는 의미가 코드 사용 금지 수준으로 약해진다. 단일 모듈로 결정한다면 별도 JVM test suite/source set로 classpath를 분리해야 하므로, 이번 요구에는 두 모듈이 더 단순하다.

### 버전과 호환성

**→ 요청 후보와 BOM의 차이를 명시한다.** [Boot 4.1.1 dependencies POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)을 직접 읽어 **Framework 7.0.9, Kotlin 2.3.21, coroutines 1.10.2, JUnit Jupiter 6.0.3**를 확인했다. 따라서 Kotlin 2.4.21/core·test·reactor·slf4j 1.11.0 사용은 BOM 기본값의 의도적인 override다. Kotlin compiler plugin, stdlib, reflect 버전과 모든 coroutines artifact를 함께 정렬한다.

**→ Gradle 9.8.1은 실험 후보, 9.7.0은 보수적 대안이다.** [Kotlin 공식 호환 표](https://kotlinlang.org/docs/gradle-configure-project.html)는 KGP **2.4.20~2.4.21의 완전 지원 범위를 7.6.3~9.7.0**으로 적는다. 범위 밖이 곧 실행 불가라는 뜻은 아니지만 이번 조합의 호환성에는 **확신 없음**이다. 요청대로 9.8.1을 유지하려면 scaffold 단계에서 wrapper 실행, 양 모듈 test compile, MVC/JDBC smoke를 통과한 뒤 확정한다. 실패하거나 지원 범위 내 구성이 우선이면 9.7.0을 제안한다.

**→ JDK 25는 Gradle 9.1.0+에서 실행 지원한다.** [Gradle 호환 표](https://docs.gradle.org/current/userguide/compatibility.html)에 근거해 9.7.0/9.8.1 모두 이 조건을 만족한다. [Boot 4.1.1 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)은 Java 17~26과 Gradle 8.14+/9.x를 지원한다. Java/Kotlin toolchain은 25로 맞추고 JVM target도 양쪽을 일치시킨다. compiler의 target 25 처리와 IDE 지원은 scaffold smoke에서 확인한다.

### Gradle Kotlin DSL과 의존성

**→ 플러그인:** 루트에서 Kotlin JVM 2.4.21, Kotlin Spring 2.4.21, Boot 4.1.1을 버전 고정 후 필요한 모듈에 적용한다. 1부에는 Kotlin JVM만, 2부에는 Kotlin JVM/Spring/Boot를 적용한다. JDBC 중심이므로 JPA/noarg 플러그인은 넣지 않는다. BOM은 Gradle platform 방식 또는 dependency-management 방식 **하나만** 선택하고 override 정책을 명확히 한다.

**→ 1부:** `kotlinx-coroutines-core:1.11.0`, test에 `kotlinx-coroutines-test:1.11.0`, JUnit Jupiter, AssertJ와 JUnit Platform launcher를 둔다. 2부와 Jupiter 버전을 맞추면 비교가 쉽다. Spring BOM을 1부에 가져오는 대신 필요한 test 버전만 명시한다. `useJUnitPlatform()`을 양쪽에 적용한다. “JUnit5 학습 테스트 스타일”과 “실행 엔진은 BOM의 Jupiter 6”을 구분해 문서화한다.

**→ 2부 runtime:** `spring-boot-starter-webmvc`, `spring-boot-starter-jdbc`, `kotlin-reflect`, Boot에 맞는 Kotlin JSON module, coroutines core/reactor/slf4j를 둔다. JSON은 **Boot 4의 Jackson 3 기본**을 전제로 BOM과 starter 구성을 확인하고 Jackson 2의 좌표/import를 기계적으로 복사하지 않는다. H2는 우선 test runtime에만 둔다. 실제 앱 실행까지 목표가 확대될 때 runtime datasource 설정을 추가한다.

**→ 2부 test:** Boot 4의 `spring-boot-starter-webmvc-test`와 필요한 JDBC test 지원, coroutines-test 1.11.0을 사용한다. 단순 JDBC bean 테스트에는 과도한 test starter를 추가하지 않는다. MockMvc/Boot test annotation의 정확한 import는 해당 버전 API에서 확인한다. 외부 API mocking library 없이 gate 기반 fake부터 시작한다. [Boot starter 목록](https://docs.spring.io/spring-boot/reference/using/build-systems.html), [Boot 4 migration](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

**→ MVC에도 coroutines-reactor가 필요하다.** 서버를 WebFlux로 전환하려는 의존성이 아니라 Spring의 suspend 적응 경로를 위한 bridge다. `spring-boot-starter-webflux`를 필수로 넣지 않는다. 선택 WebClient 실습이 생기면 Boot 4의 webclient starter와 실제 HTTP connector를 확인하되 서버 application type은 SERVLET로 검증한다. [공식 coroutine dependencies](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html#coroutines.dependencies)

### wrapper와 실행 경험

**→ wrapper는 다음 구현 단계의 필수 산출물이다.** maintainer가 공식 배포본으로 `gradlew`, `gradlew.bat`, wrapper jar/properties를 생성하고 distribution SHA-256을 고정한다. 학습자는 설치 없이 `./gradlew`를 사용한다. **이번 단계에는 wrapper를 포함한 어떤 scaffold도 생성하지 않는다.**

**→ 실행 단위:** `./gradlew :coroutines-core-study:test --tests 'study.core.basics.stage0.Stage0Test'`처럼 FQCN으로 선택한다. 서로 다른 패키지의 `Stage0Test` 이름 중복은 의도된 구조다. 순수/서비스 가상 시간 테스트, MVC/JDBC integration, 실제 실행 실험을 태그로 구분해 선택할 수 있게 한다.

**→ CI 정책:** 초기 learning tests의 실패는 의도된 상태이며 infra 실패와 구분해야 한다. a/b/c를 전부 제외해 전체 test를 초록색으로 만드는 방식은 학습 목적을 훼손한다. 제공되는 infrastructure smoke는 별도 태그로 실행하고, 선택 stage의 학습 실패는 그대로 보여 준다. coverage 수치나 “전체 테스트 반드시 성공”을 초기 배포 합격 기준으로 쓰지 않는다.

## 5. 놓치기 쉬운 리스크와 반대 의견

**1 → 가장 큰 리스크는 suspend가 안전한 JDBC 트랜잭션을 자동 제공한다는 인상이다.** 선언만 바꾸는 예제보다 동기 baseline, 실제 중단 관찰, 경계 분리 구현을 연속해서 보여 줘야 한다. unsafe 사례의 고정 기대값은 버전 실증 뒤 정하고, 트랜잭션 실패를 a 과제로 단순히 “맞는 결과로 고쳐라”라고 내지 않는다.

**2 → 공식 문서 예제도 그대로 정답 패턴으로 복사하지 않는다.** 독립 async/GlobalScope 예시는 현상을 설명할 수 있지만 MVC 요청 처리의 수명 소유권을 숨긴다. 참고 링크는 읽기 자료이며 이번 backend의 운영 패턴과 같은 의미가 아니다.

**3 → 정답 없음과 신뢰할 수 있는 콘텐츠는 양립해야 한다.** 학습자 저장소에는 답안을 넣지 않되 설계자는 fake/gate/검증 fixture의 종료 가능성과 버전 동작을 사전 확인해야 한다. 이번 문서는 설계 제안이고 실행 검증을 했다는 뜻이 아니다. **실제 scaffold 검증과 버전별 characterization은 다음 구현 단계에 남아 있다.**

**4 → a만 많으면 코드를 읽지 않고 assert를 수정하는 활동이 된다.** stage0에는 a를 활용하고 stage1/2는 b와 c로 인과관계를 확인한다. 각 stage의 완료 조건은 테스트 통과 + “왜 그런 결과가 나왔을까?”에 대한 2~3문장 설명이다. 교육 평가를 reflection/source text 검사로 대신하지 않는다.

**5 → 모든 주제에 stage를 세 개 두면 분량이 커진다.** 채널 연결 튜토리얼과 advanced propagation/real-server 실험은 확장으로 표시한다. 필수 진행에서는 1부의 각 주제 핵심 계약, 2부 controller → blockingio → transactions → contextpropagation → aggregation을 먼저 완료한다. lifecycle/testing/virtualthreads는 반드시 소개하되 깊은 실험은 후반으로 배치한다.

**6 → MVC 사용자에게 코루틴이 항상 필요한 것은 아니다.** JDBC 중심 동기 CRUD에는 JDK 25 가상 스레드가 기존 transaction/ThreadLocal 모델을 유지하는 선택지다. 코루틴의 이점은 특히 외부 호출 조합, 취소, 수명 구조를 다룰 때 설명해야 한다. 실제 운영 선택은 처리량 실험과 유지보수 비용까지 봐야 하며 학습 테스트의 가상 시간 결과로 결정하지 않는다.

**7 → 취소 실습이 서버 자원 보호를 보장하지 않는다.** fake는 협력적으로 멈추지만 JDBC driver나 blocking HTTP client는 다를 수 있다. 실제 connection/query/read timeout과 동시성 상한을 설정하지 않으면 요청은 끝났어도 작업은 남는다. MockMvc로 client disconnect 전파를 증명할 수 있다고 쓰지 않는다.

**8 → 최신 버전 채택은 교육 내용을 흐릴 수 있다.** KGP/Gradle 지원 상한, BOM override, Jackson 3, Jupiter 6, Boot test 패키지 변화가 동시에 들어온다. 최신 후보는 유지하되 재현 가능한 smoke 결과를 확보하고, 문제가 나면 지원 범위 안의 Gradle 또는 BOM 기준 의존성으로 좁히는 선택을 코디네이터가 명시해야 한다.

**9 → stage별 Spring bean을 넓게 스캔하면 다른 과제가 섞인다.** 같은 상품 API의 여러 미완성 controller/service가 동시에 등록되지 않게 stage별 명시적 import와 test configuration으로 context를 구성한다. broad component scan, 공유 DB 이름, hook 전역 설정으로 다른 stage의 실패가 번지는 상황을 방지한다. fixture의 시작/해제 gate는 기본 스레드 실행 정책의 일부가 아니라 테스트 전용 관찰 장치임을 표시한다.

**→ 다음 결정:** 코디네이터는 이 제안과 자신의 설계를 대조해 단원 분량, Gradle 9.8.1 검증 또는 9.7.0 선택, unsafe transaction characterization의 기대값 확정 순서를 결정한다. **그 이후에만 scaffold와 학습 테스트를 만든다.**
