package com.phiro.sabercraft.client.sky;

/**
 * The sun and the moon of a planet (Phase 10): how big and in which color the vanilla sun, the
 * second sun and the vanilla moon are drawn. Only the size and the tint of the vanilla sprites
 * change, so day, night, moon phases and weather work as before.
 *
 * @param sunSize         size of the sun, 1 = vanilla
 * @param sunColor        tint of the sun (RGB); white keeps the vanilla color
 * @param secondSunSize   size of the second sun relative to vanilla (only with a second sun)
 * @param secondSunColor  tint of the second sun (RGB)
 * @param moonSize        size of the moon, 1 = vanilla
 * @param moonColor       tint of the moon (RGB)
 * @param moonAlpha       how strongly the moon shows, 0..1 (thin smoke or haze hides it partly)
 */
public record PlanetCelestials(float sunSize, int sunColor, float secondSunSize, int secondSunColor, float moonSize, int moonColor,
		float moonAlpha) {
	/** Vanilla sun and moon. */
	public static final PlanetCelestials VANILLA = new PlanetCelestials(1.0F, 0xFFFFFF, 0.65F, 0xFFFFFF, 1.0F, 0xFFFFFF, 1.0F);

	/** The sun and moon of a planet: Tatooine's big white sun with a smaller orange one, and so on. */
	public static PlanetCelestials of(PlanetPreset planet) {
		return switch (planet.id()) {
			// Tatoo I, large and white-yellow, and Tatoo II, smaller and orange.
			case "tatooine" -> new PlanetCelestials(1.3F, 0xFFF2CC, 0.8F, 0xFFA552, 0.85F, 0xF0E4D0, 0.9F);
			// A sun dimmed to amber by the smog over the city.
			case "coruscant" -> new PlanetCelestials(1.05F, 0xFFD49A, 0.65F, 0xFFFFFF, 0.9F, 0xD8D4E8, 0.85F);
			// A warm sun over the forest; the moon stands small beside the gas giant.
			case "endor" -> new PlanetCelestials(1.1F, 0xFFF0D4, 0.65F, 0xFFFFFF, 0.7F, 0xE0E8E0, 0.8F);
			// A small, cold sun far away; a large icy moon.
			case "hoth" -> new PlanetCelestials(0.75F, 0xE8F2FF, 0.65F, 0xFFFFFF, 1.25F, 0xDCEAFF, 1.0F);
			// A huge, dim red disc behind the smoke; the moon is red and faint.
			case "mustafar" -> new PlanetCelestials(1.6F, 0xFF5A30, 0.65F, 0xFFFFFF, 0.9F, 0xD07050, 0.55F);
			// A soft, golden sun; a big pale moon.
			case "naboo" -> new PlanetCelestials(1.1F, 0xFFEEC0, 0.65F, 0xFFFFFF, 1.35F, 0xF4EEE6, 1.0F);
			default -> VANILLA;
		};
	}
}
