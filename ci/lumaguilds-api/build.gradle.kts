plugins {
    kotlin("jvm") version "2.3.20"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    sourceSets.main {
        kotlin.srcDir(providers.gradleProperty("lumaSourceDir"))
        kotlin.include("net/lumalyte/lg/api/GuildLookup.kt", "net/lumalyte/lg/api/GuildVisualLookup.kt")
    }
}

tasks.jar {
    archiveFileName.set("LumaGuilds-api.jar")
}
