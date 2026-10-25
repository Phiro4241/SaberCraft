package com.phiro.sabercraft.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.character.CharacterConfig;
import com.phiro.sabercraft.client.character.OutfitClient;

/**
 * Phase 10: the skin layer bug. The player gets a skin of their own whose whole second layer
 * (hat, jacket, sleeves, trouser legs) and cape are bright magenta, a color no SaberCraft skin
 * uses. Then every character and outfit is chosen in turn, several times and in a quick row, and
 * the picture is searched for magenta: from the front and the back in third person and in first
 * person with an empty hand. Nothing of the own skin may be left. Back on the own skin, all of it
 * must be there again. The same runs with a slim (Alex) skin, a character skin file of your own
 * without a second layer, and with your own skin's second layer switched off.
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/skin_*}.
 */
public class SkinLayerGameTest implements FabricClientGameTest {
	private static final Identifier WIDE_SKIN = Identifier.fromNamespaceAndPath("sabercraft-gametest", "skin_layer_test_wide");
	private static final Identifier SLIM_SKIN = Identifier.fromNamespaceAndPath("sabercraft-gametest", "skin_layer_test_slim");
	private static final Identifier CAPE = Identifier.fromNamespaceAndPath("sabercraft-gametest", "skin_layer_test_cape");
	/** Pixels of the own skin's second layer that a plain skin shows at least, in each view. */
	private static final int PLAIN_MINIMUM = 150;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			CharacterConfig.get().outfit = "";
			register(client, WIDE_SKIN, testSkin(false));
			register(client, SLIM_SKIN, testSkin(true));
			register(client, CAPE, testCape());
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0 ~ 0 180 10");
			server.runCommand("kill @e[type=!player]");
			context.runOnClient(client -> {
				client.options.fov().set(30);
				client.gui.hud.toggle();
			});
			context.waitTicks(20);

			for (boolean slim : new boolean[] {false, true}) {
				String tag = slim ? "slim" : "wide";
				useOwnSkin(context, slim);
				ownSkin(context, tag, "start");

				for (Outfit outfit : Outfit.ALL) {
					choose(context, outfit.id().toString());
					character(context, outfit, tag);
				}

				// Quick changes, several in a row without waiting, and back: nothing may stick.
				for (int round = 0; round < 3; round++) {
					for (Outfit outfit : Outfit.CHARACTERS) {
						context.runOnClient(client -> OutfitClient.choose(outfit.id().toString()));
						context.waitTicks(1);
					}

					context.runOnClient(client -> OutfitClient.choose(""));
					context.waitTicks(1);
				}

				context.waitTicks(5);
				ownSkin(context, tag, "after_quick_changes");
				choose(context, Outfit.OBI_WAN.id().toString());
				character(context, Outfit.OBI_WAN, tag + "_again");
				choose(context, "");
				ownSkin(context, tag, "end");
			}

			// A character skin file of your own without any second layer.
			context.runOnClient(client -> {
				Path target = OutfitClient.characterFile(Outfit.LUKE);
				try (InputStream in = SaberCraft.class.getResourceAsStream("/assets/sabercraft/textures/outfit/obi_wan.png")) {
					Files.createDirectories(target.getParent());
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
					// Clear its whole second layer.
					NativeImage image = NativeImage.read(Files.newInputStream(target));
					for (int[] box : SECOND_LAYER) {
						for (int y = box[1]; y < box[3]; y++) {
							for (int x = box[0]; x < box[2]; x++) {
								image.setPixel(x, y, 0);
							}
						}
					}
					image.writeToFile(target);
					image.close();
				} catch (IOException error) {
					throw new UncheckedIOException(error);
				}
				OutfitClient.reloadCharacterSkins();
			});
			choose(context, Outfit.LUKE.id().toString());
			check(context.computeOnClient(client -> OutfitClient.hasOwnSkin(Outfit.LUKE)), "the skin file without a second layer is used");
			character(context, Outfit.LUKE, "own_file_without_layer");
			context.runOnClient(client -> {
				try {
					Files.deleteIfExists(OutfitClient.characterFile(Outfit.LUKE));
				} catch (IOException error) {
					throw new UncheckedIOException(error);
				}
				OutfitClient.reloadCharacterSkins();
			});

			// Your own second layer switched off in the skin options: still none on a character,
			// the character's own second layer is shown, and back home the option is respected.
			context.runOnClient(client -> {
			client.options.setModelPart(PlayerModelPart.HAT, false);
			client.options.broadcastOptions();
		});
			choose(context, Outfit.VADER.id().toString());
			check(context.computeOnClient(client -> client.player.isModelPartShown(PlayerModelPart.HAT)),
					"a character always shows the second layer of its own skin");
			character(context, Outfit.VADER, "own_hat_off");
			choose(context, "");
			check(context.computeOnClient(client -> !client.player.isModelPartShown(PlayerModelPart.HAT)),
					"back on your own skin your own choice (hat off) counts again");
			context.runOnClient(client -> {
			client.options.setModelPart(PlayerModelPart.HAT, true);
			client.options.broadcastOptions();
		});
			context.waitTicks(5);

			context.runOnClient(client -> {
				client.gui.hud.toggle();
				client.options.fov().set(70);
				client.options.setCameraType(CameraType.FIRST_PERSON);
				OutfitClient.choose("");
			});
			context.waitTicks(5);
		}
	}

	/** The second-layer areas of a 64 x 64 skin, as x0, y0, x1, y1. */
	private static final int[][] SECOND_LAYER = {
			{32, 0, 64, 16}, {16, 32, 40, 48}, {40, 32, 56, 48}, {0, 32, 16, 48}, {48, 48, 64, 64}, {0, 48, 16, 64},
	};

	private static void choose(ClientGameTestContext context, String outfit) {
		context.runOnClient(client -> OutfitClient.choose(outfit));
		context.waitTicks(5);
	}

	/** Your own skin: everything of the test skin is there, in every view. */
	private static void ownSkin(ClientGameTestContext context, String tag, String step) {
		Identifier expected = context.computeOnClient(client -> client.player.getSkin().body().texturePath());
		check(expected.getNamespace().equals("sabercraft-gametest"), tag + " " + step + ": your own skin is used (" + expected + ")");
		check(context.computeOnClient(client -> client.player.getSkin().cape() != null), tag + " " + step + ": your own cape is there");

		for (CameraType camera : CameraType.values()) {
			int count = magenta(shot(context, camera, "skin_" + tag + "_own_" + step));
			check(count >= PLAIN_MINIMUM, tag + " " + step + ": your second layer is visible in " + camera + " (" + count + " pixels)");
		}
	}

	/** A character or outfit: only its own skin, nothing of yours, in every view. */
	private static void character(ClientGameTestContext context, Outfit outfit, String tag) {
		String name = outfit.id().getPath();
		PlayerSkin skin = context.computeOnClient(client -> client.player.getSkin());
		OutfitClient.Look look = context.computeOnClient(client -> OutfitClient.look(client.player.getUUID()));
		check(look != null && skin.body().texturePath().equals(look.texture()), tag + " " + name + ": the skin itself is the character's (" + skin.body().texturePath() + ")");
		check(skin.cape() == null && skin.elytra() == null, tag + " " + name + ": no cape or elytra of your own skin is left");
		check(skin.model() == PlayerModelType.WIDE, tag + " " + name + ": the character's own model shape is used");
		check(context.computeOnClient(client -> client.getEntityRenderDispatcher().getPlayerRenderer(client.player).getModel().leftArm != null),
				tag + " " + name + ": a player renderer is found");

		for (CameraType camera : CameraType.values()) {
			int count = magenta(shot(context, camera, "skin_" + tag + "_" + name));
			check(count == 0, tag + " " + name + ": nothing of your own skin in " + camera + " (" + count + " pixels)");
		}
	}

	private static Path shot(ClientGameTestContext context, CameraType camera, String name) {
		// In first person a wide view and the HUD on (without it there is no hand), so the empty
		// hand with its sleeve is in the picture.
		boolean first = camera == CameraType.FIRST_PERSON;
		context.runOnClient(client -> {
			client.options.setCameraType(camera);
			client.options.fov().set(first ? 70 : 30);
			if (first) {
				client.gui.hud.toggle();
			}
		});
		context.waitTicks(3);
		Path file = context.takeScreenshot(name + "_" + camera.name().toLowerCase(java.util.Locale.ROOT));
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			client.options.fov().set(30);
			if (first) {
				client.gui.hud.toggle();
			}
		});
		return file;
	}

	/** Pixels that are clearly the magenta of the test skin's second layer. */
	private static int magenta(Path file) {
		BufferedImage image;

		try {
			image = ImageIO.read(file.toFile());
		} catch (IOException error) {
			throw new UncheckedIOException(error);
		}

		int count = 0;

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int rgb = image.getRGB(x, y);
				int r = rgb >> 16 & 0xFF;
				int g = rgb >> 8 & 0xFF;
				int b = rgb & 0xFF;

				if (r > 90 && b > 90 && g * 3 < Math.min(r, b) && Math.abs(r - b) * 4 < Math.max(r, b)) {
					count++;
				}
			}
		}

		return count;
	}

	/** Gives the local player one of the test skins, the way the skin manager would. */
	private static void useOwnSkin(ClientGameTestContext context, boolean slim) {
		context.runOnClient(client -> {
			UUID self = client.player.getUUID();
			PlayerInfo info = client.getConnection().getPlayerInfo(self);
			Identifier body = slim ? SLIM_SKIN : WIDE_SKIN;
			PlayerSkin skin = new PlayerSkin(new ClientAsset.ResourceTexture(body, body), new ClientAsset.ResourceTexture(CAPE, CAPE),
					null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, true);

			try {
				Field lookup = PlayerInfo.class.getDeclaredField("skinLookup");
				lookup.setAccessible(true);
				lookup.set(info, (Supplier<PlayerSkin>) () -> skin);
			} catch (ReflectiveOperationException error) {
				throw new AssertionError("cannot set the test skin", error);
			}

			for (PlayerModelPart part : PlayerModelPart.values()) {
				client.options.setModelPart(part, true);
			}

			client.options.broadcastOptions();
		});
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getSkin().model() == (slim ? PlayerModelType.SLIM : PlayerModelType.WIDE)),
				"the test skin is in use (" + (slim ? "slim" : "wide") + ")");
	}

	/** A skin with a skin-colored base and a fully magenta second layer. */
	private static NativeImage testSkin(boolean slim) {
		NativeImage image = new NativeImage(64, 64, true);

		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				image.setPixel(x, y, 0);
			}
		}

		// Base: head skin colored, body blue, arms skin colored, legs dark blue.
		fill(image, 0, 0, 32, 16, 0xFFC89070);
		fill(image, 16, 16, 40, 32, 0xFF3050A0);
		fill(image, 40, 16, 56, 32, 0xFFC89070);
		fill(image, 32, 48, 48, 64, 0xFFC89070);
		fill(image, 0, 16, 16, 32, 0xFF203060);
		fill(image, 16, 48, 32, 64, 0xFF203060);

		for (int[] box : SECOND_LAYER) {
			fill(image, box[0], box[1], box[2], box[3], 0xFFFF00FF);
		}

		return image;
	}

	private static NativeImage testCape() {
		NativeImage image = new NativeImage(64, 32, true);
		fill(image, 0, 0, 64, 32, 0xFFFF00FF);
		return image;
	}

	private static void fill(NativeImage image, int x0, int y0, int x1, int y1, int argb) {
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				image.setPixel(x, y, argb);
			}
		}
	}

	private static void register(Minecraft client, Identifier id, NativeImage image) {
		client.getTextureManager().register(id, new DynamicTexture(id::toString, image));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
