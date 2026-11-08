dependencyResolutionManagement {
	repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
	repositories {
		mavenCentral()
		// The scalafix plugin is published to the Gradle plugin portal only, not to Maven Central
		gradlePluginPortal {
			content {
				includeGroup("io.github.cosmicsilence")
			}
		}
	}
	versionCatalogs {
		create("libs") {
			from(files("../gradle/libs.versions.toml"))
		}
	}
}

rootProject.name = "build-logic"
