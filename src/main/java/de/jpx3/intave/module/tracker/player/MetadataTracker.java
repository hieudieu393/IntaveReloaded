/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.EntityPose;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_METADATA;

public final class MetadataTracker extends Module {

	@PacketSubscription(
		packetsOut = {
			ENTITY_METADATA
		}
	)
	public void trackMetadata(
		User user, EntityMetadataReader reader,
		PacketEvent event
	) {
		if (!reader.targetEntityIdIsSameAs(user)) {
			return;
		}
		@Nullable BlockPosition sleepingBedPosition = sleepingBedPosition(reader);
		@Nullable Boolean newGliding = newGlidingStatus(user, reader);
		@Nullable Pose newPose = newPoseStatus(user, reader);

		user.packetTickFeedback(event, () -> {
			MovementMetadata movement = user.meta().movement();
			movement.sleepingBedPosition = sleepingBedPosition;
			if (sleepingBedPosition != null) {
				Position newPosition = positionFromBedPosition(sleepingBedPosition);
				movement.setPosition(newPosition);
				movement.setVerifiedLastPosition(newPosition, "Bed sleep");
			}
			if (newGliding != null) {
				movement.gliding = newGliding;
			}
			if (newPose != null) {
				movement.setPose(newPose);
			}
		});
	}

	private @Nullable BlockPosition sleepingBedPosition(EntityMetadataReader reader) {
		return reader.bedPosition().orElse(null);
	}

	private Position positionFromBedPosition(BlockPosition bedPosition) {
		return new Position(
			bedPosition.x() + 0.5,
			bedPosition.y() + 0.6875,
			bedPosition.z() + 0.5
		);
	}

	private Boolean newGlidingStatus(User user, EntityMetadataReader reader) {
		if (!user.meta().protocol().canUseElytra()) {
			return false;
		}
		Object elytraObject = reader.fetchRaw(0);
		if (elytraObject == null) {
			return null;
		}
		byte data = (byte) elytraObject;
		return (data & 1 << 7) != 0;
	}

	private @Nullable Pose newPoseStatus(User user, EntityMetadataReader reader) {
		if (!MinecraftVersions.VER1_14_0.atOrAbove() || !user.meta().protocol().applyModernCollider()
		) {
			return null;
		}
		Object rawPose = reader.fetchRaw(6);
		if (rawPose == null) {
			return null;
		}
		try {
			EntityPose entityPose = rawPose instanceof EntityPose
				? (EntityPose) rawPose
				: EntityPose.fromNms(rawPose);
			switch (entityPose) {
				case STANDING:
					return Pose.STANDING;
				case FALL_FLYING:
					return Pose.FALL_FLYING;
				case SLEEPING:
					return Pose.SLEEPING;
				case SWIMMING:
					return Pose.SWIMMING;
				case CROUCHING:
					return Pose.CROUCHING;
				default:
					return null;
			}
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
