package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class WorldGenerator {
	private static WorldGenerator INSTANCE = null;
	private static final String PREGEN_PREFIX = "pregen_";
	private static final String GENERATING_SUFFIX = ".generating";
	private final LinkedBlockingQueue<String> mPregeneratedWorlds = new LinkedBlockingQueue<>();
	private final ConcurrentSkipListMap<String, CompletableFuture<String>> mPregeneratingWorlds = new ConcurrentSkipListMap<>();

	private WorldGenerator() {
		INSTANCE = this;

		// Get list of pregenerated/pregenerating worlds
		File root = new File(".");
		String[] childPaths = root.list();
		if (childPaths == null) {
			// What
			MMLog.severe("Failed to list pregenerated worlds");
			return;
		}
		for (String name : childPaths) {
			if (!name.startsWith(PREGEN_PREFIX)) {
				continue;
			}
			if (name.endsWith(GENERATING_SUFFIX)) {
				File failedGeneratedInstance = new File(root, name);
				MMLog.info("Deleting failed generating world " + name);
				if (!failedGeneratedInstance.delete()) {
					MMLog.severe("Failed to delete failed generating instance " + name);
				}
				continue;
			}
			MMLog.info("Detected pregenerated world " + name);
			mPregeneratedWorlds.add(name);
		}

		// TODO Start generating instances
	}

	public static WorldGenerator getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new WorldGenerator();
		}
		return INSTANCE;
	}

	public int pregeneratedInstances() {
		return mPregeneratedWorlds.size();
	}

	public boolean worldExists(String name) {
		File target = new File(name);
		return target.isDirectory() && new File(target, "level.dat").isFile();
	}

	public World getWorldInstance(String name) {
		MMLog.fine("Preparing world " + name);
		if (worldExists(name)) {
			MMLog.fine("World already exists: " + name);
			return Bukkit.getWorld(name);
		}

		// Wait for next world to be ready
		String pregeneratedWorldName = null;
		while (pregeneratedWorldName == null) {
			try {
				pregeneratedWorldName = mPregeneratedWorlds.take();
			} catch (InterruptedException ignored) {}
		}

		MMLog.fine("Moving " + pregeneratedWorldName + " to " + name);
		File oldPath = new File(pregeneratedWorldName);
		File target = new File(name);
		if (!oldPath.renameTo(target)) {
			if (worldExists(pregeneratedWorldName)) {
				MMLog.warning("Failed to move " + pregeneratedWorldName + " to " + name);
				mPregeneratedWorlds.add(pregeneratedWorldName);
			}
			if (worldExists(name)) {
				MMLog.info("World " + name + " somehow appeared without moving a preloaded world");
			} else {
				MMLog.severe("Failed to load " + name + "!");
			}
		}
		return Bukkit.getWorld(name);
	}

	private CompletableFuture<String> generateWorldInstance() {
		String templateName = WorldManagementPlugin.getTemplateWorldName();
		CompletableFuture<String> future = new CompletableFuture<>();
		if (!worldExists(templateName)) {
			MMLog.severe("Template world does not exist!");
			future.completeExceptionally(new Exception("Template world does not exist!"));
			return future;
		}

		String pregenBase = PREGEN_PREFIX + WorldManagementPlugin.getBaseWorldName();
		String pregenName = null;
		boolean foundSlot = false;
		for (int pregenIndex = 0; pregenIndex < WorldManagementPlugin.getPregeneratedInstances(); pregenIndex++) {
			pregenName = pregenBase + pregenIndex;
			if (mPregeneratedWorlds.contains(pregenName)) {
				continue;
			}
			CompletableFuture<String> existingFuture = mPregeneratingWorlds.computeIfAbsent(pregenName, key -> future);
			if (existingFuture != future) {
				foundSlot = true;
				break;
			}
		}

		if (pregenName == null) {
			MMLog.warning("Pregen instance limit <= 0!");
			future.completeExceptionally(new Exception("Pregen instance limit <= 0!"));
			return future;
		}
		if (!foundSlot) {
			MMLog.info("All pregen slots filled.");
			future.completeExceptionally(new Exception("All pregen slots filled."));
			return future;
		}

		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
			// TODO copy world
		});
		return future;
	}
}
