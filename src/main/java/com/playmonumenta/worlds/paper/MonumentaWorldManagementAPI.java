package com.playmonumenta.worlds.paper;

import java.io.File;
import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

public class MonumentaWorldManagementAPI {

	private static String[] AVAILABLE_WORLDS_CACHE = new String[0];

	/**
	 * Checks whether the named world exists and could be loaded, using the cache. Fast and suitable for main thread.
	 */
	public static boolean isCachedWorldAvailable(String worldName) {
		for (String name : AVAILABLE_WORLDS_CACHE) {
			if (name.equals(worldName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether the named world exists and could be loaded.
	 *
	 * Note that this uses file I/O and so will be slow - recommend calling this only from an async thread
	 */
	public static boolean isWorldAvailable(String worldName) {
		File test = new File(worldName);
		if (test.isDirectory() && new File(test, "level.dat").isFile()) {
			// File is a directory - and contains level.dat
			return true;
		}
		return false;
	}

	/**
	 * Gets a list of all the named world folders from the cache. Fast and suitable for main thread.
	 */
	public static String[] getCachedAvailableWorlds() {
		return AVAILABLE_WORLDS_CACHE;
	}

	/**
	 * Gets a list of all the named world folders.
	 *
	 * Note that this uses file I/O and so will be slow - recommend calling this only from an async thread
	 *
	 * Updates the available worlds cache, but may take a tick or two before the cache is updated
	 */
	public static String[] getAvailableWorlds() {
		File root = new File(".");
		String[] directories = root.list((current, name) -> {
			File test = new File(current, name);
			if (test.isDirectory() && new File(test, "level.dat").isFile()) {
				// File is a directory - and contains level.dat
				return true;
			}
			return false;
		});
		Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
			AVAILABLE_WORLDS_CACHE = directories;
		});
		return directories;
	}

	/**
	 * Refreshes the available worlds cache async. Expected to be called on main thread.
	 * Note that the cache may not be updated for several ticks!
	 */
	public static void refreshCachedAvailableWorlds() {
		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
			getAvailableWorlds();
		});
	}

	/**
	 * Gets the specified world, loading and optionally creating it if needed.
	 *
	 * Will always return a non-null world, or throw an exception if the request is not possible
	 *
	 * If world is already loaded will return it (fast)
	 * If world already exists but is not loaded, will load that world (slow, maybe a few ticks on good hardware)
	 * If world does not exist and copyTemplateIfNotExist, will copy that world and then load it (extremely slow, possibly several seconds)
	 *
	 * Note that this uses file I/O and so will be slow - recommend calling this only from an async thread
	 *
	 * Caller should specify calledAsync = false if called from the main game loop thread, or calledAsync = true if called async
	 */
	public static World ensureWorldLoaded(String worldName, boolean calledAsync, boolean copyTemplateIfNotExist) throws Exception {
		WorldManagementPlugin plugin = WorldManagementPlugin.getInstance();
		if (plugin == null) {
			throw new Exception("MonumentaWorldManagement plugin is not loaded");
		}

		/* Try to get existing world first */
		World newWorld = null;
		if (calledAsync) {
			newWorld = Bukkit.getScheduler().callSyncMethod(WorldManagementPlugin.getInstance(), () -> Bukkit.getWorld(worldName)).get();
		} else {
			newWorld = Bukkit.getWorld(worldName);
		}
		if (newWorld != null) {
			return newWorld;
		}

		//TODO Check redis to make sure world isn't loaded or created elsewhere

		/* Copy world if it doesn't exist */
		File worldFolder = new File(worldName);
		if (!worldFolder.isDirectory()) {
			/* Not allowed to create so return null */
			if (!copyTemplateIfNotExist) {
				throw new Exception("World '" + worldName + "' does not exist and copyTemplateIfNotExist is false");
			}

			//TODO: This needs to be a config option, or bundled with this plugin somehow
			Process process = Runtime.getRuntime().exec("/automation/utility_code/copy_world.py" + " " + WorldManagementPlugin.getTemplateWorldName() + " " + worldName);

			int exitVal = process.waitFor();
			if (exitVal != 0) {
				String msg = "Failed to copy world '" + "template" + "' to '" + worldName + "': " + exitVal;
				plugin.getLogger().severe(msg);
				throw new Exception(msg);
			}

			if (calledAsync) {
				Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
					AVAILABLE_WORLDS_CACHE = Arrays.copyOf(AVAILABLE_WORLDS_CACHE, AVAILABLE_WORLDS_CACHE.length + 1);
					AVAILABLE_WORLDS_CACHE[AVAILABLE_WORLDS_CACHE.length - 1] = worldName;
				});
			} else {
				AVAILABLE_WORLDS_CACHE = Arrays.copyOf(AVAILABLE_WORLDS_CACHE, AVAILABLE_WORLDS_CACHE.length + 1);
				AVAILABLE_WORLDS_CACHE[AVAILABLE_WORLDS_CACHE.length - 1] = worldName;
			}

			plugin.getLogger().fine("Created new world instance '" + worldName + "'");
		}

		plugin.getLogger().fine("Loaded world '" + worldName + "'");
		if (calledAsync) {
			return Bukkit.getScheduler().callSyncMethod(WorldManagementPlugin.getInstance(), () ->
				new WorldCreator(worldName).type(WorldType.NORMAL).generateStructures(false).environment(Environment.NORMAL).createWorld()
			).get();
		} else {
			return new WorldCreator(worldName).type(WorldType.NORMAL).generateStructures(false).environment(Environment.NORMAL).createWorld();
		}
	}

	public static void unloadWorld(String worldName) throws Exception {
		World world = Bukkit.getWorld(worldName);
		if (world == null) {
			throw new Exception("World '" + worldName + "' is not loaded");
		}

		if (Bukkit.getWorlds().get(0).equals(world)) {
			throw new Exception("Can't unload main world '" + worldName + "'");
		}

		//TODO Mark world as unloaded in redis

		if (!world.getPlayers().isEmpty()) {
			throw new Exception("Can't unload world '" + worldName + "' because there are still players in it");
		}

		if (!Bukkit.unloadWorld(world, true)) {
			throw new Exception("Unloading world '" + worldName + "' failed, unknown reason");
		}
	}
}
