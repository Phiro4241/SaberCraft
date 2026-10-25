package com.phiro.sabercraft.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.components.AbstractWidget;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.character.CharacterConfig;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.config.JsonConfig;

/**
 * Phase 10: the new settings are in the menu, a real mouse click changes them, and they are still
 * there after a restart (the files are read again, as at game start).
 *
 * <ul>
 *   <li>Sky menu: planet sun and moon, planet sounds and their volume.</li>
 *   <li>Music menu: victory and defeat pieces.</li>
 *   <li>Character: a profile of your own stays chosen.</li>
 *   <li>Server file {@code abilities.json}: {@code hilt_traits} and {@code duels_enabled}.</li>
 * </ul>
 */
public class SettingsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			ImmersionConfig config = ImmersionConfig.get();
			config.planetCelestials = true;
			config.planetAmbient = true;
			config.planetAmbientVolume = 0.5F;
			config.musicStingers = true;
			config.musicMode = ImmersionConfig.MODE_SABERCRAFT;
			ImmersionConfig.save();
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(60);

			// Sky menu: three real clicks.
			context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SPACE));
			context.waitTicks(3);
			click(context, "Planet sun and moon", 0.5);
			click(context, "Planet sounds:", 0.5);
			click(context, "Planet sound volume", 0.25);
			context.waitTicks(3);
			context.takeScreenshot("settings_sky");
			check(!context.computeOnClient(client -> ImmersionConfig.get().planetCelestials), "the click turned the planet sun and moon off");
			check(!context.computeOnClient(client -> ImmersionConfig.get().planetAmbient), "the click turned the planet sounds off");
			float volume = context.computeOnClient(client -> ImmersionConfig.get().planetAmbientVolume);
			check(volume > 0.15F && volume < 0.35F, "the slider set the planet sound volume to about a quarter: " + volume);

			// Music menu.
			context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.MUSIC));
			context.waitTicks(3);
			click(context, "Victory & defeat", 0.5);
			check(!context.computeOnClient(client -> ImmersionConfig.get().musicStingers), "the click turned victory and defeat off");
			context.runOnClient(client -> client.gui.setScreen(null));

			// The file, and a "restart": the settings are read again from it.
			JsonObject file = context.computeOnClient(client -> read("immersion_client"));
			check(!file.get("planet_celestials").getAsBoolean() && !file.get("planet_ambient").getAsBoolean()
					&& !file.get("music_stingers").getAsBoolean(), "the file has the new settings");
			check(Math.abs(file.get("planet_ambient_volume").getAsFloat() - volume) < 1.0E-4F, "the file has the volume");
			context.runOnClient(client -> {
				ImmersionConfig.get().planetAmbient = true;
				ImmersionConfig.load();
			});
			check(context.computeOnClient(client -> !ImmersionConfig.get().planetAmbient && !ImmersionConfig.get().planetCelestials
					&& !ImmersionConfig.get().musicStingers && Math.abs(ImmersionConfig.get().planetAmbientVolume - volume) < 1.0E-4F),
					"after reading the file again everything is as chosen");

			// A profile of your own stays chosen.
			context.runOnClient(client -> {
				CharacterConfig.get().outfit = "profile:knight";
				CharacterConfig.save();
				CharacterConfig.get().outfit = "";
				CharacterConfig.load();
			});
			check("profile:knight".equals(context.computeOnClient(client -> CharacterConfig.get().outfit)), "the chosen profile is saved");

			// Server settings.
			context.runOnClient(client -> {
				AbilityConfig config = AbilityConfig.get();
				config.hiltTraits = false;
				config.duelsEnabled = false;
				JsonConfig.save("abilities", config);
				config.hiltTraits = true;
				config.duelsEnabled = true;
				AbilityConfig.load();
			});
			check(context.computeOnClient(client -> !AbilityConfig.get().hiltTraits && !AbilityConfig.get().duelsEnabled),
					"hilt_traits and duels_enabled are read from abilities.json");

			// Back to the defaults for the tests that follow.
			context.runOnClient(client -> {
				ImmersionConfig config = ImmersionConfig.get();
				config.planetCelestials = true;
				config.planetAmbient = true;
				config.planetAmbientVolume = 0.5F;
				config.musicStingers = true;
				ImmersionConfig.save();
				CharacterConfig.get().outfit = "";
				CharacterConfig.save();
				AbilityConfig ability = AbilityConfig.get();
				ability.hiltTraits = true;
				ability.duelsEnabled = true;
				JsonConfig.save("abilities", ability);
			});
		}
	}

	/** Clicks the widget whose text starts with {@code label}, at {@code across} (0..1) of its width. */
	private static void click(ClientGameTestContext context, String label, double across) {
		double[] at = context.computeOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().startsWith(label)) {
					double scale = client.getWindow().getGuiScale();
					return new double[] {(widget.getX() + widget.getWidth() * across) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
				}
			}

			throw new AssertionError("No widget '" + label + "' in the menu");
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static JsonObject read(String name) {
		try {
			return JsonParser.parseString(Files.readString(JsonConfig.path(name))).getAsJsonObject();
		} catch (IOException error) {
			throw new UncheckedIOException(error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
