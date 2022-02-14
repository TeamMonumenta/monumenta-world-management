package com.playmonumenta.worlds.paper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class WorldManagementListener implements Listener {

	private @Nullable BukkitTask mUnloadTask = null;
	private int mHighestSeenInstance = 0;

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
		} else {
			int score = ScoreboardUtils.getScoreboardValue(player, WorldManagementPlugin.getInstanceObjective()).orElse(0);
			if (score <= 0) {
				player.sendMessage(ChatColor.RED + "You joined an instanced world without an instance assigned to you. Unless you are an operator, this is probably a bug");
			} else {
				try {
					World world = MonumentaWorldManagementAPI.ensureWorldLoaded(WorldManagementPlugin.getBaseWorldName() + score, false, WorldManagementPlugin.allowInstanceAutocreation());
					event.setWorld(world);

					if (score > mHighestSeenInstance) {
						mHighestSeenInstance = score;
						refreshPregeneration(WorldManagementPlugin.getInstance(), WorldManagementPlugin.getInstance().getLogger(), 5 * 20); // 5s delay
					}

					if (!event.getWorld().getName().equals(event.getLastSavedWorldName())) {
						// JOIN: The player is joining this world after having last been on a different world (or null)
						if (WorldManagementPlugin.getJoinInstanceCommand() != null) {
							Bukkit.getScheduler().runTaskLater(WorldManagementPlugin.getInstance(), () -> {
								Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " at @s run " + WorldManagementPlugin.getJoinInstanceCommand());
							}, 1);
						}
					} else {
						// REJOIN: The player is joining this world after having most recently left this world
						if (WorldManagementPlugin.getRejoinInstanceCommand() != null) {
							Bukkit.getScheduler().runTaskLater(WorldManagementPlugin.getInstance(), () -> {
								Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " at @s run " + WorldManagementPlugin.getRejoinInstanceCommand());
							}, 1);
						}
					}
				} catch (Exception ex) {
					String msg = "Failed to load your assigned world instance " + score + ": " + ex.getMessage();
					player.sendMessage(msg);
					WorldManagementPlugin.getInstance().getLogger().warning(msg);
					ex.printStackTrace();
				}
			}
		}

		if (WorldManagementPlugin.getNotifyWorldPermission() != null && player.hasPermission(WorldManagementPlugin.getNotifyWorldPermission())) {
			Bukkit.getScheduler().runTaskLater(WorldManagementPlugin.getInstance(), () -> {
				player.sendMessage(ChatColor.GREEN + "Joined world " + event.getWorld().getName());
			}, 1);
		}
	}

	protected void reloadConfig(Plugin plugin) {
		if (mUnloadTask != null && !mUnloadTask.isCancelled()) {
			mUnloadTask.cancel();
			mUnloadTask = null;
		}

		if (WorldManagementPlugin.getUnloadInactiveWorldAfterTicks() > 0) {
			Map<UUID, Integer> worldIdleTimes = new HashMap<>();

			mUnloadTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
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

		if (WorldManagementPlugin.getPregeneratedInstances() > 0) {
			/* Instance pregeneration was set in config */
			String rboardName = WorldManagementPlugin.getPregeneratedRBoardName();
			String rboardKey = WorldManagementPlugin.getPregeneratedRBoardKey();
			Logger logger = plugin.getLogger();

			if (rboardName != null && rboardKey != null) {
				Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
					try {
						Map<String, String> result = MonumentaRedisSyncAPI.rboardGet(rboardName, rboardKey).get();
						String score = result.get(rboardKey);
						if (score == null) {
							logger.warning("Tried to get rboard value " + rboardName + " -> " + rboardKey + " but got null");
							logger.warning("Defaulting to " + mHighestSeenInstance + ", which is probably not what you want");
						} else {
							int rboard = Integer.parseInt(score);
							if (rboard >= mHighestSeenInstance) {
								mHighestSeenInstance = rboard;
								logger.info("Pregeneration enabled: Setting highest seen instance to " + mHighestSeenInstance + " from RBoard");
							} else {
								logger.info("Pregeneration enabled: Leaving highest seen instance at " + mHighestSeenInstance + " which is already higher than value " + rboard + " from RBoard");
							}
						}
					} catch (Exception ex) {
						logger.severe("Caught exception while fetching highest seen instance: " + ex.getMessage());
						ex.printStackTrace();
					}
					refreshPregeneration(plugin, logger, 15 * 20); // 15s delay
				});
			} else {
				refreshPregeneration(plugin, logger, 15 * 20); // 15s delay
			}
		}
	}

	/**
	 * Starts pregeneration of instances that are missing relative to mHighestSeenInstance
	 *
	 * Can run this sync or async.
	 */
	void refreshPregeneration(Plugin plugin, Logger logger, int baseDelayTicks) {
		if (WorldManagementPlugin.getPregeneratedInstances() > 0) {
			logger.info("Refreshing instance pregeneration: Highest seen instance: " + mHighestSeenInstance);

			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				MonumentaWorldManagementAPI.getAvailableWorlds(); // Updates the cache

				for (int i = 0; i < WorldManagementPlugin.getPregeneratedInstances(); i++) {
					int instance = mHighestSeenInstance + 1 + i;
					String name = WorldManagementPlugin.getBaseWorldName() + instance;

					pregenerate(plugin, logger, name, baseDelayTicks + 15*20*i); // Run tasks 15s apart, just to avoid overloading the startup process
				}
			});
		}
	}

	/**
	 * Causes a world to be pregenerated if it doesn't exist.
	 *
	 * Can run this sync or async. Will test the cache before unnecessarily scheduling world generation
	 */
	void pregenerate(Plugin plugin, Logger logger, String name, int delayTicks) {
		logger.fine("Requested pregeneration of instance " + name);
		if (!MonumentaWorldManagementAPI.isCachedWorldAvailable(name)) {
			Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
				logger.info("Starting pregeneration of instance " + name);
				try {
					MonumentaWorldManagementAPI.ensureWorldLoaded(name, true, true);
					logger.info("Instance " + name + " pregeneration complete");
				} catch (Exception ex) {
					logger.severe("Failed to pregenerate world " + name + ": " + ex.getMessage());
					ex.printStackTrace();
				}
			}, delayTicks);
			logger.fine("Scheduled pregeneration of instance " + name + " which will start in " + delayTicks + " ticks");
		}
	}
}
