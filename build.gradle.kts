import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
	id("com.playmonumenta.gradle-config") version "1.+"
}

dependencies {
	compileOnly("dev.jorel:commandapi-bukkit-core:9.4.1")
	compileOnly("com.playmonumenta:redissync:4.1:all")
	compileOnly("com.bergerkiller.bukkit:BKCommonLib:1.19.4-v2")
	compileOnly("com.google.code.gson:gson:2.8.5")
}

monumenta {
	name("MonumentaWorldManagement")
	paper(
		"com.playmonumenta.worlds.paper.WorldManagementPlugin", BukkitPluginDescription.PluginLoadOrder.POSTWORLD, "1.19",
		depends = listOf("CommandAPI", "MonumentaRedisSync"),
		softDepends = listOf()
	)
	waterfall(
		"com.playmonumenta.worlds.bungee.WorldManagementBungeePlugin", "1.19",
		depends = listOf("MonumentaRedisSync")
	)
}
