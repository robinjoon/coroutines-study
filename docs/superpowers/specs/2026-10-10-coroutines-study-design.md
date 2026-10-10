# 코루틴 + Spring MVC 학습 프로젝트 설계

- 작성일: 2026-10-10
- 협의: Claude(코디네이터) ↔ Codex gpt-6.1-sol, Orca run `run_6b98e4eb710b`
- 근거 문서: `docs/discussion/01-codex-curriculum-proposal.md`, `docs/discussion/02-codex-response.md`
- 학습자: Kotlin은 쓰지만 코루틴은 처음. Spring MVC 중심. 정답 코드는 제공하지 않음.
- 학습자가 설계 결정을 위임했으므로 아래 결정은 코디네이터가 확정했다.

## 1. 목표

공식 코루틴 가이드(https://kotlinlang.org/docs/coroutines-guide.html)의 개념을 학습 테스트로 익히고,
그 지식을 Spring Boot MVC 백엔드의 실무 문제(블로킹 JDBC, 트랜잭션, MDC, 병렬 외부 호출, 수명 관리)에 적용한다.
양식은 우아한테크코스 `java-jdbc/study`(stage별 학습 테스트 + 개념 주석 + 공식 링크 + 질문)를 따른다.

## 2. 확정 결정

| 항목 | 결정 |
| --- | --- |
| 모듈 | `coroutines-core-study`(1부, Spring 없음), `spring-mvc-study`(2부). 모듈 간 코드 의존 없음 |
| 버전 | JDK 25 toolchain, Boot 4.1.1(BOM 그대로), Framework 7.0.9, Kotlin/KGP 2.3.21, coroutines 1.10.2, JUnit 6.0.3, AssertJ 3.27.7, Gradle wrapper 9.3.0 |
| 순서 | 공식 목차가 아니라 개념 의존 순서. 공식 가이드는 각 테스트 주석의 링크로 연결 |
| 분량 | 30 stage / 40 테스트 (표는 02-codex-response.md 2절이 정본) |
| 범위 제외 | WebFlux 서버, R2DBC, CI 정책, 태그 체계, 성능 벤치마크 |
| opt-in | `ExperimentalCoroutinesApi`는 빌드에서 모듈 전체 opt-in (학습자가 `@OptIn`에 걸리지 않게) |

## 3. 파일 규약

- 테스트: `<module>/src/test/kotlin/study/<core|mvc>/<unit>/stageN/StageNTest.kt`
- 학습자가 채우는 구현(b)과 fixture는 **같은 stage 패키지의 test 소스**에 둔다(우테코 study와 동일). `src/main`에는 학습 코드를 두지 않는다.
- 단원(unit) 이름: core = `basics, composition, cancellation, context, exceptions, flow, channels, sharedstate`,
  mvc = `controller, blockingio, aggregation, transactions, contextpropagation, lifecycle, virtualthreads`.
- 2부는 `@SpringBootApplication`을 두지 않는다. 각 stage 테스트가 `@SpringBootTest(classes = [...])`로 자기 설정만 명시적으로 로드해 stage 간 bean이 섞이지 않게 한다.
- `study.<core|mvc>.smoke.SmokeTest`는 환경 확인용으로 항상 통과해야 한다.

## 4. 테스트 작성 규약

각 테스트 위에 KDoc으로 다음을 쓴다.

1. 개념 2~4문장
2. 공식 문서 링크(가이드 절 또는 API/Spring 레퍼런스)
3. 관찰 지점
4. 마지막 줄에 "왜 그런 결과가 나왔을까?" 류의 질문

과제 유형:

- **a (기대값 수정)**: 실제 동작을 관찰하고 고칠 기대값을 일부러 틀린 값(`containsExactly("")`, `isEqualTo(-1)`, `isTrue()`↔`isFalse()` 등)으로 둔다. 해당 줄 위에 `// TODO(a): ...` 주석.
- **b (구현)**: 같은 패키지의 함수/클래스 본문을 `TODO("설명")`으로 둔다. 테스트 코드 자체는 정답 계약이므로 학습자가 고치지 않는다. 구현 위치에 `// TODO(b): ...` 주석.
- **c (주석 해제 관찰)**: 기본 상태 기대값은 a처럼 틀린 값으로 두고, 바꿔볼 한 줄을 주석으로 둔다. KDoc에 "기대값을 맞춘 뒤 이 줄의 주석을 해제하고 다시 실행해 무엇이 달라지는지 확인하라"와 복원 방법을 적는다.

결정성 규약:

- 1부는 `runTest`의 가상 시간(`currentTime`, `runCurrent`, `advanceTimeBy`, `advanceUntilIdle`)과 `events` 리스트를 기본 검증 수단으로 쓴다. gate(`CompletableDeferred`/latch)는 꼭 필요한 테스트에만 쓴다.
- 실제 thread가 필요한 테스트는 테스트가 직접 만든 executor를 쓰고 `finally`/`@AfterEach`에서 닫는다. 공용 `Dispatchers.Default/IO` worker의 이름을 단언하지 않는다.
- 실시간 측정, 성능 순위, 독립 코루틴 간 println 순서는 단언하지 않는다.
- 학습자 상태에서도 모든 테스트는 **유한 시간 안에 끝나야 한다**(hang 금지). 실패는 AssertionError 또는 NotImplementedError로만 난다.
- 2부 DB는 테스트(클래스)별 고유 in-memory H2 이름을 쓰고, rollback 결과는 테스트 트랜잭션 밖(별도 connection)에서 확인한다.

## 5. 정답 없음 + 신뢰성 검증 프로토콜

- 참조 풀이는 git에서 제외된 `.solutions/` 아래 저장소와 같은 상대 경로로 둔다. **커밋하지 않으며 작업 종료 시 삭제한다.**
  - `.solutions/default/<repo 상대경로>`: a의 올바른 기대값, b의 구현, c의 기본 상태 기대값을 담은 파일 전체 사본
  - `.solutions/toggled/<repo 상대경로>`: c 테스트에서 주석 줄을 해제하고 그 상태의 올바른 기대값을 담은 파일 전체 사본
  - `.solutions/expected-failures.txt`: 학습자 상태에서 실패해야 하는 `FQCN#method` 목록(한 줄에 하나)
- `.solutions/verify.sh <module>`이 확인하는 것:
  1. 학습자 상태: 실패 테스트 집합 == expected-failures(해당 모듈), 실패 원인은 AssertionError/NotImplementedError만, 나머지는 통과
  2. default overlay: 전부 통과
  3. toggled overlay(있으면): 전부 통과
- 트랜잭션·MDC 등 버전 의존 동작의 기대값은 이 과정에서 Boot 4.1.1 실측으로 확정하고, 주석에 "Boot 4.1.1 / Framework 7.0.9에서 확인"이라고 명시한다.

## 6. 구현 분담

| 영역 | 담당 |
| --- | --- |
| 루트 빌드, wrapper, README, 이 스펙, `.solutions/verify.sh` | Claude |
| `coroutines-core-study/**` (1부) + 해당 `.solutions` | Codex gpt-6.1-sol (Orca 워커) |
| `spring-mvc-study/**` (2부) + 해당 `.solutions` | Claude |
| 교차 리뷰 | 서로의 모듈을 리뷰하고 지적은 원 소유자가 수정 |

## 7. README

루트 README에 다음을 둔다: 학습 방법, 과제 유형 a/b/c, 진행 순서(1부 → 2부), 실행 명령(`./gradlew :<module>:test --tests 'FQCN'`),
runTest/가상 시간 도구 안내, 처음 실행 시 실패하는 테스트 목록(의도된 실패).
