package com.phiro.sabercraft.blaster;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import com.phiro.sabercraft.SaberCraft;

/**
 * A kind of blaster (Phase 10). A bow becomes a blaster with {@code /sabercraft blaster <type>};
 * the type is stored in the bow's vanilla {@code minecraft:custom_model_data} as {@code strings[0]},
 * like the hilt of a saber, so every client sees it and the item model ({@code items/bow.json})
 * picks the blaster model. A bow without a type stays a normal bow.
 *
 * <p>Each type differs in damage, bolt speed, fire rate, heat per shot and spread, so none is best
 * everywhere: the DL-44 hits hardest but fires slowly, the repeater fires two bolts at once but
 * overheats fast and slows you down, the A280 is the most precise and fastest at range, Ezra's
 * blaster stuns instead of killing quickly. New types only need an entry in {@link #ALL}, a model
 * (tools/gen_blasters.py), sounds and a name.
 *
 * @param id          the type, stored on the bow
 * @param damage      damage of one bolt (before Power)
 * @param speed       blocks per tick the bolt flies
 * @param interval    ticks between shots (the vanilla use key repeats every 4 ticks at most)
 * @param heat        heat per shot; at 100 the blaster overheats and has to cool down
 * @param spread      inaccuracy, as vanilla's projectile uncertainty (bow: 1.0)
 * @param bolts       bolts per shot
 * @param color       color of the bolt, one of the blade colors
 * @param boltLength  length of the drawn bolt in blocks
 * @param stun        ticks of slowness a hit gives (0 = none)
 * @param slowsShooter whether firing slows the shooter down (heavy weapons)
 * @param rifle       a long gun (sits a little further forward in first person); all blasters are
 *                    aimed with both hands like a loaded crossbow
 */
public record BlasterType(Identifier id, float damage, float speed, int interval, float heat, float spread, int bolts, Identifier color,
		float boltLength, int stun, boolean slowsShooter, boolean rifle) {
	private static final Identifier RED = SaberCraft.id("red");
	private static final Identifier BLUE = SaberCraft.id("blue");
	private static final Identifier YELLOW = SaberCraft.id("yellow");

	/** E-11: the balanced standard blaster rifle. */
	public static final BlasterType E11 = new BlasterType(SaberCraft.id("e11"), 5.0F, 3.0F, 5, 8.0F, 1.2F, 1, RED, 1.6F, 0, false, true);
	/** DL-44: a heavy pistol, strong single shots at a slower rate. */
	public static final BlasterType DL44 = new BlasterType(SaberCraft.id("dl44"), 8.0F, 3.2F, 12, 15.0F, 0.4F, 1, RED, 2.0F, 0, false, false);
	/** A280: a precise rifle for middle distances, the fastest bolts. */
	public static final BlasterType A280 = new BlasterType(SaberCraft.id("a280"), 6.5F, 4.2F, 8, 10.0F, 0.2F, 1, RED, 2.3F, 0, false, true);
	/** Heavy repeater: two bolts per shot, overheats fast, slows you down. */
	public static final BlasterType REPEATER = new BlasterType(SaberCraft.id("repeater"), 3.0F, 2.8F, 4, 9.0F, 2.5F, 2, RED, 1.4F, 0, true, true);
	/** Rebel blaster (a DH-17 style pistol): light and quick, blue bolts. */
	public static final BlasterType REBEL = new BlasterType(SaberCraft.id("rebel"), 4.5F, 3.4F, 5, 7.0F, 0.8F, 1, BLUE, 1.6F, 0, false, false);
	/**
	 * Ezra Bridger's blaster: the stun setting of his first lightsaber's built-in blaster (Star
	 * Wars Rebels, seasons 1 and 2). Weak, but each hit slows the target down.
	 */
	public static final BlasterType EZRA = new BlasterType(SaberCraft.id("ezra"), 2.5F, 3.0F, 6, 7.0F, 0.6F, 1, YELLOW, 1.1F, 40, false, false);

	/** All types, in menu and command order. */
	public static final List<BlasterType> ALL = List.of(E11, DL44, A280, REPEATER, REBEL, EZRA);

	public static Optional<BlasterType> byId(@Nullable Identifier id) {
		return ALL.stream().filter(type -> type.id().equals(id)).findFirst();
	}

	/** The blaster type of a stack, or null if it is not a converted bow. */
	public static @Nullable BlasterType of(ItemStack stack) {
		if (!stack.is(Items.BOW)) {
			return null;
		}

		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		String value = data != null ? data.getString(0) : null;
		return value == null || value.isEmpty() ? null : byId(Identifier.tryParse(value)).orElse(null);
	}

	/** The translation key of the name, {@code sabercraft.blaster.<path>}. */
	public String translationKey() {
		return "sabercraft.blaster." + this.id.getPath();
	}
}
