package com.phiro.sabercraft.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.character.CharacterConfig;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.client.select.SaberSelection;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * The character: your own skin, an original outfit over it, or an outfit file of your own, with
 * a preview of your character. Built-in outfits are shown to other players with the mod; your own
 * files only to you. Each outfit suggests a saber, which can be taken over for the sword in hand.
 */
public class CharacterMenuScreen extends SaberMenuScreen {
	private static final int LIST_WIDTH = 120;
	private final List<Choice> choices = new ArrayList<>();
	private Component status = Component.empty();
	private Button suggestButton;

	private record Choice(String value, AbstractWidget button) {
	}

	public CharacterMenuScreen() {
		super(Component.translatable("sabercraft.menu.character.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.CHARACTER;
	}

	@Override
	protected void initPanel() {
		this.choices.clear();
		int y = this.top + 24;
		this.addChoice("", Component.translatable("sabercraft.outfit.none"), Component.translatable("sabercraft.outfit.none.description"), y);

		for (Outfit outfit : Outfit.ALL) {
			y += 22;
			this.addChoice(outfit.id().toString(), Component.translatable(outfit.translationKey()),
					Component.translatable(outfit.translationKey() + ".description"), y);
		}

		// Files of your own: one button that steps through them.
		List<String> files = OutfitClient.ownFiles();
		y += 22;

		if (files.isEmpty()) {
			Button none = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.outfit.file.none"), b -> {})
					.bounds(this.left + 10, y, LIST_WIDTH, 20)
					.tooltip(Tooltip.create(Component.translatable("sabercraft.outfit.file.description")))
					.build());
			none.active = false;
		} else {
			String current = CharacterConfig.get().outfit;
			String start = current.startsWith(OutfitClient.FILE_PREFIX) ? current.substring(OutfitClient.FILE_PREFIX.length()) : files.getFirst();
			CycleButton<String> fileButton = this.addRenderableWidget(CycleButton.builder((String name) -> Component.literal(name), files.contains(start) ? start : files.getFirst())
					.withValues(files)
					.withTooltip(v -> Tooltip.create(Component.translatable("sabercraft.outfit.file.description")))
					.displayOnlyValue()
					.create(this.left + 10, y, LIST_WIDTH, 20, Component.translatable("sabercraft.outfit.file"),
							(button, name) -> OutfitClient.choose(OutfitClient.FILE_PREFIX + name)));
			this.choices.add(new Choice(OutfitClient.FILE_PREFIX, fileButton));
		}

		int right = this.left + LIST_WIDTH + 20;
		int rightWidth = PANEL_WIDTH - LIST_WIDTH - 30;
		this.suggestButton = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.menu.character.take_saber"), b -> this.takeSaber())
				.bounds(right, this.top + 168, rightWidth, 20)
				.tooltip(Tooltip.create(Component.translatable("sabercraft.menu.character.take_saber.description")))
				.build());

		int bottom = this.top + PANEL_HEIGHT - 28;
		this.addRenderableWidget(CycleButton.onOffBuilder(CharacterConfig.get().showOtherOutfits)
				.withTooltip(v -> Tooltip.create(Component.translatable("sabercraft.option.show_other_outfits.description")))
				.create(this.left + 10, bottom, 150, 20, Component.translatable("sabercraft.option.show_other_outfits"), (button, v) -> {
					CharacterConfig.get().showOtherOutfits = v;
					CharacterConfig.save();
				}));
		this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.menu.character.folder"), b -> Util.getPlatform().openPath(OutfitClient.folder()))
				.bounds(this.left + 166, bottom, 76, 20)
				.build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.bounds(this.left + 246, bottom, 74, 20)
				.build());
	}

	private void addChoice(String value, Component name, Component description, int y) {
		Button button = this.addRenderableWidget(Button.builder(name, b -> {
			OutfitClient.choose(value);
			this.status = Component.empty();
		}).bounds(this.left + 10, y, LIST_WIDTH, 20).tooltip(Tooltip.create(description)).build());
		this.choices.add(new Choice(value, button));
	}

	private Optional<Outfit> chosen() {
		return OutfitClient.builtIn(CharacterConfig.get().outfit);
	}

	private void takeSaber() {
		Optional<Outfit> outfit = this.chosen();
		InteractionHand hand = this.minecraft.player == null ? null : SaberSelection.saberHand(this.minecraft.player);

		if (outfit.isEmpty() || hand == null) {
			this.status = Component.translatable("sabercraft.customize.no_saber");
			return;
		}

		this.status = switch (SaberSelection.apply(this.minecraft, hand, outfit.get().suggested())) {
			case SENT, CREATIVE -> Component.translatable("sabercraft.menu.character.saber_taken");
			case UNSUPPORTED -> Component.translatable("sabercraft.customize.unsupported");
			case NO_SABER -> Component.translatable("sabercraft.customize.no_saber");
		};
	}

	@Override
	public void tick() {
		Optional<Outfit> outfit = this.chosen();
		this.suggestButton.active = outfit.isPresent() && this.minecraft.player != null && SaberSelection.saberHand(this.minecraft.player) != null;
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		String current = CharacterConfig.get().outfit;

		for (Choice choice : this.choices) {
			boolean selected = choice.value().equals(OutfitClient.FILE_PREFIX) ? current.startsWith(OutfitClient.FILE_PREFIX) : choice.value().equals(current);

			if (selected) {
				AbstractWidget button = choice.button();
				graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, 0xFFFFFFFF);
			}
		}

		// Your character, turning towards the mouse like in the inventory.
		int right = this.left + LIST_WIDTH + 20;
		int rightWidth = PANEL_WIDTH - LIST_WIDTH - 30;
		int previewTop = this.top + 22;
		int previewBottom = this.top + 130;
		graphics.fill(right, previewTop, right + rightWidth, previewBottom, 0x60000000);

		if (this.minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, right, previewTop, right + rightWidth, previewBottom, 46, 0.0625F,
					mouseX, mouseY, this.minecraft.player);
		}

		// What the outfit suggests.
		Optional<Outfit> outfit = this.chosen();
		Component name = outfit.<Component>map(o -> Component.translatable(o.translationKey()))
				.orElse(current.startsWith(OutfitClient.FILE_PREFIX) ? Component.literal(current.substring(OutfitClient.FILE_PREFIX.length()))
						: Component.translatable("sabercraft.outfit.none"));
		graphics.centeredText(this.font, name, right + rightWidth / 2, previewBottom + 6, 0xFFFFFFFF);

		if (outfit.isPresent()) {
			SaberVariant saber = outfit.get().suggested();
			Component line = SaberHilts.get(saber.hilt()).displayName().copy()
					.append(" · ").append(Component.translatable("sabercraft.color." + saber.color().getPath()))
					.append(" · ").append(Component.translatable("sabercraft.style." + saber.style().getPath()));
			graphics.textWithWordWrap(this.font, Component.translatable("sabercraft.menu.character.suggests", line), right, previewBottom + 18,
					rightWidth, 0xFFB0B0B8);
		} else {
			graphics.textWithWordWrap(this.font, Component.translatable(current.startsWith(OutfitClient.FILE_PREFIX)
					? "sabercraft.outfit.file.description" : "sabercraft.outfit.none.description"), right, previewBottom + 18, rightWidth, 0xFFB0B0B8);
		}

		if (!this.status.getString().isEmpty()) {
			graphics.textWithWordWrap(this.font, this.status, right, this.top + 192, rightWidth, 0xFFFFD060);
		}
	}
}
