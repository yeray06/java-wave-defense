rootProject.name = "java-wave-defense"

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories { mavenCentral() }
}

include("script", "core-sim", "platform", "game", "lwjgl3", "sim-cli")
