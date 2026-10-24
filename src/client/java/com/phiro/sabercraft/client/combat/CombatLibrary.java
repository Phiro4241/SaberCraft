package com.phiro.sabercraft.client.combat;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.DodgeDirection;

/**
 * Loads the fighting styles and their moves from {@code assets/sabercraft/combat/moves.json}.
 *
 * <p>The file is a normal client resource, so a resource pack can replace it, and it is read
 * again on every resource reload (F3+T). New styles and moves need no Java code: a style lists
 * its moves by id, a move lists its keyframes. {@code tools/preview_moves.py} draws the moves of
 * the file as stick figures.
 *
 * <p>If the file is broken, the error is logged and the last working version stays active. Without
 * any styles the vanilla swing animation is used.
 */
public final class CombatLibrary implements ResourceManagerReloadListener {
	public static final CombatLibrary INSTANCE = new CombatLibrary();
	public static final Identifier FILE = SaberCraft.id("combat/moves.json");

	private Map<Identifier, CombatStyle> styles = Map.of();
	private Map<Identifier, CombatMove> moves = Map.of();

	private CombatLibrary() {
	}

	public @Nullable CombatStyle style(@Nullable Identifier id) {
		return id != null ? this.styles.get(id) : null;
	}

	public @Nullable CombatMove move(Identifier id) {
		return this.moves.get(id);
	}

	public Map<Identifier, CombatStyle> styles() {
		return Collections.unmodifiableMap(this.styles);
	}

	public Map<Identifier, CombatMove> moves() {
		return Collections.unmodifiableMap(this.moves);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Optional<Resource> resource = manager.getResource(FILE);

		if (resource.isEmpty()) {
			SaberCraft.LOGGER.error("SaberCraft combat file {} is missing, sabers use the vanilla swing", FILE);
			return;
		}

		try (Reader reader = resource.get().openAsReader()) {
			this.load(JsonParser.parseReader(reader).getAsJsonObject());
		} catch (Exception e) {
			SaberCraft.LOGGER.error("Could not load the SaberCraft combat file {}, keeping the previous moves", FILE, e);
		}
	}

	/** Parses the whole file and only then replaces the current styles. */
	public void load(JsonObject root) {
		Map<Identifier, CombatMove> newMoves = new LinkedHashMap<>();
		JsonObject moveObjects = GsonHelper.getAsJsonObject(root, "moves");

		for (Map.Entry<String, JsonElement> entry : moveObjects.entrySet()) {
			Identifier id = Identifier.parse(entry.getKey());

			try {
				newMoves.put(id, parseMove(id, entry.getValue().getAsJsonObject()));
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("Move " + id + ": " + e.getMessage(), e);
			}
		}

		Map<Identifier, CombatStyle> newStyles = new LinkedHashMap<>();
		JsonObject styleObjects = GsonHelper.getAsJsonObject(root, "styles");

		for (Map.Entry<String, JsonElement> entry : styleObjects.entrySet()) {
			Identifier id = Identifier.parse(entry.getKey());

			try {
				newStyles.put(id, parseStyle(id, entry.getValue().getAsJsonObject(), newMoves));
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("Style " + id + ": " + e.getMessage(), e);
			}
		}

		this.moves = newMoves;
		this.styles = newStyles;
		SaberCraft.LOGGER.info("Loaded {} saber combat style(s) with {} move(s)", newStyles.size(), newMoves.size());
	}

	private static CombatMove parseMove(Identifier id, JsonObject json) {
		float duration = GsonHelper.getAsFloat(json, "duration");
		float strike = GsonHelper.getAsFloat(json, "strike", duration * 0.3F);
		JsonArray firstPerson = GsonHelper.getAsJsonArray(json, "first_person", new JsonArray());
		JsonArray thirdPerson = GsonHelper.getAsJsonArray(json, "third_person", new JsonArray());
		String sound = GsonHelper.getAsString(json, "sound", "swing");

		return new CombatMove(
				id,
				duration,
				strike,
				GsonHelper.getAsFloat(json, "interrupt", Math.min(duration, strike + 2.0F)),
				GsonHelper.getAsFloat(json, "blend_in", 1.5F),
				GsonHelper.getAsFloat(json, "blend_out", 3.5F),
				CombatMove.StrikeSound.valueOf(sound.toUpperCase(java.util.Locale.ROOT)),
				GsonHelper.getAsFloat(json, "volume", 1.0F),
				GsonHelper.getAsBoolean(json, "trail", true),
				track(firstPerson, "grip", 3),
				track(firstPerson, "aim", 3),
				track(thirdPerson, "main_arm", 3),
				track(thirdPerson, "off_arm", 3),
				track(thirdPerson, "body", 2),
				track(thirdPerson, "spin", 1),
				track(thirdPerson, "item", 3),
				track(firstPerson, "camera", 3),
				track(thirdPerson, "main_leg", 3),
				track(thirdPerson, "off_leg", 3),
				track(thirdPerson, "lean", 1),
				track(thirdPerson, "root", 3),
				track(thirdPerson, "crouch", 1),
				json.has("more_strikes") ? floats(json.get("more_strikes"), json.getAsJsonArray("more_strikes").size()) : new float[0]
		);
	}

	/** Collects the keyframes that set {@code key}. */
	private static @Nullable KeyTrack track(JsonArray frames, String key, int dimensions) {
		List<Float> times = new ArrayList<>();
		List<float[]> values = new ArrayList<>();

		for (JsonElement element : frames) {
			JsonObject frame = element.getAsJsonObject();

			if (frame.has(key)) {
				times.add(GsonHelper.getAsFloat(frame, "t"));
				values.add(floats(frame.get(key), dimensions));
			}
		}

		return KeyTrack.of(times, values, dimensions);
	}

	private static float[] floats(JsonElement element, int dimensions) {
		if (element.isJsonPrimitive()) {
			return new float[] {element.getAsFloat()};
		}

		JsonArray array = element.getAsJsonArray();
		float[] values = new float[array.size()];

		for (int i = 0; i < values.length; i++) {
			values[i] = array.get(i).getAsFloat();
		}

		return values;
	}

	private static CombatStyle parseStyle(Identifier id, JsonObject json, Map<Identifier, CombatMove> moves) {
		List<CombatMove> combo = new ArrayList<>();

		for (JsonElement element : GsonHelper.getAsJsonArray(json, "combo")) {
			combo.add(requireMove(moves, element.getAsString()));
		}

		if (combo.isEmpty()) {
			throw new IllegalArgumentException("needs at least one combo move");
		}

		FirstPersonPose idleFirst = firstPersonPose(GsonHelper.getAsJsonObject(json, "idle_first_person", new JsonObject()));
		ThirdPersonPose idleThird = thirdPersonPose(GsonHelper.getAsJsonObject(json, "idle_third_person", new JsonObject()));
		Map<DodgeDirection, CombatMove> dodges = new EnumMap<>(DodgeDirection.class);
		JsonObject dodgeObject = GsonHelper.getAsJsonObject(json, "dodge", new JsonObject());

		for (DodgeDirection direction : DodgeDirection.values()) {
			String key = direction.name().toLowerCase(java.util.Locale.ROOT);

			if (dodgeObject.has(key)) {
				dodges.put(direction, requireMove(moves, GsonHelper.getAsString(dodgeObject, key)));
			}
		}

		return new CombatStyle(
				id,
				List.copyOf(combo),
				optionalMove(json, "sprint", moves),
				optionalMove(json, "jump", moves),
				optionalMove(json, "special", moves),
				GsonHelper.getAsFloat(json, "combo_window", 16.0F),
				idleFirst,
				idleThird,
				json.has("guard_first_person") ? firstPersonPose(GsonHelper.getAsJsonObject(json, "guard_first_person")) : idleFirst.copy(),
				json.has("guard_third_person") ? thirdPersonPose(GsonHelper.getAsJsonObject(json, "guard_third_person")) : idleThird.copy(),
				json.has("run_third_person") ? thirdPersonPose(GsonHelper.getAsJsonObject(json, "run_third_person")) : null,
				json.has("air_third_person") ? thirdPersonPose(GsonHelper.getAsJsonObject(json, "air_third_person")) : null,
				Map.copyOf(dodges),
				optionalMove(json, "land", moves),
				optionalMove(json, "land_heavy", moves),
				optionalMove(json, "guard_hit", moves),
				optionalMove(json, "parry", moves),
				optionalMove(json, "guard_break", moves)
		);
	}

	private static @Nullable CombatMove optionalMove(JsonObject json, String key, Map<Identifier, CombatMove> moves) {
		return json.has(key) ? requireMove(moves, GsonHelper.getAsString(json, key)) : null;
	}

	private static CombatMove requireMove(Map<Identifier, CombatMove> moves, String id) {
		CombatMove move = moves.get(Identifier.parse(id));

		if (move == null) {
			throw new IllegalArgumentException("unknown move " + id);
		}

		return move;
	}

	private static FirstPersonPose firstPersonPose(JsonObject json) {
		FirstPersonPose pose = FirstPersonPose.REST.copy();

		if (json.has("grip")) {
			pose.setGrip(floats(json.get("grip"), 3));
		}

		if (json.has("aim")) {
			pose.setAim(floats(json.get("aim"), 3));
		}

		return pose;
	}

	private static ThirdPersonPose thirdPersonPose(JsonObject json) {
		ThirdPersonPose pose = ThirdPersonPose.rest();

		if (json.has("main_arm")) {
			System.arraycopy(floats(json.get("main_arm"), 3), 0, pose.mainArm, 0, 3);
		}

		if (json.has("off_arm")) {
			System.arraycopy(floats(json.get("off_arm"), 3), 0, pose.offArm, 0, 3);
			pose.offArmWeight = 1.0F;
		}

		if (json.has("body")) {
			System.arraycopy(floats(json.get("body"), 2), 0, pose.body, 0, 2);
		}

		if (json.has("item")) {
			System.arraycopy(floats(json.get("item"), 3), 0, pose.item, 0, 3);
		}

		if (json.has("main_leg")) {
			System.arraycopy(floats(json.get("main_leg"), 3), 0, pose.mainLeg, 0, 3);
			pose.legWeight = 1.0F;
		}

		if (json.has("off_leg")) {
			System.arraycopy(floats(json.get("off_leg"), 3), 0, pose.offLeg, 0, 3);
			pose.legWeight = 1.0F;
		}

		if (json.has("lean")) {
			pose.lean = GsonHelper.getAsFloat(json, "lean");
		}

		if (json.has("root")) {
			System.arraycopy(floats(json.get("root"), 3), 0, pose.root, 0, 3);
		}

		if (json.has("crouch")) {
			pose.crouch = GsonHelper.getAsFloat(json, "crouch");
		}

		return pose;
	}
}
