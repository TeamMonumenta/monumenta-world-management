package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldGenerator {
	private static WorldGenerator INSTANCE = null;
	private static final String PREGEN_PREFIX = "pregen_";
	private static final String GENERATING_SUFFIX = ".generating";
	// TODO Rework to handle multiple template worlds
	private final LinkedBlockingQueue<String> mPregeneratedWorlds = new LinkedBlockingQueue<>();
	private final ConcurrentSkipListMap<String, CompletableFuture<String>> mPregeneratingWorlds = new ConcurrentSkipListMap<>();
	private @Nullable BukkitRunnable mPregenScheduler = null;

	private WorldGenerator() {
		INSTANCE = this;

		if (!WorldManagementPlugin.isInstanced()) {
			MMLog.info("This shard is not instanced, shutting down world generator.");
		}

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

		// TODO File watcher to restart generation if pregenerated world is deleted? Command to regenerate pregenerated instances?

		// Start generating instances
		schedulePregeneration();
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

	public int pregeneratingInstances() {
		return mPregeneratingWorlds.size();
	}

	public static boolean worldExists(String name) {
		File target = new File(name);
		return target.isDirectory() && new File(target, "level.dat").isFile();
	}

	public void getWorldInstance(String worldName) {
		MMLog.fine("Preparing world " + worldName);
		if (worldExists(worldName)) {
			MMLog.fine("World already exists: " + worldName);
			return;
		}

		// Wait for next world to be ready
		String pregeneratedWorldName = null;
		while (pregeneratedWorldName == null) {
			try {
				pregeneratedWorldName = mPregeneratedWorlds.take();
			} catch (InterruptedException ignored) {}
		}

		MMLog.fine("Moving " + pregeneratedWorldName + " to " + worldName);
		File oldPath = new File(pregeneratedWorldName);
		File target = new File(worldName);
		if (!oldPath.renameTo(target)) {
			if (worldExists(pregeneratedWorldName)) {
				MMLog.warning("Failed to move " + pregeneratedWorldName + " to " + worldName);
				mPregeneratedWorlds.add(pregeneratedWorldName);
			}
			if (worldExists(worldName)) {
				MMLog.info("World " + worldName + " somehow appeared without moving a preloaded world");
			} else {
				MMLog.severe("Failed to load " + worldName + "!");
			}
		}

		schedulePregeneration();
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
			if (existingFuture == future) {
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

		String pregeneratedWorldName = pregenName;
		MMLog.info("Starting pregeneration of " + pregeneratedWorldName);
		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
			try {
				// Generate the instance
				String generatingWorldName = pregeneratedWorldName + GENERATING_SUFFIX;
				Process process = Runtime.getRuntime().exec(WorldManagementPlugin.getCopyWorldCommand() + " " + templateName + " " + generatingWorldName);
				int exitVal = process.waitFor();
				if (exitVal != 0) {
					String msg = "Failed to copy world '" + templateName + "' to '" + generatingWorldName + "': " + exitVal;
					MMLog.severe(msg);
					throw new Exception(msg);
				}

				// Move to pregenerated world path
				File generatingWorld = new File(generatingWorldName);
				File pregeneratedWorld = new File(pregeneratedWorldName);
				if (!generatingWorld.renameTo(pregeneratedWorld)) {
					String msg = "Failed to move pregenerating world " + generatingWorld + " to " + pregeneratedWorld;
					MMLog.severe(msg);
					throw new Exception(msg);
				}

				// Mark as complete and return pregen world name
				mPregeneratedWorlds.add(pregeneratedWorldName);
				mPregeneratingWorlds.remove(pregeneratedWorldName);
				MMLog.info("Finished pregenerating " + pregeneratedWorldName);
				future.complete(pregeneratedWorldName);
			} catch (Exception ex) {
				MMLog.severe("Pregeneration failed: " + pregeneratedWorldName);
				future.completeExceptionally(ex);
			}
		});
		return future;
	}

	/*
	 * Start generating instances every 15 seconds, if they're not already generating
	 */
	public void schedulePregeneration() {
		if (!WorldManagementPlugin.isInstanced()) {
			return;
		}
		if (mPregenScheduler != null) {
			return;
		}

		mPregenScheduler = new BukkitRunnable() {
			@Override
			public void run() {
				if (pregeneratedInstances() + pregeneratingInstances() >= WorldManagementPlugin.getPregeneratedInstances()) {
					MMLog.info("All pregeneration started (" + pregeneratingInstances() + ") or complete (" + pregeneratedInstances() + ").");
					mPregenScheduler = null;
					this.cancel();
					return;
				}

				// TODO Use future?
				generateWorldInstance();
			}
		};
		mPregenScheduler.runTaskTimerAsynchronously(WorldManagementPlugin.getInstance(), 0, 15 * 20);
	}
}
