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

package de.jpx3.intave.check.movement.physics.recording;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

final class MovementRecordingRejectionTuningTests {
	private static final Path REJECTION_TUNING_RECORDINGS = Paths.get(
		"src", "test", "resources", "physics_test_runs", "pending"
	);

	@TestFactory
	Stream<DynamicTest> pendingMovementRecordings() throws IOException {
		List<Path> recordingPaths = MovementRecordingPhysicsTests.findMovementRecordings(REJECTION_TUNING_RECORDINGS);
		if (recordingPaths.isEmpty()) {
			return Stream.empty();
		}
		return recordingPaths.stream()
			.map(recordingPath -> dynamicTest(
				MovementRecordingPhysicsTests.resourcePathOf(recordingPath),
				() -> MovementRecordingPhysicsTests.processRecordingResource(
					MovementRecordingPhysicsTests.resourcePathOf(recordingPath),
					(tick, simulation, actualMotion) -> {
						System.out.println(simulation.offsetDifference() + " ");
					}
				)
			));
	}
}
