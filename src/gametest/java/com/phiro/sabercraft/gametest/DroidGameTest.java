package com.phiro.sabercraft.gametest;

import java.util.List;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.client.droid.DroidClient;
import com.phiro.sabercraft.client.menu.DroidMenuScreen;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidMarker;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;

/**
 * Phase 11: the droids, through the droid menu and real clicks of the player.
 *
 * <p>The menu shows every droid and calls them; the server keeps the limit of four and never makes
 * a second droid of the same kind. The droids follow (also after a teleport, and out of a glass
 * cage they are stuck in), wait when told to, answer a right-click, and their abilities work:
 * R2-D2 repairs, C-3PO reports, BB-8 marks, Chopper zaps, the mouse droid fetches, the probe droid
 * finds ore, the B1 shoots and K-2SO shoves. BB-8 rolls with its head on top. After closing and
 * opening the world the same droids are back, once each, and no pig is left over. On a dedicated
 * server a client sees its droid, and it is gone when the player leaves.
 *
 * <p>Screenshots: {@code droid_menu_*}, {@code droids_lineup_*}, {@code droid_bb8_rolling},
 * {@code droid_*_ability}, {@code droid_dedicated_server}.
 */
public class DroidGameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-80, -64, -80, 80, 320, 80);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(5));
		TestWorldSave save;
		UUID self;

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("kill @e[type=!player]");
			context.waitTicks(20);
			self = context.computeOnClient(client -> client.player.getUUID());

			menu(context, server, self);
			follow(context, server, self);
			stuck(context, server, self);
			bb8(context, server, self);
			interact(context, server, self);
			abilities(context, server, self);
			secondCrew(context, server, self);
		}

		reopen(context, save, self);
		dedicatedServer(context);
	}

	/** The gallery, calling four droids, the limit and no duplicates. */
	private static void menu(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.waitFor(client -> DroidClient.status() != null, 40);
		check(context.computeOnClient(client -> DroidClient.status() != null && DroidClient.count() == 0), "the server tells the client about its droids on join");
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.DROIDS));
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.gui.screen() instanceof DroidMenuScreen), "the droid area of the menu opens");
		context.takeScreenshot("droid_menu_r2d2");

		for (DroidType type : List.of(DroidType.BB8, DroidType.R2D2, DroidType.C3PO, DroidType.CHOPPER)) {
			int before = server.computeOnServer(s -> Droids.liveCount());
			context.clickScreenButton(type.translationKey());
			context.waitTicks(3);
			check(context.computeOnClient(client -> ((DroidMenuScreen) client.gui.screen()).chosen()) == type, "a click on the card chooses " + type.displayName());

			if (type == DroidType.BB8) {
				context.waitTicks(30);
				context.takeScreenshot("droid_menu_bb8");
			}

			context.clickScreenButton("sabercraft.droid.menu.call");
			context.waitFor(client -> DroidClient.entry(type) != null, 40);
			check(server.computeOnServer(s -> Droids.liveCount()) == before + 1, type.displayName() + " is called");
			check(server.computeOnServer(s -> inWorld(s, type)) == 1, type.displayName() + " is in the world once");
			context.waitTicks(5);
		}

		context.waitFor(client -> clientDroids(client.level) == 4, 40);
		check(context.computeOnClient(client -> clientDroids(client.level)) == 4, "the client sees the four droids");
		check(server.computeOnServer(s -> SaberAdvancements.has(me(s, self), SaberAdvancements.DROID_FRIEND)), "the first droid gives These Are the Droids");
		context.takeScreenshot("droid_menu_four_out");

		// The limit: a fifth droid is refused (the button is off, a request by hand changes nothing).
		context.clickScreenButton(DroidType.MOUSE.translationKey());
		context.waitTicks(3);
		check(!context.computeOnClient(client -> ((DroidMenuScreen) client.gui.screen()).callActive()), "with four droids out the call button is off");
		context.runOnClient(client -> DroidClient.command("spawn", DroidType.MOUSE));
		context.waitTicks(10);
		check(server.computeOnServer(s -> Droids.liveCount()) == 4 && server.computeOnServer(s -> inWorld(s, DroidType.MOUSE)) == 0,
				"the server refuses a fifth droid");

		// Calling a droid that is out calls it back, it is never a second one.
		context.clickScreenButton(DroidType.R2D2.translationKey());
		context.waitTicks(3);
		context.clickScreenButton("sabercraft.droid.menu.recall");
		context.waitTicks(10);
		context.runOnClient(client -> DroidClient.command("spawn", DroidType.R2D2));
		context.waitTicks(10);
		check(server.computeOnServer(s -> inWorld(s, DroidType.R2D2)) == 1 && server.computeOnServer(s -> Droids.liveCount()) == 4,
				"calling R2-D2 again makes no second R2-D2");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/** Follows the player, also after a teleport far away; waits when told to. */
	private static void follow(ClientGameTestContext context, TestServerContext server, UUID self) {
		lineUp(server, self, List.of(DroidType.R2D2, DroidType.C3PO, DroidType.BB8, DroidType.CHOPPER));
		context.getInput().lookAt(180.0F, 15.0F);
		context.waitTicks(30);
		context.takeScreenshot("droids_lineup_day");
		server.runCommand("time set midnight");
		context.waitTicks(5);
		context.takeScreenshot("droids_lineup_night");
		server.runCommand("time set day");
		setStaying(server, self, false);

		server.runCommand("tp @a 40 ~ 0");
		context.waitTicks(80);
		check(server.computeOnServer(s -> allNear(s, self, 6.0)), "after a teleport 40 blocks away every droid is back next to the player");

		// Stay: the droid stays where it is when the player walks off.
		context.runOnClient(client -> DroidClient.command("stay", DroidType.C3PO));
		context.waitFor(client -> DroidClient.entry(DroidType.C3PO) != null && DroidClient.entry(DroidType.C3PO).staying(), 20);
		check(context.computeOnClient(client -> DroidClient.entry(DroidType.C3PO).staying()), "C-3PO waits");
		var at = server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.C3PO).position());
		server.runCommand("tp @a 60 ~ 0");
		context.waitTicks(60);
		check(server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.C3PO).position().distanceTo(at)) < 1.5, "a waiting droid does not follow");
		context.runOnClient(client -> DroidClient.command("stay", DroidType.C3PO));
		context.waitTicks(80);
		check(server.computeOnServer(s -> allNear(s, self, 6.0)), "told to follow again, it comes back");
	}

	/** A droid locked in a glass cage gets out to its owner by itself. */
	private static void stuck(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(40);
		lineUp(server, self, List.of(DroidType.R2D2, DroidType.C3PO, DroidType.BB8, DroidType.CHOPPER));
		var bb8 = server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.BB8).blockPosition());
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:glass hollow", bb8.getX() - 1, bb8.getY() - 1, bb8.getZ() - 1,
				bb8.getX() + 1, bb8.getY() + 2, bb8.getZ() + 1));
		setStaying(server, self, false);
		server.runCommand("tp @a 9 ~ 0");
		context.waitTicks(140);
		check(server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.BB8).distanceTo(me(s, self))) < 5.0, "BB-8 does not stay stuck in a glass cage");
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", bb8.getX() - 1, bb8.getY() - 1, bb8.getZ() - 1,
				bb8.getX() + 1, bb8.getY() + 2, bb8.getZ() + 1));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:grass_block", bb8.getX() - 1, bb8.getY() - 1, bb8.getZ() - 1,
				bb8.getX() + 1, bb8.getY() - 1, bb8.getZ() + 1));
	}

	/** BB-8 rolls: its walk animation runs, and it is seen rolling from the side. */
	private static void bb8(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a -1 ~ 0 90 0");
		context.waitTicks(30);
		lineUp(server, self, List.of(DroidType.R2D2, DroidType.C3PO, DroidType.CHOPPER), 3.0);
		// BB-8 rolls past in front of the player, from left to right.
		server.computeOnServer(s -> {
			DroidEntity droid = Droids.droid(me(s, self), DroidType.BB8);
			droid.setStaying(true);
			droid.snapTo(-4.0, me(s, self).getY(), -3.0, 0.0F, 0.0F);
			droid.getNavigation().moveTo(-4.0, me(s, self).getY(), 5.0, 1.0);
			return null;
		});
		context.getInput().lookAt(90.0F, 10.0F);
		context.waitTicks(10);
		check(context.computeOnClient(client -> bb8Walk(client)) > 0.1F, "BB-8 rolls (its roll animation runs)");
		context.takeScreenshot("droid_bb8_rolling");
		context.waitTicks(40);
		setStaying(server, self, false);
	}

	/** A right-click on a droid: it answers; sneaking and right-clicking switches between waiting and following. */
	private static void interact(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(20);
		lineUp(server, self, List.of(DroidType.C3PO, DroidType.BB8, DroidType.CHOPPER), 3.0);
		// Close enough to reach it (three blocks).
		lineUp(server, self, List.of(DroidType.R2D2), -2.4);
		context.getInput().lookAt(180.0F, 25.0F);
		context.waitFor(client -> DroidMarker.of(client.crosshairPickEntity) == DroidType.R2D2, 60);
		int before = context.computeOnClient(client -> DroidClient.eventsSeen());
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitFor(client -> DroidClient.eventsSeen() > before, 20);
		check(context.computeOnClient(client -> DroidClient.eventsSeen()) > before, "R2-D2 answers a right-click (its dome spins)");
		context.waitTicks(4);
		context.takeScreenshot("droid_r2d2_talk");
		check(server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.R2D2).isStaying()), "a plain right-click does not change waiting");

		// R2-D2 was told to wait (by the line-up); sneaking and right-clicking makes it follow.
		context.waitTicks(25);
		until(context, client -> DroidMarker.of(client.crosshairPickEntity) == DroidType.R2D2, 60, "R2-D2 is under the crosshair again");
		context.getInput().holdKey(options -> options.keyShift);
		until(context, client -> client.player.isShiftKeyDown(), 20, "the player sneaks");
		context.waitTicks(3);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(4);
		context.getInput().releaseKey(options -> options.keyShift);
		context.waitFor(client -> DroidClient.entry(DroidType.R2D2) != null && !DroidClient.entry(DroidType.R2D2).staying(), 20);
		check(!server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.R2D2).isStaying())
				&& !context.computeOnClient(client -> DroidClient.entry(DroidType.R2D2).staying()), "sneak and right-click: R2-D2 follows again");
		setStaying(server, self, false);
	}

	/** The four abilities, from the menu's button. */
	private static void abilities(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(20);
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword[damage=800]");
		lineUp(server, self, List.of(DroidType.R2D2, DroidType.C3PO, DroidType.BB8, DroidType.CHOPPER));
		context.waitTicks(5);

		// R2-D2 repairs the sword in hand.
		ability(context, DroidType.R2D2);
		check(server.computeOnServer(s -> me(s, self).getMainHandItem().getDamageValue()) == 800 - 1561 / 4, "R2-D2 repairs a quarter of the wear");
		context.takeScreenshot("droid_r2d2_ability");
		check(!context.computeOnClient(client -> ((DroidMenuScreen) client.gui.screen()).abilityActive()), "right after, R2-D2's ability waits for its cooldown");

		// C-3PO reports.
		var at = server.computeOnServer(s -> me(s, self).position());
		server.runCommand(String.format("summon minecraft:husk %.1f %.1f %.1f {NoAI:1b,PersistenceRequired:1b,Silent:1b}", at.x + 2.0, at.y, at.z - 9.0));
		ability(context, DroidType.C3PO);
		check(server.computeOnServer(s -> SaberAdvancements.progress(me(s, self), SaberAdvancements.LOYAL_COMPANION).getCriterion("c3po").isDone()),
				"C-3PO's report counts for Loyal Companions");

		// BB-8 marks the husk.
		ability(context, DroidType.BB8);
		check(server.computeOnServer(s -> husks(s).stream().allMatch(husk -> husk.hasEffect(MobEffects.GLOWING))), "BB-8's scan makes the husk glow");
		context.setScreen(() -> null);
		context.getInput().lookAt(180.0F, 5.0F);
		context.waitTicks(10);
		context.takeScreenshot("droid_bb8_ability");

		// Chopper zaps a husk right next to it.
		server.runCommand("kill @e[type=husk]");
		var chopper = server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.CHOPPER).position());
		server.runCommand(String.format("summon minecraft:husk %.2f %.2f %.2f {NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:20f}",
				chopper.x + 1.5, chopper.y, chopper.z));
		context.waitTicks(5);
		ability(context, DroidType.CHOPPER);
		check(server.computeOnServer(s -> husks(s).getFirst().getHealth()) < 20.0F, "Chopper's zap hurts the husk next to it");
		server.runCommand("kill @e[type=husk]");
		context.setScreen(() -> null);
		setStaying(server, self, false);
	}

	/** The second crew: mouse droid, probe droid, B1 and K-2SO. */
	private static void secondCrew(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.DROIDS));
		context.waitTicks(5);

		for (DroidType type : List.of(DroidType.R2D2, DroidType.C3PO, DroidType.BB8, DroidType.CHOPPER)) {
			context.clickScreenButton(type.translationKey());
			context.waitTicks(3);
			context.clickScreenButton("sabercraft.droid.menu.dismiss");
			context.waitFor(client -> DroidClient.entry(type) == null, 20);
		}

		check(server.computeOnServer(s -> Droids.liveCount()) == 0 && server.computeOnServer(s -> allPigs(s)) == 0, "sent home, the droids are gone");

		for (DroidType type : List.of(DroidType.MOUSE, DroidType.PROBE, DroidType.B1, DroidType.K2SO)) {
			context.clickScreenButton(type.translationKey());
			context.waitTicks(3);
			context.clickScreenButton("sabercraft.droid.menu.call");
			context.waitFor(client -> DroidClient.entry(type) != null, 20);
		}

		context.setScreen(() -> null);
		lineUp(server, self, List.of(DroidType.MOUSE, DroidType.PROBE, DroidType.B1, DroidType.K2SO));
		context.getInput().lookAt(180.0F, 15.0F);
		context.waitTicks(30);
		context.takeScreenshot("droids_lineup_second");
		setStaying(server, self, false);

		// The mouse droid fetches dropped items.
		server.runCommand("summon minecraft:item 6 ~ -6 {Item:{id:\"minecraft:emerald\",count:5}}");
		context.waitTicks(10);
		server.runCommand("clear @a minecraft:emerald");
		command(context, server, self, "ability", DroidType.MOUSE);
		context.waitTicks(20);
		check(server.computeOnServer(s -> me(s, self).getInventory().countItem(Items.EMERALD)) == 5, "the mouse droid brings the dropped emeralds");

		// The probe droid finds diamond ore under the player.
		var feet = server.computeOnServer(s -> me(s, self).blockPosition());
		server.runCommand(String.format("setblock %d %d %d minecraft:diamond_ore", feet.getX() + 2, feet.getY() - 2, feet.getZ() + 2));
		var probe = command(context, server, self, "ability", DroidType.PROBE);
		check(probe.contains("diamond") || probe.contains("probe.found"), "the probe droid finds the diamond ore: " + probe);

		// The B1 shoots at a husk in sight.
		var b1 = server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.B1).position());
		server.runCommand(String.format("summon minecraft:husk %.2f %.2f %.2f {NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:20f}", b1.x, b1.y, b1.z - 7.0));
		context.waitTicks(5);
		command(context, server, self, "ability", DroidType.B1);
		context.waitTicks(30);
		check(server.computeOnServer(s -> husks(s).isEmpty() || husks(s).getFirst().getHealth() < 20.0F), "the B1's bolts hit the husk");
		server.runCommand("kill @e[type=husk]");

		// K-2SO shoves a husk next to the player away.
		var me = server.computeOnServer(s -> me(s, self).position());
		server.runCommand(String.format("summon minecraft:husk %.2f %.2f %.2f {PersistenceRequired:1b,Silent:1b,Health:20f}", me.x + 2.0, me.y, me.z));
		context.waitTicks(5);
		command(context, server, self, "ability", DroidType.K2SO);
		context.waitTicks(10);
		check(server.computeOnServer(s -> husks(s).getFirst().distanceTo(me(s, self))) > 3.0, "K-2SO shoves the husk away");
		server.runCommand("kill @e[type=husk]");
		check(server.computeOnServer(s -> {
			var progress = SaberAdvancements.progress(me(s, self), SaberAdvancements.LOYAL_COMPANION);
			return progress.isDone();
		}), "every droid's ability once: Loyal Companions");
	}

	/** After closing and opening the world the same droids come back, once each. */
	private static void reopen(ClientGameTestContext context, TestWorldSave save, UUID self) {
		try (TestSingleplayerContext singleplayer = save.open()) {
			context.waitTicks(80);
			TestServerContext server = singleplayer.getServer();

			for (DroidType type : List.of(DroidType.MOUSE, DroidType.PROBE, DroidType.B1, DroidType.K2SO)) {
				check(server.computeOnServer(s -> inWorld(s, type)) == 1, type.displayName() + " is back once after opening the world");
			}

			check(server.computeOnServer(s -> allPigs(s)) == 4, "no droid was saved as a pig, none is there twice");
			context.waitFor(client -> clientDroids(client.level) == 4, 40);
			check(context.computeOnClient(client -> DroidClient.count()) == 4, "the menu knows the four droids again");
		}
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("time set day");
				context.waitFor(client -> DroidClient.status() != null, 40);
				context.runOnClient(client -> DroidClient.command("spawn", DroidType.BB8));
				context.waitFor(client -> clientDroids(client.level) == 1, 60);
				check(context.computeOnClient(client -> clientDroids(client.level)) == 1, "dedicated server: the client sees its BB-8");
				context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				context.waitTicks(20);
				context.takeScreenshot("droid_dedicated_server");
				context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			}

			context.waitTicks(20);
			check(server.computeOnServer(s -> Droids.liveCount() == 0), "dedicated server: the droid leaves with its player");
		}
	}

	// Helpers.

	/** Clicks the card and the ability button of the droid menu, and waits for the answer. */
	private static void ability(ClientGameTestContext context, DroidType type) {
		if (!context.computeOnClient(client -> client.gui.screen() instanceof DroidMenuScreen)) {
			context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.DROIDS));
			context.waitTicks(5);
		}

		context.clickScreenButton(type.translationKey());
		context.waitTicks(3);
		int before = context.computeOnClient(client -> DroidClient.eventsSeen());
		context.clickScreenButton("sabercraft.droid.menu.ability");
		context.waitFor(client -> DroidClient.eventsSeen() > before, 20);
		check(context.computeOnClient(client -> DroidClient.eventsSeen()) > before, type.displayName() + " uses its ability");
		context.waitTicks(5);
	}

	/** Runs a droid command as the menu would, on the server, and returns the message key or text. */
	private static String command(ClientGameTestContext context, TestServerContext server, UUID self, String action, DroidType type) {
		String message = server.computeOnServer(s -> {
			Droids.resetCooldowns();
			var component = Droids.command(me(s, self), action, type.id());
			return component == null ? "" : component.getContents().toString();
		});
		context.waitTicks(5);
		return message;
	}

	/** Puts the droids in a row three blocks in front of the player (who looks north), facing them. */
	private static void lineUp(TestServerContext server, UUID self, List<DroidType> types) {
		lineUp(server, self, types, -3.5);
	}

	/** Puts the droids in a row {@code dz} blocks north (negative) or south of the player, waiting. */
	private static void lineUp(TestServerContext server, UUID self, List<DroidType> types, double dz) {
		server.computeOnServer(s -> {
			ServerPlayer player = me(s, self);
			double x = player.getX() - (types.size() - 1) * 0.75;

			for (DroidType type : types) {
				DroidEntity droid = Droids.droid(player, type);
				droid.setStaying(true);
				droid.snapTo(x, player.getY(), player.getZ() + dz, 0.0F, 0.0F);
				droid.setYHeadRot(0.0F);
				droid.setYBodyRot(0.0F);
				x += 1.5;
			}

			return null;
		});
	}

	private static void setStaying(TestServerContext server, UUID self, boolean staying) {
		server.computeOnServer(s -> {
			for (DroidType type : DroidType.values()) {
				DroidEntity droid = Droids.droid(me(s, self), type);

				if (droid != null) {
					droid.setStaying(staying);
				}
			}

			return null;
		});
	}

	private static boolean allNear(MinecraftServer server, UUID self, double distance) {
		ServerPlayer player = me(server, self);

		for (DroidType type : DroidType.values()) {
			DroidEntity droid = Droids.droid(player, type);

			if (droid != null && droid.distanceTo(player) > distance) {
				SaberCraft.LOGGER.info("{} is {} blocks away", type.displayName(), droid.distanceTo(player));
				return false;
			}
		}

		return true;
	}

	private static float bb8Walk(Minecraft client) {
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Pig pig && DroidMarker.of(pig) == DroidType.BB8) {
				return pig.walkAnimation.speed();
			}
		}

		return 0.0F;
	}

	private static List<Zombie> husks(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(Zombie.class, AREA, Entity::isAlive);
	}

	private static int inWorld(MinecraftServer server, DroidType type) {
		return server.overworld().getEntitiesOfClass(Pig.class, AREA, pig -> pig.isAlive() && DroidMarker.of(pig) == type).size();
	}

	private static int allPigs(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(Pig.class, AREA, Entity::isAlive).size();
	}

	private static int clientDroids(Level level) {
		return level.getEntitiesOfClass(Pig.class, AREA, pig -> DroidMarker.of(pig) != null).size();
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	/** Waits until the condition holds on the client, at most {@code ticks}; fails with the message. */
	private static void until(ClientGameTestContext context, java.util.function.Predicate<Minecraft> condition, int ticks, String message) {
		for (int i = 0; i < ticks && !context.computeOnClient(condition::test); i++) {
			context.waitTick();
		}

		check(context.computeOnClient(condition::test), message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
