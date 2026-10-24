package com.phiro.sabercraft.command;

import java.util.List;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.item.ItemConversion;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * The commands of Phase 10, all for the item in the player's main hand:
 *
 * <ul>
 * <li>{@code /sabercraft convert [color] [hilt]}: a sword (wood to netherite) becomes a saber. The
 * hilt fits its material unless one is given; on a saber the command changes color or hilt.</li>
 * <li>{@code /sabercraft revert}: a saber becomes the plain sword again, a blaster the plain bow.</li>
 * <li>{@code /sabercraft blaster [type]}: a bow becomes a blaster (E-11 unless a type is given);
 * on a blaster the command changes the type. {@code /sabercraft blaster revert} turns it back.</li>
 * </ul>
 *
 * <p>The item stays the same stack (see {@link ItemConversion}). Whether every player may use the
 * commands or only operators is set in {@code abilities.json} ({@code commands_for_everyone}).
 * Messages are translated on clients with SaberCraft and shown in English otherwise.
 */
public final class SaberCraftCommands {
	private static final SuggestionProvider<CommandSourceStack> COLORS = (context, builder) ->
			SharedSuggestionProvider.suggest(paths(SaberVariant.COLORS), builder);
	private static final SuggestionProvider<CommandSourceStack> HILTS = (context, builder) ->
			SharedSuggestionProvider.suggest(paths(SaberVariant.HILTS), builder);
	private static final SuggestionProvider<CommandSourceStack> BLASTERS = (context, builder) ->
			SharedSuggestionProvider.suggest(BlasterType.ALL.stream().map(type -> type.id().getPath()), builder);

	private SaberCraftCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher));
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		Predicate<CommandSourceStack> operator = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		Predicate<CommandSourceStack> allowed = source -> AbilityConfig.get().commandsForEveryone || operator.test(source);
		dispatcher.register(Commands.literal("sabercraft")
				.then(Commands.literal("convert").requires(allowed)
						.executes(context -> convert(context, null, null))
						.then(Commands.argument("color", StringArgumentType.word()).suggests(COLORS)
								.executes(context -> convert(context, StringArgumentType.getString(context, "color"), null))
								.then(Commands.argument("hilt", StringArgumentType.word()).suggests(HILTS)
										.executes(context -> convert(context, StringArgumentType.getString(context, "color"),
												StringArgumentType.getString(context, "hilt"))))))
				.then(Commands.literal("revert").requires(allowed)
						.executes(SaberCraftCommands::revert))
				.then(Commands.literal("blaster").requires(allowed)
						.executes(context -> blaster(context, null))
						.then(Commands.literal("revert").executes(SaberCraftCommands::revert))
						.then(Commands.argument("type", StringArgumentType.word()).suggests(BLASTERS)
								.executes(context -> blaster(context, StringArgumentType.getString(context, "type"))))));
	}

	private static int convert(CommandContext<CommandSourceStack> context, String color, String hilt) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		Identifier colorId = color == null ? null : SaberCraft.id(color);
		Identifier hiltId = hilt == null ? null : SaberCraft.id(hilt);

		if (colorId != null && !SaberVariant.COLORS.contains(colorId)) {
			return fail(context, "sabercraft.command.unknown_color", "Unknown blade color %s. Possible: %s", color, String.join(", ", paths(SaberVariant.COLORS)));
		}

		if (hiltId != null && !SaberVariant.HILTS.contains(hiltId)) {
			return fail(context, "sabercraft.command.unknown_hilt", "Unknown hilt %s. Possible: %s", hilt, String.join(", ", paths(SaberVariant.HILTS)));
		}

		return switch (ItemConversion.toSaber(stack, colorId, hiltId)) {
			case DONE -> ok(context, "sabercraft.command.converted", "%s is now a lightsaber. Change hilt, color and style with V.", stack.getHoverName());
			case CHANGED -> ok(context, "sabercraft.command.changed", "The lightsaber got a new hilt or color.");
			case ALREADY -> fail(context, "sabercraft.command.already_saber", "This already is a lightsaber. Give a color (and hilt) to change it, or use /sabercraft revert.");
			case WRONG_ITEM -> fail(context, "sabercraft.command.not_a_sword", "Hold a sword in your main hand: wooden, stone, copper, iron, golden, diamond or netherite.");
			default -> fail(context, "sabercraft.command.not_possible", "That does not work with this item.");
		};
	}

	private static int revert(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);

		if (BlasterType.of(stack) != null) {
			ItemConversion.fromBlaster(stack);
			return ok(context, "sabercraft.command.reverted_bow", "The blaster is a normal bow again.");
		}

		return switch (ItemConversion.fromSaber(stack)) {
			case DONE -> ok(context, "sabercraft.command.reverted_sword", "%s is a normal sword again.", stack.getHoverName());
			case NOT_CONVERTED -> fail(context, "sabercraft.command.not_converted", "This item is not a lightsaber or blaster.");
			default -> fail(context, "sabercraft.command.nothing_to_revert", "Hold a lightsaber or blaster in your main hand.");
		};
	}

	private static int blaster(CommandContext<CommandSourceStack> context, String type) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		BlasterType chosen = type == null ? null : BlasterType.byId(SaberCraft.id(type)).orElse(null);

		if (type != null && chosen == null) {
			return fail(context, "sabercraft.command.unknown_blaster", "Unknown blaster %s. Possible: %s", type,
					String.join(", ", BlasterType.ALL.stream().map(t -> t.id().getPath()).toList()));
		}

		return switch (ItemConversion.toBlaster(stack, chosen)) {
			case DONE -> ok(context, "sabercraft.command.blaster", "The bow is now a blaster: %s. Right-click fires.",
					Component.translatableWithFallback((chosen != null ? chosen : BlasterType.E11).translationKey(), (chosen != null ? chosen : BlasterType.E11).id().getPath()));
			case CHANGED -> ok(context, "sabercraft.command.blaster_changed", "The blaster is now a %s.",
					Component.translatableWithFallback(chosen.translationKey(), chosen.id().getPath()));
			case ALREADY -> fail(context, "sabercraft.command.already_blaster", "This already is a blaster. Give a type to change it, or use /sabercraft blaster revert.");
			default -> fail(context, "sabercraft.command.not_a_bow", "Hold a bow in your main hand.");
		};
	}

	private static int ok(CommandContext<CommandSourceStack> context, String key, String fallback, Object... args) {
		context.getSource().sendSuccess(() -> Component.translatableWithFallback(key, fallback, args), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> context, String key, String fallback, Object... args) {
		context.getSource().sendFailure(Component.translatableWithFallback(key, fallback, args));
		return 0;
	}

	private static List<String> paths(List<Identifier> ids) {
		return ids.stream().map(Identifier::getPath).toList();
	}
}
