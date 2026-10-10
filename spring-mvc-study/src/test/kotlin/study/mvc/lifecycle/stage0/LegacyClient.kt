package study.mvc.lifecycle.stage0

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * 오래된 동기 API를 감싼 클라이언트. 누군가 suspend 함수 안에서 runBlocking으로 다른 suspend 호출을 기다리게 만들었다.
 */
class LegacyClient(private val events: MutableList<String>) {

    suspend fun call(name: String) {
        events += "$name 시작"
        runBlocking { delay(100) }
        // delay(100)
        events += "$name 끝"
    }
}
