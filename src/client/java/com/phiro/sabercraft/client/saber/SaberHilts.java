package com.phiro.sabercraft.client.saber;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.saber.SaberVariant;

/**
 * All hilt designs, in menu order. The ids are shared with the server in {@link SaberVariant}.
 */
public final class SaberHilts {
	private static final Map<Identifier, SaberHilt> HILTS = new LinkedHashMap<>();

	/** The flat hilt of Phase 3. Every sword without a stored hilt uses it. */
	public static final SaberHilt STANDARD = register(new SaberHilt(SaberVariant.STANDARD, SaberProfiles.DEFAULT, SaberVariant.RED, 1));
	/** Classic silver hilt with black grip ridges and a clamp. */
	public static final SaberHilt GUARDIAN = register(new SaberHilt(SaberVariant.GUARDIAN, SaberProfiles.DEFAULT, SaberVariant.BLUE, 1));
	/** Black hilt with silver ribs and red buttons, with a deeper, heavier sound and ignition. */
	public static final SaberHilt DARK_LORD = register(new SaberHilt(SaberVariant.DARK_LORD, SaberProfiles.DARK, SaberVariant.RED, 1));
	/** Black and silver hilt with a vented emitter. */
	public static final SaberHilt SENTINEL = register(new SaberHilt(SaberVariant.SENTINEL, SaberProfiles.DEFAULT, SaberVariant.GREEN, 1));
	/** Silver hilt with a flared, vented emitter, after Obi-Wan Kenobi (Phase 8). */
	public static final SaberHilt KENOBI = register(new SaberHilt(SaberVariant.KENOBI, SaberProfiles.DEFAULT, SaberVariant.BLUE, 1));
	/** Black grip and silver body with a long emitter cone, after Luke Skywalker (Phase 8). */
	public static final SaberHilt SKYWALKER = register(new SaberHilt(SaberVariant.SKYWALKER, SaberProfiles.DEFAULT, SaberVariant.GREEN, 1));
	/** Experimental long hilt with a blade at each end. */
	public static final SaberHilt DOUBLE = register(new SaberHilt(SaberVariant.DOUBLE, SaberProfiles.DOUBLE, SaberVariant.RED, 2));

	private SaberHilts() {
	}

	private static SaberHilt register(SaberHilt hilt) {
		if (!SaberVariant.HILTS.contains(hilt.id())) {
			throw new IllegalArgumentException("Hilt " + hilt.id() + " is missing in SaberVariant.HILTS");
		}

		HILTS.put(hilt.id(), hilt);
		return hilt;
	}

	/** The hilt with the given id, or the standard hilt for {@code null} and unknown ids. */
	public static SaberHilt get(@Nullable Identifier id) {
		SaberHilt hilt = id != null ? HILTS.get(id) : null;
		return hilt != null ? hilt : STANDARD;
	}

	public static Map<Identifier, SaberHilt> all() {
		return Collections.unmodifiableMap(HILTS);
	}
}
