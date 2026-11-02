package com.phiro.sabercraft.blaster;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 13: keeps impact sounds from piling up. When many bolts hit the same spot within a moment
 * (a held trigger, a salvo, a burst), only the first plays its sound; the sparks and smoke still
 * show for every hit. Separate spots, or the same spot a little later, sound again.
 */
public final class ImpactSounds {
	/** Hits closer than this to a recent one are heard as that one, blocks. */
	static final double RADIUS = 3.0;
	/** How long a hit keeps others near it quiet, ticks. */
	static final int WINDOW = 3;
	private static final int REMEMBERED = 32;
	private static final Deque<Recent> RECENT = new ArrayDeque<>();
	private static int skipped;

	private record Recent(ServerLevel level, Vec3 at, long time, int window) {
	}

	private ImpactSounds() {
	}

	/** Whether an impact sound at {@code at} may play now; if so, it is remembered. */
	public static boolean allow(ServerLevel level, Vec3 at) {
		return allow(level, at, WINDOW, RADIUS);
	}

	/** Like {@link #allow(ServerLevel, Vec3)} with a window and radius of its own (bigger blasts). */
	public static synchronized boolean allow(ServerLevel level, Vec3 at, int window, double radius) {
		long now = level.getGameTime();
		RECENT.removeIf(recent -> now - recent.time() > recent.window() || now < recent.time());

		for (Recent recent : RECENT) {
			if (recent.level() == level && recent.at().distanceToSqr(at) < radius * radius && now - recent.time() <= window) {
				skipped++;
				return false;
			}
		}

		RECENT.addFirst(new Recent(level, at, now, window));

		while (RECENT.size() > REMEMBERED) {
			RECENT.removeLast();
		}

		return true;
	}

	/** Only for tests: how many impact sounds were left out so far. */
	public static int skipped() {
		return skipped;
	}
}
