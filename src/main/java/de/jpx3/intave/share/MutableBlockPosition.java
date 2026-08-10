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

public final class MutableBlockPosition {
	public static final int PACKED_HORIZONTAL_LENGTH = 1 + ClientMath.calculateLogBaseTwo(ClientMath.roundUpToPowerOfTwo(30000000));
	public static final int PACKED_Y_LENGTH = 64 - 2 * PACKED_HORIZONTAL_LENGTH;
	private static final long PACKED_X_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
	private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
	private static final long PACKED_Z_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
	private static final int Z_OFFSET = PACKED_Y_LENGTH;
	private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH;

	private int x;
	private int y;
	private int z;

	public MutableBlockPosition() {
		this(0, 0, 0);
	}

	public MutableBlockPosition(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public MutableBlockPosition(BlockPosition position) {
		this(position.getX(), position.getY(), position.getZ());
	}

	public int x() {
		return this.x;
	}

	public int y() {
		return this.y;
	}

	public int z() {
		return this.z;
	}

	public void setX(int x) {
		this.x = x;
	}

	public void setY(int y) {
		this.y = y;
	}

	public void setZ(int z) {
		this.z = z;
	}

	public void set(int currentX, int currentY, int currentZ) {
		this.x = currentX;
		this.y = currentY;
		this.z = currentZ;
	}

	public BlockPosition toBlockPosition() {
		return new BlockPosition(this.x, this.y, this.z);
	}

	public long asLong() {
		return asLong(this.x(), this.y(), this.z());
	}

	public static long asLong(int p_121883_, int p_121884_, int p_121885_) {
		long i = 0L;
		i |= (p_121883_ & PACKED_X_MASK) << X_OFFSET;
		i |= (p_121884_ & PACKED_Y_MASK);
		return i | (p_121885_ & PACKED_Z_MASK) << Z_OFFSET;
	}

	@Override
	public String toString() {
		return "MutableBlockPosition{" +
			"x=" + this.x +
			", y=" + this.y +
			", z=" + this.z +
			'}';
	}

	@Override
	public int hashCode() {
		int result = Integer.hashCode(this.x);
		result = 31 * result + Integer.hashCode(this.y);
		result = 31 * result + Integer.hashCode(this.z);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		MutableBlockPosition other = (MutableBlockPosition) obj;
		return this.x == other.x && this.y == other.y && this.z == other.z;
	}
}
