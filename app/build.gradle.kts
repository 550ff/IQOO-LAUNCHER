plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.iqoo.launcher"
 compileSdk = 36
 defaultConfig { applicationId = "com.iqoo.launcher"; minSdk = 23; targetSdk = 28; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
