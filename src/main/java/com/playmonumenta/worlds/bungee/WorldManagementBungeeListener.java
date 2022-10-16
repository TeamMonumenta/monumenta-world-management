package com.playmonumenta.worlds.bungee;

import com.playmonumenta.networkrelay.GatherHeartbeatDataEventBungee;
import de.myzelyam.api.vanish.BungeeVanishAPI;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class WorldManagementBungeeListener implements Listener {
	private static final String PLUGIN_IDENTIFIER = "com.playmonumenta.worlds";

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
	public void gatherHeartbeatDataEvent(GatherHeartbeatDataEventBungee event) throws Exception {
		event.setPluginData(PLUGIN_IDENTIFIER, new JsonObject());
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void serverSwitchEvent(ServerSwitchEvent event) {
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerDisconnectEvent(PlayerDisconnectEvent event) {
	}
}
