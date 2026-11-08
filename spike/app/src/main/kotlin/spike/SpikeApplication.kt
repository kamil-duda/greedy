package spike

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SpikeApplication

fun main(args: Array<String>) {
	runApplication<SpikeApplication>(*args)
}
