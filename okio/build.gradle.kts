import aQute.bnd.gradle.BundleTaskExtension
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import kotlinx.validation.ApiValidationExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
  kotlin("multiplatform")
  id("app.cash.burst")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("build-support")
  id("binary-compatibility-validator")
}

/*
 * Here's the main hierarchy of variants. Any `expect` functions in one level of the tree are
 * `actual` functions in a (potentially indirect) child node.
 *
 * ```
 *   common
 *   |-- js
 *   |-- jvm
 *   |-- native
 *   |   |-- mingw
 *   |   |   '-- mingwX64
 *   |   '-- unix
 *   |       |-- apple
 *   |       |   |-- iosArm64
 *   |       |   |-- iosX64
 *   |       |   |-- macosX64
 *   |       |   |-- tvosArm64
 *   |       |   |-- tvosX64
 *   |       |   |-- watchosArm32
 *   |       |   |-- watchosArm64
 *   |       '-- linux
 *   |           |-- linuxX64
 *   |           '-- linuxArm64
 *   '-- wasm
 *       '-- wasmJs
 *       '-- wasmWasi
 * ```
 *
 * The `nonJvm`, `nonJs`, `nonApple`, etc. source sets exclude the corresponding platforms.
 *
 * The `hashFunctions` source set builds on all platforms. It ships as a main source set on non-JVM
 * platforms and as a test source set on the JVM platform.
 *
 * The `systemFileSystem` source set is used on jvm and native targets, and provides the FileSystem.SYSTEM property.
 */
kotlin {
  configureOrCreateOkioPlatforms()

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    group("common") {
      group("unix") {
        group("linux")
        group("apple")
      }
      group("wasm") {
        withWasmJs()
        withWasmWasi()
      }

      group("systemFileSystem") {
        withJvm()
        group("native")
      }
      group("zlib") {
        withJvm()
        group("native")
      }

      group("nonApple") {
        withCompilations {
          (it.target as? KotlinNativeTarget)?.konanTarget?.family?.isAppleFamily != true
        }
      }
      group("nonJvm") {
        withCompilations { it.target !is KotlinJvmTarget }
      }
      group("nonJs") {
        withCompilations { it.target.platformType.name != "js" }
      }
      group("nonWasm") {
        withCompilations { it.target.platformType.name != "wasm" }
      }

      group("hashFunctions") {
        group("nonJvm")
      }
    }
  }

  sourceSets {
    all {
      languageSettings.apply {
        // Required for CPointer etc. since Kotlin 1.9.
        optIn("kotlinx.cinterop.ExperimentalForeignApi")
        // Required for Contract API. since Kotlin 1.3.
        optIn("kotlin.contracts.ExperimentalContracts")
      }
    }
    matching { it.name.endsWith("Test") }.all {
      languageSettings {
        optIn("kotlin.time.ExperimentalTime")
      }
    }

    val commonMain by getting
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(projects.okioTestingSupport)
      }
    }

    val nonWasmTest by getting {
      dependencies {
        implementation(libs.kotlin.time)
        implementation(projects.okioFakefilesystem)
      }
    }

    val zlibTest by getting {
      dependencies {
        implementation(libs.test.assertk)
      }
    }

    val jvmTest by getting {
      kotlin.srcDir("src/hashFunctions")
      dependencies {
        implementation(libs.test.junit)
        implementation(libs.test.assertj)
        implementation(libs.test.jimfs)
      }
    }
  }

  targets.withType<KotlinNativeTargetWithTests<*>> {
    binaries {
      // Configure a separate test where code runs in background
      test("background", setOf(NativeBuildType.DEBUG)) {
        freeCompilerArgs += "-trw"
      }
    }
    testRuns {
      val background by creating {
        setExecutionSourceFrom(binaries.getByName("backgroundDebugTest") as TestExecutable)
      }
    }
  }
}

tasks {
  val jvmJar by getting(Jar::class) {
    // BundleTaskExtension() crashes unless there's a 'main' source set.
    sourceSets.create(SourceSet.MAIN_SOURCE_SET_NAME)
    val bndExtension = BundleTaskExtension(this)
    bndExtension.setBnd(
      """
      Export-Package: okio
      Automatic-Module-Name: okio
      Bundle-SymbolicName: com.squareup.okio
      """,
    )
    // Call the extension when the task has finished to modify the jar to contain OSGi metadata.
    doLast {
      bndExtension.buildAction()
        .execute(this)
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = JavadocJar.Empty()),
  )
}

plugins.withId("binary-compatibility-validator") {
  configure<ApiValidationExtension> {
    ignoredProjects += "jmh"
  }
}
