package study.core.smoke

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SmokeTest {
    @Test
    fun virtualTime() = runTest {
        delay(1_000)
        assertThat(currentTime).isEqualTo(1_000)
    }
}
