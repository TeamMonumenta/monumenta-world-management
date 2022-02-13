package com.playmonumenta.worlds.paper;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.scheduler.BukkitRunnable;

public class MonumentaWorldManagementAPI {

	private static class LoadWorldTask {
		private static final LoadWorldTask PLACEHOLDER = new LoadWorldTask(null, null, null);

		/*
		 * This is a class that contains a task (mSupplier) that needs to be run to load and return a world (mFuture)
		 * Either all three fields are null (a placeholder task), or all three are non-null
		 */
		private final @Nullable String mName;
		private final @Nullable Supplier<World> mSupplier;
		private final @Nullable CompletableFuture<World> mFuture;

		private LoadWorldTask(@Nullable String name, @Nullable Supplier<World> supplier, @Nullable CompletableFuture<World> future) {
			mName = name;
			mSupplier = supplier;
			mFuture = future;
		}

		/**
		 * Run the supplier now, and use that result to complete the future.
		 *
		 * This should only be called on the main thread!
		 */
		private void runNow() {
			if (mName == null || mSupplier == null || mFuture == null) {
				Logger log = WorldManagementPlugin.getInstance().getLogger();
				log.warning("Attempted to run placeholder load world task!");
				new Exception().printStackTrace();
				return;
			}

			if (mFuture.isDone()) {
				Logger log = WorldManagementPlugin.getInstance().getLogger();
				log.warning("Tried to complete world future '" + mName + "' but it was already completed.");
				log.warning("This is actually expected in some very rare corner cases and is only bad if the server crashes shortly afterwards.");
				log.warning("If the server continues running, ignore the below stacktrace");
				new Exception().printStackTrace();
			} else {
				mFuture.complete(mSupplier.get());
			}
		}

		/**
		 * Waits for the future to complete (BLOCKING!) and returns the result
		 */
		private @Nullable World get() throws Exception {
			if (mName == null || mSupplier == null || mFuture == null) {
				Logger log = WorldManagementPlugin.getInstance().getLogger();
				log.warning("Attempted to get placeholder load world task!");
				new Exception().printStackTrace();
				return null;
			}

			return mFuture.get();
		}

		private boolean isPlaceholder() {
			return mName == null || mSupplier == null || mFuture == null;
		}
	}

	private static String[] AVAILABLE_WORLDS_CACHE = new String[0];
	private static Map<String, LoadWorldTask> LOADING_WORLDS = new ConcurrentHashMap<>();

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
	 * ensureWorldLoaded, but will use the config-specified template world if copying is needed.
	 */
	public static World ensureWorldLoaded(String worldName, boolean calledAsync, boolean copyTemplateIfNotExist) throws Exception {
		return ensureWorldLoaded(worldName, calledAsync, copyTemplateIfNotExist, WorldManagementPlugin.getTemplateWorldName());
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
	 *
	 * copyFromWorldName should be the name of the world folder to use as a template to copy if world creation is allowed and necessary to satisfy the request
	 *
	 * XXX WARNING: The locking for this function is complex - take care making even small changes to control flow
	 */
	public static World ensureWorldLoaded(String worldName, boolean calledAsync, boolean copyTemplateIfNotExist, String copyFromWorldName) throws Exception {
		WorldManagementPlugin plugin = WorldManagementPlugin.getInstance();
		if (plugin == null) {
			throw new Exception("MonumentaWorldManagement plugin is not loaded");
		}
		Logger logger = plugin.getLogger();
		logger.fine("ensureWorldLoaded enter: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);

		/* Note this may block the main thread! It's necessary though */
		int lockFail = 0;
		final int timeout = calledAsync ? 20000 : 600000; // 20s main thread, 10 minutes async
		/* ----- TRY LOCK ----- */
		LoadWorldTask task;
		while ((task = LOADING_WORLDS.putIfAbsent(worldName, LoadWorldTask.PLACEHOLDER)) != null) {
			logger.fine("ensureWorldLoaded lockfail: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName + " lockFail=" + lockFail);

			// This key already exists (failed to lock)
			if (!calledAsync) {
				/*
				 * If this is on the main thread, then there's a small possibility that an async thread is holding this lock
				 * and is waiting on a call on the main thread to complete loading the world so it can unlock.
				 *
				 * Problem is that if this is also already on the main thread, there's no way for that task to get executed
				 * (the main thread is stuck in this loop instead, by design).
				 *
				 * So - need to pull that task if it exists, cancel it (so it doesn't run twice), and then run it directly
				 */
				if (!task.isPlaceholder()) {
					logger.fine("ensureWorldLoaded lockfail runRow(): worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName + " lockFail=" + lockFail);
					task.runNow();
				}
			}

			Thread.sleep(10); // Sleep between lock checks
			lockFail += 10;

			if (lockFail > timeout) {
				// Note lock was never acquired by this point
				// Need some kind of timeout to prevent the server from crashing due to this becoming an infinite loop
				throw new Exception("Failed to lock world '" + worldName + "' after retrying for " + (timeout / 1000) + "s");
			}
		}

		logger.fine("ensureWorldLoaded locked: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName + " lockFail=" + lockFail);

		/* ----- LOCK'ed after this point ----- */

		/* Try to get existing world first */
		World newWorld = null;
		if (calledAsync) {
			// Create a new task that will return an existing world if it exists
			LoadWorldTask loadTask = new LoadWorldTask(worldName, () -> Bukkit.getWorld(worldName), new CompletableFuture<>());

			/*
			 * Schedule a task to run on the main thread to complete the future (by getting an existing world)
			 * In the event that a main thread caller is already waiting on this result above, it will complete
			 * the future, and then when the task finally runs, it won't have anything to do (which is fine).
			 *
			 * This prevents a possible deadlock when needing to run something on the main thread here but the
			 * main thread is already in this function waiting for the result (meaning no tasks can be processed)
			 */
			Bukkit.getScheduler().runTask(plugin, () -> {
				logger.fine("ensureWorldLoaded getWorld runNow(): worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);
				loadTask.runNow();
			});

			/*
			 * Update the lock to store this new task instead of the placeholder
			 * Can do this directly since the lock is already held at this point
			 */
			LoadWorldTask oldTask = LOADING_WORLDS.put(worldName, loadTask);
			if (oldTask == null) {
				logger.severe("Updated lock with new load world task but the old one was null. Definitely a bug!");
			} else if (!oldTask.isPlaceholder()) {
				logger.severe("Updated lock with new load world task but the old one was not a placeholder. Definitely a bug!");
			}

			/*
			 * Block and wait for the task to complete. This will either get completed by the scheduled task here,
			 * or another main-thread caller of this function requesting the same world
			 */
			newWorld = loadTask.get();

			/*
			 * Set the lock for this back to the placeholder task
			 */
			LOADING_WORLDS.put(worldName, LoadWorldTask.PLACEHOLDER);
		} else {
			newWorld = Bukkit.getWorld(worldName);
		}
		if (newWorld != null) {
			/* +++++ UNLOCK +++++ */
			LOADING_WORLDS.remove(worldName);
			logger.fine("ensureWorldLoaded found existing unlocked: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);
			return newWorld;
		}

		logger.fine("ensureWorldLoaded world not loaded: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);

		//TODO Check redis to make sure world isn't loaded or created elsewhere

		/* Copy world if it doesn't exist */
		File worldFolder = new File(worldName);
		if (worldFolder.isDirectory()) {
			logger.fine("ensureWorldLoaded folder exists: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);
		} else {
			/* Not allowed to create so return null */
			if (!copyTemplateIfNotExist) {
				LOADING_WORLDS.remove(worldName); /* +++++ UNLOCK +++++ */
				throw new Exception("World '" + worldName + "' does not exist and copyTemplateIfNotExist is false");
			}

			Process process = Runtime.getRuntime().exec(WorldManagementPlugin.getCopyWorldCommand() + " " + copyFromWorldName + " " + worldName);

			int exitVal = process.waitFor();
			if (exitVal != 0) {
				String msg = "Failed to copy world '" + "template" + "' to '" + worldName + "': " + exitVal;
				logger.severe(msg);
				LOADING_WORLDS.remove(worldName); /* +++++ UNLOCK +++++ */
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

			logger.fine("ensureWorldLoaded created new: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);
		}

		if (calledAsync) {
			// Create a new task that will return an existing world if it exists
			Supplier<World> supplier = () -> new WorldCreator(worldName).type(WorldType.NORMAL).generateStructures(false).environment(Environment.NORMAL).createWorld();
			LoadWorldTask createTask = new LoadWorldTask(worldName, supplier, new CompletableFuture<>());

			/*
			 * Schedule a task to run on the main thread to complete the future (by getting an existing world)
			 * In the event that a main thread caller is already waiting on this result above, it will complete
			 * the future, and then when the task finally runs, it won't have anything to do (which is fine).
			 *
			 * This prevents a possible deadlock when needing to run something on the main thread here but the
			 * main thread is already in this function waiting for the result (meaning no tasks can be processed)
			 */
			Bukkit.getScheduler().runTask(plugin, () -> {
				logger.fine("ensureWorldLoaded createWorld runNow(): worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);
				createTask.runNow();
			});

			/*
			 * Update the lock to store this new task instead of the placeholder
			 * Can do this directly since the lock is already held at this point
			 */
			LoadWorldTask oldTask = LOADING_WORLDS.put(worldName, createTask);
			if (oldTask == null) {
				logger.severe("Updated lock with new load world task but the old one was null. Definitely a bug!");
			} else if (!oldTask.isPlaceholder()) {
				logger.severe("Updated lock with new load world task but the old one was not a placeholder. Definitely a bug!");
			}

			/*
			 * Block and wait for the task to complete. This will either get completed by the scheduled task here,
			 * or another main-thread caller of this function requesting the same world
			 */
			logger.fine("ensureWorldLoaded async loadworld .get(): worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);
			newWorld = createTask.get();
		} else {
			logger.fine("ensureWorldLoaded sync loadworld: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);
			newWorld = new WorldCreator(worldName).type(WorldType.NORMAL).generateStructures(false).environment(Environment.NORMAL).createWorld();
		}
		logger.fine("ensureWorldLoaded loaded world: worldName=" + worldName + " calledAsync=" + calledAsync + " copyTemplateIfNotExist=" + copyTemplateIfNotExist + " copyFromWorldName=" + copyFromWorldName);

		LOADING_WORLDS.remove(worldName); /* +++++ UNLOCK +++++ */

		if (newWorld == null) {
			throw new Exception("Failed to create world '" + worldName + "' - world is somehow null after creating which should never happen");
		}
		return newWorld;
	}

	public static CompletableFuture<Void> unloadWorld(String worldName) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		World world = Bukkit.getWorld(worldName);
		if (world == null) {
			future.completeExceptionally(new Exception("World '" + worldName + "' is not loaded"));
			return future;
		}

		if (Bukkit.getWorlds().get(0).equals(world)) {
			future.completeExceptionally(new Exception("Can't unload main world '" + worldName + "'"));
			return future;
		}

		//TODO Mark world as unloaded in redis

		if (!world.getPlayers().isEmpty()) {
			future.completeExceptionally(new Exception("Can't unload world '" + worldName + "' because there are still players in it"));
			return future;
		}

		world.setKeepSpawnInMemory(false);
		for (Chunk chunk : world.getLoadedChunks()) {
			world.unloadChunkRequest(chunk.getX(), chunk.getZ());
		}

		new BukkitRunnable() {
			int mTicks = 0;

			@Override
			public void run() {
				mTicks += 1;

				if (world.getPlayers().size() > 0) {
					// A player showed up, cancel
					this.cancel();
					future.completeExceptionally(new Exception("Player showed up on world " + world.getName() + ", cancelling unload"));
					return;
				}

				Chunk[] chunksLeft = world.getLoadedChunks();
				if (chunksLeft.length == 0) {
					// All the chunks unloaded, try to unload the world now
					this.cancel();

					if (Bukkit.unloadWorld(world, true)) {
						// Success!
						future.complete(null);
					} else {
						// Nope
						future.completeExceptionally(new Exception("Unloading world '" + worldName + "' failed, unknown reason"));
					}
					return;
				}

				// Still more chunks to unload, keep trying to unload them
				for (Chunk chunk : chunksLeft) {
					world.unloadChunkRequest(chunk.getX(), chunk.getZ());
				}

				WorldManagementPlugin.getInstance().getLogger().fine("Unloading chunks for world " + world.getName() + ", still " + chunksLeft.length + " left to go");

				if (mTicks >= 100) {
					this.cancel();
					future.completeExceptionally(new Exception("Timed out waiting for chunks to unload for world " + world.getName()));
					return;
				}
			}
		}.runTaskTimer(WorldManagementPlugin.getInstance(), 1, 1);

		return future;
	}

	/**
	 * Deletes a world.
	 *
	 * Does most of its work on an async thread, and completes the future on the main thread when done.
	 *
	 * Checks that the world is actually a world (has a level.dat file), and will also
	 * refuse to delete the folder if it contains more than one level of subfolder.
	 *
	 * Suggest using .whenComplete((unused, ex) -> your code) to do something on the main thread when done
	 */
	public static CompletableFuture<Void> deleteWorld(String worldName) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		if (!isWorldAvailable(worldName)) {
			future.completeExceptionally(new Exception("Can't delete world '" + worldName + "' which either doesn't exist or is not a world"));
			return future;
		}

		World world = Bukkit.getWorld(worldName);
		if (world != null) {
			future.completeExceptionally(new Exception("Can't delete world '" + worldName + "' which is loaded"));
			return future;
		}

		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
			try {
				// Make sure the folder's depth is appropriate for a world, and cancel if not so it doesn't delete something else
				final int maxDepth = 2;
				final Path rootPath = Paths.get(worldName);
				final int rootPathDepth = rootPath.getNameCount();
				try (Stream<Path> stream = Files.walk(rootPath)) {
					boolean tooManyLevels = stream.anyMatch((e) -> e.getNameCount() - rootPathDepth > maxDepth);
					if (tooManyLevels) {
						throw new Exception("Can't delete world '" + worldName + "' which has folder depth > " + maxDepth);
					}
				} catch (Exception ex) {
					throw ex;
				}

				// Delete all files recursively but do **not** follow symbolic links
				Path directory = Paths.get(worldName);
				Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				   @Override
				   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					   Files.delete(file);
					   return FileVisitResult.CONTINUE;
				   }

				   @Override
				   public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					   Files.delete(dir);
					   return FileVisitResult.CONTINUE;
				   }
				});

				getAvailableWorlds(); // Update the cache

				Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
					future.complete(null);
				});
			} catch (Exception ex) {
				future.completeExceptionally(ex);
				ex.printStackTrace();
			}
		});

		return future;
	}
}
