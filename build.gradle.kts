plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "com.aegistree"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
