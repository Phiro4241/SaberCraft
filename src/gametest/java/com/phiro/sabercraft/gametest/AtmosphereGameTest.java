package com.phiro.sabercraft.gametest;

import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.blaster.BlasterClient;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;

/**
 * Phase 11 G: drawing and holstering blasters, and the menu transitions.
 *
 * <p>Through the real hotbar keys: taking a blaster into the hand draws it (once, with the
 * first-person swing up into aim that ends within about half a second), switching from one blaster to
 * another draws the new one without holstering, putting it away holsters it, and with the option
 * off nothing is drawn or heard. The SaberCraft menu fades each page in and is done within a
 * second; with the option off a page is there at once. Screenshots: {@code atmosphere_*}.
 */
public class AtmosphereGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			blasters(context, server, self);
			menu(context);
		}
	}

	/** A stick in slot 1, an E-11 in slot 2 and a DL-44 in slot 3. */
	private static void blasters(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayer(self);
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.STICK));
			player.getInventory().setItem(1, new ItemStack(Items.BOW));
			player.getInventory().setItem(2, new ItemStack(Items.BOW));
		});
		convert(context, server, self, 1, "e11");
		convert(context, server, self, 2, "dl44");
		select(context, 0);
		context.waitTicks(10);
		check(context.computeOnClient(client -> BlasterType.of(client.player.getInventory().getItem(1))) == BlasterType.E11, "slot 2 holds an E-11");
		check(context.computeOnClient(client -> BlasterType.of(client.player.getInventory().getItem(2))) == BlasterType.DL44, "slot 3 holds a DL-44");

		int draws = BlasterClient.draws();
		int holsters = BlasterClient.holsters();
		select(context, 1);
		context.waitTicks(6);
		check(BlasterClient.draws() == draws + 1, "taking the E-11 draws it once");
		check(context.computeOnClient(client -> BlasterClient.drawing(InteractionHand.MAIN_HAND)) > 0.0F, "drawn, it is still swinging up");
		// Vanilla's own swap has lifted it by now: the picture shows it turning up into aim.
		context.takeScreenshot("atmosphere_draw");
		context.waitTicks(12);
		check(context.computeOnClient(client -> BlasterClient.drawing(InteractionHand.MAIN_HAND)) == 0.0F, "within half a second it is in aim");
		context.takeScreenshot("atmosphere_aim");

		// The swing held at three points, for pictures.
		for (float point : new float[] {1.0F, 0.6F, 0.3F}) {
			context.runOnClient(client -> BlasterClient.holdDrawForTests(point));
			context.waitTicks(2);
			context.takeScreenshot("atmosphere_draw_" + Math.round(point * 100));
		}

		context.runOnClient(client -> BlasterClient.holdDrawForTests(-1.0F));

		select(context, 2);
		context.waitTicks(2);
		check(BlasterClient.draws() == draws + 2 && BlasterClient.holsters() == holsters, "blaster to blaster draws the new one, no holster");

		select(context, 0);
		context.waitTicks(2);
		check(BlasterClient.holsters() == holsters + 1 && BlasterClient.draws() == draws + 2, "putting it away holsters it");

		context.runOnClient(client -> ImmersionConfig.get().blasterDraw = false);
		select(context, 1);
		context.waitTicks(2);
		check(BlasterClient.draws() == draws + 2, "with the option off nothing is drawn");
		check(context.computeOnClient(client -> BlasterClient.drawing(InteractionHand.MAIN_HAND)) == 0.0F, "with the option off the blaster is in aim at once");
		select(context, 0);
		context.waitTicks(2);
		check(BlasterClient.holsters() == holsters + 1, "with the option off nothing is holstered");
		context.runOnClient(client -> ImmersionConfig.get().blasterDraw = true);
	}

	/** Converts the bow in a slot through the command, as a player would with it in hand. */
	private static void convert(ClientGameTestContext context, TestServerContext server, UUID self, int slot, String type) {
		select(context, slot);
		server.runOnServer(s -> s.getPlayerList().getPlayer(self).getInventory().setSelectedSlot(slot));
		context.waitTicks(2);
		server.runCommand("execute as @p run sabercraft blaster " + type);
		context.waitTicks(5);
	}

	private static void select(ClientGameTestContext context, int slot) {
		context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
	}

	private static void menu(ClientGameTestContext context) {
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SHIPS));
		check(context.computeOnClient(client -> page(client).transition()) < 1.0F, "a new page starts faded out");
		context.takeScreenshot("atmosphere_menu_transition");
		until(context, client -> page(client).transition() >= 1.0F, 20, "and is fully there within a second");

		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.GRAPHICS));
		check(context.computeOnClient(client -> page(client).transition()) < 1.0F, "switching the area fades the next page in");
		until(context, client -> page(client).transition() >= 1.0F, 20, "the graphics page is there");
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(2);
		context.takeScreenshot("atmosphere_menu_graphics");

		context.runOnClient(client -> {
			ImmersionConfig.get().menuTransitions = false;
			SaberMenuScreen.open(client, SaberMenuScreen.Tab.MUSIC);
		});
		check(context.computeOnClient(client -> page(client).transition()) >= 1.0F, "with transitions off a page is there at once");
		context.runOnClient(client -> {
			ImmersionConfig.get().menuTransitions = true;
			client.gui.setScreen(null);
		});
	}

	private static SaberMenuScreen page(Minecraft client) {
		return (SaberMenuScreen) client.gui.screen();
	}

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
