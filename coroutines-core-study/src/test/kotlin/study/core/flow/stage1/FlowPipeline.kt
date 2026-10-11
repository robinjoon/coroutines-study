package study.core.flow.stage1

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

fun selectedNumbers(source: Flow<Int>): Flow<Int> {
    // TODO(b): 두 배로 바꾼 뒤 4의 배수만 골라 두 개까지만 내보내도록 구성한다.
    TODO("map, filter, take로 필요한 값만 생산하는 파이프라인을 구성하세요")
}

fun upstreamOn(
    source: Flow<Int>,
    dispatcher: CoroutineDispatcher,
): Flow<Int> {
    // TODO(b): collector의 context를 바꾸지 않고 upstream에 dispatcher를 적용한다.
    // upstream을 전부 모으지 않고 값을 전달하며, 수집자가 끝나면 아직 실행 중인 upstream도 정리한다.
    TODO("flowOn으로 생산 구간의 context를 지정하세요")
}
