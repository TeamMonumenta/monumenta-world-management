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
		Player player = event.getPlayer();

		if (!WorldManagementPlugin.isInstanced()) {
			// If not an instanced server, still try to load the player's last world & put them there
			try {
				World world = MonumentaWorldManagementAPI.ensureWorldLoaded(event.getLastSavedWorldName(), false, false);
				event.setWorld(world);
			} catch (Exception ex) {
				String msg = "Failed to load the last world you were on (" + event.getLastSavedWorldName() + "): " + ex.getMessage();
				player.sendMessage(msg);
				WorldManagementPlugin.getInstance().getLogger().warning(msg);
				ex.printStackTrace();
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
		if (mRunnable != null) {
			mRunnable.cancel();
			mRunnable = null;
		}

		if (WorldManagementPlugin.getUnloadInactiveWorldAfterTicks() > 0) {
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
								MonumentaWorldManagementAPI.unloadWorld(world.getName()).whenComplete((unused, ex) -> {
									if (ex != null) {
										plugin.getLogger().warning("Failed to unload world '" + world.getName() + "': " + ex.getMessage());
									} else {
										mWorldIdleTimes.remove(world.getUID());
										plugin.getLogger().info("Unloaded world " + world.getName());
									}
								});
								// Even though it hasn't unloaded yet, reset its idle time so it won't attempt to unload constantly
								mWorldIdleTimes.put(world.getUID(), 0);
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
