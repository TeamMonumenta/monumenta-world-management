package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import com.playmonumenta.worlds.common.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldGenerator {
	private static class TemplatePregenState {
		public final String mName;
		public final int mLimit;
		public final Pattern mRegex;
		public final LinkedBlockingQueue<String> mPregenerated = new LinkedBlockingQueue<>();

		public TemplatePregenState(String name, int limit) {
			mName = name;
			mLimit = limit;
			mRegex = Pattern.compile(PREGEN_PREFIX + name + "(\\d+)");
		}

		public float fractionGenerated() {
			return (float) mPregenerated.size() / mLimit;
		}
	}

	private static @Nullable WorldGenerator INSTANCE = null;
	private static final String PREGEN_PREFIX = "pregen_";
	private static final String GENERATING_SUFFIX = ".generating";
	private static final int MAX_PREGEN_SEQUENTIAL_FAILURES = 5;
	private final ConcurrentMap<String, TemplatePregenState> mPregenStates = new ConcurrentSkipListMap<>();
	private @Nullable BukkitRunnable mPregenScheduler = null;

	private WorldGenerator() {
		INSTANCE = this;
	}

	public static WorldGenerator getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new WorldGenerator();
		}
		return INSTANCE;
	}

	public void reloadConfig() {
		Map<String, Integer> templatePregenLimits = WorldManagementPlugin.getPregeneratedInstanceLimits();
		if (templatePregenLimits.isEmpty()) {
			MMLog.info("No template pregeneration specified, shutting down world generator.");
			return;
		}

		Iterator<Map.Entry<String, Integer>> templateNameIter = templatePregenLimits.entrySet().iterator();
		while (templateNameIter.hasNext()) {
			Map.Entry<String, Integer> entry = templateNameIter.next();
			String templateName = entry.getKey();
			int pregenLimit = entry.getValue();
			char finalChar = templateName.charAt(templateName.length() - 1);
			if (finalChar >= '0' && finalChar <= '9') {
				MMLog.warning("templates may not end with a number: " + templateName);
				templateNameIter.remove();
				continue;
			}
			mPregenStates.put(templateName, new TemplatePregenState(templateName, pregenLimit));
		}
		if (mPregenStates.isEmpty()) {
			MMLog.info("No valid templates, shutting down world generator.");
			return;
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

				try {
					FileUtils.deleteRecursively(failedGeneratedInstance.toPath());
				} catch (IOException ex) {
					MMLog.severe("Failed to delete failed generating instance " + name + ": " + ex.getMessage());
				}
				continue;
			}

			for (Map.Entry<String, TemplatePregenState> entry : mPregenStates.entrySet()) {
				TemplatePregenState pregenState = entry.getValue();
				Matcher matcher = pregenState.mRegex.matcher(name);
				if (matcher.matches()) {
					MMLog.info("Detected pregenerated world " + name);
					pregenState.mPregenerated.add(name);
					break;
				}
			}
		}

		// TODO File watcher to restart generation if pregenerated world is deleted? Command to regenerate pregenerated instances?

		// Start generating instances
		schedulePregeneration();
	}

	public float progress() {
		int completed = 0;
		int limit = 0;
		for (TemplatePregenState state : mPregenStates.values()) {
			int stateLimit = state.mLimit;
			if (stateLimit <= 0) {
				continue;
			}
			int stateCompleted = state.mPregenerated.size();
			completed += Math.min(stateCompleted, stateLimit);
			limit += stateLimit;
		}
		if (limit == 0) {
			return 1.0f;
		}
		return (float) completed / (float) limit;
	}

	public int pregeneratedInstances(String templateName) {
		TemplatePregenState pregenState = mPregenStates.get(templateName);
		if (pregenState == null) {
			return 0;
		}
		return pregenState.mPregenerated.size();
	}

	public static boolean worldExists(String name) {
		File target = new File(name);
		return target.isDirectory() && new File(target, "level.dat").isFile();
	}

	public void getWorldInstance(String worldName, String templateName) throws Exception {
		MMLog.fine("Preparing world " + worldName);
		if (worldExists(worldName)) {
			MMLog.fine("World already exists: " + worldName);
			return;
		}

		TemplatePregenState pregenState = mPregenStates.get(templateName);
		if (pregenState == null) {
			throw new Exception("No such template world " + templateName);
		}

		// Try to get the next pregenerated world
		// If one is not available, throw an exception
		// Only wait a very short period of time - otherwise the watchdog may crash the server before one is available
		String pregeneratedWorldName = pregenState.mPregenerated.poll(1, TimeUnit.SECONDS);
		if (pregeneratedWorldName == null) {
			schedulePregeneration();
			throw new Exception("No pregenerated worlds are currently available");
		}

		MMLog.fine("Moving " + pregeneratedWorldName + " to " + worldName);
		File oldPath = new File(pregeneratedWorldName);
		File target = new File(worldName);
		if (!oldPath.renameTo(target)) {
			if (worldExists(pregeneratedWorldName)) {
				MMLog.warning("Failed to move " + pregeneratedWorldName + " to " + worldName);
				pregenState.mPregenerated.add(pregeneratedWorldName);
			}
			if (worldExists(worldName)) {
				MMLog.info("World " + worldName + " somehow appeared without moving a preloaded world");
			} else {
				MMLog.severe("Failed to load " + worldName + "!");
			}
		}

		schedulePregeneration();
	}

	/**
	 * Generates the next instance.
	 *
	 * Returns true if still instances that need generating, false if done for now
	 *
	 * Should only be called on an async thread, will block for a long time!
	 */
	private boolean generateWorldInstance() throws Exception {
		TemplatePregenState templateState = null;
		for (TemplatePregenState pregenState : mPregenStates.values()) {
			if (templateState == null) {
				templateState = pregenState;
				continue;
			}
			if (pregenState.fractionGenerated() < templateState.fractionGenerated()) {
				templateState = pregenState;
			}
		}
		if (templateState == null) {
			MMLog.severe("No template found!");
			throw new Exception("No template found!");
		}
		if (templateState.fractionGenerated() >= 1.0) {
			MMLog.info("Completed all pregeneration for now");
			// Indicate no more work
			return false;
		}
		TemplatePregenState pregenState = templateState;
		String templateName = pregenState.mName;

		if (!worldExists(templateName)) {
			MMLog.severe("Template world does not exist!");
			throw new Exception("Template world does not exist!");
		}

		String pregenBase = PREGEN_PREFIX + templateName;
		String pregenName = null;
		for (int pregenIndex = 0; pregenIndex < pregenState.mLimit; pregenIndex++) {
			pregenName = pregenBase + pregenIndex;
			if (!pregenState.mPregenerated.contains(pregenName)) {
				break;
			}
		}

		if (pregenName == null) {
			MMLog.warning("Pregen instance limit <= 0!");
			throw new Exception("Pregen instance limit <= 0!");
		}

		String pregeneratedWorldName = pregenName;
		MMLog.info("Starting pregeneration of " + pregeneratedWorldName
			+ " (" + (pregeneratedInstances(templateName) + 1)
			+ "/" + pregenState.mLimit
			+ ", " + (int) (100 * progress()) + "% total)");

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
		pregenState.mPregenerated.add(pregeneratedWorldName);
		MMLog.info("Finished pregenerating " + pregeneratedWorldName
			+ " (" + pregeneratedInstances(templateName)
			+ "/" + pregenState.mLimit
			+ ", " + (int) (100 * progress()) + "% total)");

		// Indicate done and probably more work to do
		return true;
	}

	/*
	 * Start generating instances if they're not already generating
	 */
	public void schedulePregeneration() {
		if (mPregenScheduler != null) {
			return;
		}

		mPregenScheduler = new BukkitRunnable() {
			int mFailures = 0;

			@Override
			public void run() {
				boolean workToDo = true;
				while (workToDo) {
					try {
						workToDo = generateWorldInstance();
					} catch (Exception ex) {
						MMLog.warning("Got exception during instance pregen: " + ex.getMessage());
						mPregenScheduler = null;

						mFailures += 1;
						if (mFailures < MAX_PREGEN_SEQUENTIAL_FAILURES) {
							// Try again - less than the failure limit
							continue;
						}

						// Hit retry limit, cancel generation
						MMLog.severe("Got " + mFailures + " back-to-back pregeneration failures; aborting pregeneration");
						this.cancel();
						return;
					}

					mFailures = 0;
				}

				MMLog.info("All pregeneration complete.");
				mPregenScheduler = null;
				this.cancel();
			}
		};
		mPregenScheduler.runTaskAsynchronously(WorldManagementPlugin.getInstance());
	}
}
