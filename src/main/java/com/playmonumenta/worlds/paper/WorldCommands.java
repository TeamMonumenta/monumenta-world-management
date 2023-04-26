package com.playmonumenta.worlds.paper;

import com.bergerkiller.bukkit.lightcleaner.lighting.LightingService;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.LocationType;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldCommands {
	private static final Map<UUID, Integer> INITIAL_VIEW_DISTANCES = new HashMap<>();

	@SuppressWarnings("unchecked")
	public static void register(WorldManagementPlugin worldPlugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("worldmanagement")
				.withSubcommand(new CommandAPICommand("changeloglevel")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.changeloglevel"))
					.withSubcommand(new CommandAPICommand("INFO")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.INFO);
						}))
					.withSubcommand(new CommandAPICommand("FINE")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.FINE);
						}))
					.withSubcommand(new CommandAPICommand("FINER")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.FINER);
						}))
					.withSubcommand(new CommandAPICommand("FINEST")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.FINEST);
						})))
				.withSubcommand(new CommandAPICommand("setviewdistance")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.setviewdistance"))
					.withArguments(new LocationArgument("location", LocationType.PRECISE_POSITION))
					.withArguments(new IntegerArgument("value", -1, 32))
					.executes((sender, args) -> {
						World world = ((Location)args[0]).getWorld();
						int distance = (Integer)args[1];

						if (distance > 0) {
							if (!INITIAL_VIEW_DISTANCES.containsKey(world.getUID())) {
								INITIAL_VIEW_DISTANCES.put(world.getUID(), world.getViewDistance());
							}

							world.setViewDistance(distance);
							sender.sendMessage("View distance for world '" + world.getName() + "' set to " + distance);
						} else {
							Integer initial = INITIAL_VIEW_DISTANCES.get(world.getUID());
							if (initial == null) {
								sender.sendMessage("Original view distance for world '" + world.getName() + "' unchanged, currently " + world.getViewDistance());
							} else {
								world.setViewDistance(initial);
								sender.sendMessage("View distance for world '" + world.getName() + "' reset to " + initial);
							}
						}
					}))
				.withSubcommand(new CommandAPICommand("listworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.listworlds"))
					.executes((sender, args) -> {
						sender.sendMessage("Currently loaded worlds:");
						for (World world : Bukkit.getWorlds()) {
							List<String> listOfPlayerNames = world.getPlayers().stream().map(HumanEntity::getName).collect(Collectors.toList());
							sender.sendMessage("  " + world.getName() + ": " + String.join(", ", listOfPlayerNames));
						}
						if (sender instanceof Player) {
							sender.sendMessage("Current world: " + ((Player)sender).getWorld().getName());
						}
					}))
				.withSubcommand(new CommandAPICommand("loadworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.loadworld"))
					.withArguments(new StringArgument("worldName").replaceSuggestions(
						ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
					.executes((sender, args) -> {
						String worldName = (String)args[0];

						try {
							MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, null);
						} catch (Exception ex) {
							throw CommandAPI.failWithString(ex.getMessage());
						}

						sender.sendMessage("Loaded world: " + worldName);
					}))
				.withSubcommand(new CommandAPICommand("unloadworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.unloadworld"))
					.withArguments(new StringArgument("worldName").replaceSuggestions(
						ArgumentSuggestions.strings((info) -> Bukkit.getWorlds().stream().map(World::getName).toArray(String[]::new))))
					.executes((sender, args) -> {
						String worldName = (String)args[0];

						sender.sendMessage("Started unloading world: " + worldName);
						MonumentaWorldManagementAPI.unloadWorld(worldName).whenComplete((unused, ex) -> {
							if (ex != null) {
								sender.sendMessage("Failed to unload world '" + worldName + "': " + ex.getMessage());
							} else {
								sender.sendMessage("Unloaded world '" + worldName + "'");
							}
						});
					}))
				.withSubcommand(new CommandAPICommand("unloadAllEmptyWorlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.unloadallemptyworlds"))
					.executes((sender, args) -> {
						sender.sendMessage("Started unloading empty worlds");
						for (World world : Bukkit.getWorlds()) {
							if (world.getPlayers().size() == 0 && !world.equals(Bukkit.getWorlds().get(0))) {
								String worldName = world.getName();
								MonumentaWorldManagementAPI.unloadWorld(worldName).whenComplete((unused, ex) -> {
									if (ex != null) {
										sender.sendMessage("Failed to unload world '" + worldName + "': " + ex.getMessage());
									} else {
										sender.sendMessage("Unloaded world '" + worldName + "'");
									}
								});
							}
						}
					}))
				.withSubcommand(new CommandAPICommand("forceworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.forceworld"))
					.withArguments(new EntitySelectorArgument.OnePlayer("player"))
					.withArguments(new StringArgument("worldName").replaceSuggestions(
						ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
					.executes((sender, args) -> {
						forceWorld(sender, (Player)args[0], (String)args[1]);
					}))
				.withSubcommand(new CommandAPICommand("sortworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.sortworld"))
					.withArguments(new EntitySelectorArgument.OnePlayer("player"))
					.executes((sender, args) -> {
						try {
							MonumentaWorldManagementAPI.sortWorld((Player)args[0]);
						} catch (Exception ex) {
							ex.printStackTrace();
							throw CommandAPI.failWithString(ex.getMessage());
						}
					}))
				.withSubcommand(new CommandAPICommand("createworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.createworld"))
					.withArguments(new StringArgument("worldName"))
					.withArguments(new StringArgument("templateName"))
					.executes((sender, args) -> {
						String worldName = (String)args[0];
						String templateName = (String)args[1];

						if (MonumentaWorldManagementAPI.isWorldAvailable(worldName)) {
							sender.sendMessage("World '" + worldName + "' already exists, this command is for creating new worlds");
							return;
						}

						sender.sendMessage("Started creating world '" + worldName + "' from template");
						try {
							MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, templateName);
							sender.sendMessage("Created and loaded world '" + worldName + "' from master copy");
						} catch (Exception ex) {
							sender.sendMessage("Failed to create world '" + worldName + "': " + ex.getMessage());
						}
					}))
				.withSubcommand(new CommandAPICommand("copyworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.copyworld"))
					.withArguments(new StringArgument("copyFromWorldName").replaceSuggestions(
						ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
					.withArguments(new StringArgument("newWorldName"))
					.executes((sender, args) -> {
						String fromWorldName = (String)args[0];
						String newWorldName = (String)args[1];

						sender.sendMessage("Attempting to copy world '" + fromWorldName + "' to '" + newWorldName + "'...");
						MonumentaWorldManagementAPI.copyWorld(fromWorldName, newWorldName).whenComplete((unused, ex) -> {
							if (ex != null) {
								sender.sendMessage(ex.getMessage());
							} else {
								sender.sendMessage("Successfully copied world '" + fromWorldName + "' to '" + newWorldName + "'");
							}
						});
					}))
				.withSubcommand(new CommandAPICommand("deleteworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.deleteworld"))
					.withArguments(new StringArgument("worldName").replaceSuggestions(
						ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
					.executes((sender, args) -> {
						String worldName = (String)args[0];

						MonumentaWorldManagementAPI.deleteWorld(worldName).whenComplete((unused, ex) -> {
							if (ex != null) {
								sender.sendMessage("Failed to delete world '" + worldName + "': " + ex.getMessage());
							} else {
								sender.sendMessage("Deleted world '" + worldName + "'");
							}
						});
					}))
				// Upgrade all worlds, no arguments
				.withSubcommand(new CommandAPICommand("upgradeallworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.upgradeworlds"))
					// This command can only be called via the console to protect against accidental use
					.executesConsole((sender, args) -> {
						// Get a list of all worldNames in alphabetical order
						List<String> worldNames = Arrays.asList(MonumentaWorldManagementAPI.getAvailableWorlds());
						worldNames.sort(String::compareTo);

						// Upgrade them
						upgradeWorlds(worldNames);
					}))
				// Upgrade all worlds, starting with the one specified
				.withSubcommand(new CommandAPICommand("upgradeallworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.upgradeworlds"))
					.withArguments(new StringArgument("startWorldName").replaceSuggestions(
						ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
					// This command can only be called via the console to protect against accidental use
					.executesConsole((sender, args) -> {
						String startWorld = (String)args[0];

						// Get a list of all worldNames in alphabetical order
						List<String> worldNames = Arrays.asList(MonumentaWorldManagementAPI.getAvailableWorlds());
						worldNames.sort(String::compareTo);

						// Trim off any that come before the user-supplied argument
						int startIndex = worldNames.indexOf(startWorld);
						if (startIndex < 0) {
							sender.sendMessage("Invalid world '" + startWorld + "'");
							return;
						}
						worldNames = worldNames.subList(startIndex, worldNames.size());

						// Upgrade them
						upgradeWorlds(worldNames);
					}))
				// Upgrade a specific world
				.withSubcommand(new CommandAPICommand("upgradeworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.upgradeworld"))
					.withArguments(new StringArgument("worldName").replaceSuggestions(
						ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
					// This command can only be called via the console to protect against accidental use
					.executesConsole((sender, args) -> {
						String worldName = (String)args[0];
						if (!MonumentaWorldManagementAPI.isWorldAvailable(worldName)) {
							sender.sendMessage("Invalid world '" + worldName + "'");
							return;
						}

						// Upgrade
						upgradeWorlds(List.of(worldName));
					}))
			).register();


		// Register a copy of "/monumenta worldmanagement forceworld @s world" as "/world <world>" for convenience
		new CommandAPICommand("world")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.forceworld"))
			.withArguments(new StringArgument("worldName").replaceSuggestions(
				ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
			.executesPlayer((player, args) -> {
				forceWorld(player, player, (String)args[0]);
			})
			.register();

		// Register a copy of "/monumenta worldmanagement listworlds" as "/worlds" for convenience
		new CommandAPICommand("worlds")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.listworlds"))
			.executes((sender, args) -> {
				sender.sendMessage("Currently loaded worlds:");
				for (World world : Bukkit.getWorlds()) {
					List<String> listOfPlayerNames = world.getPlayers().stream().map(HumanEntity::getName).collect(Collectors.toList());
					sender.sendMessage("  " + world.getName() + ": " + String.join(", ", listOfPlayerNames));
				}
				if (sender instanceof Player) {
					sender.sendMessage("Current world: " + ChatColor.AQUA + ((Player)sender).getWorld().getName());
				}
			})
			.register();

		new CommandAPICommand("tptoworld")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.tptoworld"))
			.withArguments(new EntitySelectorArgument.ManyPlayers("targets"))
			.withArguments(new StringArgument("worldName").replaceSuggestions(
				ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
			.withArguments(new LocationArgument("location"))
			.executes((sender, args) -> {
				try {
					teleportToWorld((Collection<Entity>) args[0], (String) args[1], (Location) args[2], 0, 0);
				} catch (Exception e) {
					e.printStackTrace();
				}
			})
			.register();

		new CommandAPICommand("tptoworld")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.tptoworld"))
			.withArguments(new EntitySelectorArgument.ManyPlayers("targets"))
			.withArguments(new StringArgument("worldName").replaceSuggestions(
				ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds())))
			.withArguments(new LocationArgument("location"))
			.withArguments(new FloatArgument("yaw"))
			.withArguments(new FloatArgument("pitch"))
			.executes((sender, args) -> {
				try {
					teleportToWorld((Collection<Entity>) args[0], (String) args[1], (Location) args[2], (Float) args[3], (Float) args[4]);
				} catch (Exception e) {
					e.printStackTrace();
				}
			})
			.register();
	}

	public static void teleportToWorld(Collection<Entity> targets, String world, Location loc, float yaw, float pitch) throws Exception {
		World actualWorld = MonumentaWorldManagementAPI.ensureWorldLoaded(world, null);
		Location newLoc = new Location(actualWorld, loc.getX(), loc.getY(), loc.getZ(), yaw, pitch);
		for (Entity player : targets) {
			player.teleport(newLoc);
		}
	}

	private static void forceWorld(CommandSender sender, Player player, String worldName) {
		// Important - need to save the player's location data on the existing world
		player.saveData();
		Bukkit.getScheduler().runTaskLater(WorldManagementPlugin.getInstance(), () -> {
			try {
				World newWorld = MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, null);

				MonumentaRedisSyncAPI.getPlayerWorldData(player, newWorld).applyToPlayer(player);
			} catch (Exception ex) {
				sender.sendMessage(ChatColor.RED + ex.getMessage());
				ex.printStackTrace();
			}
		}, 1);

		player.sendMessage("Loaded world '" + worldName + "' and moved to it");
	}

	/**
	 * Loads all chunks of input world names.
	 * <p>
	 * For each specified world name:
	 * - Load world,
	 * - Run LightCleaner on it
	 * - Wait for completion
	 * - Unload world (continue on failure)
	 * <p>
	 * Operates on one world at a time. Input world names should be validated beforehand
	 */
	private static void upgradeWorlds(List<String> worldNamesIn) {
		// Copy the input list so it can be modified locally
		List<String> worldNames = new ArrayList<>(worldNamesIn);

		Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
			Logger log = WorldManagementPlugin.getInstance().getLogger();
			int numUpgraded = worldNames.size();
			log.info("Started upgrading " + numUpgraded + " worlds...");

			new BukkitRunnable() {
				@Nullable String mLastWorldName = null;
				long mStartTime = 0;

				@Override
				public void run() {
					// If LightingService is done
					if (!LightingService.isProcessing()) {
						// If there was a last world, unload it
						if (mLastWorldName != null) {
							log.info("Finished upgrading world '" + mLastWorldName + "'");
							log.fine(() -> "Upgrading took " + (System.currentTimeMillis() - mStartTime) + " milliseconds");
							MonumentaWorldManagementAPI.unloadWorld(mLastWorldName).whenComplete((unused, ex) -> {
								if (ex != null) {
									log.severe("Failed to unload world '" + mLastWorldName + "': " + ex.getMessage());
								} else {
									log.info("Unloaded world '" + mLastWorldName + "'");
								}
							});
						}

						if (worldNames.isEmpty()) {
							// Done, exit
							log.info("Completed upgrading " + numUpgraded + " worlds");
							this.cancel();
							return;
						}

						// Not done, keep processing
						mLastWorldName = worldNames.remove(0);
						mStartTime = System.currentTimeMillis();
						log.info("Started upgrading world '" + mLastWorldName + "'");

						// Load world
						World world;
						try {
							world = MonumentaWorldManagementAPI.ensureWorldLoaded(mLastWorldName, null);
						} catch (Exception ex) {
							// Severe error, fail and stop loading new worlds so it doesn't get missed
							log.severe("Failed to load world '" + mLastWorldName + "': " + ex.getMessage());
							this.cancel();
							return;
						}

						// Use LightCleaner to safely load all the chunks of the world (and fix light as a bonus)
						LightingService.scheduleWorld(world);
					}
				}
			}.runTaskTimer(WorldManagementPlugin.getInstance(), 0, 20);
		});
	}
}
