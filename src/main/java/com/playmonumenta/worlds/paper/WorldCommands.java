package com.playmonumenta.worlds.paper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.lightcleaner.lighting.LightingService;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.LocationType;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldCommands {
	private static final Map<UUID, Integer> INITIAL_VIEW_DISTANCES = new HashMap<>();

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
							sender.sendMessage("View distance for world '" + world.getName() + "' set to " + Integer.toString(distance));
						} else {
							Integer initial = INITIAL_VIEW_DISTANCES.get(world.getUID());
							if (initial == null) {
								sender.sendMessage("Original view distance for world '" + world.getName() + "' unchanged, currently " + Integer.toString(world.getViewDistance()));
							} else {
								world.setViewDistance(initial);
								sender.sendMessage("View distance for world '" + world.getName() + "' reset to " + Integer.toString(initial));
							}
						}
					}))
				.withSubcommand(new CommandAPICommand("listworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.listworlds"))
					.executes((sender, args) -> {
						sender.sendMessage("Currently loaded worlds:");
						for (World world : Bukkit.getWorlds()) {
							List<String> listOfPlayerNames = world.getPlayers().stream().map((player) -> player.getName()).collect(Collectors.toList());
							sender.sendMessage("  " + world.getName() + ": " + String.join(", ", listOfPlayerNames));
						}
						if (sender instanceof Player) {
							sender.sendMessage("Current world: " + ((Player)sender).getWorld().getName());
						}
					}))
				.withSubcommand(new CommandAPICommand("loadworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.loadworld"))
					.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds()))
					.executes((sender, args) -> {
						String worldName = (String)args[0];

						try {
							MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, false, false);
						} catch (Exception ex) {
							CommandAPI.fail(ex.getMessage());
						}

						sender.sendMessage("Loaded world: " + worldName);
					}))
				.withSubcommand(new CommandAPICommand("unloadworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.unloadworld"))
					.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> Bukkit.getWorlds().stream().map((world) -> world.getName()).toArray(String[]::new)))
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
				.withSubcommand(new CommandAPICommand("forceworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.forceworld"))
					.withArguments(new EntitySelectorArgument("player", EntitySelector.ONE_PLAYER))
					.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds()))
					.executes((sender, args) -> {
						forceWorld(sender, (Player)args[0], (String)args[1]);
					}))
				.withSubcommand(new CommandAPICommand("sortworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.sortworld"))
					.withArguments(new EntitySelectorArgument("player", EntitySelector.ONE_PLAYER))
					.executes((sender, args) -> {
						try {
							MonumentaWorldManagementAPI.sortWorld((Player)args[0]);
						} catch (Exception ex) {
							ex.printStackTrace();
							CommandAPI.fail(ex.getMessage());
						}
					}))
				.withSubcommand(new CommandAPICommand("createworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.createworld"))
					.withArguments(new StringArgument("worldName"))
					.executes((sender, args) -> {
						String worldName = (String)args[0];

						if (MonumentaWorldManagementAPI.isWorldAvailable(worldName)) {
							sender.sendMessage("World '" + worldName + "' already exists, this command is for creating new worlds");
							return;
						}

						sender.sendMessage("Started creating world '" + worldName + "' from master copy");
						Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
							try {
								MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, true, true);
								Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
									sender.sendMessage("Created and loaded world '" + worldName + "' from master copy");
								});
							} catch (Exception ex) {
								Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
									sender.sendMessage("Failed to create world '" + worldName + "': " + ex.getMessage());
								});
							}
						});

					}))
				.withSubcommand(new CommandAPICommand("createworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.createworld"))
					.withArguments(new StringArgument("worldName"))
					.withArguments(new StringArgument("copyFromWorldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds()))
					.executes((sender, args) -> {
						String worldName = (String)args[0];
						String copyFromWorldName = (String)args[1];

						if (MonumentaWorldManagementAPI.isWorldAvailable(worldName)) {
							sender.sendMessage("World '" + worldName + "' already exists, this command is for creating new worlds");
							return;
						}

						if (!MonumentaWorldManagementAPI.isWorldAvailable(copyFromWorldName)) {
							sender.sendMessage("Copy-from world '" + copyFromWorldName + "' does not exist");
							return;
						}

						sender.sendMessage("Started creating world '" + worldName + "' using '" + copyFromWorldName + "' as the template");
						Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
							try {
								MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, true, true, copyFromWorldName);
								Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
									sender.sendMessage("Created and loaded world '" + worldName + "' using '" + copyFromWorldName + "' as the template");
								});
							} catch (Exception ex) {
								Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
									sender.sendMessage("Failed to create world '" + worldName + "': " + ex.getMessage());
								});
							}
						});
					}))
				.withSubcommand(new CommandAPICommand("deleteworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.deleteworld"))
					.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds()))
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
				.withSubcommand(new CommandAPICommand("upgradeworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.upgradeworlds"))
					.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds()))
					.executes((sender, args) -> {
						String startWorld = (String)args[0];
						Logger log = WorldManagementPlugin.getInstance().getLogger();
						// Get a list of worlds in alphabetical order
						List<World> worlds = Bukkit.getWorlds();
						if (!startWorld.toLowerCase().equals("null")) {
							for (int i = 0; i < worlds.size(); i++) {
								if (worlds.get(i).getName().equals(startWorld)) {
									worlds = worlds.subList(i, worlds.size());
									break;
								}
							}
						}
						worlds.sort((World w1, World w2) -> w1.getName().compareTo(w2.getName()));
						for (World world : worlds) {
							new BukkitRunnable() {
								String worldName = world.getName();

								@Override
								public void run() {
									// Load world
									try {
										MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, false, false);
									} catch (Exception ex) {
										CommandAPI.fail(ex.getMessage());
									}
									// Lightcleaner API
									final long lightTime = System.currentTimeMillis();
									LightingService.scheduleWorld(world);
									log.finer(() -> "scheduleLighting took " + Long.toString(System.currentTimeMillis() - lightTime) + " milliseconds (main thread)"); // STOP -->
									// Unload world
									MonumentaWorldManagementAPI.unloadWorld(worldName).whenComplete((unused, ex) -> {
										if (ex != null) {
											sender.sendMessage("Failed to unload world '" + worldName + "': " + ex.getMessage());
										} else {
											sender.sendMessage("Unloaded world '" + worldName + "'");
										}
									});
									// Message
									log.fine(String.format("World %s has finished upgrading.", world.getName()));
								}
							}.runTaskLater(WorldManagementPlugin.getInstance(), 0);
						}
					}))
			).register();


		// Register a copy of "/monumenta worldmanagement forceworld @s world" as "/world <world>" for convenience
		new CommandAPICommand("world")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.forceworld"))
			.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds()))
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
					List<String> listOfPlayerNames = world.getPlayers().stream().map((player) -> player.getName()).collect(Collectors.toList());
					sender.sendMessage("  " + world.getName() + ": " + String.join(", ", listOfPlayerNames));
				}
				if (sender instanceof Player) {
					sender.sendMessage("Current world: " + ChatColor.AQUA + ((Player)sender).getWorld().getName());
				}
			})
			.register();
	}

	private static void forceWorld(CommandSender sender, Player player, String worldName) throws WrapperCommandSyntaxException {
		// Important - need to save the player's location data on the existing world
		player.saveData();
		Bukkit.getScheduler().runTaskLater(WorldManagementPlugin.getInstance(), () -> {
			try {
				World newWorld = MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, false, false);

				MonumentaRedisSyncAPI.getPlayerWorldData(player, newWorld).applyToPlayer(player);
			} catch (Exception ex) {
				sender.sendMessage(ChatColor.RED + ex.getMessage());
				ex.printStackTrace();
			}
		}, 1);

		player.sendMessage("Loaded world '" + worldName + "' and moved to it");
	}
}
