plugins {
    // Auto-download the JDK 21 toolchain when it is missing on the machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "aeristree"

include(
    "aegistree-spi",
    "aegistree-core",
    "aegistree-evaluators",
    "aegistree-jev-client",
    "aegistree-server",
    "aegistree-spi-kafka",
)
