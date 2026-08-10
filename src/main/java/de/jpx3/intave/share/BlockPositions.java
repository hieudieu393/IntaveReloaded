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


import de.jpx3.intave.annotate.Nullable;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public interface BlockPositions extends Iterable<MutableBlockPosition> {
	@Override
	@NotNull Iterator<MutableBlockPosition> iterator();

	default Iterable<BlockPosition> immutable() {
		return () -> new Iterator<BlockPosition>() {
			final Iterator<MutableBlockPosition> cursorIterator = iterator();

			@Override
			public boolean hasNext() {
				return cursorIterator.hasNext();
			}

			@Override
			public @Nullable BlockPosition next() {
				MutableBlockPosition cursor = cursorIterator.next();
				if (cursor == null) return null;
				return cursor.toBlockPosition();
			}
		};
	}

	default @NotNull BlockPositions and(BlockPositions other) {
		return () -> new Iterator<MutableBlockPosition>() {
			final Iterator<MutableBlockPosition> thisIterator = iterator();
			final Iterator<MutableBlockPosition> otherIterator = other.iterator();

			@Override
			public boolean hasNext() {
				return thisIterator.hasNext() || otherIterator.hasNext();
			}

			@Override
			public @Nullable MutableBlockPosition next() {
				if (thisIterator.hasNext()) {
					return thisIterator.next();
				}
				if (otherIterator.hasNext()) {
					return otherIterator.next();
				}
				return null;
			}
		};
	}

	default @NotNull BlockPositions distinct() {
		return () -> new Iterator<MutableBlockPosition>() {
			private final LongSet visitedBlocks = new LongOpenHashSet();
			private final Iterator<MutableBlockPosition> thisIterator = iterator();

			@Override
			public boolean hasNext() {
				while (thisIterator.hasNext()) {
					MutableBlockPosition cursor = thisIterator.next();
					if (visitedBlocks.add(cursor.asLong())) {
						return true;
					}
				}
				return false;
			}

			@Override
			public MutableBlockPosition next() {
				while (thisIterator.hasNext()) {
					MutableBlockPosition cursor = thisIterator.next();
					if (visitedBlocks.add(cursor.asLong())) {
						return cursor;
					}
				}
				return null;
			}
		};
	}
}