plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "vn.edu.usth.covidstats"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "vn.edu.usth.covidstats"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The watchlist evidence flow deliberately spans several separate
        // instrumentation invocations and toggles device networking. Running
        // it without a storagePhase argument is reported as four failures by
        // AndroidJUnitRunner, so keep it out of the ordinary connected suite.
        testInstrumentationRunnerArguments["notClass"] =
            "vn.edu.usth.covidstats.WatchlistIntegrationTest"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Mang va bieu do
    implementation(libs.volley)
    implementation(libs.mpandroidchart)

    // Khung giao dien
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Man hinh, tab va danh sach
    implementation(libs.fragment)
    implementation(libs.viewpager)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)

    // Kiem thu
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
