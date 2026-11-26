plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":aegistree-core"))
    implementation(project(":aegistree-evaluators"))
    implementation(project(":aegistree-jev-client"))
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}
