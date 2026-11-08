package org.kduda.greedy.app

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.postgresql.PostgreSQLContainer

/** The whole application on a real port with its PostgreSQL datasource: health is up (database included). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationIT {
	@LocalServerPort
	private var port: Int = 0

	private val client by lazy { RestTestClient.bindToServer().baseUrl("http://127.0.0.1:$port").build() }

	@Test
	fun `health is up`() {
		client
			.get()
			.uri("/actuator/health")
			.exchange()
			.expectStatus()
			.isOk
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo("UP")
	}

	@Test
	fun `env is not served`() {
		client
			.get()
			.uri("/actuator/env")
			.exchange()
			.expectStatus()
			.isNotFound
	}

	/**
	 * PostgreSQL as a bean: Spring starts it before the datasource and stops it after the datasource has closed, when
	 * the (cached) context closes.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	class Containers {
		@Bean
		@ServiceConnection
		fun postgres() = PostgreSQLContainer("postgres:18.6")
	}
}
