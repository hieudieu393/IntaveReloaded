package de.jpx3.intave.module.tracker.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.RawVector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays a real client recording (obsidian mod, see src/test/resources/obsidian/reach) tick by tick through
 * {@link Entity}'s packet-driven position updates and compares the resulting simulated position against the
 * client's own ground truth ("attackedEntity" = the entity under the crosshair, sampled every tick).
 * <p>
 * Comparisons only start at the first {@code ClientboundEntityPositionSyncPacket} for the tracked entity -
 * before that the server (and therefore Intave) has no idea where the entity actually is.
 * <p>
 * The test checks if the positions of a given entity from the recording matches exactly the ones Intave would 
 * simulate based on simulation of Entity.java.
 */
class EntityRecordingReachTests {
    private static final Path RECORDING = Path.of("src/test/resources/obsidian/reach/recording-20260711-004144.json.gz");
    private static final Pattern SYNC_POSITION = Pattern.compile(
        "position=\\(([-0-9.Ee]+), ([-0-9.Ee]+), ([-0-9.Ee]+)\\)"
    );

    private static final double MATCH_TOLERANCE_BLOCKS = 0.001;
    private static final double MINIMUM_MATCH_RATE = 0.99;
    private static final double REACH_MATCH_TOLERANCE_BLOCKS = 0.01;
    private static final double MAX_REACH_PROBE_BLOCKS = 6;

    @BeforeEach
    void setUp() {
        MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    }

    @Test
    void simulatedEntityPositionMatchesClientGroundTruth() throws IOException {
        List<Frame> frames = readFrames(RECORDING);
        int targetEntityId = firstAttackedEntityId(frames);

        int seedIndex = firstPositionSyncIndex(frames, targetEntityId);
        Frame seedFrame = frames.get(seedIndex);
        Position seedPosition = positionSyncOf(seedFrame, targetEntityId);
        int comparisonStartIndex = firstComparableIndex(frames, targetEntityId, seedIndex);

        Entity entity = new Entity(
            targetEntityId,
            new EntityTypeData("obsidian-recorded-entity", hitboxSizeOf(frames, targetEntityId), -1, true, 0),
            false
        );
        entity.handleEntityPositionSync(seedPosition);

        int comparisons = 0;
        int matches = 0;
        for (int i = seedIndex + 1; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            for (JsonObject packet : frame.entityPackets) {
                if (packetEntityId(packet) != targetEntityId) {
                    continue;
                }
                applyPacket(entity, packet);
            }
            entity.onUpdate();

            if (i < comparisonStartIndex) {
                continue;
            }
            if (frame.attackedEntity != null && frame.attackedEntity.entityId == targetEntityId) {
                comparisons++;
                if (closestSimulatedDistance(entity, frame.attackedEntity.position) <= MATCH_TOLERANCE_BLOCKS) {
                    matches++;
                }
            }
        }

        assertTrue(comparisons > 0, "recording contains no comparable attacked-entity samples after the seed frame");
        double matchRate = (double) matches / comparisons;
        System.out.printf(
            "simulated entity position matched the client's ground truth in %d/%d ticks (%.2f%%), tolerance=%.3f blocks, floor=%.2f%%%n",
            matches, comparisons, matchRate * 100, MATCH_TOLERANCE_BLOCKS, MINIMUM_MATCH_RATE * 100
        );
        assertTrue(
            matchRate >= MINIMUM_MATCH_RATE,
            "simulated entity position only matched the client's ground truth in " + matches + "/" + comparisons +
                " ticks (" + matchRate + "), expected at least " + MINIMUM_MATCH_RATE
        );
    }

    @Test
    void simulatedReachMatchesClientRecordedReach() throws IOException {
        List<Frame> frames = readFrames(RECORDING);
        int targetEntityId = firstAttackedEntityId(frames);

        int seedIndex = firstPositionSyncIndex(frames, targetEntityId);
        Frame seedFrame = frames.get(seedIndex);
        Position seedPosition = positionSyncOf(seedFrame, targetEntityId);
        int comparisonStartIndex = firstComparableIndex(frames, targetEntityId, seedIndex);

        Entity entity = new Entity(
            targetEntityId,
            new EntityTypeData("obsidian-recorded-entity", hitboxSizeOf(frames, targetEntityId), -1, true, 0),
            false
        );
        entity.handleEntityPositionSync(seedPosition);

        int comparisons = 0;
        int matches = 0;
        for (int i = seedIndex + 1; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            for (JsonObject packet : frame.entityPackets) {
                if (packetEntityId(packet) != targetEntityId) {
                    continue;
                }
                applyPacket(entity, packet);
            }
            entity.onUpdate();

            if (i < comparisonStartIndex) {
                continue;
            }
            if (
                frame.attackReachDistance != null && frame.player != null
                    && frame.attackedEntity != null && frame.attackedEntity.entityId == targetEntityId
            ) {
                comparisons++;
                if (closestReachDeviation(entity, frame.player, frame.attackReachDistance) <= REACH_MATCH_TOLERANCE_BLOCKS) {
                    matches++;
                }
            }
        }

        assertTrue(comparisons > 0, "recording contains no comparable attack-reach samples after the seed frame");
        double matchRate = (double) matches / comparisons;
        System.out.printf(
            "simulated reach matched the client's recorded reach in %d/%d attacks (%.2f%%), tolerance=%.3f blocks, floor=%.2f%%%n",
            matches, comparisons, matchRate * 100, REACH_MATCH_TOLERANCE_BLOCKS, MINIMUM_MATCH_RATE * 100
        );
        assertTrue(
            matchRate >= MINIMUM_MATCH_RATE,
            "simulated reach only matched the client's recorded reach in " + matches + "/" + comparisons +
                " attacks (" + matchRate + "), expected at least " + MINIMUM_MATCH_RATE
        );
    }

    /**
     * Intave doesn't know exactly which tick a given attack packet was sent on relative to the entity's movement
     * packets, so (like {@code AttackRaytrace}'s own iterative reach search) it has to accept a hit if any recent
     * candidate position of the entity places it within reach. Mirrors {@link #closestSimulatedDistance} by scanning
     * the entity's current position plus its whole {@code positionHistory} and keeping the smallest deviation from
     * the client's recorded reach.
     */
    private static double closestReachDeviation(Entity entity, PlayerState player, double recordedReach) {
        double closest = Math.abs(simulatedReachFor(entity.boundingBox(), player) - recordedReach);
        for (int i = 0; i < entity.positionHistory.size(); i++) {
            BoundingBox candidateHitbox = Entity.entityBoundingBoxFrom(entity.positionHistory.back(i), entity);
            double deviation = Math.abs(simulatedReachFor(candidateHitbox, player) - recordedReach);
            closest = Math.min(closest, deviation);
        }
        return closest;
    }

    /**
     * Mirrors the core hit-test of {@link de.jpx3.intave.world.raytrace.Raytracing#entityRaytrace} (eye position,
     * look vector, box intercept) without the block-occlusion and pose-uncertainty handling that requires a live
     * {@code Player}/world - the recording only ever samples the entity that's actually under the crosshair.
     */
    private static double simulatedReachFor(BoundingBox hitbox, PlayerState player) {
        RawVector3d eye = new RawVector3d(player.x, player.y + player.eyeHeight, player.z);
        if (hitbox.isVecInside(eye)) {
            return 0;
        }
        RawVector3d look = lookVectorFor(player.pitch, player.yaw);
        RawVector3d target = eye.addVector(
            look.x() * MAX_REACH_PROBE_BLOCKS, look.y() * MAX_REACH_PROBE_BLOCKS, look.z() * MAX_REACH_PROBE_BLOCKS
        );
        MovingObjectPosition hit = hitbox.calculateIntercept(eye, target);
        return hit == null ? Double.MAX_VALUE : eye.distanceTo(hit.hitVec);
    }

    private static RawVector3d lookVectorFor(float pitch, float yaw) {
        float f = SinusCache.cos(-yaw * 0.017453292f - 3.1415927f, false);
        float f2 = SinusCache.sin(-yaw * 0.017453292f - 3.1415927f, false);
        float f3 = -SinusCache.cos(-pitch * 0.017453292f, false);
        float f4 = SinusCache.sin(-pitch * 0.017453292f, false);
        return new RawVector3d(f2 * f3, f4, f * f3);
    }

    private static void applyPacket(Entity entity, JsonObject packet) {
        switch (packet.get("packetType").getAsString()) {
            case "Pos":
            case "PosRot":
                entity.applyRelativeMove(
                    packet.get("xa").getAsLong(),
                    packet.get("ya").getAsLong(),
                    packet.get("za").getAsLong(),
                    4096d
                );
                break;
            case "Rot":
                // Look-only packets carry a zero position delta, but production still routes them through
                // handleEntityMovement (see EntityTracker#receiveEntityMovement), which resets the lerp counter.
                entity.applyRelativeMove(0, 0, 0, 4096d);
                break;
            case "ClientboundEntityPositionSyncPacket":
                entity.handleEntityPositionSync(parseSyncPosition(packet.get("values").getAsString()));
                break;
            default:
                // Velocity and metadata packets don't affect the entity's tracked position.
                break;
        }
    }

    private static double closestSimulatedDistance(Entity entity, Position clientPosition) {
        double closest = entity.distanceTo(new org.bukkit.util.Vector(
            clientPosition.getX(), clientPosition.getY(), clientPosition.getZ()
        ));
        for (int i = 0; i < entity.positionHistory.size(); i++) {
            closest = Math.min(closest, distance(entity.positionHistory.back(i), clientPosition));
        }
        return closest;
    }

    private static double distance(Entity.EntityPositionContext position, Position clientPosition) {
        double dx = position.posX - clientPosition.getX();
        double dy = position.posY - clientPosition.getY();
        double dz = position.posZ - clientPosition.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int firstAttackedEntityId(List<Frame> frames) {
        for (Frame frame : frames) {
            if (frame.attackedEntity != null) {
                return frame.attackedEntity.entityId;
            }
        }
        throw new IllegalStateException("recording contains no attacked-entity samples");
    }

    private static int firstPositionSyncIndex(List<Frame> frames, int targetEntityId) {
        for (int i = 0; i < frames.size(); i++) {
            for (JsonObject packet : frames.get(i).entityPackets) {
                if ("ClientboundEntityPositionSyncPacket".equals(packet.get("packetType").getAsString())
                    && packetEntityId(packet) == targetEntityId) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("recording never syncs the position of entity " + targetEntityId);
    }

    /**
     * Intave has no idea where an entity is before its position has been established by a real server packet,
     * and reach comparisons are meaningless before the first attack - so comparisons only start once both have
     * happened. Ticks are still simulated from {@code seedIndex} onward regardless, to keep the entity's tracked
     * state correct once comparisons begin.
     */
    private static int firstComparableIndex(List<Frame> frames, int targetEntityId, int seedIndex) {
        return Math.max(seedIndex, firstAttackIndex(frames, targetEntityId));
    }

    private static int firstAttackIndex(List<Frame> frames, int targetEntityId) {
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            if (
                frame.attackReachDistance != null && frame.attackedEntity != null
                    && frame.attackedEntity.entityId == targetEntityId
            ) {
                return i;
            }
        }
        throw new IllegalStateException("recording never attacks entity " + targetEntityId);
    }

    private static Position positionSyncOf(Frame frame, int targetEntityId) {
        for (JsonObject packet : frame.entityPackets) {
            if ("ClientboundEntityPositionSyncPacket".equals(packet.get("packetType").getAsString())
                && packetEntityId(packet) == targetEntityId) {
                return parseSyncPosition(packet.get("values").getAsString());
            }
        }
        throw new IllegalStateException("frame does not contain the expected position sync packet");
    }

    private static HitboxSize hitboxSizeOf(List<Frame> frames, int targetEntityId) {
        for (Frame frame : frames) {
            if (frame.attackedEntity != null && frame.attackedEntity.entityId == targetEntityId) {
                return HitboxSize.of(frame.attackedEntity.width, frame.attackedEntity.height);
            }
        }
        return HitboxSize.playerDefault();
    }

    private static Position parseSyncPosition(String values) {
        Matcher matcher = SYNC_POSITION.matcher(values);
        if (!matcher.find()) {
            throw new IllegalArgumentException("unable to parse position from: " + values);
        }
        return new Position(
            Double.parseDouble(matcher.group(1)),
            Double.parseDouble(matcher.group(2)),
            Double.parseDouble(matcher.group(3))
        );
    }

    private static int packetEntityId(JsonObject packet) {
        if (packet.has("id")) {
            return packet.get("id").getAsInt();
        }
        if (packet.has("entityId")) {
            return packet.get("entityId").getAsInt();
        }
        // e.g. ClientboundRemoveEntitiesPacket, which carries a batch of ids rather than a single one.
        return -1;
    }

    private static List<Frame> readFrames(Path path) throws IOException {
        List<Frame> frames = new ArrayList<>();
        try (
            InputStream gzip = new GZIPInputStream(Files.newInputStream(path));
            Reader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)
        ) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonObject frameObject = array.get(i).getAsJsonObject();
                frames.add(Frame.from(frameObject));
            }
        }
        return frames;
    }

    private static final class Frame {
        final List<JsonObject> entityPackets;
        final AttackedEntity attackedEntity;
        final PlayerState player;
        final Double attackReachDistance;

        private Frame(
            List<JsonObject> entityPackets, AttackedEntity attackedEntity,
            PlayerState player, Double attackReachDistance
        ) {
            this.entityPackets = entityPackets;
            this.attackedEntity = attackedEntity;
            this.player = player;
            this.attackReachDistance = attackReachDistance;
        }

        static Frame from(JsonObject frameObject) {
            List<JsonObject> packets = new ArrayList<>();
            for (var element : frameObject.getAsJsonArray("entityPackets")) {
                packets.add(element.getAsJsonObject());
            }
            AttackedEntity attackedEntity = null;
            if (frameObject.has("attackedEntity")) {
                JsonObject raw = frameObject.getAsJsonObject("attackedEntity");
                attackedEntity = new AttackedEntity(
                    raw.get("entityId").getAsInt(),
                    new Position(raw.get("x").getAsDouble(), raw.get("y").getAsDouble(), raw.get("z").getAsDouble()),
                    raw.get("width").getAsFloat(),
                    raw.get("height").getAsFloat()
                );
            }
            PlayerState player = null;
            if (frameObject.has("player")) {
                JsonObject raw = frameObject.getAsJsonObject("player");
                player = new PlayerState(
                    raw.get("x").getAsDouble(), raw.get("y").getAsDouble(), raw.get("z").getAsDouble(),
                    raw.get("yaw").getAsFloat(), raw.get("pitch").getAsFloat(), raw.get("eyeHeight").getAsDouble()
                );
            }
            Double attackReachDistance = frameObject.has("attackReachDistance")
                ? frameObject.get("attackReachDistance").getAsDouble() : null;
            return new Frame(packets, attackedEntity, player, attackReachDistance);
        }
    }

    private static final class PlayerState {
        final double x, y, z;
        final float yaw, pitch;
        final double eyeHeight;

        PlayerState(double x, double y, double z, float yaw, float pitch, double eyeHeight) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.eyeHeight = eyeHeight;
        }
    }

    private static final class AttackedEntity {
        final int entityId;
        final Position position;
        final float width, height;

        AttackedEntity(int entityId, Position position, float width, float height) {
            this.entityId = entityId;
            this.position = position;
            this.width = width;
            this.height = height;
        }
    }
}