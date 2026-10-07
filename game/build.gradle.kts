// libGDX game: screens, rendering, UI, editor, audio, input.
dependencies {
  "api"(project(":core-sim"))
  "api"(project(":platform"))
  "api"(libs.gdx)
  "api"(libs.gdx.freetype)
  // settings.json, written like the save (core-sim reads its data with the same library).
  "implementation"(platform(libs.jackson.bom))
  "implementation"(libs.jackson.databind)

  "testImplementation"(libs.gdx.backend.headless)
  "testRuntimeOnly"(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
  "testRuntimeOnly"(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}

tasks.withType<Test>().configureEach { workingDir = rootProject.file("assets") }
