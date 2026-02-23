package com.alessiodp.oreannouncer.bukkit.scheduling;

import com.alessiodp.core.common.ADPPlugin;
import com.alessiodp.core.common.scheduling.ADPScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;

/**
 * Folia-compatible scheduler that replaces the Bukkit sync executor
 * with Paper's GlobalRegionScheduler.
 *
 * The parent class {@link ADPScheduler} manages its own async thread pool
 * for {@code runAsync()}, which works on Folia without changes.
 * Only {@code getSyncExecutor()} needs replacement since Folia does not
 * support the legacy {@code Bukkit.getScheduler().runTask()} API.
 */
public class ADPFoliaScheduler extends ADPScheduler {
	private final Executor foliaSync;

	public ADPFoliaScheduler(ADPPlugin plugin) {
		super(plugin);
		Plugin bukkitPlugin = (Plugin) plugin.getBootstrap();
		GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();

		this.foliaSync = runnable -> scheduler.run(bukkitPlugin, task -> runnable.run());
	}

	@Override
	public Executor getSyncExecutor() {
		return foliaSync;
	}
}
