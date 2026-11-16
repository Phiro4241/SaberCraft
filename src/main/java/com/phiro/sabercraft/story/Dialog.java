package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * Phase 19: a conversation: lines (nodes) said by someone, each with the answers one can give (or
 * none: the line goes on to the next by itself, or ends the talk). Answers can lead to other
 * lines, set what is known or decided, start things in the mission. Only shown answers count: an
 * answer can wait for something known ({@code know.password}).
 *
 * <p>All words are in the language files: the line {@code sabercraft.story.d.<dialog>.<node>}, its
 * answers {@code sabercraft.story.d.<dialog>.<node>.<answer>}.
 */
public final class Dialog {
	/** Ends the talk. */
	public static final String END = "";

	public final String id;
	public final Map<String, Node> nodes;
	public final String first;

	private Dialog(String id, Map<String, Node> nodes, String first) {
		this.id = id;
		this.nodes = nodes;
		this.first = first;
	}

	public @Nullable Node node(String node) {
		return this.nodes.get(node);
	}

	/** A line: who says it, the answers, where it goes without answers, and what happens when it is said. */
	public record Node(String id, String speaker, boolean radio, List<Answer> answers, String next, @Nullable Consumer<StorySession> effect) {
		public String key(Dialog dialog) {
			return "sabercraft.story.d." + dialog.id + "." + this.id;
		}

		/** The answers shown now. */
		public List<Answer> shown(StorySession session) {
			List<Answer> list = new ArrayList<>();

			for (Answer answer : this.answers) {
				if (answer.shown().test(session)) {
					list.add(answer);
				}
			}

			return list;
		}
	}

	/** An answer: its words ({@code <node>.<id>}), the line it leads to ({@link #END} ends the talk) and what it does. */
	public record Answer(String id, String next, @Nullable Consumer<StorySession> effect, Predicate<StorySession> shown) {
		public String key(Dialog dialog, Node node) {
			return node.key(dialog) + "." + this.id;
		}
	}

	public static Builder of(String id) {
		return new Builder(id);
	}

	/** Writes a dialog line by line. */
	public static final class Builder {
		private final String id;
		private final Map<String, Node> nodes = new LinkedHashMap<>();
		private @Nullable String first;
		private @Nullable String node;
		private String speaker = "";
		private boolean radio;
		private List<Answer> answers = new ArrayList<>();
		private String next = END;
		private @Nullable Consumer<StorySession> effect;

		private Builder(String id) {
			this.id = id;
		}

		/** A new line said by {@code speaker} (in person). */
		public Builder say(String node, String speaker) {
			this.close();
			this.node = node;
			this.speaker = speaker;
			this.radio = false;

			if (this.first == null) {
				this.first = node;
			}

			return this;
		}

		/** A new line said over the comlink (a blue hologram picture). */
		public Builder radio(String node, String speaker) {
			this.say(node, speaker);
			this.radio = true;
			return this;
		}

		/** Without answers: goes on to that line (or ends, {@link #END}). */
		public Builder then(String next) {
			this.next = next;
			return this;
		}

		/** What happens when the line is said. */
		public Builder does(Consumer<StorySession> effect) {
			this.effect = effect;
			return this;
		}

		public Builder ask(String answer, String next) {
			return this.ask(answer, next, null, session -> true);
		}

		public Builder ask(String answer, String next, @Nullable Consumer<StorySession> effect) {
			return this.ask(answer, next, effect, session -> true);
		}

		public Builder ask(String answer, String next, @Nullable Consumer<StorySession> effect, Predicate<StorySession> shown) {
			this.answers.add(new Answer(answer, next, effect, shown));
			return this;
		}

		public Dialog build() {
			this.close();

			if (this.first == null) {
				throw new IllegalStateException("Dialog " + this.id + " has no lines");
			}

			for (Node node : this.nodes.values()) {
				check(node.next(), node);

				for (Answer answer : node.answers()) {
					check(answer.next(), node);
				}
			}

			return new Dialog(this.id, Map.copyOf(this.nodes), this.first);
		}

		private void check(String next, Node from) {
			if (!next.isEmpty() && !this.nodes.containsKey(next)) {
				throw new IllegalStateException("Dialog " + this.id + ": line " + from.id() + " leads to unknown line " + next);
			}
		}

		private void close() {
			if (this.node != null) {
				this.nodes.put(this.node, new Node(this.node, this.speaker, this.radio, List.copyOf(this.answers), this.next, this.effect));
			}

			this.node = null;
			this.answers = new ArrayList<>();
			this.next = END;
			this.effect = null;
		}
	}
}
