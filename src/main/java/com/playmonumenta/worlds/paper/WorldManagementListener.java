package com.playmonumenta.worlds.paper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldManagementListener implements Listener {

	private BukkitRunnable mRunnable = null;

	protected WorldManagementListener(Plugin plugin) {
		reloadConfig(plugin);
	}


	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerJoinSetWorldEvent(PlayerJoinSetWorldEvent event) {
		if (!WorldManagementPlugin.isInstanced()) {
			// Nothing to do if not instanced
			return;
		}

		Player player = event.getPlayer();

		int score = ScoreboardUtils.getScoreboardValue(player, WorldManagementPlugin.getInstanceObjective()).orElse(0);
		if (score <= 0) {
			player.sendMessage("You joined an instanced world without an instance assigned to you. Unless you are an operator, this is probably a bug");
		} else {
			try {
				World world = MonumentaWorldManagementAPI.ensureWorldLoaded(WorldManagementPlugin.getBaseWorldName() + score, false, WorldManagementPlugin.allowInstanceAutocreation());
				event.setWorld(world);
			} catch (Exception ex) {
				String msg = "Failed to load your assigned world instance " + score + ": " + ex.getMessage();
				player.sendMessage(msg);
				WorldManagementPlugin.getInstance().getLogger().warning(msg);
				ex.printStackTrace();
			}
		}
	}

	protected void reloadConfig(Plugin plugin) {
		if (mRunnable != null) {
			mRunnable.cancel();
			mRunnable = null;
		}

		if (WorldManagementPlugin.isInstanced() && WorldManagementPlugin.getUnloadInactiveWorldAfterTicks() > 0) {
			mRunnable = new BukkitRunnable() {
				Map<UUID, Integer> mWorldIdleTimes = new HashMap<>();

				@Override
				public void run() {
					List<World> worlds = Bukkit.getWorlds();
					for (int i = 1; i < worlds.size(); i++) { // Ignore the primary world
						World world = worlds.get(i);

						if (world.getPlayers().size() > 0) {
							mWorldIdleTimes.put(world.getUID(), 0);
						} else {
							Integer idleTime = mWorldIdleTimes.getOrDefault(world.getUID(), 0) + 200;
							if (idleTime > WorldManagementPlugin.getUnloadInactiveWorldAfterTicks()) {
								plugin.getLogger().info("Unloading world '" + world.getName() + "' which has had no players for " + idleTime + " ticks");
								mWorldIdleTimes.remove(world.getUID());
								Bukkit.unloadWorld(world, true);
							} else {
								mWorldIdleTimes.put(world.getUID(), idleTime);
							}
						}
					}
				}
			};
			mRunnable.runTaskTimer(plugin, 200, 200);
		}
	}
}
