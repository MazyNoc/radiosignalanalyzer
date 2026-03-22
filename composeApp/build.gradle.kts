import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.example.radiosignalanalyzer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RadioSignalAnalyzer"
            packageVersion = "1.0.0"
            description = "Flipper Zero SubGhz RAW signal analyzer"
            copyright = "© 2025"

            macOS {
                bundleID = "com.example.radiosignalanalyzer"
            }
        }
    }
}

// Convenience task: ./gradlew :composeApp:macApp
tasks.register("macApp") {
    group = "distribution"
    description = "Builds the macOS .dmg installer → build/compose/binaries/main/dmg/"
    dependsOn("packageDmg")
    doLast {
        val dmgDir = layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile
        val dmg = dmgDir.listFiles()?.firstOrNull { it.extension == "dmg" }
        if (dmg != null) println("\n✔ DMG ready: ${dmg.absolutePath}\n")
    }
}
