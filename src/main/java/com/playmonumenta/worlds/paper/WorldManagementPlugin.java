package com.playmonumenta.worlds.paper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.playmonumenta.worlds.common.CustomLogger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class WorldManagementPlugin extends JavaPlugin {
	private static @Nullable WorldManagementPlugin INSTANCE = null;

	private static @Nullable CustomLogger mLogger = null;
	private static String mTemplateWorldName = "template";
	private static String mBaseWorldName = "world";
	private static boolean mSortWorldByScoreOnJoin = false;
	private static boolean mSortWorldByScoreOnRespawn = false;
	private static boolean mAllowInstanceAutocreation = false;
	private static int mPregeneratedInstances = 0;
	private static int mUnloadInactiveWorldAfterTicks = 10 * 60 * 20;
	private static String mInstanceObjective = "Instance";
	private static @Nullable String mJoinInstanceCommand = null;
	private static @Nullable String mRejoinInstanceCommand = null;
	private static @Nullable String mRespawnInstanceCommand = null;
	private static @Nullable String mNotifyWorldPermission = "monumenta.worldmanagement.worldnotify";
	private static String mCopyWorldCommand = "cp -a";

	private @Nullable WorldManagementListener mListener = null;
	private @Nullable WorldGenerator mGenerator = null;

	@Override
	public void onLoad() {
		WorldCommands.register(this);
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		loadConfig();

		mGenerator = WorldGenerator.getInstance();
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
		try {
			getLogger().setLevel(Level.parse(logLevel));
			printConfig("log-level", logLevel);
		} catch (Exception ex) {
			getLogger().warning("log-level=" + logLevel + " is invalid - defaulting to INFO");
		}

		mTemplateWorldName = config.getString("template-world-name", mTemplateWorldName);
		printConfig("template-world-name", mTemplateWorldName);

		mBaseWorldName = config.getString("base-world-name", mBaseWorldName);
		printConfig("base-world-name", mBaseWorldName);

		boolean deprecatedInstanced = config.getBoolean("is-instanced", false);
		if (deprecatedInstanced) {
			getLogger().warning("config 'is-instanced' is deprecated, please use the newer more specific config options");
			mSortWorldByScoreOnJoin = true;
			mSortWorldByScoreOnRespawn = true;
		}

		mSortWorldByScoreOnJoin = config.getBoolean("sort-world-by-score-on-join", mSortWorldByScoreOnJoin);
		printConfig("sort-world-by-score-on-join", mSortWorldByScoreOnJoin);

		mSortWorldByScoreOnRespawn = config.getBoolean("sort-world-by-score-on-respawn", mSortWorldByScoreOnRespawn);
		printConfig("sort-world-by-score-on-respawn", mSortWorldByScoreOnRespawn);

		mAllowInstanceAutocreation = config.getBoolean("allow-instance-autocreation", mAllowInstanceAutocreation);
		printConfig("allow-instance-autocreation", mAllowInstanceAutocreation);

		mPregeneratedInstances = config.getInt("pregenerated-instances", mPregeneratedInstances);
		printConfig("pregenerated-instances", mPregeneratedInstances);
		if (mPregeneratedInstances <= 0) {
			getLogger().warning("Highly recommend setting pregenerated-instances > 0. Instance autocreation may not work at all without this");
		}

		mUnloadInactiveWorldAfterTicks = config.getInt("unload-inactive-world-after-ticks", mUnloadInactiveWorldAfterTicks);
		printConfig("unload-inactive-world-after-ticks", mUnloadInactiveWorldAfterTicks);

		mInstanceObjective = config.getString("instance-objective", mInstanceObjective);
		printConfig("instance-objective", mInstanceObjective);

		mJoinInstanceCommand = config.getString("join-instance-command", mJoinInstanceCommand);
		if (mJoinInstanceCommand != null && (mJoinInstanceCommand.equals("null") || mJoinInstanceCommand.isEmpty())) {
			mJoinInstanceCommand = null;
		}
		printConfig("join-instance-command", mJoinInstanceCommand);

		mRejoinInstanceCommand = config.getString("rejoin-instance-command", mRejoinInstanceCommand);
		if (mRejoinInstanceCommand != null && (mRejoinInstanceCommand.equals("null") || mRejoinInstanceCommand.isEmpty())) {
			mRejoinInstanceCommand = null;
		}
		printConfig("rejoin-instance-command", mRejoinInstanceCommand);

		mRespawnInstanceCommand = config.getString("respawn-instance-command", mRespawnInstanceCommand);
		if (mRespawnInstanceCommand != null && (mRespawnInstanceCommand.equals("null") || mRespawnInstanceCommand.isEmpty())) {
			mRespawnInstanceCommand = null;
		}
		printConfig("respawn-instance-command", mRespawnInstanceCommand);

		mNotifyWorldPermission = config.getString("notify-world-permission", mNotifyWorldPermission);
		if (mNotifyWorldPermission != null && (mNotifyWorldPermission.equals("null") || mNotifyWorldPermission.isEmpty())) {
			mNotifyWorldPermission = null;
		}
		printConfig("notify-world-permission", mNotifyWorldPermission);

		mCopyWorldCommand = config.getString("copy-world-command", mCopyWorldCommand);
		printConfig("copy-world-command", mCopyWorldCommand);

		if (mListener != null) {
			mListener.reloadConfig();
		}
	}

	private <T> void printConfig(String configKey, @Nullable T value) {
		getLogger().info(configKey + "=" + (value == null ? "null" : value));
	}

	public static String getTemplateWorldName() {
		return mTemplateWorldName;
	}

	public static String getBaseWorldName() {
		return mBaseWorldName;
	}

	public static boolean isSortWorldByScoreOnJoin() {
		return mSortWorldByScoreOnJoin;
	}

	public static boolean isSortWorldByScoreOnRespawn() {
		return mSortWorldByScoreOnRespawn;
	}

	public static boolean allowInstanceAutocreation() {
		return mAllowInstanceAutocreation;
	}

	public static int getPregeneratedInstances() {
		return mPregeneratedInstances;
	}

	public static int getUnloadInactiveWorldAfterTicks() {
		return mUnloadInactiveWorldAfterTicks;
	}

	public static String getInstanceObjective() {
		return mInstanceObjective;
	}

	public static @Nullable String getJoinInstanceCommand() {
		return mJoinInstanceCommand;
	}

	public static @Nullable String getRejoinInstanceCommand() {
		return mRejoinInstanceCommand;
	}

	public static @Nullable String getRespawnInstanceCommand() {
		return mRespawnInstanceCommand;
	}

	public static @Nullable String getNotifyWorldPermission() {
		return mNotifyWorldPermission;
	}

	public static String getCopyWorldCommand() {
		return mCopyWorldCommand;
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
	}

	@Override
	public @NotNull Logger getLogger() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}
		return mLogger;
	}

	protected void setLogLevel(Level level) {
		super.getLogger().info("Changing log level to: " + level.toString());
		getLogger().setLevel(level);
	}

	/* If this ever returned null everything would explode anyway, no reason to add error handling around this */
	@SuppressWarnings("NullAway")
	protected static WorldManagementPlugin getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("WorldManagementPlugin accessed before loading");
		}
		return INSTANCE;
	}

	protected WorldGenerator getWorldGenerator() {
		if (mGenerator == null) {
			mGenerator = WorldGenerator.getInstance();
		}
		return mGenerator;
	}
}
