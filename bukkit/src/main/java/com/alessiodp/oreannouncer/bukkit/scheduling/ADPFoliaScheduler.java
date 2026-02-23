package com.alessiodp.oreannouncer.bukkit.scheduling;

import com.alessiodp.core.common.ADPPlugin;
import com.alessiodp.core.common.scheduling.ADPScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Folia-compatible scheduler that replaces the Bukkit sync executor
 * with Folia's GlobalRegionScheduler via reflection.
 *
 * The parent class {@link ADPScheduler} manages its own async thread pool
 * for {@code runAsync()}, which works on Folia without changes.
 * Only {@code getSyncExecutor()} needs replacement since Folia does not
 * support the legacy {@code Bukkit.getScheduler().runTask()} API.
 *
 * Uses reflection to avoid compile-time dependency on Folia-specific API,
 * allowing the plugin to compile against standard Spigot API.
 */
public class ADPFoliaScheduler extends ADPScheduler {
	private final Executor foliaSync;

	public ADPFoliaScheduler(ADPPlugin plugin) {
		super(plugin);
		Plugin bukkitPlugin = (Plugin) plugin.getBootstrap();

		try {
			// Bukkit.getGlobalRegionScheduler() -> GlobalRegionScheduler
			Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
			Object globalScheduler = getGlobalRegionScheduler.invoke(null);

			// GlobalRegionScheduler.run(Plugin, Consumer<ScheduledTask>) -> void
			Method runMethod = globalScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);

			this.foliaSync = runnable ->
					invokeFoliaRun(runMethod, globalScheduler, bukkitPlugin, runnable);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to initialize Folia scheduler", e);
		}
	}

	@Override
	public Executor getSyncExecutor() {
		return foliaSync;
	}

	@SuppressWarnings("unchecked")
	private static void invokeFoliaRun(Method runMethod, Object scheduler, Plugin plugin, Runnable runnable) {
		try {
			runMethod.invoke(scheduler, plugin, (Consumer<Object>) task -> runnable.run());
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to execute task via Folia scheduler", e);
		}
	}
}
