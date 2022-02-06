import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.hidetake.groovy.ssh.core.Remote
import org.hidetake.groovy.ssh.core.RunHandler
import org.hidetake.groovy.ssh.core.Service
import org.hidetake.groovy.ssh.session.SessionHandler

plugins {
    java
    `maven-publish`
    id("com.palantir.git-version") version "0.12.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.1" // Generates plugin.yml
    id("net.minecrell.plugin-yml.bungee") version "0.5.1" // Generates bungee.yml
    id("org.hidetake.ssh") version "2.10.1"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }

    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }

    maven {
        url = uri("https://raw.githubusercontent.com/TeamMonumenta/monumenta-network-relay/master/mvn-repo/")
    }

    maven {
        url = uri("https://raw.githubusercontent.com/TeamMonumenta/monumenta-redis-sync/master/mvn-repo/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("dev.jorel.CommandAPI:commandapi-core:6.0.0")
    compileOnly("com.playmonumenta:monumenta-network-relay:1.0")
    compileOnly("com.playmonumenta:redissync:3.0")

    // Bungeecord deps
    compileOnly("net.md-5:bungeecord-api:1.15-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.8.5")
}

group = "com.playmonumenta"
val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
description = "MonumentaWorldManagement"
java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

// Configure plugin.yml generation
bukkit {
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD
    main = "com.playmonumenta.worlds.paper.WorldManagementPlugin"
    apiVersion = "1.13"
    name = "MonumentaWorldManagement"
    authors = listOf("The Monumenta Team")
    depend = listOf("CommandAPI", "MonumentaNetworkRelay", "MonumentaRedisSync")
}

// Configure bungee.yml generation
bungee {
    name = "MonumentaWorldManagement"
    main = "com.playmonumenta.worlds.bungee.WorldManagementBungeePlugin"
    author = "The Monumenta Team"
    depends = setOf("MonumentaNetworkRelay", "MonumentaRedisSync")
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

val basicssh = remotes.create("basicssh") {
    host = "admin-eu.playmonumenta.com"
    port = 8822
    user = "epic"
    agent = true
    knownHosts = allowAnyHosts
}

val adminssh = remotes.create("adminssh") {
    host = "admin-eu.playmonumenta.com"
    port = 9922
    user = "epic"
    agent = true
    knownHosts = allowAnyHosts
}

tasks.create("stage-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(basicssh) {
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/stage/m12/server_config/plugins")
                execute("cd /home/epic/stage/m12/server_config/plugins && rm -f MonumentaWorldManagement.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaWorldManagement.jar")
            }
        }
    }
}

tasks.create("build-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(adminssh) {
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/project_epic/server_config/plugins")
                execute("cd /home/epic/project_epic/server_config/plugins && rm -f MonumentaWorldManagement.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaWorldManagement.jar")
                execute("cd /home/epic/project_epic/mobs/plugins && rm -f MonumentaWorldManagement.jar && ln -s ../../server_config/plugins/MonumentaWorldManagement.jar")
            }
        }
    }
}

tasks.create("play-deploy") {
    val shadowJar by tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar)
    doLast {
        ssh.runSessions {
            session(adminssh) {
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/play/m8/server_config/plugins")
                put(shadowJar.archiveFile.get().getAsFile(), "/home/epic/play/m11/server_config/plugins")
                execute("cd /home/epic/play/m8/server_config/plugins && rm -f MonumentaWorldManagement.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaWorldManagement.jar")
                execute("cd /home/epic/play/m11/server_config/plugins && rm -f MonumentaWorldManagement.jar && ln -s " + shadowJar.archiveFileName.get() + " MonumentaWorldManagement.jar")
            }
        }
    }
}

fun Service.runSessions(action: RunHandler.() -> Unit) =
    run(delegateClosureOf(action))

fun RunHandler.session(vararg remotes: Remote, action: SessionHandler.() -> Unit) =
    session(*remotes, delegateClosureOf(action))

fun SessionHandler.put(from: Any, into: Any) =
    put(hashMapOf("from" to from, "into" to into))
