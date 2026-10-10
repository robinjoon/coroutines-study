# 2부 교차 리뷰

**높음 1개·중간 6개·낮음 2개. 외부 timeout을 일반 오류로 바꾸는 참조 풀이 오류를 우선 수정해야 한다.**

- 리뷰 일자: 2026-10-11
- 검증 구성: JDK 25, Boot 4.1.1, Framework 7.0.9, coroutines 1.10.2
- 범위: 2부 학습자 소스, default/toggled 풀이, 실패 목록, README, 설계 문서 8절
- 원본 소스는 수정하지 않았다. 모든 실행·변형 실험은 저장소 밖 임시 복사본에서 진행했다.
- 이 문서는 리뷰 시점의 지적이다. 원 소유자가 적용한 후속 수정은 재검증해야 한다.

## 지적 목록

### H1. 높음: 참조 풀이가 외부 timeout의 취소를 서비스 오류로 바꾼다

**위치:** `.solutions/default/spring-mvc-study/src/test/kotlin/study/mvc/aggregation/stage1/ProductSummaryService.kt:50`, `spring-mvc-study/src/test/kotlin/study/mvc/aggregation/stage1/Stage1Test.kt:110`.

**근거:** 참조 풀이의 `catch (TimeoutCancellationException)`은 자체 300 ms deadline과 호출자의 timeout을 구분하지 않는다. 정상 참조 풀이를 `withTimeout(50) { service.summaryWithDeadline(1) }`로 호출하니 실제 예외는 `CancellationException`이 아니라 `SummaryTimeoutException`이었다. 두 클라이언트는 취소되지만 호출자에게 전달되는 실패 종류가 계약과 다르다. 기존 테스트는 명시적 `Job.cancel()`만 다뤄 이 오류를 놓친다.

**제안:** 자신의 300 ms 예산이 끝난 경우만 변환하고 외부 취소는 재전파한다. 자체 timeout만 `null`로 바꾸는 `withTimeoutOrNull`을 활용할 수 있다. 기존 deadline 테스트에 외부 50 ms timeout case를 추가하고, 외부 취소 예외 및 두 호출의 정리를 함께 확인한다. [coroutines 1.10.2 Timeout.kt, 93–108행](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/Timeout.kt#L93)은 중첩 timeout의 소유자를 구분한다.

### M1. 중간: 비동기 시작·실행 위치 검사만으로 runBlocking을 걸러내지 못한다

**위치:** `spring-mvc-study/src/test/kotlin/study/mvc/controller/stage1/Stage1Test.kt:44`, `spring-mvc-study/src/test/kotlin/study/mvc/blockingio/stage1/Stage1Test.kt:64`, `spring-mvc-study/src/test/kotlin/study/mvc/transactions/stage1/Stage1Test.kt:69`.

**근거:** 다음 잘못된 풀이가 기존 테스트를 모두 통과했다.

- 핸들러를 `suspend`로 선언하되 내부에서 `runBlocking { productService.find(id) }`를 호출: controller 테스트 2개 통과.
- JDBC 저장소에서 `withContext` 대신 `runBlocking(jdbcDispatcher)` 사용: JDBC adapter 테스트 통과.
- OrderFacade에서 `withContext` 대신 `runBlocking(txDispatcher)` 사용: 트랜잭션 경계 테스트 통과.

작업이 올바른 worker에서 실행돼도 호출자는 블로킹될 수 있다. 특히 `suspend` 핸들러는 내부에서 블로킹해도 Spring의 Mono 반환 경로를 타므로 `asyncStarted()`는 여전히 참이다. 현재 controller KDoc은 일반 함수의 runBlocking을 거부한다는 점에서는 맞지만, 목표인 runBlocking 부재까지 증명하지는 않는다.

**제안:** 완료를 제어하는 서비스/DAO fixture를 주입한다. release 전에도 첫 MockMvc perform이 반환하고, adapter/facade 호출자의 다른 작업이 진행되는지 관찰한다. 실제 request/worker 경계에는 bounded gate를 사용하고 실패 시 finally에서 해제한다. 경과 시간 숫자를 재는 테스트는 피한다.

### M2. 중간: MDC 테스트가 우연한 복원과 worker 미사용 풀이를 허용한다

**위치:** `spring-mvc-study/src/test/kotlin/study/mvc/contextpropagation/stage1/Stage1Test.kt:49`, `spring-mvc-study/src/test/kotlin/study/mvc/contextpropagation/stage1/Stage1Test.kt:67`.

**근거:** 두 잘못된 풀이를 각각 실행해 기존 테스트가 통과함을 확인했다.

- `withContext(worker)` 안에서 MDC를 직접 put하고 finally에서 이전 map 복원.
- worker를 사용하지 않고 `withContext(MDCContext(...))`만 적용.

첫 풀이는 A가 100 ms, B가 50 ms인 현재 완료 순서가 스택처럼 겹쳐 우연히 복원된다. gate로 A 시작 → B 시작 → A 완료 → B 완료를 보장한 추가 실험에서는 기대 `(req-A, req-B)` 대신 `(req-B, null)`이 나왔다. 두 번째 풀이는 caller에서만 실행되지만 worker 실행 여부를 검사하지 않아 통과한다.

**제안:** 블록 안에서 실제 worker의 dispatcher 객체 또는 thread ID를 확인한다. 가상 시간이나 bounded gate로 완료 순서를 교차시키고, 중단 후 요청 ID와 worker 원래 값 복원을 검증한다. 실패·취소 후 복원도 같은 테스트의 case로 넣는다. [1.10.2 MDCContext 소스](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/integration/kotlinx-coroutines-slf4j/src/MDCContext.kt)는 중단·재개마다 설치와 복원을 수행한다.

### M3. 중간: bulkhead의 취소 후 permit 반환을 검증하지 않는다

**위치:** `spring-mvc-study/src/test/kotlin/study/mvc/blockingio/stage1/Stage1Test.kt:84`.

**근거:** `acquire(); dao.findName(id); release()`로 구현하고 finally를 생략해도 기존 5개 성공 요청 테스트는 통과했다. permit 2개를 점유한 작업을 취소한 뒤 같은 repository로 다시 호출하는 실험에서는 200 ms의 가상 timeout 안에 완료하지 못하고 `null`을 반환했다. 정상 결과·maxActive·300 ms만으로 자원 반환 계약은 확인할 수 없다.

**제안:** 같은 repository의 활성 작업을 취소한 뒤 새 호출이 진입하고 정상 완료하는 case를 추가한다. 실패 fixture도 제공해 일반 실패 후 재진입을 확인하면 좋다. [1.10.2 Semaphore.withPermit 소스](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/sync/Semaphore.kt#L77)는 finally에서 permit을 반환한다.

### M4. 중간: ServletException의 포괄 허용이 의도하지 않은 원인도 검증 성공으로 처리한다

**위치:** `docs/superpowers/specs/2026-10-10-coroutines-study-design.md:91`, `.solutions/verify.sh:56`, `README.md:117`.

**근거:** 임시 학습자 복사본에서 controller TODO를 `throw NullPointerException("unexpected bug")`로 바꿨다. 이 상태에서도 verify는 학습자 1개 통과·17개 실패, default/toggled 각각 18개 통과와 함께 `VERIFY OK: spring-mvc-study`를 출력했다. 원인이 미구현이 아니라 NPE여도 ServletException이라는 외부 타입과 실패 메서드가 같으면 승인된다. README가 설명한 미구현 래핑만 허용하는 규칙보다 실제 허용 범위가 넓다.

**제안:** ServletException을 허용할 때 cause가 `NotImplementedError` 또는 허용된 assertion인지 확인한다. 또는 테스트 경계에서 그 원인만 다시 던져 기존 타입 판별을 유지한다. 설계 8절에도 래핑된 원인 검사 조건을 적는다. ServletException으로 감싸지는 현상 자체는 문제없다.

### M5. 중간: JDBC timeout·취소의 한계에 관한 필수 설명이 빠졌다

**위치:** `spring-mvc-study/src/test/kotlin/study/mvc/blockingio/stage1/Stage1Test.kt:58`, `spring-mvc-study/src/test/kotlin/study/mvc/aggregation/stage1/Stage1Test.kt:73`.

**근거:** blockingio는 IO 격리 후에도 JDBC가 블로킹임을 설명하지만, 코루틴 timeout이 이미 실행 중인 JDBC query의 중단을 보장하지 않는다는 설명은 없다. aggregation은 취소 가능한 delay fake만 사용한다. 합의 표의 blockingio 부연은 query/connection timeout·DB pool과 코루틴 취소를 구분하도록 명시했다. 학습자가 fake의 취소 결과를 실제 driver까지 확대할 수 있다.

**제안:** blockingio KDoc에 “코루틴 취소는 실행 중인 JDBC 호출 중단을 보장하지 않는다. query/connection timeout과 pool 제한은 별도다”를 적고, aggregation에도 fake의 취소 검증 범위를 한 문장으로 한정한다. driver 중단 실험을 새로 만들 필요는 없다. [협력적 취소 가이드](https://kotlinlang.org/docs/coroutines-cancellation.html#cancellation-is-cooperative), [Spring JDBC 연결·timeout 문서](https://docs.spring.io/spring-framework/reference/data-access/jdbc/connections.html).

### M6. 중간: deadline 메서드의 일반 실패 정책은 별도로 검증되지 않는다

**위치:** `spring-mvc-study/src/test/kotlin/study/mvc/aggregation/stage1/Stage1Test.kt:81`, `spring-mvc-study/src/test/kotlin/study/mvc/aggregation/stage1/ProductSummaryService.kt:38`.

**근거:** TODO는 summary와 같은 필수/선택 실패 정책을 요구한다. 그러나 deadline 테스트에는 선택 timeout·전체 timeout·부모 취소만 있다. deadline 메서드에서 선택 실패를 처리하는 호출을 제거하고 `stockClient.stock(id)`를 직접 사용해도 기존 테스트가 통과했다. 재고가 50 ms에 일반 실패하는 추가 case는 부분 응답 대신 실패했다. 다른 메서드인 summary의 성공·실패 검증은 이 계약을 대신하지 않는다.

**제안:** 기존 deadline 테스트에 예산 안의 성공, 선택 재고 일반 실패, 필수 가격 일반 실패 case를 넣는다. 각각 완전 응답·부분 응답·원래 가격 예외 및 재고 취소를 확인한다. 테스트 메서드 수는 유지할 수 있다.

### L1. 낮음: 개별 테스트 KDoc의 링크·버전 표기가 규약과 다르다

**위치:** `spring-mvc-study/src/test/kotlin/study/mvc/transactions/stage0/Stage0Test.kt:50`, `spring-mvc-study/src/test/kotlin/study/mvc/contextpropagation/stage1/Stage1Test.kt:34` 등 Stage 테스트 17개.

**근거:** 공식 문서 링크는 클래스 KDoc에 있고 17개 메서드 KDoc에는 직접 링크가 없다. 설계 4절은 각 테스트 위의 KDoc에 개념·공식 링크·관찰 지점·마지막 질문을 요구한다. MDC 실측의 버전 표기도 테스트 주석에는 없어 README의 버전 설명에 의존한다.

**제안:** 각 메서드에 관련 절 링크 한 줄과 관찰할 값 한 줄을 두고, 버전 의존 관찰에는 확인한 조합을 짧게 적는다. 대부분의 질문은 API 암기보다 자원·요청 경계의 이유를 묻고 있어 유지할 만하다.

### L2. 낮음: GlobalScope의 실패가 관찰되지 않는다는 표현이 부정확하다

**위치:** `spring-mvc-study/src/test/kotlin/study/mvc/lifecycle/stage1/Stage1Test.kt:17`.

**근거:** `GlobalScope.launch`의 실패가 “아무도 모른다”는 설명은 JVM의 기본 예외 보고 동작을 숨긴다. handler 없는 root launch의 미처리 예외도 platform handler 및 `Thread.uncaughtExceptionHandler`로 전달된다. 문제는 앱의 관리·종료·복구 정책과 연결되지 않는다는 점이다. [1.10.2 예외 처리 소스](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CoroutineExceptionHandler.kt#L5), [JVM 마지막 예외 처리 경로](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/internal/CoroutineExceptionHandlerImpl.kt#L24).

**제안:** “애플리케이션 컨텍스트 종료와 자동으로 연결되지 않고, 실패를 앱의 정책대로 수집·처리하기 어렵다”로 바꾼다. JVM 프로세스가 종료돼도 작업이 계속 살아 있는 것처럼 읽히지 않도록 컨텍스트 종료와 프로세스 종료도 구분한다.

## 확인했지만 문제 없음

**1 → transactions/stage0의 관찰값과 Mono/AOP 원인은 맞다.** 기본 풀이에서 동기 메서드는 활성 트랜잭션과 롤백을, suspend 메서드는 비활성과 두 행 잔존을 확인했다. suspend 본문의 `delay(10)`을 제거해도 동일한 테스트 2개가 통과했다. 본 저장소처럼 coroutines-reactor가 있는 구성으로 결론을 한정한다.

Spring 7.0.9 소스 경로도 설명과 일치한다. [AopUtils, 351행](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-aop/src/main/java/org/springframework/aop/support/AopUtils.java#L351)은 suspend 호출을 [CoroutinesUtils의 cold Mono](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-core/src/main/java/org/springframework/core/CoroutinesUtils.java#L127)로 만든다. [TransactionAspectSupport, 333행](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-tx/src/main/java/org/springframework/transaction/interceptor/TransactionAspectSupport.java#L333)의 Platform 경로는 Mono 반환 뒤 트랜잭션을 끝낸다. 그 후 [CglibAopProxy.processReturnType, 423행](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-aop/src/main/java/org/springframework/aop/framework/CglibAopProxy.java#L423)이 Mono를 await하면서 본문을 실행한다. 따라서 이 구성의 원인은 중단 후 thread 변경만으로 설명할 수 없고, c에서 a로 바꾼 결정도 타당하다.

**2 → 원래 학습자 상태와 두 참조 overlay는 종료하며 예정된 결과를 낸다.** 원본 verify 재실행 결과는 `VERIFY OK: spring-mvc-study`다. 학습자 상태는 SmokeTest 1개 통과·17개 의도된 실패, default/toggled는 각각 18개 모두 통과했다. 이 결과와 M4의 원인 판별 취약점은 구분해야 한다.

**3 → await만의 try-catch, 일반 Job, dispatcher 누락은 실제로 거부한다.** 변형 실험에서 일반 함수 runBlocking controller의 두 테스트는 assertion 실패, JDBC/OrderFacade의 dispatcher 누락은 assertion 실패, await-only 재고 실패 처리는 StockUnavailableException 실패였다. SupervisorJob을 Job으로 바꾸면 lifecycle 테스트가 최대 5초 내 timeout으로 거부했다. 이들은 잘못된 풀이를 넣은 실험 결과이며 원본 학습자 실패 유형이 아니다.

**4 → MDCContext snapshot 설명과 정상 참조 풀이의 복원은 맞다.** 명시한 요청 ID map을 context element로 설치하고, 중단·재개에 맞춰 worker 원래 값으로 복원한다. 내부 MDC.put 변경의 자동 재캡처를 기대하지 말라는 설명도 1.10.2 소스와 일치한다. M2는 이 정상 풀이가 틀렸다는 지적이 아니라 다른 잘못된 풀이도 통과한다는 지적이다.

**5 → 가상 스레드 토글은 실제 서버에서 확인한다.** 기본 설정에서는 요청·명시적 dispatcher 모두 platform thread, 토글에서는 요청만 virtual thread로 관찰하는 두 overlay가 통과했다. thread 이름·성능 순위·JDK 21 synchronized pinning을 단언하지 않는다. 전용 dispatcher는 destroyMethod로 닫는다.

**6 → README 명령·테스트 표·실패 개수는 현재 파일과 맞다.** 두 모듈 SmokeTest 선택, FQCN 클래스 선택, 변경된 transactions 메서드 선택 명령이 유효하다. 1부 23개·2부 17개 표와 실패 목록이 맞고, 정답 부재 및 a/b/c 학습 방법도 일치한다. M4의 원인 판별 조건은 별도 수정이 필요하다.

**7 → 구현 힌트와 질문은 대체로 입문자에게 적절하다.** 블로킹 위치·동시 호출 상한·필수/선택 실패 정책은 구현 계약으로 명시하고, b 함수는 TODO 상태다. c는 변경과 복원할 줄을 지정한다. 질문은 커넥션 풀, 다른 사용자 로그, 형제 취소처럼 결과가 실무에서 갖는 의미를 묻는다.

## 실험 기록

- 원본 검증: `.solutions/verify.sh spring-mvc-study`, 학습자 `pass=1 fail=17`, default/toggled 각각 `pass=18 fail=0`.
- 변형 A: suspend 핸들러 runBlocking, JDBC/트랜잭션 runBlocking, 직접 MDC 변경·복원, finally 없는 permit 반환을 함께 적용. 관련 기존 테스트 8개 모두 통과. 같은 실행에서 트랜잭션 fixture의 delay 제거 후 두 기준선도 통과.
- 추가 probe 3개: 외부 50 ms deadline은 SummaryTimeoutException, MDC 교차 완료는 `(req-B, null)`, 취소 후 bulkhead 새 호출은 `null`. 요구 계약을 단언한 probe 3개 모두 실패.
- 변형 B: 일반 핸들러 runBlocking, dispatcher 없는 JDBC/트랜잭션 호출, 일반 Job, await-only catch, worker 없는 MDCContext. 기존 테스트 7개 중 6개 실패, worker 없는 MDC 테스트 1개 통과.
- 변형 C: deadline 메서드의 재고 일반 실패 처리 제거. 기존 deadline 테스트 통과, 50 ms 재고 실패의 부분 응답 probe 실패.
- 검증기 probe: 학습자 controller TODO 대신 NPE. verify는 default/toggled까지 모두 실행하고 `VERIFY OK` 출력.

후속 작업은 원 소유자의 수정과 같은 계약에 대한 재검증이다. 테스트 이름·메서드 수를 늘리지 않고 기존 메서드의 입력 case와 fixture를 보강할 수 있다.
