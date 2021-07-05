package com.playmonumenta.worlds.paper;

import java.util.logging.Level;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;

public class ChangeLogLevelCommand {
	public static void register(WorldManagementPlugin relayPlugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("worldManagement")
				.withSubcommand(new CommandAPICommand("changeLogLevel")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.changeloglevel"))
					.withSubcommand(new CommandAPICommand("INFO")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.INFO);
						}))
					.withSubcommand(new CommandAPICommand("FINE")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.FINE);
						}))
					.withSubcommand(new CommandAPICommand("FINER")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.FINER);
						}))
					.withSubcommand(new CommandAPICommand("FINEST")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.FINEST);
						}))
			)).register();
	}
}
