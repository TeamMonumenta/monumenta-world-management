package com.playmonumenta.worlds.paper;

import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class WorldManagementListener implements Listener {

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerJoinSetWorldEvent(PlayerJoinSetWorldEvent event) {
		WorldManagementPlugin wmp = WorldManagementPlugin.getInstance();
		if (!wmp.isInstanced()) {
			// Nothing to do if not instanced
			return;
		}

		Player player = event.getPlayer();

		int score = ScoreboardUtils.getScoreboardValue(player, wmp.getInstanceObjective()).orElse(0);
		if (score <= 0) {
			player.sendMessage("You joined an instanced world without an instance assigned to you. Unless you are an operator, this is probably a bug");
		} else {
			try {
				World world = MonumentaWorldManagementAPI.ensureWorldLoaded(wmp.getBaseWorldName() + score, false, wmp.mAllowInstanceAutocreation());
				event.setWorld(world);
			} catch (Exception ex) {
				String msg = "Failed to load your assigned world instance " + score + ": " + ex.getMessage();
				player.sendMessage(msg);
				wmp.getLogger().warning(msg);
				ex.printStackTrace();
			}
		}
	}
}
