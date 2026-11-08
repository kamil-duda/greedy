plugins { scala }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
	implementation(libs.scala.library)
	compileOnly(libs.spark.sql.api)
}
