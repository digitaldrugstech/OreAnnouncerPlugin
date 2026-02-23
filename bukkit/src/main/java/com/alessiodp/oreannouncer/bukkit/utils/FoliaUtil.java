package com.alessiodp.oreannouncer.bukkit.utils;

/**
 * Utility class for detecting and interacting with Folia server runtime.
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
}
