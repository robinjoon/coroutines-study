# 1부 교차 리뷰 (Claude → Codex)

- 리뷰 일자: 2026-10-11
- 범위: `coroutines-core-study/src/test/**`, `.solutions/*/coroutines-core-study/**`, `.solutions/expected-failures-coroutines-core-study.txt`
- 독립 재검증: `.solutions/verify.sh coroutines-core-study` → 학습자 상태 1 통과·23 의도된 실패, default/toggled 각 24 통과 (`VERIFY OK`)

## 지적

### L1. 낮음: a 과제의 답을 KDoc이 미리 말한다
`exceptions/stage0/Stage0Test.kt`의 `supervisedAsyncExposesFailureAtAwait` KDoc에 "이 구성에서는 handler가 그 실패를 관찰하지 않는다"가 있다. 바로 아래 TODO(a)가 묻는 `beforeAwait`의 답이다. 관찰 질문으로 바꾸는 것을 제안한다.

### M1. 중간: 잘못된 풀이 거부 여부가 검증되지 않았다
2부 교차 리뷰(03)에서 runBlocking·MDC·permit 관련 잘못된 풀이가 기존 테스트를 통과하는 문제가 실제로 발견됐다. 1부 b 과제 12개도 같은 관점의 검증이 필요하다.
예: `GlobalScope.async`/`CoroutineScope(Job())`로 자식을 만들어 구조화된 동시성을 깨는 풀이, `catch (e: Exception)`으로 취소를 삼키는 fallback, `launch` 후 `join` 없이 반환, Mutex를 읽기·쓰기에 따로 거는 풀이, `finally` 없는 active 감소, `flowOn` 위치를 collector 쪽에 두는 풀이 등.
2부는 `.solutions/mkwrong.py`, `.solutions/wrong-cases-<module>.txt`, `.solutions/verify-wrong.sh`로 15개 변형을 자동 검증한다. 같은 도구로 1부도 검증할 것을 제안한다.

## 확인했지만 문제 없음

- 학습자 상태에서 무한 대기 없이 끝나도록 처리했다(`if (parent.isCompleted) parent.await()`, 독립 SupervisorJob을 finally에서 정리).
- `loadOrFallback`(취소를 fallback으로 삼키지 않기)이 2부 aggregation의 정책과 잘 이어진다.
- 가상 시간·사건 리스트 위주라 입문자가 읽기 쉽고, 스레드 이름을 단언하지 않는다(context는 thread ID와 dispatcher 객체 비교).
- c 과제(cancellation/stage2)는 한 줄 토글과 복원 방법이 명확하고, toggled overlay가 통과한다.

## 참고: 말투
1부 KDoc은 "확인하라" 명령조, 2부는 "~하자" 권유조다. 큰 문제는 아니어서 수정 대상에서 뺐다.
