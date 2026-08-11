package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerActionReader;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public class SmartSpeed extends PlayerCheckPart<PlacementAnalysis> {
	private final List<Rotation> pastRotations = new LinkedList<>();
	private final List<Integer> placementSpeedHistory = new LinkedList<>();
	private final List<Long> preplacementSneakDelay = new LinkedList<>();
	private final List<Long> postplacementSneakDelay = new LinkedList<>();
	private final List<Placement> placementHistory = new LinkedList<>();
	private long lastPlacementTick;
	private int ticksSinceHardFaultClick = 100;
	private int ticksSinceBlockPlacement = 100;
	private boolean startSneakInThisTick;
	private boolean stopSneakInThisTick;
	private boolean sneakChangedInThisTick;
	private boolean placedInThisTick;
	private long lastSneakStart;
	private long lastPlace;
	private long lastSneakDuration = 10;
	private long sneakDuration;
	private double sneakVL;
	private long tickCount;

	public SmartSpeed(User user, PlacementAnalysis parent) {
		super(user, parent);
	}

	@PacketSubscription(packetsIn = {BLOCK_PLACE, USE_ITEM})
	public void receivePlacementPacket(ProtocolPacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);
		BlockInteractionReader reader = PacketReaders.readerOf(event);
		try {
			if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
				int facing = reader.enumDirection();
				if (facing == 255) {
					ticksSinceHardFaultClick = 0;
					return;
				}
				Material material = user.meta().inventory().heldItemType();
				if (!material.isBlock() || !material.isSolid()) return;

				if (placementSpeedHistory.size() > 100) placementSpeedHistory.remove(0);
				placementSpeedHistory.add(ticksSinceBlockPlacement);
				if (placementHistory.size() > 100) placementHistory.remove(0);
				BlockPosition blockPosition = reader.nativeBlockPosition();
				Direction direction = reader.direction();
				boolean near = placementHistory.stream().anyMatch(placement -> placement.position.distanceTo(blockPosition) < 1.1);
				long currentTick = tickCount;
				long lastPlacement = lastPlacementTick;
				int duration = (int) (currentTick - lastPlacement);
				float rotationSinceLastPlacement = 0;
				float highestPitch = 0;
				if (lastPlacement != -1 && pastRotations.size() > 2) {
					for (int i = pastRotations.size() - 1; i >= Math.max(1, pastRotations.size() - duration); i--) {
						Rotation rotation = pastRotations.get(i);
						rotationSinceLastPlacement += rotation.distanceTo(pastRotations.get(i - 1));
						highestPitch = Math.max(highestPitch, rotation.pitch());
					}
				}
				placementHistory.add(new Placement(blockPosition, direction, ticksSinceBlockPlacement, tickCount, near));
				if (placementSpeedHistory.size() >= 3) {
					double average = 0;
					int requiredElements = 3;
					for (int i = placementSpeedHistory.size() - 1; i >= Math.max(0, placementSpeedHistory.size() - requiredElements); i--) {
						Direction placementDirection = placementHistory.get(i) == null ? Direction.UP : placementHistory.get(i).direction();
						if (placementDirection != null && placementDirection.axis().isVertical()) {
							requiredElements++;
							continue;
						}
						average += placementSpeedHistory.get(i);
					}
					average /= 3;
				}
				ticksSinceBlockPlacement = 0;
				lastPlacementTick = tickCount;
				placedInThisTick = true;
			}
		} finally {
			reader.release();
		}
	}

	@PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {FLYING, POSITION_LOOK, LOOK, POSITION})
	public void on(ProtocolPacketEvent event) {
		User user = userOf(event.getPlayer());
		MovementMetadata movementData = user.meta().movement();
		if (pastRotations.size() > 100) pastRotations.remove(0);
		if (movementData.rotationYaw != movementData.lastRotationYaw || movementData.rotationPitch != movementData.lastRotationPitch) {
			pastRotations.add(movementData.rotation());
		} else {
			pastRotations.add(pastRotations.size() > 1 ? pastRotations.get(pastRotations.size() - 1) : Rotation.zero());
		}
		if (placedInThisTick) {
			long diff = startSneakInThisTick ? 0 : tickCount - lastSneakStart;
			if (preplacementSneakDelay.size() > 100) preplacementSneakDelay.remove(0);
			preplacementSneakDelay.add(diff);
			boolean suspiciousSneaking = diff <= 2 && lastSneakDuration <= 2;
			if (!suspiciousSneaking && sneakVL > 0) sneakVL -= 0.05;
			else if (suspiciousSneaking) sneakVL += diff > 1 ? 0.1 : 1;
		}
		if (sneakChangedInThisTick) {
			long diff = placedInThisTick ? 0 : tickCount - lastPlace;
			if (postplacementSneakDelay.size() > 100) postplacementSneakDelay.remove(0);
			postplacementSneakDelay.add(diff);
			boolean suspiciousSneaking = diff <= 2 && lastSneakDuration <= 2;
			if (!suspiciousSneaking && sneakVL > 0) sneakVL -= 0.05;
			else if (suspiciousSneaking) sneakVL += diff > 1 ? 0.1 : 1;
		}
		if (startSneakInThisTick) lastSneakStart = tickCount;
		if (placedInThisTick) lastPlace = tickCount;
		startSneakInThisTick = false;
		stopSneakInThisTick = false;
		sneakChangedInThisTick = false;
		placedInThisTick = false;
		ticksSinceHardFaultClick++;
		ticksSinceBlockPlacement++;
		tickCount++;
		sneakDuration++;
	}

	@PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = ENTITY_ACTION_IN)
	public void receiveEntityActionPacket(ProtocolPacketEvent event) {
		PlayerActionReader reader = PacketReaders.readerOf(event);
		try {
			PlayerAction action = reader.playerAction();
			if (action.isStartSneak()) {
				startSneakInThisTick = true;
				sneakChangedInThisTick = true;
				sneakDuration = 0;
			} else if (action.isStopSneak()) {
				stopSneakInThisTick = true;
				sneakChangedInThisTick = true;
				lastSneakDuration = sneakDuration;
				sneakDuration = 0;
			}
		} finally {
			reader.release();
		}
	}

	private static class Placement {
		private final BlockPosition position;
		private final Direction direction;
		private final int ticksSinceLast;
		private final long tickCount;
		private final boolean connected;
		private boolean wasSneakingSinceLast;
		public Placement(BlockPosition position, Direction direction, int ticksSinceLast, long tickCount, boolean connected) {
			this.position = position;
			this.direction = direction;
			this.ticksSinceLast = ticksSinceLast;
			this.connected = connected;
			this.tickCount = tickCount;
		}
		public BlockPosition position() { return position; }
		public Direction direction() { return direction; }
		public int ticksSince() { return ticksSinceLast; }
		public boolean wasSneakingSince() { return wasSneakingSinceLast; }
		public void registerSneak() { wasSneakingSinceLast = true; }
		public long tickCountAt() { return tickCount; }
	}

	private boolean blockAgainstWasPlaced(User user, Block blockAgainst) {
		Vector vector = blockAgainst.getLocation().toVector();
		for (Placement placement : placementHistory) {
			if (placement.position().distanceTo(vector) == 0) return true;
		}
		return false;
	}
}
