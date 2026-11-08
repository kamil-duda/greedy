package org.kduda.greedy.app

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.env.Environment
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * The skeleton serves health only, without details, and listens on localhost only. Runs without a database, so the
 * datasource's health check is off here (`ApplicationIT` checks health with PostgreSQL).
 */
@SpringBootTest(properties = ["management.health.db.enabled=false"])
@AutoConfigureMockMvc
class ActuatorExposureTest {
	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var environment: Environment

	@Test
	fun `health is up and shows only the status`() {
		mockMvc.get("/actuator/health").andExpect {
			status { isOk() }
			content { json("""{"status":"UP"}""", JsonCompareMode.STRICT) }
		}
	}

	@ParameterizedTest
	@ValueSource(
		strings = [
			"/actuator",
			"/actuator/env",
			"/actuator/heapdump",
			"/actuator/shutdown",
			"/actuator/info",
			"/actuator/mappings",
			"/actuator/health/diskSpace",
			"/actuator/health/liveness",
			"/actuator/health/readiness",
		],
	)
	fun `no other actuator endpoint is served`(path: String) {
		mockMvc.get(path).andExpect { status { isNotFound() } }
	}

	@Test
	fun `the server binds to localhost only`() {
		environment.getProperty("server.address") shouldBe "127.0.0.1"
	}
}
