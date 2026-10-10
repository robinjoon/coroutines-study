# 커리큘럼 2차 협의 응답

**→ 권고 합의안은 30개 stage·40개 테스트, Boot 4.1.1 BOM 기준 Kotlin 2.3.21·coroutines 1.10.2·Gradle 9.3.0이다.** 분량과 장치를 줄이되, 트랜잭션 경계·취소 전파·실제 thread 이동을 관찰하는 검증은 유지한다. 아래 조건을 포함한 안에 대한 **내 남은 이견은 0개**이며, 코디네이터의 최종 수락은 아직 확인하지 않았다.

## 1. A~G 항목별 입장과 근거

### A. 분량: 부분동의, 30개 stage로 확정 제안

**→ 입문자에게 51개 stage는 과하다는 판단에 동의한다.** channelintro는 channels에 흡수하고, testing은 README 도구 안내와 controller에 흡수한다. virtualthreads는 관찰형 stage0 하나로 줄인다. 모든 단원에 stage2를 두는 기존 제안은 철회한다.

**→ 제안의 산술은 조정해야 한다.** 1부 8×2=16, cancellation/exceptions에 stage2를 각각 추가하면 18, 2부 필수 6×2=12와 virtualthreads 1을 합치면 **31개**다. **cancellation에만 stage2를 남기고 exceptions의 부모 취소는 stage1에 포함**하면 1부 17 + 2부 13 = **30개**가 된다. 예외 단원은 stage를 늘리는 대신 stage0/1에 각각 테스트 두 개를 둔다.

**→ 테스트 수는 1부 23 + 2부 17 = 40개다.** 채널 capacity 비교, Flow buffer/catch 심화, MDC 내부 변경 snapshot, Micrometer 자동 전파 설정, 가상 스레드 성능 실험은 추가 테스트 없이 주석의 선택 읽기로 남긴다. 생략한 범위를 이름으로 알리되 기본 과정에 다시 끼워 넣지 않는다.

### B. 순서: 부분동의, 개념 의존 순서 채택

**→ 제안한 순서에 동의한다.** basics → composition → cancellation → context → exceptions → flow → channels → sharedstate로 진행한다. async 조합을 먼저 경험하고 실패·취소를 공부하며, context 이후에 flowOn을 배우면 앞뒤 단원을 오가는 일이 줄어든다.

**→ 다만 취소가 async를 반드시 전제한다는 근거에는 동의하지 않는다.** Job, launch, join은 basics에서 익힐 수 있고 취소는 그것만으로도 설명할 수 있다. composition을 먼저 두는 이유는 필수 전제 때문이 아니라, 이후 실패·timeout을 적용할 구체적인 작업 조합이 생기기 때문이다. [조합 공식 가이드](https://kotlinlang.org/docs/composing-suspending-functions.html), [취소 공식 가이드](https://kotlinlang.org/docs/coroutines-cancellation.html)

**→ 최초 요구의 “공식 가이드 순서”를 변경하는 합의안임을 최종 스펙에 명시한다.** 공식 가이드는 주제별 읽기 자료로 사용하고 각 테스트 주석에 해당 절 링크를 붙인다. 공식 목차를 따른다고 표현하지 않는다.

### C. 가독성: 부분동의, events·가상 시간 기본 + 필요한 실제 경계 유지

**→ 1부에서 gate를 기본 장치로 사용하지 않는 데 동의한다.** 단일 StandardTestDispatcher와 공유 scheduler, `currentTime`, `runCurrent()`, events를 기본으로 한다. 자식 시작/완료와 취소는 가상 시간을 단계별로 진행해 관찰하고, 공용 fixture는 필요할 때 사건 기록기 하나까지만 둔다. 단일 thread 테스트의 사건 기록에는 평범한 리스트로도 충분하다. [coroutines-test 공식 문서](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)

**→ 실제 thread·블로킹 경계까지 가상 시간으로 대체하는 데는 반대한다.** 컨텍스트 실습의 전용 실행기, MVC async 완료, blocking JDBC adapter의 점유, runBlocking의 caller 점유에는 해당 경계를 통제하는 최소 장치가 필요하다. 특히 MockMvc 대기 중 scheduler가 진행하지 못하는 교착과 “IO면 thread가 반드시 바뀐다”는 잘못된 단언을 피해야 한다.

**→ 합의 방식:** 1부의 lost update는 명시적인 read → yield → write와 고정 test scheduler로 재현한다. 실제 다중 thread 경쟁의 일반적인 실행 순서를 증명한다고 설명하지 않는다. 2부에서는 필요한 테스트 내부에 시작/해제 gate 또는 latch를 직접 두고, 범용 동기화 DSL은 만들지 않는다. 실제 thread에서 공유하는 사건 목록만 thread-safe하게 만든다.

### D. 버전: (1)에 동의, Gradle 9.3.0 권고

**→ 이번 학습 목표에는 BOM override의 이득보다 정렬 비용이 크다.** launch/async, 취소, Flow, MDCContext, MVC suspend·JDBC 경계는 1.10.2로 학습할 수 있다. Kotlin 2.4.21·coroutines 1.11.0에서만 가능한 기능을 필수 목표로 삼지 않으므로 **Boot BOM 그대로 따르는 (1)**을 선택한다.

**→ 공식 KGP 2.3.21의 Gradle 완전 지원 범위는 7.6.3~9.3.0이다.** 상한은 **9.3.0**이며, JDK 25에서 Gradle 실행 지원의 하한은 **9.1.0**이다. Boot 4.1.1이 지원하는 Gradle 9.x 범위와의 교집합 안에서 **9.3.0**을 고른다. [Kotlin KGP 호환 표](https://kotlinlang.org/docs/gradle-configure-project.html), [Gradle Java 호환 표](https://docs.gradle.org/current/userguide/compatibility.html), [Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)

**→ “BOM이 관리하는 조합”과 “우리 테스트가 검증된 조합”은 구분한다.** Boot 4.1.1 BOM은 Framework 7.0.9, Kotlin 2.3.21, coroutines 1.10.2를 관리하지만 그것만으로 이번 미션의 suspend/AOP·MDC 동작이 입증되지는 않는다. 실측을 생략하는 근거로 쓰지 않는다. [Boot 4.1.1 BOM 원문](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)

### E. 정답 없음 + 신뢰성: 부분동의, 풀이 검증과 배포 상태 검증 모두 필요

**→ 비공개 참조 풀이로 검증한 뒤 학습자 상태로 되돌리는 방식에 동의한다.** 참조 풀이는 이후 구현 단계의 저장소 밖 scratch 복사본에서 실행한다. 학습자 저장소에는 풀이 소스·패치·컴파일 결과를 옮기지 않는다. 이 협의 단계에서는 scratch나 scaffold를 생성하지 않는다.

**→ “모든 테스트 green”만으로는 충분하지 않다.** c는 주석 해제 전후의 두 실행 경로를 모두 확인한다. b는 정상뿐 아니라 timeout·취소·실패에서도 계약을 지키는지 확인한다. 복구 후에는 **의도된 실패 목록과 실제 실패의 FQCN·테스트명·실패 원인이 일치**해야 한다. 실패 개수만 같거나, TODO 대신 잘못된 bean 설정으로 실패하면 합격이 아니다.

**→ 의도된 실패 목록은 README에 둔다.** a는 지정된 기대값 단언 실패, b는 지정된 TODO/미구현 계약 실패, c는 기본 상태의 기대 결과와 변경 후 관찰을 구분한다. 컴파일 실패, context 기동 실패, teardown의 미처리 예외, 종료되지 않는 작업은 의도된 실패에 포함하지 않는다.

**→ 트랜잭션과 MDC 실측으로 고정할 항목도 좁힌다.** 정확한 Boot/BOM 조합, manager, proxy 경유 여부, 강제 중단과 전용 thread 이동 조건을 기록한다. transaction 활성 상태와 **별도 connection의 commit/rollback 결과**를 함께 검증하고, MDC는 전파 후 복원까지 확인한다. 실측 전에는 **확신 없음**, 실측 후에는 **그 구성에서 확인한 결과**라고 적는다. 한 구성의 관찰을 모든 Spring 6/7에 일반화하지 않는다.

### F. YAGNI: 동의, CI·태그 제거 + README 실패 목록 유지

**→ 별도 CI 정책과 태그 체계는 이번 범위에서 뺀다.** README에는 모듈/단원 진행 순서, `--tests` 실행 명령, a/b/c, runTest 도구 안내와 E의 의도된 실패 목록만 둔다. 테스트 선택은 FQCN으로 충분하다.

**→ 검증 자체를 빼는 것은 아니다.** 코디네이터가 참조 풀이와 복구 상태를 로컬에서 검증하고 결과를 확인한다. 실제 thread 실행기의 종료, stage별 bean 격리, DB 상태 분리도 구현의 필수 조건으로 유지한다. 이를 위한 별도 정책 문서는 만들지 않는다.

### G. 구현 분담: 동의, 공유 빌드 파일의 소유자까지 지정

**→ 승인 후 Codex가 1부, Claude가 2부를 구현하고 서로 교차 리뷰하는 분담에 동의한다.** 1부의 scheduler·예외 검증과 2부의 Spring/JDBC 경계를 각 담당자가 집중해서 만들 수 있다. 모듈 간 소스 의존은 두지 않는다.

**→ 공유 파일은 Claude가 단독 소유하는 안을 제안한다.** 루트 settings/build, wrapper와 README는 Claude, `coroutines-core-study/**`는 Codex, `spring-mvc-study/**`는 Claude가 편집한다. 먼저 버전과 빌드 골격을 고정한 다음 모듈 작업을 나누고, 교차 리뷰는 지적을 전달한 뒤 원래 소유자가 수정한다. 참조 풀이 검증과 학습자 상태 복구를 마친 뒤 최종 검수한다. 이번 문서는 구현 착수나 하위 작업 디스패치를 하지 않는다.

## 2. 최종 합의 커리큘럼 표

**→ 총량: 1부 8단원·17 stage·23개 테스트, 2부 7단원·13 stage·17개 테스트 = 30 stage·40개 테스트다.** 이는 코디네이터 수락을 위한 최종 합의안이다. 표의 테스트 수는 이름 기준이며, 성공/실패 입력을 바꿔 실행하는 추가 case 수와는 구분한다.

**→ 표 규칙:** core = `coroutines-core-study`, mvc = `spring-mvc-study`다. 경로는 각각 `study.core.<단원>.stageN.StageNTest`, `study.mvc.<단원>.stageN.StageNTest`로 한다. **a** 기대값 관찰·수정, **b** TODO 구현, **c** 주석 해제 전후 관찰이며, 각 테스트 주석은 개념·공식 링크·“왜 그런 결과가 나왔을까?”를 포함한다.

### 1부 01. basics

[공식 문서](https://kotlinlang.org/docs/coroutines-basics.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | basics | 0 | `suspendDoesNotStartAnotherCoroutine` | a | suspend 선언만으로 동시 실행되지 않음을 순차 events로 관찰 |
| core | basics | 0 | `launchStartsWhenSchedulerRuns` | a | launch 생성 직후와 runCurrent 후의 사건 차이 |
| core | basics | 1 | `parentReturnsAfterChildrenComplete` | b | 가상 시간으로 자식 완료와 부모 반환의 선후 관계 검증 |
| core | basics | 1 | `joinWaitsButDoesNotProduceValue` | b | Job 완료 대기와 결과값 반환을 구분 |

### 1부 02. composition

[공식 문서](https://kotlinlang.org/docs/composing-suspending-functions.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | composition | 0 | `sequentialCallsAccumulateVirtualDelay` | a | 두 100 ms 작업을 순차 호출할 때의 currentTime 관찰 |
| core | composition | 1 | `asyncStartsBothTasksBeforeAwaiting` | b | 두 async 시작 사건과 결과 조합·가상 경과 시간 검증 |

### 1부 03. cancellation

[공식 문서](https://kotlinlang.org/docs/coroutines-cancellation.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | cancellation | 0 | `cancelRequestAndCompletionAreDifferent` | a | cancel 요청 직후와 join 후 finally·Job 상태를 구분 |
| core | cancellation | 1 | `timeoutStopsChildrenAndRunsFinally` | b | 100 ms 예산으로 1,000 ms 자식 지연을 취소·정리 |
| core | cancellation | 1 | `cancellationDoesNotBecomeFallback` | b | 취소된 자식의 CancellationException 재전파와 fallback 부재 |
| core | cancellation | 2 | `suspendingCleanupNeedsExplicitPolicy` | c | NonCancellable 적용 전후의 유한한 suspend 정리 관찰 |

**→ 협력적 취소도 이 단원에서 설명한다.** delay와 취소 확인 지점이 없는 CPU 작업의 차이를 주석으로 짚는다. 무한 busy loop는 실행 과제로 만들지 않는다. 취소된 Job이 fallback으로 정상 복구되는 것으로 설명하지 않는다.

### 1부 04. context

[공식 문서](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | context | 0 | `childrenInheritDispatcherButHaveOwnJob` | a | dispatcher 객체 상속과 부모/자식 Job 관계 관찰 |
| core | context | 1 | `withContextMovesOnlySelectedBlock` | b | 전용 실행기의 thread ID와 원래 context 복귀 검증 |

### 1부 05. exceptions

[공식 문서](https://kotlinlang.org/docs/exception-handling.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | exceptions | 0 | `supervisedLaunchReportsUnhandledFailure` | a | 독립 supervisor scope의 handler 사건을 관찰 |
| core | exceptions | 0 | `supervisedAsyncExposesFailureAtAwait` | a | Deferred 실패와 await·handler 관측 차이 |
| core | exceptions | 1 | `supervisorKeepsHealthySiblingRunning` | b | 개별 실패 처리로 정상 sibling 결과를 보존 |
| core | exceptions | 1 | `supervisorStillPropagatesParentCancellation` | b | supervisor도 부모 취소 시 자식을 종료 |

**→ 예외 fixture는 test dispatcher를 쓰되 독립 SupervisorJob을 명시한다.** handler 사건을 events에 기록하고 테스트가 scope를 정리한다. 일반 async 자식도 await 전 부모를 취소할 수 있다는 설명을 붙여 “async 예외는 await할 때만 생긴다”는 오해를 막는다.

### 1부 06. flow

[공식 문서](https://kotlinlang.org/docs/coroutines-flow.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | flow | 0 | `coldFlowRunsForEachCollector` | a | 생성 시점과 두 번 collect한 생산 횟수를 구분 |
| core | flow | 1 | `pipelineProducesOnlyRequestedItems` | b | map/filter/take 결과와 upstream 정리 검증 |
| core | flow | 1 | `flowOnChangesUpstreamNotCollector` | b | 공유 scheduler의 서로 다른 dispatcher 객체로 context 경계 검증 |

### 1부 07. channels

[공식 문서](https://kotlinlang.org/docs/channels.html), [흡수한 입문 튜토리얼](https://kotlinlang.org/docs/coroutines-and-channels.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | channels | 0 | `sendWaitsForReceiveOnRendezvousChannel` | a | runCurrent 전후 send 완료 사건으로 capacity 0 관찰 |
| core | channels | 1 | `ownedProducerClosesAndCancelsWithConsumer` | b | 정상 수집에서는 close, 소비 조기 종료에서는 생산자 정리 |

### 1부 08. sharedstate

[공식 문서](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| core | sharedstate | 0 | `splitReadYieldWriteLosesAnUpdate` | a | 단일 test scheduler의 명시적 중단 지점으로 lost update 재현 |
| core | sharedstate | 1 | `mutexProtectsWholeReadModifyWrite` | b | 임계구역 전체 보호로 count·동시 진입 상한 검증 |

**→ 보호 구현의 임계구역 안에서는 두 참가자를 기다리는 barrier를 쓰지 않는다.** read/write 중간의 유한한 suspend 지점은 허용하되 잠금을 얻지 못한 다른 작업을 기다리게 만들지 않는다. plain `counter++`가 단일 thread에서 저절로 경쟁한다고 주장하지 않는다.

### 2부 01. controller

[Spring 코루틴 지원](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html), [MockMvc async](https://docs.spring.io/spring-framework/reference/testing/mockmvc/hamcrest/async-requests.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| mvc | controller | 0 | `suspendEndpointStartsServletAsync` | a | 명시적 중단 후 asyncStarted·최종 dispatch의 차이 관찰 |
| mvc | controller | 1 | `asyncDispatchProducesServiceResponse` | b | runBlocking 없이 서비스 호출 후 최종 HTTP JSON 검증 |
| mvc | controller | 1 | `controllerFailureReachesAdviceAfterDispatch` | b | 비동기 실패가 Advice를 거쳐 지정 오류 응답으로 전달 |

**→ testing 단원의 핵심을 여기서 유지한다.** request 수행 후 scheduler/gate를 진행시키고 async 결과를 기다린 뒤 asyncDispatch한다. 첫 요청의 HTTP 상태만 보고 최종 결과라고 판단하지 않는다. MockMvc는 실제 client disconnect 검증 도구가 아니다.

### 2부 02. blockingio

[Dispatchers.IO](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| mvc | blockingio | 0 | `suspendRepositoryStillOccupiesCaller` | a | 제어 가능한 blocking adapter로 suspend와 thread 점유 구분 |
| mvc | blockingio | 1 | `jdbcAdapterUsesInjectedBlockingDispatcher` | b | 명시적 blocking dispatcher 안의 JDBC 실행 위치·결과 검증 |
| mvc | blockingio | 1 | `databaseBulkheadCapsActiveCalls` | b | 5개 요청에서 semaphore 상한 2로 활성 호출 수 제한 |

**→ timeout의 한계는 반드시 설명한다.** coroutine 취소는 blocking JDBC 중단을 보장하지 않는다. query/connection timeout과 DB pool은 별개이며 IO로 옮겨도 JDBC는 blocking이다. 점유 관찰은 통제 adapter의 증거이고 실제 driver의 query 취소를 검증한 것으로 확대하지 않는다.

### 2부 03. aggregation

[조합](https://kotlinlang.org/docs/composing-suspending-functions.html), [취소](https://kotlinlang.org/docs/coroutines-cancellation.html), [supervision](https://kotlinlang.org/docs/exception-handling.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| mvc | aggregation | 0 | `sequentialAggregationAccumulatesDelay` | a | 가격·재고 fake 각 100 ms의 순차 비용 관찰 |
| mvc | aggregation | 1 | `parallelAggregationAppliesFailurePolicy` | b | 병렬 시작·필수 실패 취소·선택 실패 부분 응답 검증 |
| mvc | aggregation | 1 | `deadlinesAndParentCancellationStopOutstandingCalls` | b | 전체 300 ms·선택 100 ms 예산과 외부 취소를 구분 |

**→ 성공과 실패를 같은 계약의 입력별 case로 검증한다.** 필수 가격 실패는 전체 오류, 선택 재고 실패는 명시적 부분 응답이다. 전체 deadline과 외부 CancellationException을 일반 부분 실패로 삼키지 않는다. 단위 테스트의 fake timeout과 실제 HTTP read timeout은 구분한다.

### 2부 04. transactions

[트랜잭션 구현](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html), [7.0.9 소스](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionAspectSupport.java)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| mvc | transactions | 0 | `blockingTransactionalBaselineRollsBack` | a | 동기 proxy의 rollback을 별도 connection의 행으로 관찰 |
| mvc | transactions | 0 | `suspensionChangesObservedTransactionBoundary` | c | 중단·전용 thread 이동 전후의 실제 transaction 상태·DB 결과 |
| mvc | transactions | 1 | `blockingTransactionStaysInsideIoBoundary` | b | 비-suspend 트랜잭션 bean의 전체 동기 작업을 IO 경계 안에서 완료 |

**→ 여기서는 두 stage로 줄여도 baseline → 함정 → 개선의 세 계약을 유지한다.** 관찰 테스트 자체에 @Transactional을 붙이지 않고 실제 proxy로 호출한다. 강제 중단·이동 예제의 정확한 최초 활성 상태/commit 시점에는 **확신 없음**이며 E의 실측 후 고정한다. JDBC용 imperative manager와 reactive manager를 혼동하거나 transaction ThreadLocal을 복사하는 풀이를 허용하지 않는다.

### 2부 05. contextpropagation

[MDCContext](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-slf4j/kotlinx.coroutines.slf4j/-m-d-c-context/)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| mvc | contextpropagation | 0 | `plainThreadLocalIsAbsentOnFreshWorker` | a | 값이 없는 전용 worker로 옮겨 ThreadLocal 유실 관찰 |
| mvc | contextpropagation | 1 | `mdcSnapshotSurvivesAndRestoresWorkerContext` | b | 두 요청 ID의 전파·중단 후 값·worker 원래 값 복원 |

**→ MDCContext는 snapshot이라는 설명을 유지한다.** 내부 MDC.put 변경의 자동 재캡처를 기대하지 않으며, Micrometer 자동 context 전파를 별도 구현 과제로 만들지 않는다. Framework 7의 자동 전파 가능성을 “항상 유실된다”는 설명으로 덮지 않고, 실습의 dependency/hook 설정을 명시해 직접 ThreadLocal 실험과 구분한다.

### 2부 06. lifecycle

[runBlocking](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html), [CoroutineScope](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| mvc | lifecycle | 0 | `nestedRunBlockingOccupiesCallerUntilRelease` | c | bounded latch로 caller 점유와 suspend 호출 경계 비교 |
| mvc | lifecycle | 1 | `applicationScopeSupervisesAndClosesOwnedJobs` | b | 실패 관측·정상 sibling 유지·context 종료 시 작업 정리 |

**→ request 수명과 app 수명을 주석에 명시한다.** 응답에 필요한 작업은 request scope를 유지한다. application scope는 종료 정책이 있는 앱 작업용이며 durable queue가 아니다. bean은 자기 Job과 전용 executor를 정리하고 공용 Dispatchers.IO를 닫지 않는다.

### 2부 07. virtualthreads

[Boot 가상 스레드](https://docs.spring.io/spring-boot/reference/features/spring-application.html)

| 모듈 | 단원 | stage | 테스트명 | 유형 | 한 줄 학습 포인트 |
| --- | --- | --- | --- | --- | --- |
| mvc | virtualthreads | 0 | `virtualThreadSettingChangesRequestThreadNotExplicitDispatcher` | c | 실제 서버의 isVirtual·동일 업무 결과·명시적 platform dispatcher를 비교 |

**→ 이 한 stage만 실제 서버 설정 전후 비교로 둔다.** JDK 25의 `Thread.isVirtual()`로 진단하고 thread 이름이나 처리량 우열은 단언하지 않는다. Boot 설정이 모든 coroutine dispatcher를 virtual thread로 바꾸지 않는다는 점과 동기 JDBC의 대안 모델을 설명한다. JDK 21의 synchronized pinning 설명을 JDK 25에 그대로 적용하지 않는다.

## 3. 버전 결론과 적용 경계

**→ 확정 권고: JDK 25 + Boot 4.1.1 + Framework 7.0.9 + Kotlin/KGP 2.3.21 + coroutines 1.10.2 + Gradle wrapper 9.3.0.** Kotlin JVM/Spring plugin과 stdlib/reflect를 함께 정렬하고, core/test/reactor/slf4j coroutines artifact도 1.10.2로 통일한다. 1부는 Spring 없이 같은 Kotlin·coroutines 버전만 명시한다.

**→ 최신 override 안 (2)는 채택하지 않는다.** Kotlin 2.4.21 + coroutines 1.11.0 + Gradle 9.7.0 자체를 불가능하다고 판단한 것은 아니다. 이번 과정에 새 버전 전용 학습 목표가 없으므로 BOM에서 벗어나야 할 이유가 없다. Gradle 9.8.1은 두 안의 KGP 완전 지원 상한 밖이라 제외한다.

**→ 호환 표는 실행 검증을 대신하지 않는다.** 다음 구현 단계에서 wrapper/JDK 기동, 두 모듈 test compile, Spring context와 JDBC baseline, 참조 풀이 성공, c 변경 경로 성공, 학습자 상태 복구 후 의도된 실패 일치까지 확인한다. 이 문서 작성 과정에서는 scaffold나 테스트 실행을 하지 않았다.

**→ Spring 6/Boot 3 비교는 설명 범위로 유지한다.** 이 미션의 동작 보장은 고정한 Boot 4.1.1 구성에 한정한다. 버전 의존 현상은 테스트 주석에 실제 버전과 조건을 남기고, 다른 major에서 같은 결과라고 단언하지 않는다.

## 4. 남은 이견과 다음 결정

**→ 남은 실질 이견: 0개, 위 조건을 포함한 합의안 기준이다.** 부분동의 항목의 쟁점은 30 stage로 산술 조정(A), 순서 변경 근거 수정(B), 실제 경계 장치 예외(C), c 양쪽 경로·복구 실패 원인 검증(E)으로 해소하는 안을 제시했다. 코디네이터가 이 조건을 거부하거나 다른 해석을 선택하면 해당 항목을 다시 협의한다.

**→ 승인 전 확인할 결정은 세 가지다.** 공식 가이드 순서에서 개념 순서로 바꾸는 최종 스펙, Gradle 9.3.0/BOM 고정, 공유 빌드·README의 Claude 단독 소유다. 이는 미해결 기술 주장이나 별도 구현 과제가 아니라 합의안의 승인 항목이다.

**→ 승인 후 다음 작업:** Claude가 공유 빌드 골격을 만들고 Codex 1부/Claude 2부를 구현한다. 이후 참조 풀이·변경 경로 검증 → 학습자 상태 복구 → 실패 목록 대조 → 상호 리뷰 순으로 완료한다.
