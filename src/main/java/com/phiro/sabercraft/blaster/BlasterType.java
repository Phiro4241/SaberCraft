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
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * A kind of blaster (Phase 10, more types and 3D models in Phase 11). A bow becomes a blaster with
 * {@code /sabercraft blaster <type>} or in the blaster menu (key V); the type is stored in the
 * bow's vanilla {@code minecraft:custom_model_data} as {@code strings[0]}, like the hilt of a saber,
 * so every client sees it and the item model ({@code items/bow.json}) picks the blaster model. A
 * bow without a type stays a normal bow.
 *
 * <p>Each type differs in damage, bolt speed, fire rate, range, heat per shot and spread, so none
 * is best everywhere: the DL-44 hits hard but fires slowly, the DC-15A reaches furthest, the
 * SE-14C is a quick but weak sidearm, the bowcaster hits hardest of all and throws targets back
 * but is the slowest, the repeater fires two bolts at once but overheats fast and slows you down,
 * Ezra's blaster stuns. Every shot costs one arrow (Phase 11, see {@link Blasters}). New types only
 * need an entry in {@link #ALL}, a model (tools/gen_blasters.py), a sound and a name.
 *
 * @param id          the type, stored on the bow
 * @param damage      damage of one bolt (before Power)
 * @param speed       blocks per tick the bolt flies
 * @param interval    ticks between shots (the vanilla use key repeats every 4 ticks at most)
 * @param heat        heat per shot; at 100 the blaster overheats and has to cool down
 * @param spread      inaccuracy, as vanilla's projectile uncertainty (bow: 1.0)
 * @param bolts       bolts per shot (one arrow per shot, however many bolts)
 * @param color       color of the bolt, one of the blade colors
 * @param boltLength  length of the drawn bolt in blocks
 * @param stun        ticks of slowness a hit gives (0 = none)
 * @param slowsShooter whether firing slows the shooter down (heavy weapons)
 * @param rifle       a long gun; all blasters are aimed with both hands like a loaded crossbow
 * @param range       blocks a bolt flies before it fades (Phase 11)
 * @param knockback   extra push of a hit, added to Punch (Phase 11)
 */
public record BlasterType(Identifier id, float damage, float speed, int interval, float heat, float spread, int bolts, Identifier color,
		float boltLength, int stun, boolean slowsShooter, boolean rifle, float range, float knockback) {
	private static final Identifier RED = SaberCraft.id("red");
	private static final Identifier BLUE = SaberCraft.id("blue");
	private static final Identifier GREEN = SaberCraft.id("green");
	private static final Identifier YELLOW = SaberCraft.id("yellow");

	/** DL-44: a heavy pistol, strong single shots at a slower rate. */
	public static final BlasterType DL44 = new BlasterType(SaberCraft.id("dl44"), 8.0F, 3.2F, 12, 15.0F, 0.4F, 1, RED, 2.0F, 0, false, false, 48.0F, 0.0F);
	/** E-11: the balanced standard blaster rifle, quick but not very precise. */
	public static final BlasterType E11 = new BlasterType(SaberCraft.id("e11"), 5.0F, 3.0F, 5, 8.0F, 1.2F, 1, RED, 1.6F, 0, false, true, 40.0F, 0.0F);
	/** DC-15A: the long rifle of the clone troopers, precise and with the longest reach. */
	public static final BlasterType DC15A = new BlasterType(SaberCraft.id("dc15a"), 6.0F, 4.0F, 7, 9.0F, 0.3F, 1, BLUE, 2.2F, 0, false, true, 72.0F, 0.0F);
	/** SE-14C: a light sidearm, the quickest to fire, weak and short-ranged. */
	public static final BlasterType SE14C = new BlasterType(SaberCraft.id("se14c"), 3.5F, 3.2F, 4, 6.0F, 0.9F, 1, RED, 1.3F, 0, false, false, 32.0F, 0.0F);
	/** Bowcaster: the heaviest single shot, slow, throws the target back. */
	public static final BlasterType BOWCASTER = new BlasterType(SaberCraft.id("bowcaster"), 11.0F, 2.6F, 20, 22.0F, 0.2F, 1, GREEN, 2.4F, 0, false, true, 56.0F, 1.0F);
	/** A280: a precise rifle for middle distances, the fastest bolts. */
	public static final BlasterType A280 = new BlasterType(SaberCraft.id("a280"), 6.5F, 4.2F, 8, 10.0F, 0.2F, 1, RED, 2.3F, 0, false, true, 64.0F, 0.0F);
	/** Heavy repeater: two bolts per shot, overheats fast, slows you down. */
	public static final BlasterType REPEATER = new BlasterType(SaberCraft.id("repeater"), 3.0F, 2.8F, 4, 9.0F, 2.5F, 2, RED, 1.4F, 0, true, true, 40.0F, 0.0F);
	/** Rebel blaster (a DH-17 style pistol): light and quick, blue bolts. */
	public static final BlasterType REBEL = new BlasterType(SaberCraft.id("rebel"), 4.5F, 3.4F, 5, 7.0F, 0.8F, 1, BLUE, 1.6F, 0, false, false, 40.0F, 0.0F);
	/**
	 * Ezra Bridger's blaster: the stun setting of his first lightsaber's built-in blaster (Star
	 * Wars Rebels, seasons 1 and 2). Weak, but each hit slows the target down.
	 */
	public static final BlasterType EZRA = new BlasterType(SaberCraft.id("ezra"), 2.5F, 3.0F, 6, 7.0F, 0.6F, 1, YELLOW, 1.1F, 40, false, false, 32.0F, 0.0F);

	/** All types, in menu and command order. */
	/**
	 * The laser cannons of the X-Wing (Phase 11): fast, long bolts. Not a blaster to hold, so not
	 * in {@link #ALL}; only {@link #byId} knows it, so bolts in flight are drawn right.
	 */
	public static final BlasterType XWING = new BlasterType(SaberCraft.id("xwing"), 8.0F, 6.0F, 5, 0.0F, 0.15F, 1, RED, 4.5F, 0, false, true, 128.0F, 0.4F);
	public static final List<BlasterType> ALL = List.of(DL44, E11, DC15A, SE14C, BOWCASTER, A280, REPEATER, REBEL, EZRA);

	public static Optional<BlasterType> byId(@Nullable Identifier id) {
		return XWING.id().equals(id) ? Optional.of(XWING) : ALL.stream().filter(type -> type.id().equals(id)).findFirst();
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

	/**
	 * The blaster a stack fires on right-click: a converted bow, or a saber on Ezra Bridger's hilt,
	 * which has his stun blaster built in (Phase 10). Null for everything else.
	 */
	public static @Nullable BlasterType firing(ItemStack stack) {
		BlasterType type = of(stack);

		if (type == null && SaberVariant.canHold(stack) && SaberVariant.EZRA.equals(SaberVariant.hiltOf(stack))) {
			return EZRA;
		}

		return type;
	}

	/** Ticks a bolt of this type lives: its range divided by its speed. */
	public int lifetime() {
		return Math.max(1, (int) Math.ceil(this.range / this.speed));
	}

	/** Shots per second at the fire rate. */
	public float shotsPerSecond() {
		return 20.0F / this.interval;
	}

	/** The translation key of the name, {@code sabercraft.blaster.<path>}. */
	public String translationKey() {
		return "sabercraft.blaster." + this.id.getPath();
	}
}
