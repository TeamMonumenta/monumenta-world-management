package com.playmonumenta.worlds.paper;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument.EntitySelector;
import dev.jorel.commandapi.arguments.StringArgument;

public class ChangeLogLevelCommand {
	public static void register(WorldManagementPlugin worldPlugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("worldManagement")
				.withSubcommand(new CommandAPICommand("changeLogLevel")
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
					.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getAvailableWorlds()))
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

						try {
							MonumentaWorldManagementAPI.unloadWorld(worldName);
						} catch (Exception ex) {
							CommandAPI.fail(ex.getMessage());
						}

						sender.sendMessage("Unloaded world: " + worldName);
					}))
				.withSubcommand(new CommandAPICommand("forceworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.forceworld"))
					.withArguments(new EntitySelectorArgument("player", EntitySelector.ONE_PLAYER))
					.withArguments(new StringArgument("worldName").replaceSuggestions((info) -> MonumentaWorldManagementAPI.getAvailableWorlds()))
					.executes((sender, args) -> {
						Player player = (Player)args[0];
						String worldName = (String)args[1];

						try {
							World newWorld = MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, false, false);

							Location loc = player.getLocation();
							loc.setWorld(newWorld);
							player.teleport(loc);

							sender.sendMessage("Loaded world '" + worldName + "' and sent player to it");
						} catch (Exception ex) {
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

						try {
							MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, false, true);
						} catch (Exception ex) {
							CommandAPI.fail(ex.getMessage());
						}

						sender.sendMessage("Created and loaded world '" + worldName + "' from master copy");
					}))
			).register();
	}
}
