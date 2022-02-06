package com.playmonumenta.worlds.paper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class WorldManagementListener implements Listener {

	private @Nullable BukkitTask mTask = null;

	protected WorldManagementListener(Plugin plugin) {
		reloadConfig(plugin);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerJoinSetWorldEvent(PlayerJoinSetWorldEvent event) {
		Player player = event.getPlayer();

		if (!WorldManagementPlugin.isInstanced()) {
			String lastSavedWorldName = event.getLastSavedWorldName();

			if (lastSavedWorldName != null) {
				// If not an instanced server, still try to load the player's last world & put them there
				try {
					World world = MonumentaWorldManagementAPI.ensureWorldLoaded(lastSavedWorldName, false, false);
					event.setWorld(world);
				} catch (Exception ex) {
					String msg = "Failed to load the last world you were on (" + lastSavedWorldName + "): " + ex.getMessage();
					player.sendMessage(msg);
					WorldManagementPlugin.getInstance().getLogger().warning(msg);
					ex.printStackTrace();
				}
			}
			return;
		}

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
		if (mTask != null && !mTask.isCancelled()) {
			mTask.cancel();
			mTask = null;
		}

		if (WorldManagementPlugin.getUnloadInactiveWorldAfterTicks() > 0) {
			Map<UUID, Integer> worldIdleTimes = new HashMap<>();

			mTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
				List<World> worlds = Bukkit.getWorlds();
				for (int i = 1; i < worlds.size(); i++) { // Ignore the primary world
					World world = worlds.get(i);

					if (world.getPlayers().size() > 0) {
						worldIdleTimes.put(world.getUID(), 0);
					} else {
						Integer idleTime = worldIdleTimes.getOrDefault(world.getUID(), 0) + 200;
						if (idleTime > WorldManagementPlugin.getUnloadInactiveWorldAfterTicks()) {
							plugin.getLogger().info("Unloading world '" + world.getName() + "' which has had no players for " + idleTime + " ticks");
							MonumentaWorldManagementAPI.unloadWorld(world.getName()).whenComplete((unused, ex) -> {
								if (ex != null) {
									plugin.getLogger().warning("Failed to unload world '" + world.getName() + "': " + ex.getMessage());
								} else {
									worldIdleTimes.remove(world.getUID());
									plugin.getLogger().info("Unloaded world " + world.getName());
								}
							});
							// Even though it hasn't unloaded yet, reset its idle time so it won't attempt to unload constantly
							worldIdleTimes.put(world.getUID(), 0);
						} else {
							worldIdleTimes.put(world.getUID(), idleTime);
						}
					}
				}
			}, 200, 200);
		}
	}
}
