package com.phiro.sabercraft.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.client.character.CharacterConfig;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.menu.CharacterMenuScreen;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Checks the character and the menu in a real client: every outfit over the player's own skin
 * (screenshots from the front and back), the choice reaching the server and coming back to the
 * client as other players would get it, a file of your own that stays local, and every area of
 * the V menu (screenshots at the default window size).
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/character_*} and {@code menu_*}.
 */
public class CharacterGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			CharacterConfig.get().outfit = "";
			// A file of "your own": the scout texture under another name.
			Path target = OutfitClient.folder().resolve("my_outfit.png");
			try (InputStream in = SaberCraft.class.getResourceAsStream("/assets/sabercraft/textures/outfit/scout.png")) {
				Files.createDirectories(target.getParent());
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException error) {
				throw new UncheckedIOException(error);
			}
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0 ~ 0 180 10");
			// A narrow field of view, so the third-person shots show the outfit up close.
			context.runOnClient(client -> client.options.fov().set(30));
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			// V without a sword: the character area.
			context.getInput().pressKey(SaberCraftClient.CUSTOMIZE_KEY);
			context.waitTicks(3);
			check(context.computeOnClient(client -> client.gui.screen() instanceof CharacterMenuScreen), "V without a sword opens the character area");
			context.takeScreenshot("menu_character_plain");
			context.runOnClient(client -> client.gui.setScreen(null));

			for (Outfit outfit : Outfit.ALL) {
				context.runOnClient(client -> OutfitClient.choose(outfit.id().toString()));
				context.waitTicks(12);
				check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).equals(Optional.of(outfit.id())),
						"the server knows the outfit " + outfit.id());
				check(outfit.id().equals(context.computeOnClient(client -> OutfitClient.otherOutfit(self))),
						"other players are told about " + outfit.id());
				context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				context.waitTicks(3);
				context.takeScreenshot("character_" + outfit.id().getPath() + "_front");
				context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				context.waitTicks(3);
				context.takeScreenshot("character_" + outfit.id().getPath() + "_back");
				context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			}

			// A file of your own: shown to you, never sent.
			context.runOnClient(client -> OutfitClient.choose(OutfitClient.FILE_PREFIX + "my_outfit.png"));
			context.waitTicks(12);
			check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).isEmpty(), "your own file is not sent to the server");
			check(context.computeOnClient(client -> OutfitClient.look(self) != null), "your own file is shown to you");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(3);
			context.takeScreenshot("character_own_file");

			// A skin file of your own for a film character replaces its look on this computer.
			context.runOnClient(client -> {
				Path target = OutfitClient.characterFile(Outfit.VADER);
				try (InputStream in = SaberCraft.class.getResourceAsStream("/assets/sabercraft/textures/outfit/twilight.png")) {
					Files.createDirectories(target.getParent());
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException error) {
					throw new UncheckedIOException(error);
				}
				OutfitClient.reloadCharacterSkins();
				OutfitClient.choose(Outfit.VADER.id().toString());
			});
			context.waitTicks(12);
			check(context.computeOnClient(client -> OutfitClient.hasOwnSkin(Outfit.VADER)), "the character skin file is found");
			check(context.computeOnClient(client -> OutfitClient.look(self).texture().getPath().startsWith("character_skin/")),
					"the character is drawn with your own skin file");
			check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).equals(Optional.of(Outfit.VADER.id())),
					"with your own skin the character is still sent to the server");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(3);
			context.takeScreenshot("character_vader_own_skin");
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				try {
					Files.deleteIfExists(OutfitClient.characterFile(Outfit.VADER));
				} catch (IOException error) {
					throw new UncheckedIOException(error);
				}
				OutfitClient.reloadCharacterSkins();
			});
			check(context.computeOnClient(client -> !OutfitClient.look(self).texture().getPath().startsWith("character_skin/")),
					"without the file the built-in look is back");

			// Back to the plain skin.
			context.runOnClient(client -> OutfitClient.choose(""));
			context.waitTicks(12);
			check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).isEmpty(), "no outfit: the server forgets it");
			check(context.computeOnClient(client -> OutfitClient.look(self) == null), "no outfit: the plain skin");
			context.takeScreenshot("character_plain");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			// The menu with a sword in hand: every area.
			context.runOnClient(client -> OutfitClient.choose(Outfit.SHADOW.id().toString()));
			server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.waitTicks(10);
			context.getInput().pressKey(SaberCraftClient.CUSTOMIZE_KEY);
			context.waitTicks(3);
			check(context.computeOnClient(client -> client.gui.screen() instanceof SaberCustomizeScreen), "V with a sword opens the saber area");

			// Phase 9: picking a new character in the menu gives the saber in hand its hilt, color and style.
			for (Outfit character : new Outfit[] {Outfit.ANAKIN, Outfit.YODA, Outfit.DOOKU, Outfit.AHSOKA, Outfit.KYLO, Outfit.REY}) {
				context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.CHARACTER));
				context.waitTicks(3);
				context.clickScreenButton(character.translationKey());
				context.waitTicks(10);
				SaberVariant wanted = character.suggested();
				String held = server.computeOnServer(s -> {
					ItemStack stack = s.getPlayerList().getPlayer(self).getMainHandItem();
					return SaberVariant.hiltOf(stack) + " " + SaberVariant.colorOf(stack) + " " + SaberVariant.styleOf(stack);
				});
				check(held.equals(wanted.hilt() + " " + wanted.color() + " " + wanted.style()),
						character.id().getPath() + " gives the saber " + wanted + " (held: " + held + ")");
				check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).equals(Optional.of(character.id())), "the server knows " + character.id());
				context.takeScreenshot("menu_character_" + character.id().getPath());
			}

			check(context.computeOnClient(client -> OutfitClient.lookOf(Outfit.YODA).scale() < 0.7F), "Yoda is drawn small");
			context.runOnClient(client -> CharacterConfig.get().characterSize = false);
			check(context.computeOnClient(client -> OutfitClient.lookOf(Outfit.YODA).scale() == 1.0F), "with the size option off Yoda has the normal size");
			context.runOnClient(client -> {
				CharacterConfig.get().characterSize = true;
				client.gui.setScreen(null);
				OutfitClient.choose(Outfit.SHADOW.id().toString());
			});
			context.waitTicks(5);

			for (SaberMenuScreen.Tab tab : SaberMenuScreen.Tab.values()) {
				context.runOnClient(client -> SaberMenuScreen.open(client, tab));
				context.waitTicks(3);
				context.takeScreenshot("menu_" + tab.name().toLowerCase(java.util.Locale.ROOT));
			}

			context.runOnClient(client -> {
				client.gui.setScreen(null);
				client.options.fov().set(70);
				OutfitClient.choose("");
			});
			context.waitTicks(5);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
