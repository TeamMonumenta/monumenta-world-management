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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class WorldManagementListener implements Listener {
	private static @Nullable WorldManagementListener INSTANCE = null;

	private @Nullable BukkitTask mUnloadTask = null;
	private int mHighestSeenInstance = 0;
	private final Plugin mPlugin;
	private final Logger mLogger;

	protected WorldManagementListener(Plugin plugin) {
		mPlugin = plugin;
		mLogger = plugin.getLogger();
		INSTANCE = this;
		reloadConfig();
	}

	protected static @Nullable WorldManagementListener getInstance() {
		return INSTANCE;
	}

	/*
	 * Fix player respawn locations being in the overworld when actually instanced
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerRespawnEvent(PlayerRespawnEvent event) {
		if (!WorldManagementPlugin.isInstanced()) {
			/* Not instanced, don't modify respawn location */
			return;
		}

		Player player = event.getPlayer();
		int score = ScoreboardUtils.getScoreboardValue(player, WorldManagementPlugin.getInstanceObjective()).orElse(0);
		if (score <= 0) {
			player.sendMessage(ChatColor.RED + "You respawned on an instanced world without an instance assigned to you. Unless you are an operator, this is probably a bug");
		} else {
			try {
				/* World should already be loaded, just need to grab it */
				World world = MonumentaWorldManagementAPI.ensureWorldLoaded(WorldManagementPlugin.getBaseWorldName() + score, false, WorldManagementPlugin.allowInstanceAutocreation());

				if (event.getRespawnLocation().getWorld().equals(world)) {
					/* Already respawning on this world, don't need to change location */
					return;
				}

				/* Modify the event so the player respawns on this same world at spawn */
				event.setRespawnLocation(world.getSpawnLocation());

				// RESPAWN: The player is respawning in this world after having (probably) died there
				if (WorldManagementPlugin.getRespawnInstanceCommand() != null) {
					Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
						if (Bukkit.getOnlinePlayers().contains(player)) {
							mLogger.fine("Running respawn command on player=" + player.getName() + " thread=" + Thread.currentThread().getName());
							Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " at @s run " + WorldManagementPlugin.getRespawnInstanceCommand());
						}
					}, 1);
				}
			} catch (Exception ex) {
				String msg = "Failed to load your assigned world instance " + score + ": " + ex.getMessage();
				player.sendMessage(msg);
				mLogger.warning(msg);
				ex.printStackTrace();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerJoinSetWorldEvent(PlayerJoinSetWorldEvent event) {
		Player player = event.getPlayer();
		mLogger.fine("playerJoinSetWorldEvent: player=" + player.getName() + " thread=" + Thread.currentThread().getName());

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
					mLogger.warning(msg);
					ex.printStackTrace();
				}
			}
		} else {
			try {
				event.setWorld(getSortWorld(player, event.getLastSavedWorldName()));
			} catch (Exception ex) {
				mLogger.warning("Failed to set world for player " + player.getName() + ": " + ex.getMessage());
			}
		}

		if (WorldManagementPlugin.getNotifyWorldPermission() != null && player.hasPermission(WorldManagementPlugin.getNotifyWorldPermission())) {
			Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
				if (Bukkit.getOnlinePlayers().contains(player)) {
					player.sendMessage(ChatColor.GREEN + "Joined world " + event.getWorld().getName());
				}
			}, 1);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerChangedWorldEvent(PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		if (WorldManagementPlugin.getNotifyWorldPermission() != null && player.hasPermission(WorldManagementPlugin.getNotifyWorldPermission())) {
			player.sendMessage(ChatColor.GREEN + "Changed to world " + player.getLocation().getWorld().getName());
		}
	}

	protected void reloadConfig() {
		if (mUnloadTask != null && !mUnloadTask.isCancelled()) {
			mUnloadTask.cancel();
			mUnloadTask = null;
		}

		if (WorldManagementPlugin.getUnloadInactiveWorldAfterTicks() > 0) {
			Map<UUID, Integer> worldIdleTimes = new HashMap<>();

			mUnloadTask = Bukkit.getScheduler().runTaskTimer(mPlugin, () -> {
				List<World> worlds = Bukkit.getWorlds();
				for (int i = 1; i < worlds.size(); i++) { // Ignore the primary world
					World world = worlds.get(i);

					if (world.getPlayers().size() > 0) {
						worldIdleTimes.put(world.getUID(), 0);
					} else {
						Integer idleTime = worldIdleTimes.getOrDefault(world.getUID(), 0) + 200;
						if (idleTime > WorldManagementPlugin.getUnloadInactiveWorldAfterTicks()) {
							mLogger.info("Unloading world '" + world.getName() + "' which has had no players for " + idleTime + " ticks");
							MonumentaWorldManagementAPI.unloadWorld(world.getName()).whenComplete((unused, ex) -> {
								if (ex != null) {
									mLogger.warning("Failed to unload world '" + world.getName() + "': " + ex.getMessage());
								} else {
									worldIdleTimes.remove(world.getUID());
									mLogger.info("Unloaded world " + world.getName());
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

			if (rboardName != null && rboardKey != null) {
				Bukkit.getScheduler().runTaskAsynchronously(mPlugin, () -> {
					try {
						Map<String, String> result = MonumentaRedisSyncAPI.rboardGet(rboardName, rboardKey).get();
						String score = result.get(rboardKey);
						if (score == null) {
							mLogger.warning("Tried to get rboard value " + rboardName + " -> " + rboardKey + " but got null");
							mLogger.warning("Defaulting to " + mHighestSeenInstance + ", which is probably not what you want");
						} else {
							int rboard = Integer.parseInt(score);
							if (rboard >= mHighestSeenInstance) {
								mHighestSeenInstance = rboard;
								mLogger.info("Pregeneration enabled: Setting highest seen instance to " + mHighestSeenInstance + " from RBoard");
							} else {
								mLogger.info("Pregeneration enabled: Leaving highest seen instance at " + mHighestSeenInstance + " which is already higher than value " + rboard + " from RBoard");
							}
						}
					} catch (Exception ex) {
						mLogger.severe("Caught exception while fetching highest seen instance: " + ex.getMessage());
						ex.printStackTrace();
					}
					refreshPregeneration(15 * 20); // 15s delay
				});
			} else {
				refreshPregeneration(15 * 20); // 15s delay
			}
		}
	}

	/**
	 * Gets the world where a player should be sorted to based on their instance score.
	 *
	 * Throws an exception if score is 0 or the world fails to load. Will trigger instance pregeneration if applicable.
	 *
	 * If the new world's name is not the provided currentWorldName, then the join command will run, otherwise the rejoin command will run as the player (if configured)
	 *
	 * Will not actually put the player on this world - need to do this and then also set their location data.
	 *
	 * XXX: This should only be called as a precursor to moving the player to this world immediately afterwards on this same tick, otherwise the join/rejoin functions will be called incorrectly!
	 *
	 * Must be called from the main thread
	 */
	protected World getSortWorld(Player player, @Nullable String currentWorldName) throws Exception {
		int score = ScoreboardUtils.getScoreboardValue(player, WorldManagementPlugin.getInstanceObjective()).orElse(0);
		if (score <= 0) {
			throw new Exception("Tried to sort player but instance score is 0");
		}

		World world = MonumentaWorldManagementAPI.ensureWorldLoaded(WorldManagementPlugin.getBaseWorldName() + score, false, WorldManagementPlugin.allowInstanceAutocreation());

		if (score > mHighestSeenInstance) {
			mHighestSeenInstance = score;
			refreshPregeneration(5 * 20); // 5s delay
		}

		if (!world.getName().equals(currentWorldName)) {
			// JOIN: The player is joining this world after having last been on a different world (or null)
			if (WorldManagementPlugin.getJoinInstanceCommand() != null) {
				Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
					if (Bukkit.getOnlinePlayers().contains(player)) {
						mLogger.fine("Running join command on player=" + player.getName() + " thread=" + Thread.currentThread().getName());
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " at @s run " + WorldManagementPlugin.getJoinInstanceCommand());
					}
				}, 1);
			}
		} else {
			// REJOIN: The player is joining this world after having most recently left this world
			if (WorldManagementPlugin.getRejoinInstanceCommand() != null) {
				Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
					if (Bukkit.getOnlinePlayers().contains(player)) {
						mLogger.fine("Running rejoin command on player=" + player.getName() + " thread=" + Thread.currentThread().getName());
						Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " at @s run " + WorldManagementPlugin.getRejoinInstanceCommand());
					}
				}, 1);
			}
		}

		return world;
	}

	/**
	 * Starts pregeneration of instances that are missing relative to mHighestSeenInstance.
	 *
	 * Can run this sync or async. Will only pregenerate if configured to do so.
	 */
	private void refreshPregeneration(int baseDelayTicks) {
		if (WorldManagementPlugin.getPregeneratedInstances() > 0) {
			mLogger.info("Refreshing instance pregeneration: Highest seen instance: " + mHighestSeenInstance);

			Bukkit.getScheduler().runTaskAsynchronously(mPlugin, () -> {
				MonumentaWorldManagementAPI.getAvailableWorlds(); // Updates the cache

				for (int i = 0; i < WorldManagementPlugin.getPregeneratedInstances(); i++) {
					int instance = mHighestSeenInstance + 1 + i;
					String name = WorldManagementPlugin.getBaseWorldName() + instance;

					pregenerate(name, baseDelayTicks + 15*20*i); // Run tasks 15s apart, just to avoid overloading the startup process
				}
			});
		}
	}

	/**
	 * Causes a world to be pregenerated if it doesn't exist.
	 *
	 * Can run this sync or async. Will test the cache before unnecessarily scheduling world generation
	 */
	private void pregenerate(String name, int delayTicks) {
		mLogger.fine("Requested pregeneration of instance " + name);
		if (!MonumentaWorldManagementAPI.isCachedWorldAvailable(name)) {
			Bukkit.getScheduler().runTaskLaterAsynchronously(mPlugin, () -> {
				mLogger.info("Starting pregeneration of instance " + name);
				try {
					MonumentaWorldManagementAPI.ensureWorldLoaded(name, true, true);
					mLogger.info("Instance " + name + " pregeneration complete");
				} catch (Exception ex) {
					mLogger.severe("Failed to pregenerate world " + name + ": " + ex.getMessage());
					ex.printStackTrace();
				}
			}, delayTicks);
			mLogger.fine("Scheduled pregeneration of instance " + name + " which will start in " + delayTicks + " ticks");
		}
	}
}
