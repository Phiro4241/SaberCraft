package com.phiro.sabercraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Starts a test world, hands the player a netherite sword and checks that the blade renderer is
 * active. Then it takes screenshots of the blade in first person and both third person views, by
 * day and by night. The screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public class BladeScreenshotGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// Small render distance keeps the test fast on software renderers.
		context.runOnClient(client -> client.options.renderDistance().set(4));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			// Chunk rendering never reports "done" on some software renderers, so give it a fixed time.
			context.waitTicks(200);

			singleplayer.getServer().runCommand("gamerule advance_time false");
			singleplayer.getServer().runCommand("gamerule spawn_mobs false");
			singleplayer.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
			singleplayer.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:netherite_sword");
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.getInput().lookAt(20.0F, 10.0F);

			assertBladeModel(context);

			for (String time : new String[] {"day", "night"}) {
				singleplayer.getServer().runCommand("time set " + time);
				context.waitTicks(10);

				screenshot(context, CameraType.FIRST_PERSON, "blade_first_person_" + time);
				screenshot(context, CameraType.THIRD_PERSON_BACK, "blade_third_person_back_" + time);
				screenshot(context, CameraType.THIRD_PERSON_FRONT, "blade_third_person_front_" + time);
			}

			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}

	/**
	 * Compares the model size with a vanilla iron sword, which fills the whole item texture. With
	 * the blade renderer active the held netherite sword is much longer, while its inventory icon
	 * stays a plain hilt.
	 */
	private static void assertBladeModel(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ItemStack saber = new ItemStack(Items.NETHERITE_SWORD);
			ItemStack reference = new ItemStack(Items.IRON_SWORD);

			for (ItemDisplayContext held : new ItemDisplayContext[] {
					ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
					ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
					ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
					ItemDisplayContext.THIRD_PERSON_LEFT_HAND
			}) {
				double saberSize = size(bounds(client, saber, held));
				double referenceSize = size(bounds(client, reference, held));
				com.phiro.sabercraft.SaberCraft.LOGGER.info("{}: saber {} vs iron sword {}", held, saberSize, referenceSize);

				if (saberSize < referenceSize * 1.3) {
					throw new AssertionError("Blade renderer is not active in " + held);
				}
			}

			double iconSize = size(bounds(client, saber, ItemDisplayContext.GUI));
			double referenceIcon = size(bounds(client, reference, ItemDisplayContext.GUI));

			if (iconSize > referenceIcon * 1.05) {
				throw new AssertionError("Inventory icon should not contain the blade: " + iconSize + " vs " + referenceIcon);
			}
		});
	}

	private static AABB bounds(net.minecraft.client.Minecraft client, ItemStack stack, ItemDisplayContext displayContext) {
		ItemStackRenderState state = new ItemStackRenderState();
		client.getItemModelResolver().updateForTopItem(state, stack, displayContext, client.level, client.player, 0);
		return state.getModelBoundingBox();
	}

	private static double size(AABB box) {
		return Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
	}

	private static void screenshot(ClientGameTestContext context, CameraType camera, String name) {
		context.runOnClient(client -> client.options.setCameraType(camera));
		context.waitTicks(5);
		context.takeScreenshot(name);
	}
}
