// Headless balance simulator: plays the MVP with reference scripts and writes docs/BALANCE.md.
plugins { application }

dependencies {
  "implementation"(project(":core-sim"))
  "implementation"(platform(libs.jackson.bom))
  "implementation"(libs.jackson.databind)
}

application { mainClass = "io.github.yeray06.wavedefense.simcli.SimMain" }

// Paths ("assets", "docs/BALANCE.md") are relative to the repository root.
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

tasks.withType<Test>().configureEach { workingDir = rootProject.projectDir }
