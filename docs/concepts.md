# 먼저 읽기: 코루틴 기본 용어

1부 테스트 주석에는 코루틴, 스코프, Job, 디스패처 같은 단어가 해당 단원보다 먼저 나옵니다.
이 문서는 그 단어들을 **테스트를 풀 수 있을 만큼만** 짧게 설명합니다. 자세한 동작은 표시된 단원에서 직접 관찰합니다.

테스트의 정답은 여기 없습니다. 이 문서를 읽고 테스트 주석으로 돌아가 결과를 예상해 보세요.

## 한눈에 보기

| 용어 | 한 줄 설명 | 자세히 다루는 곳 |
| --- | --- | --- |
| suspend 함수 | 실행 도중 멈췄다가 나중에 이어서 실행될 수 있는 함수 | basics stage0 |
| suspend 람다 | suspend 함수와 같은 규칙을 따르는 람다. 타입은 `suspend () -> T` | composition stage1부터 파라미터로 등장 |
| 코루틴 | suspend 블록 하나를 실행하는 작업 단위 | basics |
| 코루틴 빌더 | 일반 코드에서 코루틴을 시작하는 함수(`runTest`, `runBlocking`, `launch`, `async`) | basics, composition |
| 스코프 | 새 코루틴을 시작하는 곳이자, 시작된 코루틴들의 부모 | basics stage1 |
| Job / Deferred | 시작된 코루틴 하나를 가리키는 손잡이. Deferred는 결과값까지 가진 Job | basics stage1, composition stage1 |
| CoroutineContext | 코루틴이 어떻게 실행될지 담은 설정 묶음(Job, 디스패처 등) | context stage0 |
| 디스패처 | 코루틴을 언제, 어느 스레드에서 실행할지 정하는 스케줄러 | context |
| 중단 지점 | 코루틴이 실제로 멈출 수 있는 곳(`delay`, `join`, `await`, `yield` 등 suspend 함수 호출) | cancellation stage0 |
| 시간 제한 | `withTimeout(ms) { }`은 자기가 건 제한이 만료되면 블록을 취소하고 예외를 던진다. `withTimeoutOrNull`은 자기가 건 제한의 만료만 `null`로 바꾼다 | cancellation stage1 (composition stage1에서 먼저 등장) |

## suspend 함수와 suspend 람다

```kotlin
suspend fun greet() { delay(100) }                    // suspend 함수
val task: suspend () -> Int = { delay(100); 42 }      // suspend 람다
```

- `suspend`는 "이 함수는 실행 도중 멈출 수 있다"는 표시입니다. 붙인다고 새 코루틴이 생기거나 다른 스레드로 가지 않습니다.
- suspend 함수와 suspend 람다는 **다른 suspend 함수나 suspend 람다 안에서만** 호출할 수 있습니다.
- 그렇다면 맨 처음은 어디서 시작할까요? 일반 함수인 **코루틴 빌더**가 suspend 람다를 받아 코루틴으로 실행해 줍니다.

```kotlin
@Test
fun example() = runTest {   // runTest는 일반 함수. 중괄호 블록이 suspend 람다다.
    delay(100)              // 그래서 여기서 suspend 함수를 부를 수 있다.
}
```

`runTest`의 블록 타입은 `suspend TestScope.() -> Unit`입니다. 리시버가 붙은 suspend 람다라서 블록 안의 `this`가 `TestScope`입니다.

> 왜 이런 제약이 있을까? 컴파일러는 suspend 함수에 "멈춘 뒤 어디서부터 이어서 실행할지"를 담은 `Continuation` 파라미터를 몰래 추가합니다.
> 일반 함수는 넘겨줄 `Continuation`이 없어서 suspend 함수를 부를 수 없습니다. 코루틴 빌더가 첫 `Continuation`을 만들어 줍니다.

## 코루틴과 스레드

- **스레드**는 OS가 관리하는 실행 흐름입니다. 비싸서 수천 개를 만들기 어렵습니다.
- **코루틴**은 스레드 위에서 실행되는 가벼운 작업 단위입니다. 적은 수의 스레드 위에서 많은 코루틴이 번갈아 실행됩니다.
- 코루틴이 중단 지점에서 멈추면 **스레드를 놓아줍니다.** 그 스레드는 다른 코루틴을 실행할 수 있습니다. 나중에 재개될 때는 같은 스레드일 수도, 다른 스레드일 수도 있습니다.
- 반대로 `Thread.sleep`이나 JDBC 호출처럼 스레드를 막는(블로킹) 코드는 코루틴 안에서도 스레드를 붙잡습니다. 2부 blockingio 단원의 주제입니다.

## 코루틴 빌더

| 빌더 | 어디서 부르나 | 무엇을 하나 |
| --- | --- | --- |
| `runTest { }` | 테스트 메서드(일반 함수) | 테스트용 코루틴을 시작하고, 가상 시간으로 끝까지 실행한 뒤 반환 |
| `runBlocking { }` | 일반 함수 | 코루틴을 시작하고, **끝날 때까지 현재 스레드를 막는다** |
| `launch { }` | 스코프 안 | 결과값이 없는 자식 코루틴을 시작하고 `Job`을 반환 |
| `async { }` | 스코프 안 | 결과값이 있는 자식 코루틴을 시작하고 `Deferred<T>`를 반환 |
| `coroutineScope { }` | suspend 함수 안 | 새 스코프를 열고, 블록과 그 안의 자식이 모두 끝나야 반환하는 suspend 함수 |

`launch`와 `async`는 "새 코루틴을 시작"합니다. 반면 suspend 함수를 그냥 호출하면 **지금 코루틴 안에서 이어서** 실행될 뿐입니다. 이 차이가 basics stage0의 두 테스트입니다.

## 스코프와 구조화된 동시성

`CoroutineScope`는 새 코루틴을 시작할 수 있는 곳입니다. `launch`와 `async`는 `CoroutineScope`의 확장 함수라서 스코프가 있어야 부를 수 있습니다.

스코프 안에서 시작한 코루틴은 그 스코프의 **자식**이 됩니다. 부모와 자식은 이런 약속을 지킵니다.

- **부모는 자식이 모두 끝나야 끝난다.** 본문을 다 실행해도 자식이 남아 있으면 기다립니다.
- **부모가 취소되면 자식도 취소된다.**
- **자식이 실패하면 기본적으로 부모와 형제도 취소된다.** (exceptions 단원에서 이 규칙을 바꾸는 supervisor를 배웁니다.)

이 약속을 **구조화된 동시성(structured concurrency)** 이라고 부릅니다. 덕분에 "누가 시작했는지 모르는 채 떠도는 코루틴"이 생기지 않습니다. basics stage1에서 관찰합니다.

## Job과 Deferred

```kotlin
val job: Job = launch { ... }
job.join()      // 끝날 때까지 기다린다 (suspend)
job.cancel()    // 취소를 요청한다

val deferred: Deferred<Int> = async { 42 }
deferred.await() // 끝날 때까지 기다리고 결과값을 받는다 (suspend)
```

- `Job`은 코루틴 하나의 상태(실행 중, 완료, 취소)를 알려 주고 조작하는 손잡이입니다. `isActive`, `isCompleted`, `isCancelled`로 상태를 볼 수 있습니다.
- `Deferred<T>`는 `Job`에 결과값이 더해진 것입니다.
- 자식 코루틴의 Job은 부모 Job의 자식으로 연결됩니다. 이 연결이 위에서 말한 부모-자식 관계의 실체입니다.

## CoroutineContext와 디스패처

`CoroutineContext`는 코루틴 하나에 붙은 설정 묶음입니다. 대표적인 요소는 다음과 같습니다.

- `Job`: 이 코루틴의 수명
- `CoroutineDispatcher`: 실행할 스레드와 시점
- `CoroutineName`, `CoroutineExceptionHandler` 등

요소들은 `+`로 합칩니다. 예: `Dispatchers.IO + CoroutineName("db")`

context의 요소는 키로 꺼냅니다. 예: `coroutineContext[Job]`, `coroutineContext[ContinuationInterceptor]`.
디스패처는 멈췄던 코루틴을 어디서 이어 실행할지 결정하는 요소이므로 `ContinuationInterceptor` 키로 꺼냅니다.
테스트에서 `[ContinuationInterceptor]`가 보이면 "이 코루틴의 디스패처"라고 읽으면 됩니다.

**디스패처**는 "이 코루틴을 언제, 어느 스레드에서 실행할까"를 정하는 스케줄러입니다.

| 디스패처 | 쓰임 |
| --- | --- |
| `Dispatchers.Default` | CPU 계산용 공유 스레드풀 |
| `Dispatchers.IO` | 블로킹 IO(JDBC, 파일)용 스레드풀 |
| 직접 만든 디스패처 | `Executor.asCoroutineDispatcher()`로 만든 전용 스레드풀. 테스트에서 스레드 이동을 확인할 때 씁니다 |
| `StandardTestDispatcher` | `runTest`의 기본값. 아래에서 설명 |

**중요한 점**: `runTest`의 기본 `StandardTestDispatcher`에서 `launch`와 `async`는 블록을 그 자리에서 바로 실행하지 않고 새 코루틴을 **대기열에 등록**합니다. 실제로 언제 실행할지는 디스패처가 정합니다. 다른 디스패처나 시작 옵션(예: `CoroutineStart.UNDISPATCHED`)에서는 시작 시점이 달라질 수 있습니다.

## 코루틴, Job, 대기열의 작업은 서로 다르다

| 말 | 정체 | 비유 |
| --- | --- | --- |
| 코루틴 | `launch { }` 블록이 실행되는 하나의 흐름. 멈췄다가 이어서 실행될 수 있다 | 진행 중인 일 하나 |
| Job | 그 코루틴을 바깥에서 다루는 손잡이. 상태 확인, 기다리기, 취소 | 택배 운송장 번호 |
| 대기열의 작업 | 디스패처가 "이제 실행할 차례"로 줄 세워 둔 **코루틴의 다음 실행 구간** | 처리 대기 중인 접수표 한 장 |

코루틴과 Job은 같은 것의 두 측면입니다. `launch`가 돌려주는 객체는 사실 코루틴 객체 자체이고, 그것을 `Job` 타입으로 보여 줄 뿐입니다.
"코루틴"은 실행되는 코드 흐름을, "Job"은 그 흐름의 수명과 상태를 말할 때 씁니다.

반면 **대기열의 작업은 코루틴 하나에 여러 개 생깁니다.** 코루틴은 멈췄다 다시 시작할 때마다 "이어서 실행할 구간"을 새로 줄 세웁니다.

```kotlin
launch {
    A
    delay(50)
    B
    delay(50)
    C
}
```

이 코루틴 하나는 실행되는 동안 대기열에 세 번 들어갑니다.

1. `launch` 때: 처음부터 첫 `delay`까지(A)가 줄을 선다.
2. 첫 `delay(50)`에서 멈춘 뒤: B 구간이 **가상 시각 50**에 실행되도록 예약된다.
3. 두 번째 `delay(50)`에서 멈춘 뒤: C 구간이 **가상 시각 100**에 예약된다.

그동안 Job은 계속 하나입니다.

테스트 주석에서 "dispatcher가 작업을 대기열에 넣는다"고 할 때의 "작업"은 Job이 아니라 이 실행 구간을 뜻합니다.

## runTest의 스케줄러와 가상 시간

`runTest`의 기본 디스패처 `StandardTestDispatcher`는 **스레드 하나와 작업 대기열 하나**처럼 동작합니다.

1. `launch`로 시작한 코루틴은 대기열에 등록됩니다. 아직 실행되지 않습니다.
2. 지금 실행 중인 코루틴이 중단 지점에서 멈추면, 그 틈에 대기열의 작업이 실행됩니다.
3. `runCurrent()`를 부르면 **현재 가상 시각에** 실행할 수 있는 작업을 실행합니다.

여기에 **가상 시간**이 더해집니다.

- `delay(100)`은 실제로 기다리지 않습니다. "가상 시각 +100에 이어서 실행"으로 예약해 둘 뿐입니다.
- `runCurrent()`는 시계를 앞으로 돌리지 않습니다. 미래로 예약된 작업은 실행하지 않습니다.
- `advanceTimeBy(ms)`, `advanceUntilIdle()`은 시계를 앞으로 돌립니다.
- `join()`, `await()`처럼 무언가를 기다리며 멈추면, 더 할 일이 없을 때 스케줄러가 시계를 다음 예약 시각으로 앞당깁니다.
- `currentTime`으로 지금 가상 시각을 읽을 수 있습니다.

```kotlin
runTest {
    launch { println("child") }  // 대기열에 등록만 된다
    println("parent")            // 부모가 먼저 계속 실행된다
}                                // 본문이 끝난 뒤 남은 자식을 runTest가 끝까지 실행한다
// 출력: parent, child
```

basics stage0의 `launchStartsWhenSchedulerRuns`는 이 대기열과 가상 시각을 세 시점에서 관찰하는 테스트입니다.
각 시점에 자식 블록의 **어디까지** 실행되었을지 위 규칙으로 예상해 보세요.

도구별 사용법은 [README의 도구 안내](../README.md#도구-안내-코루틴-테스트)에 정리되어 있습니다.

## 취소는 협력적이다

`job.cancel()`은 취소를 **요청**합니다. 코루틴은 다음 중단 지점에 도착했을 때 그 요청을 확인하고 `CancellationException`을 던지며 멈춥니다.
그래서 중단 지점이 없는 긴 계산은 취소 요청에 바로 반응하지 않습니다. cancellation 단원에서 관찰합니다.

`CancellationException`은 "실패"가 아니라 "멈추라는 신호"입니다. 일반 예외처럼 잡아서 삼키면 안 된다는 점을 cancellation stage1에서 배웁니다.

`withTimeout`과 `withTimeoutOrNull`은 블록과 그 자식에게 시간 제한을 겁니다.
그 제한이 만료되면 `withTimeout`은 `TimeoutCancellationException`을 던지고, `withTimeoutOrNull`은 `null`을 반환합니다.
`withTimeoutOrNull`이 `null`로 바꾸는 것은 **자기가 건 제한의 만료뿐**입니다. 블록 안의 일반 예외, 취소, 블록 안쪽에 따로 건 더 짧은 `withTimeout`의 만료는 그대로 호출자에게 전달됩니다.

## 공식 문서

- [Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html): suspend 함수, 빌더, 스코프, Job
- [Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html): context, 디스패처
- [Function types](https://kotlinlang.org/docs/lambdas.html#function-types): suspend 함수 타입
- [kotlinx-coroutines-test](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/): runTest, 가상 시간
