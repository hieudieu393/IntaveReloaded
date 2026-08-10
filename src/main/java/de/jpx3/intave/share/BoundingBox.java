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

package de.jpx3.intave.share;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.block.shape.BlockRaytrace;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.link.WrapperConverter;
import de.jpx3.intave.user.User;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleSet;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;
import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.share.Direction.Axis.*;

public final class BoundingBox extends MemoryTraced implements BlockShape {
  private static final double EPSILON = 0.00001;
  private static final double TOLERANCE = 0.0000001;
  // just assuming defaults - please remove
  private static final float PLAYER_HEIGHT = 1.8f;
  private static final double HALF_WIDTH = 0.3;
  public final double minX, minY, minZ;
  public final double maxX, maxY, maxZ;
  private boolean originBox;

  public static final StreamCodec<ByteBuf, ByteBuf, BoundingBox> STREAM_CODEC = StreamCodec.of(
    (buf, box) -> {
      buf.writeDouble(box.minX);
      buf.writeDouble(box.minY);
      buf.writeDouble(box.minZ);
      buf.writeDouble(box.maxX);
      buf.writeDouble(box.maxY);
      buf.writeDouble(box.maxZ);
      buf.writeBoolean(box.originBox);
    },
    (buf) -> {
      BoundingBox boundingBox = new BoundingBox(
        buf.readDouble(), buf.readDouble(), buf.readDouble(),
        buf.readDouble(), buf.readDouble(), buf.readDouble()
      );
      if (buf.readBoolean()) {
        boundingBox.makeOriginBox();
      }
      return boundingBox;
    }
  );

  public static final StreamCodec<JsonReader, JsonWriter, BoundingBox> JSON_CODEC = object(
    doubleField("minX", box -> box.minX),
    doubleField("minY", box -> box.minY),
    doubleField("minZ", box -> box.minZ),
    doubleField("maxX", box -> box.maxX),
    doubleField("maxY", box -> box.maxY),
    doubleField("maxZ", box -> box.maxZ),
    booleanField("originBox", box -> box.originBox),
	  BoundingBox::new
  );

  public static final StreamCodec<ByteBuf, ByteBuf, List<BoundingBox>> LIST_STREAM_CODEC = ByteBufStreamCodecs.listCodecOf(STREAM_CODEC);

  public BoundingBox(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    this.minX = Math.min(x1, x2);
    this.minY = Math.min(y1, y2);
    this.minZ = Math.min(z1, z2);
    this.maxX = Math.max(x1, x2);
    this.maxY = Math.max(y1, y2);
    this.maxZ = Math.max(z1, z2);
  }

  public BoundingBox(
    double x1, double y1, double z1,
    double x2, double y2, double z2,
    boolean originBox
  ) {
    this(x1, y1, z1, x2, y2, z2);
    if (originBox) {
      makeOriginBox();
    }
  }

  public static BoundingBox fromBounds(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    return new BoundingBox(x1, y1, z1, x2, y2, z2);
  }

  public static BoundingBox originFrom(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    double d0 = Math.min(x1, x2);
    double d1 = Math.min(y1, y2);
    double d2 = Math.min(z1, z2);
    double d3 = Math.max(x1, x2);
    double d4 = Math.max(y1, y2);
    double d5 = Math.max(z1, z2);
    BoundingBox boundingBox = new BoundingBox(d0, d1, d2, d3, d4, d5);
    boundingBox.makeOriginBox();
    return boundingBox;
  }

  public static BoundingBox originFromX16(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    double fromX = Math.min(x1, x2);
    double fromY = Math.min(y1, y2);
    double fromZ = Math.min(z1, z2);
    double toX = Math.max(x1, x2);
    double toY = Math.max(y1, y2);
    double toZ = Math.max(z1, z2);
    BoundingBox boundingBox = new BoundingBox(
      fromX / 16D, fromY / 16D, fromZ / 16D,
      toX / 16D, toY / 16D, toZ / 16D
    );
    boundingBox.makeOriginBox();
    return boundingBox;
  }

  public static BoundingBox fromPosition(User user, SimulationEnvironment environment, Location location) {
    return fromPosition(user, environment, location.getX(), location.getY(), location.getZ());
  }

  public static BoundingBox fromPosition(User user, SimulationEnvironment environment, Position position) {
    return fromPosition(user, environment, position.getX(), position.getY(), position.getZ());
  }

  public static BoundingBox fromPosition(User user, SimulationEnvironment environment, BlockPosition position) {
    return fromPosition(user, environment, position.x, position.y, position.z);
  }

  public static BoundingBox fromPosition(
    User user,
    SimulationEnvironment environment,
    double positionX, double positionY, double positionZ
  ) {
    double width = environment.isInVehicle() ? environment.width() / 2.0f : environment.widthRounded();
    float height = environment.height();

    double newYMax;
    if (user.meta().protocol().roundEnvironmentNumbers()) {
      newYMax = Math.round((positionY + height) * 10000000d) / 10000000d;
    } else {
      newYMax = Math.round((positionY + height) * 10000000000d) / 10000000000d;
    }

    return new BoundingBox(
      positionX - width, positionY, positionZ - width,
      positionX + width, newYMax, positionZ + width
    );
  }

  public static BoundingBox fromNative(Object nativeBB) {
    return WrapperConverter.boundingBoxFromAABB(nativeBB);
  }

  @Deprecated
  // doomed to be inaccurate, just guesses default BB size - please remove ~richy
  public static BoundingBox fromPosition(
    double positionX, double positionY, double positionZ
  ) {
    return new BoundingBox(
      positionX - HALF_WIDTH, positionY, positionZ - HALF_WIDTH,
      positionX + HALF_WIDTH, positionY + PLAYER_HEIGHT, positionZ + HALF_WIDTH
    );
  }

  private static final BoundingBox EMPTY = new BoundingBox(0, 0, 0, 0, 0, 0);

  public static BoundingBox empty() {
    return EMPTY;
  }

  public static BoundingBox random() {
    double x1 = Math.random() * 10;
    double y1 = Math.random() * 10;
    double z1 = Math.random() * 10;
    double x2 = x1 + Math.random() * 5;
    double y2 = y1 + Math.random() * 5;
    double z2 = z1 + Math.random() * 5;
    return new BoundingBox(x1, y1, z1, x2, y2, z2);
  }

  public double min(Direction.Axis axis) {
    switch (axis) {
      case X_AXIS:
        return minX;
      case Y_AXIS:
        return minY;
      case Z_AXIS:
        return minZ;
    }
    return axis.select(this.minX, this.minY, this.minZ);
  }

  public double max(Direction.Axis axis) {
    switch (axis) {
      case X_AXIS:
        return maxX;
      case Y_AXIS:
        return maxY;
      case Z_AXIS:
        return maxZ;
    }
    return axis.select(this.maxX, this.maxY, this.maxZ);
  }

  public BoundingBox expand(Vector vec) {
    return expand(vec.getX(), vec.getY(), vec.getZ());
  }

  public BoundingBox expand(Motion motion) {
    return expand(motion.motionX(), motion.motionY(), motion.motionZ());
  }

  /**
   * Adds the coordinates to the bounding box extending it if the point lies outside the current ranges. Args: x, y, z
   */
  public BoundingBox expand(double x, double y, double z) {
    double d0 = this.minX;
    double d1 = this.minY;
    double d2 = this.minZ;
    double d3 = this.maxX;
    double d4 = this.maxY;
    double d5 = this.maxZ;

    if (x < 0.0D) {
      d0 += x;
    } else if (x > 0.0D) {
      d3 += x;
    }

    if (y < 0.0D) {
      d1 += y;
    } else if (y > 0.0D) {
      d4 += y;
    }

    if (z < 0.0D) {
      d2 += z;
    } else if (z > 0.0D) {
      d5 += z;
    }
    BoundingBox resulting = new BoundingBox(d0, d1, d2, d3, d4, d5);
    if (isOriginBox()) {
      resulting.makeOriginBox();
    }
    return resulting;
  }

  public boolean contains(double x, double y, double z) {
    return x >= this.minX && x < this.maxX && y >= this.minY && y < this.maxY && z >= this.minZ && z < this.maxZ;
  }

  public boolean contains(Position position) {
    return contains(position.getX(), position.getY(), position.getZ());
  }

  /**
   * Returns a bounding box expanded by the specified vector (if negative numbers are given it will shrink). Args: x, y,
   * z
   */
  public BoundingBox grow(double x, double y, double z) {
    double d0 = this.minX - x;
    double d1 = this.minY - y;
    double d2 = this.minZ - z;
    double d3 = this.maxX + x;
    double d4 = this.maxY + y;
    double d5 = this.maxZ + z;
    BoundingBox resulting = new BoundingBox(d0, d1, d2, d3, d4, d5);
    if (this.isOriginBox()) {
      resulting.makeOriginBox();
    }
    return resulting;
  }

  public BoundingBox grow(double value) {
    return grow(value, value, value);
  }

  public BoundingBox growHorizontally(double value) {
    return grow(value, 0, value);
  }

  public BoundingBox growVertically(double v) {
    return grow(0, v, 0);
  }

  public BoundingBox shrink(double value) {
    return grow(-value);
  }

  public BoundingBox union(BoundingBox other) {
    double d0 = Math.min(this.minX, other.minX);
    double d1 = Math.min(this.minY, other.minY);
    double d2 = Math.min(this.minZ, other.minZ);
    double d3 = Math.max(this.maxX, other.maxX);
    double d4 = Math.max(this.maxY, other.maxY);
    double d5 = Math.max(this.maxZ, other.maxZ);
    return new BoundingBox(d0, d1, d2, d3, d4, d5);
  }

  @Override
  public BoundingBox outline() {
    return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
  }

  /**
   * Offsets the current bounding box by the specified coordinates. Args: x, y, z
   */
  public BoundingBox offset(double x, double y, double z) {
    return new BoundingBox(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
  }

  public BoundingBox originOffset(double x, double y, double z) {
    BoundingBox boundingBox = new BoundingBox(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    boundingBox.makeOriginBox();
    return boundingBox;
  }

  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox other, double offset) {
    // always collide if axis is selected
    boolean collidesInXAxis = axis == X_AXIS || other.maxX > this.minX && other.minX < this.maxX;
    boolean collidesInYAxis = axis == Y_AXIS || (collidesInXAxis && other.maxY > this.minY && other.minY < this.maxY);
    boolean collidesInZAxis = axis == Z_AXIS || (collidesInYAxis && other.maxZ > this.minZ && other.minZ < this.maxZ);

    if (collidesInXAxis && collidesInYAxis && collidesInZAxis) {
      if (offset > 0.0D && other.max(axis) <= this.min(axis)) {
        double distance = this.min(axis) - other.max(axis);
        if (distance < offset) {
          offset = distance;
        }
      } else if (offset < 0.0D && other.min(axis) >= this.max(axis)) {
        double distance = this.max(axis) - other.min(axis);
        if (distance > offset) {
          offset = distance;
        }
      }
    }
    return offset;
  }

  /*
        +----+
       /    /|
      +----+ |
      |    | +
      |    |/
      +----+
   */
  public List<Position> vertices() {
    return Arrays.asList(
      new Position(minX, minY, minZ),
      new Position(minX, minY, maxZ),
      new Position(minX, maxY, minZ),
      new Position(minX, maxY, maxZ),
      new Position(maxX, minY, minZ),
      new Position(maxX, minY, maxZ),
      new Position(maxX, maxY, minZ),
      new Position(maxX, maxY, maxZ)
    );
  }

  @Override
  public BoundingBox contextualized(int posX, int posY, int posZ) {
    if (!isOriginBox()) {
      return this;
    }
    return offset(posX, posY, posZ);
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    if (isOriginBox()) {
      return this;
    }
    BoundingBox normalized = offset(-posX, -posY, -posZ);
    normalized.makeOriginBox();
    return normalized;
  }

  @Override
  public void appendUnsortedCoordsTo(Direction.Axis axis, DoubleSet appendTo) {
    appendTo.add(min(axis));
    appendTo.add(max(axis));
  }

  private List<BoundingBox> selfInListCache;

  @Override
  public List<BoundingBox> elementaryBoxes() {
    if (selfInListCache == null) {
      selfInListCache = Collections.singletonList(this);
    }
    return selfInListCache;
  }

  @Override
  public boolean isEmpty() {
    return minX == maxX || minY == maxY || minZ == maxZ;
  }

  @Override
  public boolean isCubic() {
    if (isOriginBox()) {
      return minX == 0 && minY == 0 && minZ == 0 &&
        maxX == 1 && maxY == 1 && maxZ == 1;
    } else {
      return Math.abs(maxX - minX - 1) < EPSILON &&
        Math.abs(maxY - minY - 1) < EPSILON &&
        Math.abs(maxZ - minZ - 1) < EPSILON;
    }
  }

  @Override
  public boolean strictlyInside(double positionX, double positionY, double positionZ) {
    return positionX > minX && positionX < maxX &&
      positionY > minY && positionY < maxY &&
      positionZ > minZ && positionZ < maxZ;
  }

  /**
   * Returns whether the given bounding box intersects with this one. Args: axisAlignedBB
   */
  public boolean intersectsWith(BoundingBox boundingBox) {
    return boundingBox.maxX > this.minX && boundingBox.minX < this.maxX &&
      boundingBox.maxY > this.minY && boundingBox.minY < this.maxY &&
      boundingBox.maxZ > this.minZ && boundingBox.minZ < this.maxZ;
  }

  public boolean intersectsWith(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    return maxX > this.minX && minX < this.maxX &&
      maxY > this.minY && minY < this.maxY &&
      maxZ > this.minZ && minZ < this.maxZ;
  }

  public boolean intersects(MutableBlockPosition position) {
    return intersectsWith(
      position.x(), position.y(), position.z(),
      position.x() + 1, position.y() + 1, position.z() + 1
    );
  }

  /**
   * Returns if the supplied Vec3D is completely inside the bounding box
   */
  public boolean isVecInside(RawVector3d vec) {
    return vec.x > this.minX && vec.x < this.maxX && (vec.y > this.minY && vec.y < this.maxY && vec.z > this.minZ && vec.z < this.maxZ);
  }

  public double centerX() {
    return (minX + maxX) / 2.0;
  }

  public double centerY() {
    return (minY + maxY) / 2.0;
  }

  public double centerZ() {
    return (minZ + maxZ) / 2.0;
  }

  public Position center() {
    return new Position(centerX(), centerY(), centerZ());
  }

  public double sizeX() {
    return maxX - minX;
  }

  public double sizeY() {
    return maxY - minY;
  }

  public double sizeZ() {
    return maxZ - minZ;
  }

  /**
   * Returns the average length of the edges of the bounding box.
   */
  public double averageEdgeLength() {
    double d0 = this.maxX - this.minX;
    double d1 = this.maxY - this.minY;
    double d2 = this.maxZ - this.minZ;
    return (d0 + d1 + d2) / 3.0D;
  }

  // width and height
  public String toString() {
    return String.format("size{%s,%s,%s}@mid{%s,%s,%s}", maxX - minX, maxY - minY, maxZ - minZ, minX + (maxX - minX) / 2d, minY + (maxY - minY) / 2d, minZ + (maxZ - minZ) / 2d);
  }

  /**
   * Returns a bounding box that is inset by the specified amounts
   */
  public BoundingBox contract(double x, double y, double z) {
    double d0 = this.minX + x;
    double d1 = this.minY + y;
    double d2 = this.minZ + z;
    double d3 = this.maxX - x;
    double d4 = this.maxY - y;
    double d5 = this.maxZ - z;
    return new BoundingBox(d0, d1, d2, d3, d4, d5);
  }


  public MovingObjectPosition calculateIntercept(RawVector3d vecA, RawVector3d vecB) {
    RawVector3d vec3 = vecA.getIntermediateWithXValue(vecB, this.minX);
    RawVector3d vec31 = vecA.getIntermediateWithXValue(vecB, this.maxX);
    RawVector3d vec32 = vecA.getIntermediateWithYValue(vecB, this.minY);
    RawVector3d vec33 = vecA.getIntermediateWithYValue(vecB, this.maxY);
    RawVector3d vec34 = vecA.getIntermediateWithZValue(vecB, this.minZ);
    RawVector3d vec35 = vecA.getIntermediateWithZValue(vecB, this.maxZ);
    if (!this.isVecInYZ(vec3)) {
      vec3 = null;
    }
    if (!this.isVecInYZ(vec31)) {
      vec31 = null;
    }
    if (!this.isVecInXZ(vec32)) {
      vec32 = null;
    }
    if (!this.isVecInXZ(vec33)) {
      vec33 = null;
    }
    if (!this.isVecInXY(vec34)) {
      vec34 = null;
    }
    if (!this.isVecInXY(vec35)) {
      vec35 = null;
    }
    RawVector3d vec36 = null;
    if (vec3 != null) {
      vec36 = vec3;
    }
    if (vec31 != null && (vec36 == null || vecA.squareDistanceTo(vec31) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec31;
    }
    if (vec32 != null && (vec36 == null || vecA.squareDistanceTo(vec32) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec32;
    }
    if (vec33 != null && (vec36 == null || vecA.squareDistanceTo(vec33) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec33;
    }
    if (vec34 != null && (vec36 == null || vecA.squareDistanceTo(vec34) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec34;
    }
    if (vec35 != null && (vec36 == null || vecA.squareDistanceTo(vec35) < vecA.squareDistanceTo(vec36))) {
      vec36 = vec35;
    }
    if (vec36 == null) {
      return null;
    } else {
      Direction enumfacing;
      if (vec36 == vec3) {
        enumfacing = Direction.WEST;
      } else if (vec36 == vec31) {
        enumfacing = Direction.EAST;
      } else if (vec36 == vec32) {
        enumfacing = Direction.DOWN;
      } else if (vec36 == vec33) {
        enumfacing = Direction.UP;
      } else if (vec36 == vec34) {
        enumfacing = Direction.NORTH;
      } else {
        enumfacing = Direction.SOUTH;
      }
      return new MovingObjectPosition(vec36, enumfacing);
    }
  }

  @Override
  public BlockRaytrace raytrace(Position origin, Position target) {
    int blockX = floor(origin.getX());
    int blockY = floor(origin.getY());
    int blockZ = floor(origin.getZ());
    origin = new Position(origin.getX() - blockX, origin.getY() - blockY, origin.getZ() - blockZ);
    target = new Position(target.getX() - blockX, target.getY() - blockY, target.getZ() - blockZ);

    Position xMin = raytraceX(origin, target, minX - blockX);
    Position xMax = raytraceX(origin, target, maxX - blockX);
    Position yMin = raytraceY(origin, target, minY - blockY);
    Position yMax = raytraceY(origin, target, maxY - blockY);
    Position zMin = raytraceZ(origin, target, minZ - blockZ);
    Position zMax = raytraceZ(origin, target, maxZ - blockZ);

    if (!xIntersectsWith(xMax, blockY, blockZ)) {
      xMax = null;
    }
    if (!xIntersectsWith(xMin, blockY, blockZ)) {
      xMin = null;
    }

    if (!yIntersectsWith(yMax, blockX, blockZ)) {
      yMax = null;
    }
    if (!yIntersectsWith(yMin, blockX, blockZ)) {
      yMin = null;
    }

    if (!zIntersectsWith(zMax, blockX, blockY)) {
      zMax = null;
    }
    if (!zIntersectsWith(zMin, blockX, blockY)) {
      zMin = null;
    }

    Position closest = null;
    if (xMin != null/* && (closest == null || origin.distanceSquared(xMin) < origin.distanceSquared(closest))*/) {
      closest = xMin;
    }
    if (xMax != null && (closest == null || origin.distanceSquared(xMax) < origin.distanceSquared(closest))) {
      closest = xMax;
    }

    if (yMin != null && (closest == null || origin.distanceSquared(yMin) < origin.distanceSquared(closest))) {
      closest = yMin;
    }
    if (yMax != null && (closest == null || origin.distanceSquared(yMax) < origin.distanceSquared(closest))) {
      closest = yMax;
    }

    if (zMin != null && (closest == null || origin.distanceSquared(zMin) < origin.distanceSquared(closest))) {
      closest = zMin;
    }
    if (zMax != null && (closest == null || origin.distanceSquared(zMax) < origin.distanceSquared(closest))) {
      closest = zMax;
    }

    if (closest == null) {
      return null;
    }

    Direction direction = null;

    if (closest == xMin) {
      direction = Direction.WEST;
    } else if (closest == xMax) {
      direction = Direction.EAST;
    } else if (closest == yMin) {
      direction = Direction.DOWN;
    } else if (closest == yMax) {
      direction = Direction.UP;
    } else if (closest == zMin) {
      direction = Direction.NORTH;
    } else if (closest == zMax) {
      direction = Direction.SOUTH;
    }

    return new BlockRaytrace(direction, closest.distance(origin));
  }

  private boolean xIntersectsWith(Position position, int blockY, int blockZ) {
    if (position == null) {
      return false;
    }
    return position.getY() >= minY - blockY && position.getY() <= maxY - blockY && position.getZ() >= minZ - blockZ && position.getZ() <= maxZ - blockZ;
  }

  private boolean yIntersectsWith(Position position, int blockX, int blockZ) {
    if (position == null) {
      return false;
    }
    return position.getX() >= minX - blockX && position.getX() <= maxX - blockX && position.getZ() >= minZ - blockZ && position.getZ() <= maxZ - blockZ;
  }

  private boolean zIntersectsWith(Position position, int blockX, int blockY) {
    if (position == null) {
      return false;
    }
    return position.getX() >= minX - blockX && position.getX() <= maxX - blockX && position.getY() >= minY - blockY && position.getY() <= maxY - blockY;
  }

  private Position raytraceX(Position on, Position target, double k) {
    double distanceX = target.getX() - on.getX();
    double distanceY = target.getY() - on.getY();
    double distanceZ = target.getZ() - on.getZ();
    if (distanceX * distanceX < 1.0E-7D) {
      return null;
    } else {
      double k1 = (k - on.getX()) / distanceX;
      if (k1 < 0.0D || k1 > 1.0D) {
        return null;
      } else {
        return new Position(
          on.getX() + distanceX * k1,
          on.getY() + distanceY * k1,
          on.getZ() + distanceZ * k1
        );
      }
    }
  }

  private Position raytraceY(Position on, Position target, double k) {
    double distanceX = target.getX() - on.getX();
    double distanceY = target.getY() - on.getY();
    double distanceZ = target.getZ() - on.getZ();
    if (distanceY * distanceY < 1.0E-7D) {
      return null;
    } else {
      double k1 = (k - on.getY()) / distanceY;
      if (k1 < 0.0D || k1 > 1.0D) {
        return null;
      } else {
        return new Position(
          on.getX() + distanceX * k1,
          on.getY() + distanceY * k1,
          on.getZ() + distanceZ * k1
        );
      }
    }
  }

  private Position raytraceZ(Position on, Position target, double k) {
    double distanceX = target.getX() - on.getX();
    double distanceY = target.getY() - on.getY();
    double distanceZ = target.getZ() - on.getZ();
    if (distanceZ * distanceZ < 1.0E-7D) {
      return null;
    } else {
      double scale = (k - on.getZ()) / distanceZ;
      if (scale < 0.0D || scale > 1.0D) {
        return null;
      } else {
        return new Position(
          on.getX() + distanceX * scale,
          on.getY() + distanceY * scale,
          on.getZ() + distanceZ * scale
        );
      }
    }
  }

  public Optional<Position> clip(Position from, Position to) {
    return clip(minX, minY, minZ, maxX, maxY, maxZ, from, to);
  }

  public static Optional<Position> clip(
    double minX, double minY, double minZ,
    double maxX, double maxY, double maxZ,
    Position from, Position to
  ) {
    double[] t = new double[]{1.0};
    double dX = to.getX() - from.getX();
    double dY = to.getY() - from.getY();
    double dZ = to.getZ() - from.getZ();
    Direction direction = getDirection(minX, minY, minZ, maxX, maxY, maxZ, from, t, null, dX, dY, dZ);
    if (direction == null) {
      return Optional.empty();
    } else {
      double scale = t[0];
      return Optional.of(from.add(dX * scale, dY * scale, dZ * scale));
    }
  }

  private static Direction getDirection(
    double minX, double minY, double minZ,
    double maxX, double maxY, double maxZ,
    Position from, double[] t, Direction direction, double dX, double dY, double dZ
  ) {
    if (dX > 0.0000001) {
      direction = clipPoint(t, direction, dX, dY, dZ, minX, minY, maxY, minZ, maxZ, Direction.WEST, from.getX(), from.getY(), from.getZ());
    } else if (dX < -0.0000001) {
      direction = clipPoint(t, direction, dX, dY, dZ, maxX, minY, maxY, minZ, maxZ, Direction.EAST, from.getX(), from.getY(), from.getZ());
    }

    if (dY > 0.0000001) {
      direction = clipPoint(t, direction, dY, dZ, dX, minY, minZ, maxZ, minX, maxX, Direction.DOWN, from.getY(), from.getZ(), from.getX());
    } else if (dY < -0.0000001) {
      direction = clipPoint(t, direction, dY, dZ, dX, maxY, minZ, maxZ, minX, maxX, Direction.UP, from.getY(), from.getZ(), from.getX());
    }

    if (dZ > 0.0000001) {
      direction = clipPoint(t, direction, dZ, dX, dY, minZ, minX, maxX, minY, maxY, Direction.NORTH, from.getZ(), from.getX(), from.getY());
    } else if (dZ < -0.0000001) {
      direction = clipPoint(t, direction, dZ, dX, dY, maxZ, minX, maxX, minY, maxY, Direction.SOUTH, from.getZ(), from.getX(), from.getY());
    }
    return direction;
  }

  private static Direction clipPoint(
    double[] t, Direction direction, double d0, double d1, double d2,
    double minA, double minB, double maxB, double minC, double maxC,
    Direction faceDirection, double vecAComp, double vecBComp, double vecCComp
  ) {
    double scale = (minA - vecAComp) / d0;
    double newB = vecBComp + scale * d1;
    double newC = vecCComp + scale * d2;
    if (0.0 < scale && scale < t[0] && minB - 0.0000001 < newB && newB < maxB + 0.0000001 && minC - 0.0000001 < newC && newC < maxC + 0.0000001) {
      t[0] = scale;
      return faceDirection;
    } else {
      return direction;
    }
  }

  public double nearestDistanceTo(RawVector3d fieldPoint) {
    RawVector3d rawVector3D = nearestPointTo(fieldPoint);
    return rawVector3D.distanceTo(fieldPoint);
  }

  private RawVector3d nearestPointTo(RawVector3d fieldPoint) {
    double refX = fieldPoint.x;
    double refY = fieldPoint.y;
    double refZ = fieldPoint.z;
    double pointX = refX > maxX ? maxX : Math.max(refX, minX);
    double pointY = refY > minY ? minY : Math.max(refY, minY);
    double pointZ = refZ > maxZ ? maxZ : Math.max(refZ, minZ);
    return new RawVector3d(pointX, pointY, pointZ);
  }

  public BoundingBox addJustMaxY(double expansionY) {
    return new BoundingBox(minX, minY, minZ, maxX, this.maxY + expansionY, maxZ);
  }

  public BlockPositions blockPositionsBetween() {
    return blockPositionBetween(
//      Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
//      Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ)
      minX, minY, minZ,
      maxX, maxY, maxZ
    );
  }

  public static BlockPositions blockPositionBetween(
    double myMinX, double myMinY, double myMinZ,
    double myMaxX, double myMaxY, double myMaxZ
  ) {
    int minX = floor(myMinX);
    int minY = floor(myMinY);
    int minZ = floor(myMinZ);
    int maxX = floor(myMaxX);
    int maxY = floor(myMaxY);
    int maxZ = floor(myMaxZ);

    return () -> new Iterator<MutableBlockPosition>() {
      private final MutableBlockPosition cursor = new MutableBlockPosition(0, 0, 0);
      private int xIndex;
      private int yIndex;
      private int zIndex;
      private boolean end;

      @Override
      public boolean hasNext() {
        return !end;
      }

      @Override
      public MutableBlockPosition next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        cursor.set(minX + xIndex, minY + yIndex, minZ + zIndex);
        if (zIndex < maxZ - minZ) {
          zIndex++;
        } else if (yIndex < maxY - minY) {
          yIndex++;
          zIndex = 0;
        } else if (xIndex < maxX - minX) {
          xIndex++;
          yIndex = 0;
          zIndex = 0;
        } else {
          end = true;
        }
        return cursor;
      }
    };
  }

  public BlockPositions blockPositionsBetweenDirectional(Motion motion) {
    return blockPositionBetweenDirectional(minX, minY, minZ, maxX, maxY, maxZ, motion);
  }

  public static BlockPositions blockPositionBetweenDirectional(
    double myMinX, double myMinY, double myMinZ,
    double myMaxX, double myMaxY, double myMaxZ,
    Motion motion
  ) {
    int firstX = floor(myMinX);
    int firstY = floor(myMinY);
    int firstZ = floor(myMinZ);
    int secondX = floor(myMaxX);
    int secondY = floor(myMaxY);
    int secondZ = floor(myMaxZ);
    int minX = Math.min(firstX, secondX);
    int minY = Math.min(firstY, secondY);
    int minZ = Math.min(firstZ, secondZ);
    int maxX = Math.max(firstX, secondX);
    int maxY = Math.max(firstY, secondY);
    int maxZ = Math.max(firstZ, secondZ);

    int shortSizeX = maxX - minX;
    int shortSizeY = maxY - minY;
    int shortSizeZ = maxZ - minZ;

    int dominantX = motion.motionX >= 0.0 ? minX : maxX;
    int dominantY = motion.motionY >= 0.0 ? minY : maxY;
    int dominantZ = motion.motionZ >= 0.0 ? minZ : maxZ;

    List<Direction.Axis> axisStepOrder = Direction.axisStepOrder(motion);
    Direction.Axis firstAxis = axisStepOrder.get(0);
    Direction.Axis secondAxis = axisStepOrder.get(1);
    Direction.Axis thirdAxis = axisStepOrder.get(2);

    Direction firstAxisDirection = motion.partialMotionIn(firstAxis) >= 0.0 ? firstAxis.positive() : firstAxis.negative();
    Direction secondAxisDirection = motion.partialMotionIn(secondAxis) >= 0.0 ? secondAxis.positive() : secondAxis.negative();
    Direction thirdAxisDirection = motion.partialMotionIn(thirdAxis) >= 0.0 ? thirdAxis.positive() : thirdAxis.negative();

    int sizeFirstAxis = firstAxis.select(shortSizeX, shortSizeY, shortSizeZ);
    int sizeSecondAxis = secondAxis.select(shortSizeX, shortSizeY, shortSizeZ);
    int sizeThirdAxis = thirdAxis.select(shortSizeX, shortSizeY, shortSizeZ);

    return () -> new Iterator<MutableBlockPosition>() {
      private final MutableBlockPosition cursor = new MutableBlockPosition(0, 0, 0);
      private int firstIndex;
      private int secondIndex;
      private int thirdIndex;
      private boolean end;
      private final int firstDirX = firstAxisDirection.normalX();
      private final int firstDirY = firstAxisDirection.normalY();
      private final int firstDirZ = firstAxisDirection.normalZ();
      private final int secondDirX = secondAxisDirection.normalX();
      private final int secondDirY = secondAxisDirection.normalY();
      private final int secondDirZ = secondAxisDirection.normalZ();
      private final int thirdDirX = thirdAxisDirection.normalX();
      private final int thirdDirY = thirdAxisDirection.normalY();
      private final int thirdDirZ = thirdAxisDirection.normalZ();

      @Override
      public boolean hasNext() {
        return !end;
      }

      @Override
      public MutableBlockPosition next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }

        int currentX = dominantX + firstIndex * firstDirX + secondIndex * secondDirX + thirdIndex * thirdDirX;
        int currentY = dominantY + firstIndex * firstDirY + secondIndex * secondDirY + thirdIndex * thirdDirY;
        int currentZ = dominantZ + firstIndex * firstDirZ + secondIndex * secondDirZ + thirdIndex * thirdDirZ;

        cursor.set(currentX, currentY, currentZ);

        if (thirdIndex < sizeThirdAxis) {
          thirdIndex++;
        } else if (secondIndex < sizeSecondAxis) {
          secondIndex++;
          thirdIndex = 0;
        } else if (firstIndex < sizeFirstAxis) {
          firstIndex++;
          thirdIndex = 0;
          secondIndex = 0;
        } else {
          end = true;
        }
        return cursor;
      }
    };
  }

  public boolean intersectsWithCubeAt(MutableBlockPosition position) {
    return intersectsWith(
      position.x(), position.y(), position.z(),
      position.x() + 1, position.y() + 1, position.z() + 1
    );
  }

  public static Optional<Vec3> clip(
    double minX, double minY, double minZ,
    double maxX, double maxY, double maxZ,
    Vec3 from, Vec3 to
  ) {
    Position fromPosition = Position.of(from.x, from.y, from.z);
    Position toPosition = Position.of(to.x, to.y, to.z);
    return clip(
      minX, minY, minZ, maxX, maxY, maxZ,
      fromPosition, toPosition
    ).map(position -> new Vec3(
      position.getX(), position.getY(), position.getZ()
    ));
  }

  public BoundingBox move(Motion motion) {
    return move(motion.motionX, motion.motionY, motion.motionZ);
  }

  public BoundingBox move(double x, double y, double z) {
    return new BoundingBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
  }

  /**
   * Checks if the specified vector is within the YZ dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInYZ(RawVector3d vec) {
    return vec != null && vec.y >= this.minY && vec.y <= this.maxY && vec.z >= this.minZ && vec.z <= this.maxZ;
  }

  /**
   * Checks if the specified vector is within the XZ dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInXZ(RawVector3d vec) {
    return vec != null && vec.x >= this.minX && vec.x <= this.maxX && vec.z >= this.minZ && vec.z <= this.maxZ;
  }

  /**
   * Checks if the specified vector is within the XY dimensions of the bounding box. Args: Vec3D
   */
  private boolean isVecInXY(RawVector3d vec) {
    return vec != null && vec.x >= this.minX && vec.x <= this.maxX && vec.y >= this.minY && vec.y <= this.maxY;
  }

  public String toCompactString() {
    return MathHelper.formatDouble(this.minX, 3) + ", " + MathHelper.formatDouble(this.minY, 3) + ", " + MathHelper.formatDouble(this.minZ, 3) + " -> " + MathHelper.formatDouble(this.maxX, 3) + ", " + MathHelper.formatDouble(this.maxY, 3) + ", " + MathHelper.formatDouble(this.maxZ, 3);
  }

  public boolean isAnyNaN() {
    return Double.isNaN(this.minX) || Double.isNaN(this.minY) || Double.isNaN(this.minZ) || Double.isNaN(this.maxX) || Double.isNaN(this.maxY) || Double.isNaN(this.maxZ);
  }

  public float width() {
    return (float) (maxX - minX);
  }

  public float height() {
    return (float) (maxY - minY);
  }

  public boolean isOriginBox() {
    return originBox;
  }

  public void makeOriginBox() {
    this.originBox = true;
  }

  /*

    public boolean collidedAlongVector(Vec3 p_367135_, List<AABB> p_368156_) {
        Vec3 vec3 = this.getCenter();
        Vec3 vec31 = vec3.add(p_367135_);

        for (AABB aabb : p_368156_) {
            AABB aabb1 = aabb.inflate(this.getXsize() * 0.5 - 1.0E-7, this.getYsize() * 0.5 - 1.0E-7, this.getZsize() * 0.5 - 1.0E-7);
            if (aabb1.contains(vec31) || aabb1.contains(vec3)) {
                return true;
            }

            if (aabb1.clip(vec3, vec31).isPresent()) {
                return true;
            }
        }

        return false;
    }

   */

  public boolean collidedAlongVector(Motion move, BlockShape collisionShape) {
    Position vec3 = center();
    Position vec31 = vec3.add(move);
    for (BoundingBox boundingBox : collisionShape.elementaryBoxes()) {
      BoundingBox inflatedBox = boundingBox.grow(sizeX() * 0.5 - 1.0E-7, sizeY() * 0.5 - 1.0E-7, sizeZ() * 0.5 - 1.0E-7);
      if (inflatedBox.contains(vec31) || inflatedBox.contains(vec3)) {
        return true;
      }
      if (inflatedBox.clip(vec3, vec31).isPresent()) {
        return true;
      }
    }
    return false;
  }

  public BoundingBox copy() {
    BoundingBox boundingBox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    if (originBox) {
      boundingBox.makeOriginBox();
    }
    return boundingBox;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BoundingBox that = (BoundingBox) o;
    if (Double.compare(that.minX, minX) != 0) return false;
    if (Double.compare(that.minY, minY) != 0) return false;
    if (Double.compare(that.minZ, minZ) != 0) return false;
    if (Double.compare(that.maxX, maxX) != 0) return false;
    if (Double.compare(that.maxY, maxY) != 0) return false;
    return Double.compare(that.maxZ, maxZ) == 0;
  }

  @Override
  public int hashCode() {
    int result;
	  result = Double.hashCode(minX);
	  result = 31 * result + Double.hashCode(minY);
	  result = 31 * result + Double.hashCode(minZ);
	  result = 31 * result + Double.hashCode(maxX);
	  result = 31 * result + Double.hashCode(maxY);
	  result = 31 * result + Double.hashCode(maxZ);
    return result;
  }
}
