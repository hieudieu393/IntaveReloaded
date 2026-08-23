package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Speed extends PlayerCheckPart<PlacementAnalysis> {
	private static final int CHECK_LENGTH = 8;
	private static final int DIRECTION_EVAL_LENGTH = 5;
	private final List<Long> placementSpeedHistory = GarbageCollector.watch(new ArrayList<>());
	private final List<Location> placementHistory = GarbageCollector.watch(new ArrayList<>());
	private long lastPlacement;
	private long lastHardFaultClick;

	public Speed(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(packetsIn = {BLOCK_PLACE, USE_ITEM})
	public void receivePlacementPacket(ProtocolPacketEvent event) {
		if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return;
		BlockInteractionReader reader = PacketReaders.readerOf(event);
		try {
			if (reader.enumDirection() == 255) lastHardFaultClick = System.currentTimeMillis();
		} finally {
			reader.release();
		}
	}

	@BukkitEventSubscription
	public void blockPlacement(BlockPlaceEvent place) {
		Player player = place.getPlayer();
		User user = userOf(player);
		MovementMetadata movementData = user.meta().movement();
		EffectMetadata potionData = user.meta().potions();
		Block block = place.getBlockPlaced();
		Block blockAgainst = place.getBlockAgainst();

		if (blockUnderPlayer(block, player) && blockCollisions(block) < 2) {
			if (placementSpeedHistory.size() >= CHECK_LENGTH) placementSpeedHistory.remove(0);
			if (block.getY() == blockAgainst.getY()) {
				placementSpeedHistory.add(System.currentTimeMillis() - lastPlacement);
				lastPlacement = System.currentTimeMillis();
			} else {
				placementSpeedHistory.add(System.currentTimeMillis() - lastPlacement + 1000);
			}
			if (placementSpeedHistory.size() >= CHECK_LENGTH) {
				double average = placementSpeedHistory.stream().mapToDouble(value -> value).average().orElse(500);
				boolean inOneLine = isOneLine(this.placementHistory);
				boolean noSneaking = System.currentTimeMillis() - movementData.lastTimeSneaking > 8000;
				boolean recentJump = System.currentTimeMillis() - movementData.lastTimeJumped < 750;
				float yawToNextNinetyDeg = Math.abs(user.meta().movement().rotationYaw()) % 90;
				boolean ninetyDegreeAngle = yawToNextNinetyDeg < 10 || yawToNextNinetyDeg > 80;
				double minAverage;
				if (inOneLine) {
					if (recentJump) minAverage = 300;
					else if (ninetyDegreeAngle) minAverage = noSneaking ? 500 : 350;
					else minAverage = noSneaking ? 350 : 200;
				} else {
					minAverage = ninetyDegreeAngle || noSneaking ? 300 : 150;
				}
				int speedAmplifier = potionData.potionEffectSpeedAmplifier();
				minAverage /= 0.15 * speedAmplifier * speedAmplifier + 1;
				if (average < minAverage) {
					Violation violation = Violation.builderFor(PlacementAnalysis.class)
						.forPlayer(player).withDefaultThreshold()
						.withMessage(COMMON_FLAG_MESSAGE)
						.withDetails(((int) average / 50) + " t/b, limit at " + ((int) minAverage / 50) + " t/b")
						.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
						.withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
						.withVL(average > 400 ? 3 : average < 300 ? 5 : 4).build();
					ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
					if (violationContext.violationLevelAfter() > 20) parentCheck().applyPlacementAnalysisDamageCancel(user, "1");
				}
			}
		}
		if (!place.isCancelled()) {
			if (placementHistory.size() >= DIRECTION_EVAL_LENGTH) placementHistory.remove(0);
			placementHistory.add(block.getLocation());
		}
	}

	private boolean blockUnderPlayer(Block block, Player player) {
		return block.getLocation().clone().add(0, 1, 0).distance(player.getLocation()) < 1.3;
	}

	private int blockCollisions(Block block) {
		int collisions = 0;
		for (BlockFace face : BlockFace.values()) {
			if (face == BlockFace.SELF) continue;
			if (block.getRelative(face).getType().isSolid()) collisions++;
		}
		return collisions;
	}

	private boolean isOneLine(List<Location> blocks) {
		if (blocks.size() < DIRECTION_EVAL_LENGTH) return false;
		int x = blocks.get(0).getBlockX();
		int z = blocks.get(0).getBlockZ();
		boolean sameX = true, sameZ = true;
		for (Location location : blocks) {
			sameX &= location.getBlockX() == x;
			sameZ &= location.getBlockZ() == z;
		}
		return sameX || sameZ;
	}
}
