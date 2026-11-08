package org.kduda.greedy.testing.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
import java.time.Duration

/**
 * A Spark Connect server in a container, built from the image of the local stack (`docker/spark/Dockerfile`). Spark
 * runs inside the server process (`local[2]`) with the settings of the tests' classic session: no UI, 4 shuffle
 * partitions. The tests of a JVM share one server, [shared]; Testcontainers removes it when the JVM exits.
 */
class SparkConnectContainer : GenericContainer<SparkConnectContainer>(image()) {
	init {
		withExposedPorts(CONNECT_PORT)
		withCommand(
			"/opt/spark/bin/spark-submit",
			"--class",
			"org.apache.spark.sql.connect.service.SparkConnectServer",
			"--master",
			"local[2]",
			"--conf",
			"spark.ui.enabled=false",
			"--conf",
			"spark.sql.shuffle.partitions=4",
			"--conf",
			"spark.connect.grpc.binding.port=$CONNECT_PORT",
			"spark-internal",
		)
		waitingFor(Wait.forLogMessage(".*Spark Connect server started.*", 1).withStartupTimeout(STARTUP_TIMEOUT))
	}

	/** The address for a Spark Connect client: `sc://<host>:<mapped port>`. */
	fun connectUrl(): String = "sc://$host:${getMappedPort(CONNECT_PORT)}"

	companion object {
		/** The server's gRPC port inside the container. */
		const val CONNECT_PORT = 15002

		private val STARTUP_TIMEOUT = Duration.ofMinutes(3)

		/** The server the tests of this JVM share, started on first use. */
		@JvmStatic
		val shared: SparkConnectContainer by lazy { SparkConnectContainer().apply { start() } }

		/**
		 * Built with Docker's cache and kept after the run (no delete on exit), so only the first build of a changed
		 * Dockerfile takes long. The build context is the Dockerfile's directory.
		 */
		private fun image(): ImageFromDockerfile {
			val repoRoot =
				checkNotNull(System.getProperty("greedy.repo.root")) { "system property greedy.repo.root is not set" }
			return ImageFromDockerfile("greedy-spark:4.3.0-test", false)
				.withDockerfile(Path.of(repoRoot, "docker", "spark", "Dockerfile"))
		}
	}
}
