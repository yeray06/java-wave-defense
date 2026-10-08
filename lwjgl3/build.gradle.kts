// Desktop launcher (LWJGL3 backend) and its packages with a bundled Java runtime (Construo).
import io.github.fourlastor.construo.Target

plugins {
  application
  alias(libs.plugins.construo)
}

dependencies {
  "implementation"(project(":game"))
  "implementation"(libs.gdx.backend.lwjgl3)
  "runtimeOnly"(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
  "runtimeOnly"(variantOf(libs.gdx.freetype.platform) { classifier("natives-desktop") })
}

sourceSets { main { resources.srcDir(rootProject.file("assets")) } }

val launcher = "io.github.yeray06.wavedefense.lwjgl3.Lwjgl3Launcher"

application {
  mainClass = launcher
  applicationName = "java-wave-defense"
}

tasks.named<JavaExec>("run") {
  workingDir = rootProject.file("assets")
  isIgnoreExitValue = true
}

// One jar with the game, its libraries, natives and assets: what Construo packages.
val distJar =
  tasks.register<Jar>("distJar") {
    archiveBaseName = "java-wave-defense"
    archiveClassifier = "all"
    manifest { attributes("Main-Class" to launcher) }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
  }

// Bundled runtime: Temurin from its GitHub releases (api.adoptium.net is not reachable from every
// network). Its major version must be the one in /.java-version.
val runtimeRelease = "21.0.8+9"
val javaVersion = rootProject.file(".java-version").readText().trim()

check(runtimeRelease.substringBefore('.') == javaVersion) {
  "runtimeRelease $runtimeRelease does not match .java-version $javaVersion"
}

fun temurin(file: String) =
  "https://github.com/adoptium/temurin$javaVersion-binaries/releases/download/" +
    "jdk-${runtimeRelease.replace("+", "%2B")}/" +
    "OpenJDK${javaVersion}U-jdk_${file}_hotspot_${runtimeRelease.replace('+', '_')}"

// The release tag gives it (Package workflow: v0.2.0 → 0.2.0); by hand, a development one.
val gameVersion = providers.gradleProperty("gameVersion").getOrElse("0.0.0-dev")

construo {
  name = "java-wave-defense"
  humanName = "Java Wave Defense"
  version = gameVersion
  mainClass = launcher
  jarTask = distJar.name
  outputDir = rootProject.layout.buildDirectory.dir("dist")
  jlink {
    guessModulesFromJar = false
    // What the game, libGDX and LWJGL use; a smaller runtime than the whole JDK.
    modules.addAll("java.base", "java.desktop", "java.logging", "jdk.unsupported")
  }
  roast {
    // macOS: the packaged launcher already starts on the first thread, so StartupHelper must not
    // relaunch (its relaunch needs java.management, which this runtime leaves out).
    runOnFirstThread = true
    vmArgs.add("-Dwavedefense.relaunched=true")
  }
  targets {
    create<Target.Windows>("winX64") {
      architecture = Target.Architecture.X86_64
      jdkUrl = temurin("x64_windows") + ".zip"
      jdkSha256 = "5dbb74a4edda94c2aae97a1e30f448db0ad9023a9466fe31a8c68377d9dfbac4"
      useGpuHint = true
    }
    create<Target.Linux>("linuxX64") {
      architecture = Target.Architecture.X86_64
      jdkUrl = temurin("x64_linux") + ".tar.gz"
      jdkSha256 = "f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219"
    }
    create<Target.MacOs>("macM1") {
      architecture = Target.Architecture.AARCH64
      jdkUrl = temurin("aarch64_mac") + ".tar.gz"
      jdkSha256 = "59422c2292ae4e76b87e00d8808dbe49cffa39af731e08bb0292ddb0af4e0261"
      identifier = "io.github.yeray06.wavedefense"
    }
    create<Target.MacOs>("macX64") {
      architecture = Target.Architecture.X86_64
      jdkUrl = temurin("x64_mac") + ".tar.gz"
      jdkSha256 = "0ceaf7060b2c9dbbe8ecc4fb9351c6b4cf24e4350d58772c9656589933a4fdeb"
      identifier = "io.github.yeray06.wavedefense"
    }
  }
}
