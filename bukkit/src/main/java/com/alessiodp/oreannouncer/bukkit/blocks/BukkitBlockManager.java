package com.alessiodp.oreannouncer.bukkit.blocks;

import com.alessiodp.core.common.user.User;
import com.alessiodp.core.common.utils.ADPLocation;
import com.alessiodp.core.common.utils.CommonUtils;
import com.alessiodp.oreannouncer.bukkit.addons.external.DiscordSRVHandler;
import com.alessiodp.oreannouncer.bukkit.addons.external.ItemModsHandler;
import com.alessiodp.oreannouncer.bukkit.addons.external.MMOItemsHandler;
import com.alessiodp.oreannouncer.bukkit.addons.external.PlaceholderAPIHandler;
import com.alessiodp.oreannouncer.bukkit.utils.FoliaUtil;
import com.alessiodp.oreannouncer.common.OreAnnouncerPlugin;
import com.alessiodp.oreannouncer.common.blocks.BlockManager;
import com.alessiodp.oreannouncer.common.blocks.objects.BlockData;
import com.alessiodp.oreannouncer.common.blocks.objects.OABlockImpl;
import com.alessiodp.oreannouncer.common.configuration.data.ConfigMain;
import com.alessiodp.oreannouncer.common.utils.OreAnnouncerPermission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BukkitBlockManager extends BlockManager implements Listener {
	/**
	 * In-memory block mark storage using packed coordinates with bitmask values.
	 * Keyed by world name -> packed coordinate -> bitmask of MarkType ordinals.
	 * Replaces Bukkit metadata API for thread safety and Folia compatibility.
	 */
	private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> markedBlocks = new ConcurrentHashMap<>();

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
		ConcurrentHashMap<Long, Integer> worldMap = markedBlocks.get(blockLocation.getWorld());
		if (worldMap == null) {
			return false;
		}
		Integer bits = worldMap.get(packCoordinates(blockLocation));
		return bits != null && (bits & markBit(markType)) != 0;
	}

	@Override
	public boolean markBlock(ADPLocation blockLocation, OABlockImpl block, MarkType markType) {
		// No world access here — block type is already verified at the call site.
		// countNearBlocks() only recurses into matching blocks from the origin event thread.
		// Querying the world would cause cross-region thread violations on Folia
		// when an ore vein straddles a region boundary.

		// merge() is atomic per-key; prev[0] captures the pre-existing bits (0 if absent).
		int bit = markBit(markType);
		long key = packCoordinates(blockLocation);
		int[] prev = {0};

		markedBlocks
				.computeIfAbsent(blockLocation.getWorld(), w -> new ConcurrentHashMap<>())
				.merge(key, bit, (existing, newBit) -> {
					prev[0] = existing;
					return existing | newBit;
				});

		return (prev[0] & bit) == 0;
	}

	@Override
	public void unmarkBlock(ADPLocation blockLocation, MarkType markType) {
		String world = blockLocation.getWorld();
		ConcurrentHashMap<Long, Integer> worldMap = markedBlocks.get(world);
		if (worldMap == null) {
			return;
		}
		int bit = markBit(markType);
		long key = packCoordinates(blockLocation);
		worldMap.computeIfPresent(key, (k, v) -> {
			int result = v & ~bit;
			return result == 0 ? null : result;
		});
	}

	@Override
	protected void executeBlockCommands(List<String> commands, BlockData data) {
		if (!FoliaUtil.isFolia()) {
			super.executeBlockCommands(commands, data);
			return;
		}

		// Folia: schedule on the player's entity region thread
		if (!ConfigMain.EXECUTE_COMMANDS_ENABLE || commands.isEmpty() || data.getPlayer() == null) {
			return;
		}

		UUID playerUuid = data.getPlayer().getPlayerUUID();
		Player player = Bukkit.getPlayer(playerUuid);
		if (player == null) {
			return;
		}

		Plugin bukkitPlugin = (Plugin) plugin.getBootstrap();

		FoliaUtil.runOnEntity(bukkitPlugin, player, () -> {
			User user = plugin.getPlayer(playerUuid);
			if (user == null || user.hasPermission(OreAnnouncerPermission.ADMIN_BYPASS_EXECUTE_COMMANDS)) {
				return;
			}
			dispatchCommands(commands, data, user);
		});
	}

	@Override
	protected String parsePAPI(UUID playerUuid, String message) {
		return PlaceholderAPIHandler.getPlaceholders(playerUuid, message);
	}

	// --- Cleanup ---

	@EventHandler
	public void onWorldUnload(WorldUnloadEvent event) {
		markedBlocks.remove(event.getWorld().getName());
	}

	@Override
	public void cleanup() {
		markedBlocks.clear();
	}

	// --- Internals ---

	private static int markBit(MarkType type) {
		return 1 << type.ordinal();
	}

	/**
	 * Packs block coordinates into a long for fast map lookups.
	 * Layout: 26 bits X | 26 bits Z | 12 bits Y.
	 * Supports X/Z in [-33M, +33M] and Y in [-2048, +2047].
	 */
	static long packCoordinates(ADPLocation loc) {
		int x = (int) Math.floor(loc.getX());
		int y = (int) Math.floor(loc.getY());
		int z = (int) Math.floor(loc.getZ());
		return ((long) (x & 0x3FFFFFF)) | (((long) (z & 0x3FFFFFF)) << 26) | (((long) (y & 0xFFF)) << 52);
	}
}
