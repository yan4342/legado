plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.legado.app.baselineprofile"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE"
    }

    targetProjectPath = ":app"

    flavorDimensions += listOf("mode")
    productFlavors {
        create("app") { dimension = "mode" }
    }
}

baselineProfile {
    useConnectedDevices = true
    // useConnectedDevices = false
    // managedDevices {
    //     register("pixel6Api34", com.android.build.api.dsl.ManagedVirtualDevice) {
    //         device = "Pixel 6"
    //         apiLevel = 34
    //         systemImageSource = "aosp"
    //     }
    // }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
}

androidComponents {
    onVariants { v ->
        v.instrumentationRunnerArguments.put("targetAppId", "io.legado.app.release")
    }
}
