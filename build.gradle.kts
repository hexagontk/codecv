import org.gradle.api.JavaVersion.*
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import java.lang.System.getProperty

plugins {
    kotlin("jvm") version("1.8.0")
    id("org.graalvm.buildtools.native") version("0.9.19")
}

val hexagonVersion = "2.4.4"
val hexagonExtraVersion = "2.4.0"
val vertxVersion = "4.3.7"

val modules = "java.logging"
val options = "-Xmx32m"
val icon = "$projectDir/logo.png"
val gradleScripts = "https://raw.githubusercontent.com/hexagonkt/hexagon/$hexagonVersion/gradle"

apply(from = "$gradleScripts/kotlin.gradle")
apply(from = "$gradleScripts/application.gradle")
apply(from = "$gradleScripts/native.gradle")

group = "com.hexagonkt.tools"
version = "0.9.10"
description = "CVs for programmers"

ext {
    set("modules", modules)
    set("options", options)
    set("icon", icon)
}

if (current() !in setOf(VERSION_16, VERSION_17, VERSION_18, VERSION_19))
    error("This build must be run with JDK 16+. Current: ${current()}")

extensions.configure<JavaApplication> {
    mainClass.set("co.codecv.CvKt")
}

dependencies {
    "implementation"("com.hexagonkt:http_server_jetty:$hexagonVersion")
    "implementation"("com.hexagonkt:logging_slf4j_jul:$hexagonVersion")
    "implementation"("com.hexagonkt:serialization_jackson_json:$hexagonVersion")
    "implementation"("com.hexagonkt:serialization_jackson_yaml:$hexagonVersion")
    "implementation"("com.hexagonkt:serialization_jackson_toml:$hexagonVersion")
    "implementation"("com.hexagonkt:templates_pebble:$hexagonVersion")
    "implementation"("com.hexagonkt.extra:web:$hexagonExtraVersion")
    "implementation"("io.vertx:vertx-json-schema:$vertxVersion")

    "testImplementation"("com.hexagonkt:http_client_jetty:$hexagonVersion")
}

tasks.named("classes") { dependsOn("addResources") }
tasks.named("build") { dependsOn("tarJpackage", "metadataCopy") }
tasks.named("nativeCompile") { dependsOn("metadataCopy") }

tasks.create<Copy>("addResources") {
    from(projectDir)
    include("templates/cv.html")
    include("cv.schema.json")
    into(buildDir.resolve("resources/main"))
}

tasks.create<Exec>("install") {
    dependsOn("jpackage")
    commandLine(
        "ln",
        "-sf",
        "${project.buildDir.absolutePath}/codecv/bin/codecv",
        "${getProperty("user.home")}/.local/bin/cv"
    )
}

tasks.create("release") {
    dependsOn("build")

    doLast {
        val release = version.toString()
        val actor = System.getenv("GITHUB_ACTOR")

        project.exec { commandLine = listOf("git", "config", "user.name", actor) }
        project.exec { commandLine = listOf("git", "tag", "-m", "Release $release", release) }
        project.exec { commandLine = listOf("git", "push", "--tags") }
    }
}

extensions.configure<GraalVMExtension> {
    binaries {
        named("main") {
            val os = getProperty("os.name").toLowerCaseAsciiOnly()
            val static =
                if (os.contains("mac")) "-H:+StaticExecutableWithDynamicLibC"
                else "--static"
            val monitoring =
                if (getProperty("enableMonitoring") == "true") "--enable-monitoring"
                else null

            listOfNotNull(
                static,
                monitoring,
                "--enable-preview",
                "--enable-http",
                "--enable-https",
                "--enable-url-protocols=classpath",
                "--initialize-at-build-time=com.hexagonkt.core.ClasspathHandler",
                "-R:MaxHeapSize=32m",
            )
            .forEach(buildArgs::add)
        }
    }
}
