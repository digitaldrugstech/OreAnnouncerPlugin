package com.alessiodp.oreannouncer.bukkit.scheduling;

import com.alessiodp.core.common.ADPPlugin;
import com.alessiodp.core.common.scheduling.ADPScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Folia-compatible scheduler that replaces the Bukkit sync executor
 * with Folia's GlobalRegionScheduler.
 *
 * The parent class {@link ADPScheduler} manages its own async thread pool
 * for {@code runAsync()}, which works on Folia without changes.
 * Only {@code getSyncExecutor()} needs replacement since Folia does not
 * support the legacy {@code Bukkit.getScheduler().runTask()} API.
 *
 * Uses MethodHandles (resolved once at init) to avoid compile-time
 * dependency on Folia-specific API and to allow JVM inlining.
 */
public class ADPFoliaScheduler extends ADPScheduler {
	private final Executor foliaSync;

	public ADPFoliaScheduler(ADPPlugin plugin) {
		super(plugin);
		Plugin bukkitPlugin = (Plugin) plugin.getBootstrap();

		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();

			// Bukkit.getGlobalRegionScheduler() -> GlobalRegionScheduler
			java.lang.reflect.Method getSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
			MethodHandle getScheduler = lookup.unreflect(getSchedulerMethod);
			Object globalScheduler = getScheduler.invoke();

			// Resolve run() on the public interface (return type), not the impl class.
			// Using getClass() would fail with IllegalAccessException on package-private impls.
			Class<?> schedulerInterface = getSchedulerMethod.getReturnType();
			MethodHandle runHandle = lookup.unreflect(
					schedulerInterface.getMethod("run", Plugin.class, Consumer.class));

			this.foliaSync = runnable -> {
				try {
					runHandle.invoke(globalScheduler, bukkitPlugin,
							(Consumer<Object>) task -> runnable.run());
				} catch (Throwable e) {
					plugin.getLoggerManager().logError("Failed to execute task via Folia scheduler: " + e.getMessage());
				}
			};
		} catch (Throwable e) {
			throw new RuntimeException("Failed to initialize Folia scheduler", e);
		}
	}

	@Override
	public Executor getSyncExecutor() {
		return foliaSync;
	}
}
