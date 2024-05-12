import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.errorprone.CheckSeverity

plugins {
    java
    `maven-publish`
    id("com.palantir.git-version") version "0.12.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
    id("net.minecrell.plugin-yml.bungee") version "0.5.1" // Generates bungee.yml
    id("net.ltgt.errorprone") version "2.0.2"
    id("net.ltgt.nullaway") version "1.3.0"
	id("com.playmonumenta.deployment") version "1.0"
    checkstyle
    pmd
}

repositories {
    mavenLocal()

    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }

    maven {
        url = uri("https://raw.githubusercontent.com/TeamMonumenta/monumenta-redis-sync/master/mvn-repo/")
    }

    maven {
        url = uri("https://ci.mg-dev.eu/plugin/repository/everything/")
    }

    // NBT API, pulled in by CommandAPI
    maven {
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-bukkit-core:9.4.1")
    compileOnly("com.playmonumenta:redissync:3.0")
    compileOnly("com.bergerkiller.bukkit:BKCommonLib:1.19.4-v2")
    errorprone("com.google.errorprone:error_prone_core:2.10.0")
    errorprone("com.uber.nullaway:nullaway:0.9.5")

    // Bungeecord deps
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.8.5")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

group = "com.playmonumenta"
val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
description = "MonumentaWorldManagement"

// Configure plugin.yml generation
bukkit {
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD
    main = "com.playmonumenta.worlds.paper.WorldManagementPlugin"
    apiVersion = "1.13"
    name = "MonumentaWorldManagement"
    authors = listOf("The Monumenta Team")
    depend = listOf("CommandAPI", "MonumentaRedisSync")
    softDepend = listOf()
}

// Configure bungee.yml generation
bungee {
    name = "MonumentaWorldManagement"
    main = "com.playmonumenta.worlds.bungee.WorldManagementBungeePlugin"
    author = "The Monumenta Team"
    depends = setOf("MonumentaRedisSync")
}

pmd {
    isConsoleOutput = true
    toolVersion = "6.41.0"
    ruleSets = listOf("$rootDir/pmd-ruleset.xml")
    setIgnoreFailures(true)
}

publishing {
    publications.create<MavenPublication>("maven") {
        project.shadow.component(this)
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/TeamMonumenta/monumenta-world-management")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xmaxwarns")
    options.compilerArgs.add("10000")
    options.compilerArgs.add("-Xlint:deprecation")

    options.errorprone {
        option("NullAway:AnnotatedPackages", "com.playmonumenta")

        allErrorsAsWarnings.set(true)

        /*** Disabled checks ***/
        // These we almost certainly don't want
        check("CatchAndPrintStackTrace", CheckSeverity.OFF) // This is the primary way a lot of exceptions are handled
        check("FutureReturnValueIgnored", CheckSeverity.OFF) // This one is dumb and doesn't let you check return values with .whenComplete()
        check("ImmutableEnumChecker", CheckSeverity.OFF) // Would like to turn this on but we'd have to annotate a bunch of base classes
        check("LockNotBeforeTry", CheckSeverity.OFF) // Very few locks in our code, those that we have are simple and refactoring like this would be ugly
        check("StaticAssignmentInConstructor", CheckSeverity.OFF) // We have tons of these on purpose
        check("StringSplitter", CheckSeverity.OFF) // We have a lot of string splits too which are fine for this use
        check("MutablePublicArray", CheckSeverity.OFF) // These are bad practice but annoying to refactor and low risk of actual bugs
        check("InlineMeSuggester", CheckSeverity.OFF) // This seems way overkill
    }
}

ssh.easySetup(tasks.named<ShadowJar>("shadowJar").get(), "MonumentaWorldManagement")
