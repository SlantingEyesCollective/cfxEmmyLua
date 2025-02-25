/*
 * Copyright (c) 2022. Korioz(45950144+Korioz@users.noreply.github.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import de.undercouch.gradle.tasks.download.*
import org.apache.tools.ant.taskdefs.condition.Os
import java.io.ByteArrayOutputStream

plugins {
    id("org.jetbrains.intellij").version("1.13.2")
    id("org.jetbrains.kotlin.jvm").version("1.7.10")
    id("de.undercouch.download").version("3.4.3")
}

data class BuildData(
    val ideaSDKShortVersion: String,
    // https://www.jetbrains.com/intellij-repository/releases
    val ideaSDKVersion: String,
    val sinceBuild: String,
    val untilBuild: String,
    val archiveName: String = "cfxEmmyLua",
    val jvmTarget: String = "1.8",
    val targetCompatibilityLevel: JavaVersion = JavaVersion.VERSION_17,
    val explicitJavaDependency: Boolean = true,
    // https://github.com/JetBrains/gradle-intellij-plugin/issues/403#issuecomment-542890849
    val instrumentCodeCompilerVersion: String = ideaSDKVersion
)

val buildDataList = listOf(
    BuildData(
        ideaSDKShortVersion = "231",
        ideaSDKVersion = "2023.1",
        sinceBuild = "231",
        untilBuild = "243.*"
    ),
    BuildData(
        ideaSDKShortVersion = "223",
        ideaSDKVersion = "2022.3",
        sinceBuild = "223",
        untilBuild = "223.*"
    ),
    BuildData(
        ideaSDKShortVersion = "222",
        ideaSDKVersion = "2022.2",
        sinceBuild = "222",
        untilBuild = "222.*"
    ),
    BuildData(
        ideaSDKShortVersion = "221",
        ideaSDKVersion = "2022.1",
        sinceBuild = "221",
        untilBuild = "221.*"
    ),
    BuildData(
        ideaSDKShortVersion = "211",
        ideaSDKVersion = "2021.1",
        sinceBuild = "211",
        untilBuild = "211.*"
    )
)

val buildVersion = System.getProperty("IDEA_VER") ?: buildDataList.first().ideaSDKShortVersion

val buildVersionData = buildDataList.find { it.ideaSDKShortVersion == buildVersion }!!

val emmyDebuggerVersion = "1.2.9"

val resDir = "src/main/resources"

val isWin = Os.isFamily(Os.FAMILY_WINDOWS)

val isCI = System.getenv("CI") != null

// CI
if (isCI) {
    version = System.getenv("CI_BUILD_VERSION")
    exec {
        executable = "git"
        args("config", "--global", "user.email", "45950144+Korioz@users.noreply.github.com")
    }
    exec {
        executable = "git"
        args("config", "--global", "user.name", "korioz")
    }
}

version = "${version}-IDEA${buildVersion}"

fun getRev(): String {
    val os = ByteArrayOutputStream()
    exec {
        executable = "git"
        args("rev-parse", "HEAD")
        standardOutput = os
    }
    return os.toString().substring(0, 7)
}

task("downloadEmmyDebugger", type = Download::class) {
    src(arrayOf(
        "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/darwin-arm64.zip",
        "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/darwin-x64.zip",
        "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/linux-x64.zip",
        "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/win32-x64.zip",
        "https://github.com/EmmyLua/EmmyLuaDebugger/releases/download/${emmyDebuggerVersion}/win32-x86.zip"
    ))

    dest("temp")
}

task("unzipEmmyDebugger", type = Copy::class) {
    dependsOn("downloadEmmyDebugger")
    from(zipTree("temp/win32-x86.zip")) {
        into("windows/x86")
    }
    from(zipTree("temp/win32-x64.zip")) {
        into("windows/x64")
    }
    from(zipTree("temp/darwin-x64.zip")) {
        into("mac/x64")
    }
    from(zipTree("temp/darwin-arm64.zip")) {
        into("mac/arm64")
    }
    from(zipTree("temp/linux-x64.zip")) {
        into("linux")
    }
    destinationDir = file("temp")
}

task("installEmmyDebugger", type = Copy::class) {
    dependsOn("unzipEmmyDebugger")
    from("temp/windows/x64/") {
        include("emmy_core.dll")
        into("debugger/emmy/windows/x64")
    }
    from("temp/windows/x86/") {
        include("emmy_core.dll")
        into("debugger/emmy/windows/x86")
    }
    from("temp/linux/") {
        include("emmy_core.so")
        into("debugger/emmy/linux")
    }
    from("temp/mac/x64") {
        include("emmy_core.dylib")
        into("debugger/emmy/mac/x64")
    }
    from("temp/mac/arm64") {
        include("emmy_core.dylib")
        into("debugger/emmy/mac/arm64")
    }
    destinationDir = file("src/main/resources")
}

project(":") {
    repositories {
        maven(url = "https://www.jetbrains.com/intellij-repository/releases")
        mavenCentral()
    }

    dependencies {
        implementation(fileTree(baseDir = "libs") { include("*.jar") })
        implementation("com.google.code.gson:gson:2.8.6")
        implementation("org.scala-sbt.ipcsocket:ipcsocket:1.3.0")
        implementation("org.luaj:luaj-jse:3.0.1")
        implementation("org.eclipse.mylyn.github:org.eclipse.egit.github.core:2.1.5")
        implementation("com.jgoodies:forms:1.2.1")
    }

    sourceSets {
        main {
            java.srcDirs("gen", "src/main/compat")
            resources.exclude("debugger/**")
            resources.exclude("std/**")
        }
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = buildVersionData.targetCompatibilityLevel
        targetCompatibility = buildVersionData.targetCompatibilityLevel
    }

    intellij {
        type.set("IU")
        updateSinceUntilBuild.set(false)
        downloadSources.set(!isCI)
        version.set(buildVersionData.ideaSDKVersion)
        sandboxDir.set("${project.buildDir}/${buildVersionData.ideaSDKShortVersion}/idea-sandbox")
    }

    tasks {
        buildPlugin {
            dependsOn("installEmmyDebugger")
            archiveBaseName.set(buildVersionData.archiveName)
            from(fileTree(resDir) { include("!!DONT_UNZIP_ME!!.txt") }) {
                into("/${project.name}")
            }
        }

        compileKotlin {
            kotlinOptions {
                jvmTarget = buildVersionData.jvmTarget
            }
        }

        patchPluginXml {
            sinceBuild.set(buildVersionData.sinceBuild)
            untilBuild.set(buildVersionData.untilBuild)
        }

        instrumentCode {
            compilerVersion.set(buildVersionData.instrumentCodeCompilerVersion)
        }

        publishPlugin {
            token.set(System.getenv("IDEA_PUBLISH_TOKEN"))
        }

        withType<org.jetbrains.intellij.tasks.PrepareSandboxTask> {
            doLast {
                copy {
                    from("src/main/resources/std")
                    into("$destinationDir/${pluginName.get()}/std")
                }
                copy {
                    from("src/main/resources/debugger")
                    into("$destinationDir/${pluginName.get()}/debugger")
                }
            }
        }
    }
}
