pluginManagement {
	// Convention plugins (greedy.kotlin, greedy.scala) and the third-party Gradle plugins they bring
	includeBuild("build-logic")
}

dependencyResolutionManagement {
	repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
	repositories {
		mavenCentral()
	}
}

rootProject.name = "greedy"

include("spark-rules", "test-support", "app")
