// Conventions shared by every module. Kept in the root script instead of a
// convention plugin: six similar modules don't justify buildSrc yet.

plugins { alias(libs.plugins.spotless) }

val javaVersion =
  providers.fileContents(layout.projectDirectory.file(".java-version")).asText.map { it.trim() }

// Spotless is applied once, at the root, over every module.
spotless {
  java {
    target("*/src/**/*.java")
    // Default google-java-format version pinned by this Spotless release: newer ones
    // break its glue code (Style.valueOf NoSuchMethodError with 1.37.0).
    googleJavaFormat()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("*.gradle.kts", "*/*.gradle.kts")
    ktfmt().googleStyle()
  }
}

subprojects {
  apply(plugin = "java-library")

  group = "io.github.yeray06.wavedefense"
  version = "0.1.0"

  extensions.configure<JavaPluginExtension> {
    toolchain { languageVersion = javaVersion.map { JavaLanguageVersion.of(it) } }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
  }

  dependencies {
    "testImplementation"(platform(rootProject.libs.junit.bom))
    "testImplementation"(rootProject.libs.junit.jupiter)
    "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("failed") }
  }
}
