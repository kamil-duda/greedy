package org.kduda.greedy.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GreedyApplication

// The spread is how Kotlin passes `args` on to the vararg runApplication; one copy at startup
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
	runApplication<GreedyApplication>(*args)
}
