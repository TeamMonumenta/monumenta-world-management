package com.playmonumenta.worlds.paper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.playmonumenta.worlds.common.CustomLogger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class WorldManagementPlugin extends JavaPlugin {
	private static WorldManagementPlugin INSTANCE = null;

	private static CustomLogger mLogger = null;
	private static String mTemplateWorldName = "template";
	private static String mBaseWorldName = "world";
	private static boolean mIsInstanced = false;
	private static boolean mAllowInstanceAutocreation = false;
	private static int mUnloadInactiveWorldAfterTicks = 10 * 60 * 20;
	private static String mInstanceObjective = "Instance";

	private WorldManagementListener mListener = null;

	@Override
	public void onLoad() {
		ChangeLogLevelCommand.register(this);
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		loadConfig();

		mListener = new WorldManagementListener(this);
		Bukkit.getPluginManager().registerEvents(mListener, this);

		MonumentaWorldManagementAPI.refreshCachedAvailableWorlds();
	}

	protected void loadConfig() {
		File configFile = new File(getDataFolder(), "config.yml");

		/* Create the config file & directories if it does not exist */
		if (!configFile.exists()) {
			try {
				// Create parent directories if they do not exist
				configFile.getParentFile().mkdirs();

				// Copy the default config file
				Files.copy(getClass().getResourceAsStream("/default_config.yml"), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ex) {
				getLogger().log(Level.SEVERE, "Failed to create configuration file");
			}
		}

		/* Load the config file & parse it */
		YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		String logLevel = config.getString("log-level", "INFO");
		mTemplateWorldName = config.getString("template-world-name", mTemplateWorldName);
		mBaseWorldName = config.getString("base-world-name", mBaseWorldName);
		mIsInstanced = config.getBoolean("is-instanced", mIsInstanced);
		mAllowInstanceAutocreation = config.getBoolean("allow-instance-autocreation", mAllowInstanceAutocreation);
		mUnloadInactiveWorldAfterTicks = config.getInt("unload-inactive-world-after-ticks", mUnloadInactiveWorldAfterTicks);
		mInstanceObjective = config.getString("instance-objective", mInstanceObjective);

		/* Echo config */
		try {
			getLogger().setLevel(Level.parse(logLevel));
			getLogger().info("log-level=" + logLevel);
		} catch (Exception ex) {
			getLogger().warning("log-level=" + logLevel + " is invalid - defaulting to INFO");
		}

		if (mListener != null) {
			mListener.reloadConfig(this);
		}
	}

	public static String getTemplateWorldName() {
		return mTemplateWorldName;
	}

	public static String getBaseWorldName() {
		return mBaseWorldName;
	}

	public static boolean isInstanced() {
		return mIsInstanced;
	}

	public static boolean allowInstanceAutocreation() {
		return mAllowInstanceAutocreation;
	}

	public static int getUnloadInactiveWorldAfterTicks() {
		return mUnloadInactiveWorldAfterTicks;
	}

	public static String getInstanceObjective() {
		return mInstanceObjective;
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
	}

	@Override
	public Logger getLogger() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}
		return mLogger;
	}

	protected void setLogLevel(Level level) {
		super.getLogger().info("Changing log level to: " + level.toString());
		getLogger().setLevel(level);
	}

	protected static WorldManagementPlugin getInstance() {
		return INSTANCE;
	}
}
