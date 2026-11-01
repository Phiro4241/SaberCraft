package com.phiro.sabercraft.saber;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.item.ItemConversion;
import com.phiro.sabercraft.network.ConvertSaberPayload;

/**
 * Phase 12: which swords are lightsabers, on the server.
 *
 * <p>Only swords that were chosen are sabers (see {@link SaberVariant#canHold}). A player chooses
 * one with the button in the saber menu or with {@code /sabercraft convert}; both end in
 * {@link ItemConversion}, which also checks {@code saber_swords}. With the server option
 * {@code netherite_swords_are_sabers} the old behavior comes back: a netherite sword a player
 * holds that was never chosen nor turned back becomes a standard red saber by itself.
 */
public final class SaberSwords {
	private SaberSwords() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(ConvertSaberPayload.TYPE, (payload, context) -> {
			Component message = convert(context.player(), payload.hand(), payload.toSaber());

			if (message != null) {
				context.player().sendOverlayMessage(message);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0 && AbilityConfig.get().netheriteSwordsAreSabers) {
				markNetherite(server);
			}
		});
	}

	/** {@code netherite_swords_are_sabers}: unmarked netherite swords in the hands become sabers. */
	private static void markNetherite(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = player.getItemInHand(hand);

				if (stack.is(Items.NETHERITE_SWORD) && SaberVariant.isUnmarked(stack)) {
					ItemConversion.toSaber(stack, SaberVariant.RED, SaberVariant.STANDARD);
				}
			}
		}
	}

	/** Whether this player may choose and un-choose sabers: everyone, or only operators. */
	public static boolean mayConvert(ServerPlayer player) {
		return AbilityConfig.get().commandsForEveryone || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
	}

	/** The menu buttons: makes a saber of the sword in that hand, or a plain sword again. */
	public static @Nullable Component convert(ServerPlayer player, InteractionHand hand, boolean toSaber) {
		if (!player.isAlive() || player.isSpectator()) {
			return null;
		}

		if (!mayConvert(player)) {
			return Component.translatableWithFallback("sabercraft.saber.convert.no_permission", "Only operators can choose lightsabers on this server.");
		}

		ItemStack stack = player.getItemInHand(hand);

		if (toSaber) {
			return switch (ItemConversion.toSaber(stack, null, null)) {
				case DONE -> Component.translatableWithFallback("sabercraft.command.converted", "%s is now a lightsaber. Change hilt, color and style with V.",
						stack.getHoverName());
				case ALREADY -> Component.translatableWithFallback("sabercraft.saber.convert.already", "This sword already is a lightsaber.");
				case NOT_ALLOWED -> Component.translatableWithFallback("sabercraft.saber.convert.not_allowed", "This kind of sword cannot become a lightsaber on this server.");
				default -> Component.translatableWithFallback("sabercraft.command.not_a_sword",
						"Hold a sword in your main hand: wooden, stone, copper, iron, golden, diamond or netherite.");
			};
		}

		return switch (ItemConversion.fromSaber(stack)) {
			case DONE -> Component.translatableWithFallback("sabercraft.command.reverted_sword", "%s is a normal sword again.", stack.getHoverName());
			case NOT_CONVERTED -> Component.translatableWithFallback("sabercraft.command.not_converted", "This item is not a lightsaber or blaster.");
			default -> Component.translatableWithFallback("sabercraft.command.nothing_to_revert", "Hold a lightsaber or blaster in your main hand.");
		};
	}
}
