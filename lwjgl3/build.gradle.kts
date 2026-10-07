// Desktop launcher (LWJGL3 backend).
plugins { application }

dependencies {
  "implementation"(project(":game"))
  "implementation"(libs.gdx.backend.lwjgl3)
  "runtimeOnly"(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
  "runtimeOnly"(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}

sourceSets { main { resources.srcDir(rootProject.file("assets")) } }

application {
  mainClass = "io.github.yeray06.wavedefense.lwjgl3.Lwjgl3Launcher"
  applicationName = "java-wave-defense"
}

tasks.named<JavaExec>("run") {
  workingDir = rootProject.file("assets")
  isIgnoreExitValue = true
}
