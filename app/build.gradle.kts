plugins { id("com.android.application") }
android {
    namespace = "org.poetrymemory"
    compileSdk = 36
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    defaultConfig {
        applicationId = "org.poetrymemory"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.6.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        release {
            isMinifyEnabled = false
            // PoetryMemory has its own application identity and release lifecycle.
            // This is a non-debuggable build with the existing development signing identity.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        // AGP 9.1 OverdrawDetector crashes in Kotlin FIR ("Unexpected owner function: null").
        // Keep all other lint checks enabled; see ACCEPTANCE.md for this tool limitation.
        disable += "Overdraw"
    }
}
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}
