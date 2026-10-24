package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.combat.CombatLibrary;
import com.phiro.sabercraft.client.combat.CombatMove;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.combat.CombatStyle;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * Takes screenshots of every move of every fighting style at fixed moments: the wind-up, the
 * strike, the follow-through and the recovery. Each picture shows the move three times at once:
 * in the player's own first-person view, on a mannequin seen from the side and on a mannequin
 * facing the camera. A second picture per move shows the player from behind (F5).
 *
 * <p>The animation clock is frozen for each picture, so the pictures show exactly the named
 * moment. Files: {@code build/run/clientGameTest/screenshots/gallery_*}.
 */
public class CombatGalleryGameTest implements FabricClientGameTest {
	private static final String[][] STYLES = {
			{"sabercraft:flowing", "sabercraft:guardian", "sabercraft:blue"},
			{"sabercraft:heavy", "sabercraft:dark_lord", "sabercraft:red"},
			{"sabercraft:staff", "sabercraft:double", "sabercraft:purple"},
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("tp @a 0 ~ 0 -90 5");
			server.runCommand("summon minecraft:mannequin 3.2 ~ -1.3 {Tags:[\"side\"]}");
			server.runCommand("summon minecraft:mannequin 3.6 ~ 1.4 {Tags:[\"front\"]}");
			server.runCommand("tp @e[tag=side] 3.2 ~ -1.3 0 0");
			server.runCommand("tp @e[tag=front] 3.6 ~ 1.4 115 0");
			context.waitTicks(20);

			for (String[] style : STYLES) {
				this.style(context, server, style[0], style[1], style[2]);
			}
		}
	}

	private void style(ClientGameTestContext context, TestServerContext server, String styleId, String hilt, String color) {
		String item = "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"" + hilt + "\",\"" + color + "\",\"" + styleId + "\"]}]";
		server.runCommand("item replace entity @a hotbar.0 with " + item);
		server.runCommand("item replace entity @e[type=mannequin] weapon.mainhand with " + item);
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(30);

		CombatStyle style = context.computeOnClient(client -> CombatLibrary.INSTANCE.style(net.minecraft.resources.Identifier.parse(styleId)));

		if (style == null) {
			throw new AssertionError("style " + styleId + " is not loaded");
		}

		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.takeScreenshot("gallery_" + name(styleId) + "_idle");
		List<CombatMove> moves = new ArrayList<>(style.combo());

		for (CombatMove extra : new CombatMove[] {style.sprint(), style.jump(), style.special()}) {
			if (extra != null && !moves.contains(extra)) {
				moves.add(extra);
			}
		}

		for (CombatMove move : moves) {
			float[] ages = {0.0F, move.strike() - 1.0F, move.strike(), move.strike() + 1.5F, move.duration() * 0.8F};
			float start = context.computeOnClient(client -> {
				float now = SaberTracker.INSTANCE.time(0.0F);

				for (LivingEntity entity : holders(client)) {
					CombatState state = SaberTracker.INSTANCE.combat(entity);

					if (state == null) {
						throw new AssertionError("no combat state for " + entity);
					}

					state.play(move, now);
				}

				return now;
			});

			for (float age : ages) {
				context.runOnClient(client -> SaberTracker.INSTANCE.freezeTime(start + Math.max(0.0F, age)));
				context.takeScreenshot("gallery_" + name(styleId) + "_" + name(move.id().toString()) + "_t" + String.format("%04.1f", Math.max(0.0F, age)));
			}

			context.runOnClient(client -> {
				SaberTracker.INSTANCE.freezeTime(start + move.strike());
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.takeScreenshot("gallery_" + name(styleId) + "_" + name(move.id().toString()) + "_back");
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				SaberTracker.INSTANCE.freezeTime(Float.NaN);
			});
			context.waitTicks((int) move.duration() + 2);
		}

		SaberCraft.LOGGER.info("Gallery for {} done ({} moves)", styleId, moves.size());
	}

	private static List<LivingEntity> holders(net.minecraft.client.Minecraft client) {
		List<LivingEntity> entities = new ArrayList<>();
		entities.add(client.player);

		for (var entity : client.level.entitiesForRendering()) {
			if (entity instanceof Mannequin mannequin) {
				entities.add(mannequin);
			}
		}

		return entities;
	}

	private static String name(String id) {
		return id.substring(id.indexOf(':') + 1);
	}
}
