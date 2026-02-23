package com.alessiodp.oreannouncer.bukkit.blocks;

import com.alessiodp.core.common.utils.ADPLocation;
import com.alessiodp.core.common.utils.CommonUtils;
import com.alessiodp.oreannouncer.bukkit.addons.external.DiscordSRVHandler;
import com.alessiodp.oreannouncer.bukkit.addons.external.ItemModsHandler;
import com.alessiodp.oreannouncer.bukkit.addons.external.MMOItemsHandler;
import com.alessiodp.oreannouncer.bukkit.addons.external.PlaceholderAPIHandler;
import com.alessiodp.oreannouncer.common.OreAnnouncerPlugin;
import com.alessiodp.oreannouncer.common.blocks.BlockManager;
import com.alessiodp.oreannouncer.common.blocks.objects.BlockData;
import com.alessiodp.oreannouncer.common.blocks.objects.OABlockImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BukkitBlockManager extends BlockManager {
	/**
	 * In-memory block mark storage using packed coordinates, keyed by world name.
	 * Replaces Bukkit block metadata API for thread safety and Folia compatibility.
	 */
	private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Set<MarkType>>> markedBlocks = new ConcurrentHashMap<>();

	public BukkitBlockManager(OreAnnouncerPlugin plugin) {
		super(plugin);
	}

	@Override
	public void sendGlobalAlert(BlockData data, AlertType type) {
		super.sendGlobalAlert(data, type);
		DiscordSRVHandler.dispatchAlerts(data, type);
	}

	@Override
	public boolean existsMaterial(String materialName) {
		return Material.getMaterial(CommonUtils.toUpperCase(materialName)) != null
				|| CommonUtils.toUpperCase(materialName).startsWith("ITEMMODS_")
				|| CommonUtils.toUpperCase(materialName).startsWith("MMOITEMS_");
	}

	public String getBlockType(Block block) {
		if (ItemModsHandler.isPluginBlock(block)) {
			return ItemModsHandler.getNameByBlock(block);
		} else if (MMOItemsHandler.isPluginBlock(block)) {
			return MMOItemsHandler.getNameByBlock(block);
		}
		return block.getType().name();
	}

	@Override
	public boolean isBlockMarked(ADPLocation blockLocation, MarkType markType) {
		ConcurrentHashMap<Long, Set<MarkType>> worldMap = markedBlocks.get(blockLocation.getWorld());
		if (worldMap == null) {
			return false;
		}
		Set<MarkType> marks = worldMap.get(packCoordinates(blockLocation));
		return marks != null && marks.contains(markType);
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public boolean markBlock(ADPLocation blockLocation, OABlockImpl block, MarkType markType) {
		long key = packCoordinates(blockLocation);
		String world = blockLocation.getWorld();

		// Check if already marked
		ConcurrentHashMap<Long, Set<MarkType>> worldMap = markedBlocks.get(world);
		if (worldMap != null) {
			Set<MarkType> existing = worldMap.get(key);
			if (existing != null && existing.contains(markType)) {
				return false;
			}
		}

		// Verify block type matches by querying the world
		Block bukkitBlock = getBukkitBlock(blockLocation);
		if (bukkitBlock == null) {
			return false;
		}

		String blockType = getBlockType(bukkitBlock);
		if (!block.getMaterialName().equalsIgnoreCase(blockType)
				&& !block.getVariants().contains(blockType)) {
			return false;
		}

		// Mark the block
		markedBlocks
				.computeIfAbsent(world, w -> new ConcurrentHashMap<>())
				.compute(key, (k, v) -> {
					if (v == null) {
						v = Collections.synchronizedSet(EnumSet.noneOf(MarkType.class));
					}
					v.add(markType);
					return v;
				});
		return true;
	}

	@Override
	public void unmarkBlock(ADPLocation blockLocation, MarkType markType) {
		String world = blockLocation.getWorld();
		ConcurrentHashMap<Long, Set<MarkType>> worldMap = markedBlocks.get(world);
		if (worldMap == null) {
			return;
		}
		long key = packCoordinates(blockLocation);
		worldMap.computeIfPresent(key, (k, v) -> {
			v.remove(markType);
			return v.isEmpty() ? null : v;
		});
	}

	@Override
	protected String parsePAPI(UUID playerUuid, String message) {
		return PlaceholderAPIHandler.getPlaceholders(playerUuid, message);
	}

	private Block getBukkitBlock(ADPLocation loc) {
		org.bukkit.World world = Bukkit.getWorld(loc.getWorld());
		if (world == null) {
			return null;
		}
		return new Location(world, loc.getX(), loc.getY(), loc.getZ()).getBlock();
	}

	/**
	 * Packs block coordinates into a long for fast map lookups.
	 * Layout: 26 bits X | 26 bits Z | 12 bits Y (matches Bukkit's Block key format).
	 * Supports X/Z in [-33M, +33M] and Y in [-2048, +2047].
	 */
	private static long packCoordinates(ADPLocation loc) {
		int x = (int) loc.getX();
		int y = (int) loc.getY();
		int z = (int) loc.getZ();
		return ((long) (x & 0x3FFFFFF)) | (((long) (z & 0x3FFFFFF)) << 26) | (((long) (y & 0xFFF)) << 52);
	}
}
