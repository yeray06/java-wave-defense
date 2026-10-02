// libGDX game: screens, rendering, UI, editor, audio, input.
dependencies {
  "api"(project(":core-sim"))
  "api"(project(":platform"))
  "api"(libs.gdx)

  "testImplementation"(libs.gdx.backend.headless)
  "testRuntimeOnly"(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}

tasks.withType<Test>().configureEach { workingDir = rootProject.file("assets") }
