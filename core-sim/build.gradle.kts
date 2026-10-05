// Deterministic simulation. Pure Java: no libGDX, no clock, no disk.
dependencies {
  "api"(project(":script"))
  "implementation"(platform(libs.jackson.bom))
  "implementation"(libs.jackson.databind)
}

// Tests read the real data files, as the game does.
tasks.withType<Test>().configureEach { workingDir = rootProject.file("assets") }
