plugins { scala }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

/** Runtime for each kind of session: classic Spark in-process, or the Spark Connect client. */
val classicRuntime by configurations.creating
val connectRuntime by configurations.creating

dependencies {
	implementation(libs.scala.library)
	compileOnly(libs.spark.sql.api)
	testCompileOnly(libs.spark.sql.api)
	testImplementation(platform("org.junit:junit-bom:5.13.4"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	classicRuntime(libs.spark.sql)
	connectRuntime(libs.spark.connect.client)
}

// The JDK module opens Spark's own launcher adds (JavaModuleOptions); classic needs them, Connect only java.nio.
val sparkOpens = listOf("java.lang", "java.lang.invoke", "java.lang.reflect", "java.io", "java.net", "java.nio",
	"java.util", "java.util.concurrent", "java.util.concurrent.atomic", "jdk.internal.ref", "sun.nio.ch", "sun.nio.cs",
	"sun.security.action", "sun.util.calendar").map { "--add-opens=java.base/$it=ALL-UNNAMED" } +
	listOf("-Djdk.reflect.useDirectMethodHandle=false", "-Dio.netty.tryReflectionSetAccessible=true")

tasks.test { enabled = false } // run through testClassic / testConnect

fun registerSuite(name: String, runtime: Configuration, session: String, jvm: List<String>) = tasks.register<Test>(name) {
	group = "verification"
	useJUnitPlatform()
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath + runtime
	systemProperty("spike.session", session)
	systemProperty("spike.results", rootProject.file("results").absolutePath)
	jvmArgs(jvm)
	testLogging { events("passed", "failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

registerSuite("testClassic", classicRuntime, "classic", sparkOpens)
registerSuite("testConnect", connectRuntime, "connect", listOf("--add-opens=java.base/java.nio=ALL-UNNAMED"))
