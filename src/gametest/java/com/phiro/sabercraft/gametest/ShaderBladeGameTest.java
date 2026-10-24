package com.phiro.sabercraft.gametest;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.CameraType;
import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.render.ShaderCompat;
import com.phiro.sabercraft.client.saber.SaberAnimations;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Checks the blade with and without an Iris shader pack (Phase 8).
 *
 * <p>Without Iris it only takes the reference pictures. With Iris on the class path it loads each
 * shader pack given in the system property {@code sabercraft.shaderpacks} (paths to the zip files,
 * separated by commas): the pack is copied into the {@code shaderpacks} folder, written into
 * {@code config/iris.properties} and Iris is reloaded, exactly as if it was picked in the Iris
 * menu. The test then checks that Iris really uses the pack, and for every pack (and without one)
 * takes pictures of all six colors in first person, a lineup with the double-bladed hilt in third
 * person, by day and by night, in front of the sky and in front of a dark wall. Each color has to
 * show up in its own color on screen.
 */
public class ShaderBladeGameTest implements FabricClientGameTest {
	private static final int SETTLE = SaberAnimations.HEAVY.igniteDelay() + SaberAnimations.HEAVY.igniteTicks() + 4;
	/** Fewest pixels of the blade color a first-person picture needs, at 854 x 480. */
	private static final int MIN_PIXELS = 150;
	/** Colors that did not show up; all pictures are taken first, then the test fails. */
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));
		List<String> packs = new ArrayList<>();
		packs.add("");

		if (FabricLoader.getInstance().isModLoaded("iris")) {
			String property = System.getProperty("sabercraft.shaderpacks", "");

			for (String path : property.split(",")) {
				if (!path.isBlank()) {
					packs.add(install(Path.of(path.trim())));
				}
			}
		}

		if (packs.size() == 1) {
			SaberCraft.LOGGER.warn("No shader pack to test: Iris is not loaded or sabercraft.shaderpacks is empty");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("kill @e[type=!player]");

			for (String pack : packs) {
				usePack(context, pack);
				String tag = pack.isEmpty() ? "vanilla" : shortName(pack);
				this.colors(context, server, tag);
				this.lineup(context, server, tag);
			}

			usePack(context, "");
		}

		check(this.failures.isEmpty(), "every blade shows its color, missing: " + this.failures);
	}

	/** First person, one picture per color, by day against the sky and at night. */
	private void colors(ClientGameTestContext context, TestServerContext server, String tag) {
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:apple");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		for (String time : new String[] {"day", "midnight"}) {
			server.runCommand("time set " + time);
			// The same view without a saber: only pixels that the blade adds are counted, so a
			// blue sky does not pass for a blue blade.
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
			context.getInput().lookAt(20.0F, -25.0F);
			context.waitTicks(SETTLE);
			Path empty = context.takeScreenshot("shader_" + tag + "_" + time + "_empty");
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));

			for (Identifier color : SaberVariant.COLORS) {
				server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\""
						+ SaberHilts.GUARDIAN.id() + "\",\"" + color + "\"]}]");
				context.getInput().lookAt(20.0F, -25.0F);
				context.waitTicks(SETTLE);
				Path picture = context.takeScreenshot("shader_" + tag + "_" + time + "_" + color.getPath());
				BladeStyle style = BladeStyles.get(color);
				int count = countColor(picture, empty, style);
				String what = tag + "/" + time + "/" + color.getPath() + " (" + count + " px)";

				if (count >= MIN_PIXELS) {
					SaberCraft.LOGGER.info("OK: blade shows its color: {}", what);
				} else {
					SaberCraft.LOGGER.error("Blade color missing: {}", what);
					this.failures.add(what);
				}
			}
		}
	}

	/** Armor stands with all colors and the double-bladed hilt, in front of the sky and a dark wall. */
	private void lineup(ClientGameTestContext context, TestServerContext server, String tag) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
		server.runCommand("kill @e[type=minecraft:armor_stand]");
		String[][] stands = {
				{SaberHilts.DARK_LORD.id().toString(), "sabercraft:red"},
				{SaberHilts.GUARDIAN.id().toString(), "sabercraft:blue"},
				{SaberHilts.SENTINEL.id().toString(), "sabercraft:green"},
				{SaberHilts.DOUBLE.id().toString(), "sabercraft:red"},
				{SaberHilts.DARK_LORD.id().toString(), "sabercraft:purple"},
				{SaberHilts.GUARDIAN.id().toString(), "sabercraft:yellow"},
				{SaberHilts.SENTINEL.id().toString(), "sabercraft:white"}
		};

		for (int i = 0; i < stands.length; i++) {
			float x = -4.5F + i * 1.5F;
			server.runCommand("execute as @p at @s run summon minecraft:armor_stand ~" + x + " ~ ~4.5 {Rotation:[180f,0f],ShowArms:1b,NoBasePlate:1b,"
					+ "Pose:{RightArm:[-75f,0f,0f]},equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1,"
					+ "components:{\"minecraft:custom_model_data\":{strings:[\"" + stands[i][0] + "\",\"" + stands[i][1] + "\"]}}}}}");
		}

		context.getInput().lookAt(0.0F, 8.0F);
		hideHud(context, true);

		for (String time : new String[] {"day", "midnight"}) {
			server.runCommand("time set " + time);
			context.waitTicks(SETTLE);
			context.takeScreenshot("shader_" + tag + "_lineup_sky_" + time);
		}

		// A dark wall behind the stands: deepslate by day, the darkest background at night.
		server.runCommand("execute as @p at @s run fill ~-7 ~-1 ~6 ~7 ~6 ~6 minecraft:polished_blackstone");
		for (String time : new String[] {"day", "midnight"}) {
			server.runCommand("time set " + time);
			context.waitTicks(10);
			context.takeScreenshot("shader_" + tag + "_lineup_wall_" + time);
		}

		server.runCommand("execute as @p at @s run fill ~-7 ~-1 ~6 ~7 ~6 ~6 minecraft:air");

		// Third person with the double-bladed hilt in the hand.
		server.runCommand("kill @e[type=minecraft:armor_stand]");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\""
				+ SaberHilts.DOUBLE.id() + "\",\"sabercraft:red\"]}]");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.getInput().lookAt(20.0F, 10.0F);

		for (String time : new String[] {"day", "midnight"}) {
			server.runCommand("time set " + time);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(SETTLE);
			context.takeScreenshot("shader_" + tag + "_double_front_" + time);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("shader_" + tag + "_double_back_" + time);
		}

		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		hideHud(context, false);
	}

	/** Copies a pack into the shaderpacks folder of the test client and returns its file name. */
	private static String install(Path pack) {
		try {
			Path folder = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
			Files.createDirectories(folder);
			Files.copy(pack, folder.resolve(pack.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
			return pack.getFileName().toString();
		} catch (IOException e) {
			throw new AssertionError("Cannot install shader pack " + pack, e);
		}
	}

	/**
	 * Selects a pack ("" for none) the way the Iris menu does: writes {@code iris.properties} and
	 * reloads Iris. Iris is reached through reflection, so the test also runs without it.
	 */
	private static void usePack(ClientGameTestContext context, String pack) {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return;
		}

		context.runOnClient(client -> {
			try {
				Path properties = FabricLoader.getInstance().getConfigDir().resolve("iris.properties");
				Files.writeString(properties, "enableShaders=" + !pack.isEmpty() + "\nshaderPack=" + pack + "\ndisableUpdateMessage=true\n");
				Class.forName("net.irisshaders.iris.Iris").getMethod("reload").invoke(null);
			} catch (ReflectiveOperationException | IOException e) {
				throw new AssertionError("Cannot switch Iris to '" + pack + "'", e);
			}
		});
		context.waitTicks(40);
		boolean inUse = context.computeOnClient(client -> ShaderCompat.shaderPackInUse());
		check(inUse == !pack.isEmpty(), pack.isEmpty() ? "Iris runs without a shader pack" : "Iris really uses the shader pack " + pack);
	}

	private static String shortName(String pack) {
		String name = pack.toLowerCase(Locale.ROOT);
		int cut = name.indexOf('_');
		return (cut > 0 ? name.substring(0, cut) : name.replace(".zip", "")).replaceAll("[^a-z0-9]", "");
	}

	private static void hideHud(ClientGameTestContext context, boolean hidden) {
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden() != hidden) {
				client.gui.hud.toggle();
			}
		});
	}

	/**
	 * Counts pixels in the blade's color (or, for the white blade, bright neutral pixels) in the
	 * right half of the picture, where the first-person blade is, that are not in that color in
	 * the picture without a saber. Shader packs change the tone of
	 * the whole picture, so the hue range is a little wider than in the Phase 5 test.
	 */
	private static int countColor(Path file, Path empty, BladeStyle style) {
		BufferedImage image = read(file);
		BufferedImage reference = read(empty);

		int rgb = style.glowColor();
		float[] target = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
		boolean neutral = target[1] < 0.3F;
		float[] hsb = new float[3];
		int count = 0;

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = image.getWidth() / 2; x < image.getWidth(); x++) {
				if (matches(image.getRGB(x, y), target, neutral, hsb) && !matches(reference.getRGB(x, y), target, neutral, hsb)) {
					count++;
				}
			}
		}

		SaberCraft.LOGGER.info("{}: {} pixels of the blade color in {}", style.id(), count, file.getFileName());
		return count;
	}

	private static boolean matches(int pixel, float[] target, boolean neutral, float[] hsb) {
		Color.RGBtoHSB(pixel >> 16 & 0xFF, pixel >> 8 & 0xFF, pixel & 0xFF, hsb);

		if (neutral) {
			return hsb[1] < 0.25F && hsb[2] > 0.88F;
		}

		float hueDistance = Math.abs(hsb[0] - target[0]);
		hueDistance = Math.min(hueDistance, 1.0F - hueDistance);
		return hueDistance < 0.08F && hsb[1] > 0.45F && hsb[2] > 0.35F;
	}

	private static BufferedImage read(Path file) {
		try {
			return ImageIO.read(file.toFile());
		} catch (IOException e) {
			throw new AssertionError("Cannot read " + file, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
