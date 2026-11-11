plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.freqhierarchy.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.freqhierarchy.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

// Os testes Kotlin leem os MESMOS vetores que o Python (../vetores na raiz do repositorio).
tasks.withType<Test>().configureEach {
    systemProperty("vetores.dir", rootDir.parentFile.resolve("vetores").absolutePath)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
