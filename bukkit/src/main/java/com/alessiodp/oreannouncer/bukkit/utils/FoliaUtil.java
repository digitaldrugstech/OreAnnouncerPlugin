package com.alessiodp.oreannouncer.bukkit.utils;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for detecting Folia server runtime and scheduling
 * entity-bound tasks via Paper's EntityScheduler API.
 */
public final class FoliaUtil {
	private static final boolean FOLIA;

	static {
		boolean folia;
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			folia = true;
		} catch (ClassNotFoundException e) {
			folia = false;
		}
		FOLIA = folia;
	}

	private FoliaUtil() {}

	public static boolean isFolia() {
		return FOLIA;
	}

	/**
	 * Schedules a task on the region thread that owns the given entity.
	 * Required for Folia when executing commands as a player or
	 * accessing player-specific state.
	 */
	public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
		entity.getScheduler().run(plugin, t -> task.run(), null);
	}
}
