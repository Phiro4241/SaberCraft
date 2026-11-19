package com.phiro.sabercraft.client.story;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryDialogPayload;

/**
 * Phase 19: a story dialog. At the bottom of the screen a panel with the face and name of who
 * speaks and their words, which appear letter by letter (as fast as the menu says, or at once),
 * with a soft sound; over the comlink the face is blue and flickers. Below come the answers, to
 * click or to choose with 1 to 4 (or the arrow keys and Enter). A line without answers goes on
 * with Space, Enter, a click or Escape; a first press only shows the whole line. The game goes
 * on behind it (nobody watches the player while they talk, though).
 *
 * <p>The server leads: it sends each line and closes the dialog; this only shows the line and
 * sends the choice back.
 */
public final class DialogScreen extends Screen {
	private static final int PORTRAIT = 56;
	private static final int PAD = 10;
	private static final int ANSWER_HEIGHT = 13;
	/** Letters per tick at the settings normal (1) and slow (2); 0 shows a line at once. */
	private static final float[] RATE = {Float.MAX_VALUE, 2.25F, 1.25F};

	private StoryDialogPayload line;
	/** The words of the line (in the SaberCraft language), how far they are shown, a pause after a sentence. */
	private String words = "";
	/** The language the words were made in (made again when the SaberCraft language changes). */
	private net.minecraft.locale.@org.jspecify.annotations.Nullable Language madeIn;
	private float shown;
	private int pause;
	private int lastBlip;
	/** The answer the keys point at, and whether the choice was sent (waiting for the next line). */
	private int selected;
	private boolean sent;
	private int sentAge;
	private int age;
	/** Where the answers are on screen (for the mouse). */
	private final List<int[]> answerBoxes = new ArrayList<>();
	private static int shownLines;

	private DialogScreen(StoryDialogPayload line) {
		super(Component.empty());
		this.line = line;
		this.start();
	}

	/** Shows a line: in the open dialog, or opens it. */
	static void open(Minecraft client, StoryDialogPayload payload) {
		shownLines++;

		if (client.gui.screen() instanceof DialogScreen screen) {
			screen.line = payload;
			screen.start();
			return;
		}

		client.gui.setScreen(new DialogScreen(payload));
	}

	private void start() {
		this.words = Component.translatable(this.line.text()).getString();
		this.madeIn = net.minecraft.locale.Language.getInstance();
		int speed = Mth.clamp(ImmersionConfig.get().storyTextSpeed, 0, 2);
		this.shown = speed == 0 ? this.words.length() : 0.0F;
		this.pause = 0;
		this.lastBlip = 0;
		this.selected = 0;
		this.sent = false;
		this.sentAge = 0;
	}

	private boolean typing() {
		return this.shown < this.words.length();
	}

	@Override
	public void tick() {
		this.age++;

		if (this.madeIn != net.minecraft.locale.Language.getInstance()) {
			// The language changed (the menu, or Minecraft's): the same line in the new words.
			boolean whole = !this.typing();
			this.words = Component.translatable(this.line.text()).getString();
			this.madeIn = net.minecraft.locale.Language.getInstance();
			this.shown = whole ? this.words.length() : Math.min(this.shown, this.words.length());
		}

		if (this.sent) {
			this.sentAge++;

			// Phase 27: the server always answers now; should a reply be lost, the screen closes by
			// itself after two seconds instead of holding the player (it used to wait for Escape).
			if (this.sentAge > 40) {
				this.onClose();
				return;
			}
		}

		if (!this.typing()) {
			return;
		}

		if (this.pause > 0) {
			this.pause--;
			return;
		}

		int before = (int) this.shown;
		this.shown = Math.min(this.words.length(), this.shown + RATE[Mth.clamp(ImmersionConfig.get().storyTextSpeed, 0, 2)]);
		int now = (int) this.shown;

		// A short rest after a sentence, a shorter one after a comma.
		for (int i = before; i < now && i < this.words.length(); i++) {
			char c = this.words.charAt(i);

			if ((c == '.' || c == '!' || c == '?' || c == '…') && i + 1 < this.words.length() && this.words.charAt(i + 1) == ' ') {
				this.shown = i + 1;
				this.pause = 5;
				break;
			}

			if ((c == ',' || c == ';' || c == '–' || c == '—') && i + 1 < this.words.length()) {
				this.shown = i + 1;
				this.pause = 2;
				break;
			}
		}

		// A soft sound every few letters.
		if ((int) this.shown - this.lastBlip >= 3 && this.minecraft != null) {
			this.lastBlip = (int) this.shown;
			StoryClient.sound(this.minecraft, "story.blip", this.line.radio() ? 0.10F : 0.14F, this.pitch(), null);
		}
	}

	/** Each voice its own: droids high, the masked one low. */
	private float pitch() {
		float pitch = switch (this.line.speaker()) {
			case "m31" -> 1.8F;
			case "r2v7", "terminal" -> 1.5F;
			case "shadow" -> 0.6F;
			case "rorrak" -> 0.7F;
			case "trooper", "guard", "pilot_a", "pilot_b", "vorr" -> 0.85F;
			case "mira", "kestrel", "ilyra" -> 1.15F;
			default -> 1.0F;
		};
		return pitch * (0.97F + (this.lastBlip * 7 % 5) * 0.015F);
	}

	// --- Input ---------------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.sent) {
			// The server has not answered: Escape lets one out after a while.
			if (event.isEscape() && this.sentAge > 40) {
				this.onClose();
			}

			return true;
		}

		List<String> answers = this.line.answers();

		if (this.typing() && (event.isSelection() || event.isEscape() || event.getDigit() >= 1)) {
			this.shown = this.words.length();
			return true;
		}

		if (answers.isEmpty()) {
			if (event.isSelection() || event.isEscape()) {
				this.choose(-1);
			}

			return true;
		}

		int digit = event.getDigit();

		if (digit >= 1 && digit <= answers.size()) {
			this.choose(digit - 1);
		} else if (event.isUp()) {
			this.selected = (this.selected + answers.size() - 1) % answers.size();
		} else if (event.isDown() || event.isCycleFocus()) {
			this.selected = (this.selected + 1) % answers.size();
		} else if (event.isConfirmation()) {
			this.choose(this.selected);
		}

		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.sent || event.button() != 0) {
			return true;
		}

		if (this.typing()) {
			this.shown = this.words.length();
			return true;
		}

		if (this.line.answers().isEmpty()) {
			this.choose(-1);
			return true;
		}

		for (int i = 0; i < this.answerBoxes.size(); i++) {
			int[] box = this.answerBoxes.get(i);

			if (event.x() >= box[0] && event.x() < box[2] && event.y() >= box[1] && event.y() < box[3]) {
				this.choose(i);
				return true;
			}
		}

		return true;
	}

	@Override
	public void mouseMoved(double x, double y) {
		for (int i = 0; i < this.answerBoxes.size(); i++) {
			int[] box = this.answerBoxes.get(i);

			if (x >= box[0] && x < box[2] && y >= box[1] && y < box[3]) {
				this.selected = i;
			}
		}
	}

	private void choose(int answer) {
		if (StoryClient.send(StoryActionPayload.ANSWER, "", answer)) {
			this.sent = true;
			this.sentAge = 0;

			if (this.minecraft != null && answer >= 0) {
				StoryClient.sound(this.minecraft, "story.blip", 0.25F, 1.35F, null);
			}
		} else {
			this.onClose();
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	// --- Drawing -------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// No blur: the ship stays in sight. Bars at the edges, like in a film, and a darker floor.
		float in = Mth.clamp((this.age + a) / 8.0F, 0.0F, 1.0F);
		int bar = Math.round(in * Math.min(22, this.height / 14));
		graphics.fill(0, 0, this.width, bar, 0xF0000000);
		graphics.fillGradient(0, this.height / 2, this.width, this.height, 0x00000000, Math.round(in * 0x90) << 24);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		StoryDialogPayload line = this.line;
		boolean radio = line.radio();
		boolean self = "funke".equals(line.speaker());
		int panelWidth = Math.min(this.width - 16, 460);
		int left = (this.width - panelWidth) / 2;
		int textLeft = self ? left + PAD : left + PAD + PORTRAIT + PAD;
		int textWidth = panelWidth - PORTRAIT - PAD * 3;

		// The whole line is laid out at once, so the words do not jump while they appear.
		List<FormattedText> rows = this.font.splitIgnoringLanguage(Component.literal(this.words), textWidth);
		List<FormattedCharSequence> answers = new ArrayList<>();
		List<Integer> answerOf = new ArrayList<>();

		for (int i = 0; i < line.answers().size(); i++) {
			Component text = Component.literal((i + 1) + "  ").append(Component.translatable(line.answers().get(i)));

			for (FormattedCharSequence part : this.font.split(text, textWidth - 8)) {
				answers.add(part);
				answerOf.add(i);
			}
		}

		int textHeight = rows.size() * 10;
		int answersHeight = answers.isEmpty() ? 0 : 6 + answers.size() * ANSWER_HEIGHT;
		int panelHeight = Math.max(PORTRAIT + PAD * 2, PAD * 2 + 4 + textHeight + answersHeight + 10);
		int top = this.height - panelHeight - 10;
		float in = Mth.clamp((this.age + a) / 6.0F, 0.0F, 1.0F);
		top += Math.round((1.0F - in) * 14.0F);
		int alpha = Math.round(in * 255);
		int edge = radio ? 0x4A7AB8 : "shadow".equals(line.speaker()) ? 0x8A3030 : 0x56647A;

		graphics.fillGradient(left, top, left + panelWidth, top + panelHeight, alpha * 15 / 16 << 24 | 0x0C121A, alpha * 15 / 16 << 24 | 0x06090E);
		graphics.outline(left, top, panelWidth, panelHeight, alpha << 24 | edge);
		graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 2, alpha / 2 << 24 | edge);

		// The face, and the name on a plate above the panel.
		int portraitX = self ? left + panelWidth - PAD - PORTRAIT : left + PAD;
		StoryPortraits.draw(graphics, line.speaker(), portraitX, top + PAD, PORTRAIT, radio, alpha);
		Component name = StoryPortraits.name(line.speaker());

		if (radio) {
			name = Component.translatable("sabercraft.story.dialog.comlink", name);
		}

		int nameWidth = this.font.width(name) + 12;
		int nameX = self ? left + panelWidth - nameWidth - PAD : left + PAD;
		graphics.fill(nameX, top - 12, nameX + nameWidth, top, alpha * 15 / 16 << 24 | 0x0C121A);
		graphics.outline(nameX, top - 12, nameWidth, 13, alpha << 24 | edge);
		graphics.text(this.font, name, nameX + 6, top - 9, alpha << 24 | this.nameColour(line.speaker(), radio));

		// The words, as far as they are shown.
		int y = top + PAD + 2;
		int left2 = (int) this.shown;

		for (FormattedText row : rows) {
			String text = row.getString();

			if (left2 <= 0) {
				break;
			}

			String part = text.length() <= left2 ? text : text.substring(0, left2);
			left2 -= text.length();
			// A wrapped line lost the space it broke at.
			left2 -= left2 > 0 && this.words.length() > 0 ? 1 : 0;
			graphics.text(this.font, part, textLeft, y, alpha << 24 | (radio ? 0xD8E6FA : 0xECEFF4));
			y += 10;
		}

		y = top + PAD + 2 + textHeight + 6;
		this.answerBoxes.clear();

		if (!this.typing() && !answers.isEmpty()) {
			int[] box = null;
			int current = -1;

			for (int i = 0; i < answers.size(); i++) {
				int answer = answerOf.get(i);

				if (answer != current) {
					current = answer;
					box = new int[] {textLeft - 4, y - 2, textLeft + textWidth, y + ANSWER_HEIGHT - 2};
					this.answerBoxes.add(box);
				} else if (box != null) {
					box[3] = y + ANSWER_HEIGHT - 2;
				}

				y += ANSWER_HEIGHT;
			}

			for (int i = 0; i < this.answerBoxes.size(); i++) {
				int[] b = this.answerBoxes.get(i);
				boolean on = i == this.selected && !this.sent;

				if (on) {
					graphics.fill(b[0], b[1], b[2], b[3], alpha / 6 << 24 | 0xFFFFFF);
					graphics.fill(b[0], b[1], b[0] + 2, b[3], alpha << 24 | (radio ? 0x8CC0FF : 0xE8C878));
				}
			}

			y = top + PAD + 2 + textHeight + 6;

			for (int i = 0; i < answers.size(); i++) {
				boolean on = answerOf.get(i) == this.selected && !this.sent;
				graphics.text(this.font, answers.get(i), textLeft + 2, y + 1, alpha << 24 | (on ? 0xFFFFFF : 0xA8B4C4));
				y += ANSWER_HEIGHT;
			}
		}

		// How to go on, small in the corner; a blinking mark while the line is done.
		if (!this.typing() && !this.sent) {
			Component how = answers.isEmpty() ? Component.translatable("sabercraft.story.dialog.continue")
					: Component.translatable("sabercraft.story.dialog.choose", line.answers().size());
			int w = this.font.width(how);
			int hx = self ? left + PAD : left + panelWidth - PAD - w;
			graphics.text(this.font, how, hx, top + panelHeight - 11, alpha * 3 / 5 << 24 | 0x8890A0);

			if (answers.isEmpty() && Util.getMillis() / 400L % 2L == 0L) {
				int mx = self ? hx + w + 6 : hx - 8;
				int my = top + panelHeight - 9;
				graphics.fill(mx, my, mx + 5, my + 1, alpha << 24 | 0xE8C878);
				graphics.fill(mx + 1, my + 1, mx + 4, my + 2, alpha << 24 | 0xE8C878);
				graphics.fill(mx + 2, my + 2, mx + 3, my + 3, alpha << 24 | 0xE8C878);
			}
		}
	}

	private int nameColour(String speaker, boolean radio) {
		if (radio) {
			return 0x8CC0FF;
		}

		return switch (speaker) {
			case "shadow" -> 0xFF6A5A;
			case "m31", "r2v7", "terminal" -> 0xE8C860;
			case "funke" -> 0x9CE0A0;
			case "trooper", "guard", "tech", "pilot_a", "pilot_b", "quartermaster", "vorr", "hale" -> 0xC8CCD4;
			default -> 0xE8D8B0;
		};
	}

	// --- For tests -----------------------------------------------------------------------------------

	/** How many lines were shown since the game started. */
	public static int shownForTest() {
		return shownLines;
	}

	/** Shows the whole line at once (as a key would). */
	public void finishForTest() {
		this.shown = this.words.length();
	}

	/** The words of the line, as shown in the player's SaberCraft language. */
	public String wordsForTest() {
		return this.words;
	}
}
