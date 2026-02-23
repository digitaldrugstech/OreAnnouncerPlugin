package com.alessiodp.oreannouncer.bukkit.utils;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Utility class for detecting and interacting with Folia server runtime.
 * MethodHandles are resolved once at class load for zero-overhead invocation.
 */
public final class FoliaUtil {
	private static final boolean FOLIA;
	private static final MethodHandle ENTITY_GET_SCHEDULER;
	private static final MethodHandle ENTITY_SCHEDULER_RUN;

	static {
		boolean folia;
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			folia = true;
		} catch (ClassNotFoundException e) {
			folia = false;
		}
		FOLIA = folia;

		if (FOLIA) {
			try {
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				Method getScheduler = Entity.class.getMethod("getScheduler");
				ENTITY_GET_SCHEDULER = lookup.unreflect(getScheduler);
				Class<?> schedulerClass = getScheduler.getReturnType();
				ENTITY_SCHEDULER_RUN = lookup.unreflect(
						schedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class));
			} catch (Throwable e) {
				throw new RuntimeException("Failed to resolve Folia entity scheduler methods", e);
			}
		} else {
			ENTITY_GET_SCHEDULER = null;
			ENTITY_SCHEDULER_RUN = null;
		}
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
		try {
			Object entityScheduler = ENTITY_GET_SCHEDULER.invoke(entity);
			ENTITY_SCHEDULER_RUN.invoke(entityScheduler, plugin,
					(Consumer<Object>) t -> task.run(), (Runnable) null);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to schedule task on entity", e);
		}
	}
}
