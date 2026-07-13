plugins {
    java
}

group = "com.itemlimiter"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    // "provided at runtime by the server" - not bundled into our jar.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.jar {
    archiveBaseName.set("ItemLimiter")
}

tasks.processResources {
    filteringCharset = "UTF-8"
}
