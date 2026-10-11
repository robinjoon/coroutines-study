package study.mvc.smoke

import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(classes = [SmokeTest.Config::class])
@AutoConfigureMockMvc
class SmokeTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun suspendControllerAndJdbc() {
        val result =
            mockMvc
                .perform(get("/smoke"))
                .andExpect(request().asyncStarted())
                .andReturn()
        mockMvc
            .perform(asyncDispatch(result))
            .andExpect(content().json("""{"value":"ok"}"""))

        assertThat(jdbcTemplate.queryForObject("select 1", Int::class.java)).isEqualTo(1)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Config.SmokeController::class)
    class Config {
        @RestController
        class SmokeController {
            @GetMapping("/smoke")
            suspend fun smoke(): Map<String, String> {
                delay(10)
                return mapOf("value" to "ok")
            }
        }
    }
}
