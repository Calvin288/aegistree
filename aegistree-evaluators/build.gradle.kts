plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":aegistree-spi"))
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test { useJUnitPlatform() }
