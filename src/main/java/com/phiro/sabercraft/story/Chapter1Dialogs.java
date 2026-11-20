package com.phiro.sabercraft.story;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Phase 19: the conversations of chapter 1 ("Nachtschatten"). Only their shape is here (who
 * speaks, which answers lead where, what they change); the words are in the language files under
 * {@code sabercraft.story.d.<dialog>.<line>}, in German and English.
 */
final class Chapter1Dialogs {
	private static final Map<String, Dialog> DIALOGS = new HashMap<>();

	private Chapter1Dialogs() {
	}

	static @Nullable Dialog get(String id) {
		if (DIALOGS.isEmpty()) {
			build();
		}

		return DIALOGS.get(id);
	}

	/** All dialogs (for tests: every key must be in both languages). */
	static java.util.Collection<Dialog> all() {
		if (DIALOGS.isEmpty()) {
			build();
		}

		return DIALOGS.values();
	}

	private static void add(Dialog dialog) {
		DIALOGS.put(dialog.id, dialog);
	}

	private static void build() {
		// --- Mission 1: in the Falcon, then the maintenance room -----------------------------------
		add(Dialog.of("m1_briefing")
				.say("start", "mira").then("situation")
				.say("situation", "mira")
				.ask("nightshade", "nightshade").ask("kestrel", "kestrel").ask("plan", "plan").ask("ready", "ready")
				.say("nightshade", "mira").then("more")
				.say("kestrel", "mira").then("more")
				.say("plan", "mira").then("more")
				.say("more", "mira")
				.ask("nightshade", "nightshade").ask("kestrel", "kestrel").ask("plan", "plan").ask("ready", "ready")
				.say("ready", "mira").does(s -> Chapter1.done(s, "m1.briefing"))
				.build());

		add(Dialog.of("m1_dax")
				.say("start", "dax")
				.ask("go", "go").ask("joke", "joke")
				.say("joke", "dax")
				.ask("go", "go")
				.say("go", "dax").does(s -> Chapter1.dock(s))
				.build());

		add(Dialog.of("m1_mira_wait")
				.say("start", "mira")
				.build());

		add(Dialog.of("m1_rorrak")
				.say("start", "rorrak").then("dax")
				.say("dax", "dax")
				.build());

		add(Dialog.of("m1_m31")
				.say("start", "m31")
				.ask("free", "free").ask("leave", Dialog.END)
				.say("free", "m31").does(s -> Chapter1.freeMouse(s)).then("follow")
				.say("follow", "m31")
				.build());

		add(Dialog.of("m1_pad")
				.say("start", "terminal").does(s -> {
					s.note("pad_maintenance");
					s.set("opt.pad");
				})
				.build());

		add(Dialog.of("m1_sign")
				.say("start", "funke").does(s -> s.set("know.sign"))
				.build());

		add(Dialog.of("m1_trooper")
				.say("start", "trooper")
				.build());

		// --- Mission 2: Kestrel, the terminal, the dead drop ---------------------------------------
		add(Dialog.of("m2_kestrel")
				.say("start", "kestrel").then("hello")
				.say("hello", "kestrel")
				.ask("nightshade", "nightshade").ask("silence", "silence").ask("out", "out").ask("task", "task")
				.say("nightshade", "kestrel").then("hub")
				.say("silence", "kestrel").then("hub")
				.say("out", "kestrel").then("hub")
				.say("hub", "kestrel")
				.ask("nightshade", "nightshade").ask("silence", "silence").ask("out", "out").ask("task", "task")
				.say("task", "kestrel").does(s -> Chapter1.done(s, "m2.kestrel"))
				.build());

		add(Dialog.of("terminal")
				.say("start", "terminal")
				.ask("map", "map").ask("diary", "diary").ask("drop", "drop").ask("hale", "hale", null, s -> s.has("choice.refuse_hale") && s.in("m5"))
				.ask("off", Dialog.END)
				.say("map", "terminal").does(s -> s.note("terminal_map")).then("back")
				.say("diary", "terminal").does(s -> s.note("terminal_diary")).then("back")
				.say("drop", "terminal").does(s -> {
					s.note("terminal_drop");
					s.set("know.drop");
					Chapter1.done(s, "m2.terminal");
				}).then("back")
				.say("hale", "terminal").does(s -> {
					s.note("terminal_intercept");
					s.set("know.hale_trap");
				}).then("back")
				.say("back", "terminal")
				.ask("map", "map").ask("diary", "diary").ask("drop", "drop").ask("hale", "hale", null, s -> s.has("choice.refuse_hale") && s.in("m5"))
				.ask("off", Dialog.END)
				.build());

		add(Dialog.of("m2_container")
				.say("start", "funke").does(s -> {
					s.set("item.stick");
					Chapter1.done(s, "m2.drop");
					// Phase 27: the second way back, where one can find it: Kestrel on the comm once
					// the container is shut (Chapter1.tick, step m2.return).
					s.set("tmp.side_hatch");
				})
				.build());

		// Phase 20: the wrong containers (the lid clatters: those near turn to look).
		add(Dialog.of("m2_crate_c3")
				.say("start", "funke").does(s -> Chapter1.wrongCrate(s, "c3"))
				.build());

		add(Dialog.of("m2_crate_f9")
				.say("start", "funke").does(s -> Chapter1.wrongCrate(s, "f9"))
				.build());

		add(Dialog.of("m2_return")
				.say("start", "kestrel").then("read")
				.say("read", "kestrel")
				.ask("stop", "resolve").ask("howmany", "howmany")
				.say("howmany", "kestrel").then("resolve")
				.say("resolve", "kestrel").does(s -> {
					s.note("list_children");
					Chapter1.done(s, "m2.return");
				})
				.build());

		add(Dialog.of("kestrel_idle")
				.say("start", "kestrel")
				.build());

		// --- Mission 3: the armory and Hale ---------------------------------------------------------
		add(Dialog.of("m3_kestrel")
				.say("start", "kestrel").then("plan")
				.say("plan", "kestrel")
				.ask("caught", "caught").ask("go", "go")
				.say("caught", "kestrel")
				.ask("go", "go")
				.say("go", "kestrel").does(s -> Chapter1.done(s, "m3.plan"))
				.build());

		// Phase 20: the security desk opens the armory and can page the quartermaster away.
		add(Dialog.of("m3_console")
				.say("start", "terminal")
				.ask("unlock", "unlocked", null, s -> !s.has("armory.open")).ask("page", "paged", null, s -> !s.has("qm.away")).ask("off", Dialog.END)
				.say("unlocked", "terminal").does(s -> {
					s.set("armory.open");
					Chapter1.done(s, "m3.console");
				})
				.ask("page", "paged", null, s -> !s.has("qm.away")).ask("off", Dialog.END)
				.say("paged", "terminal").does(Chapter1::pageQuartermaster)
				.ask("unlock", "unlocked", null, s -> !s.has("armory.open")).ask("off", Dialog.END)
				.build());

		add(Dialog.of("m3_locker")
				.say("start", "funke").does(s -> Chapter1.putOnArmour(s))
				.build());

		add(Dialog.of("m3_locker2")
				.say("start", "funke").does(s -> s.set("opt.armour2"))
				.build());

		add(Dialog.of("m3_card")
				.say("start", "funke").does(s -> {
					s.set("item.card");
					Chapter1.done(s, "m3.card");
				})
				.build());

		add(Dialog.of("m3_hale")
				.say("start", "hale").then("who")
				.say("who", "hale")
				.ask("want", "want").ask("knows", "knows")
				.say("knows", "hale").then("want")
				.say("want", "hale")
				.ask("trust", "trust", s -> s.set("trust.hale")).ask("refuse", "refuse", s -> s.set("choice.refuse_hale")).ask("why", "why")
				.say("why", "hale")
				.ask("trust", "trust", s -> s.set("trust.hale")).ask("refuse", "refuse", s -> s.set("choice.refuse_hale"))
				.say("trust", "hale").does(s -> {
					s.set("know.cellcode");
					Chapter1.haleLeaves(s);
				})
				.say("refuse", "hale").does(Chapter1::haleLeaves)
				.build());

		add(Dialog.of("quartermaster")
				.say("start", "quartermaster")
				.build());

		// --- Mission 4: the reactor -----------------------------------------------------------------
		add(Dialog.of("m4_kestrel")
				.say("start", "kestrel").thenIf(s -> s.has("trust.hale"), "trusted", "refused")
				.say("trusted", "kestrel").then("plan")
				.say("refused", "kestrel").then("plan")
				.say("plan", "kestrel")
				.ask("questions", "questions").ask("go", "go")
				.say("questions", "kestrel")
				.ask("go", "go")
				.say("go", "kestrel").does(s -> Chapter1.done(s, "m4.brief"))
				.build());

		add(Dialog.of("m4_checkpoint")
				.say("start", "guard")
				.ask("card", "pass", null, s -> s.has("item.card") && s.disguised).ask("forgot", "nope")
				.say("pass", "guard").does(s -> {
					s.set("m4.passed");
					Chapter1.done(s, "m4.checkpoint");
				})
				.say("nope", "guard")
				.build());

		// Phase 20: an officer on the reactor floor calls the player in armour over and asks.
		add(Dialog.of("m4_officer")
				.say("start", "officer_reactor")
				.ask("card", "who", null, s -> s.has("item.card")).ask("password", "fine", null, s -> s.has("know.password"))
				.ask("hale", "isb", null, s -> s.has("trust.hale")).ask("patrol", "bad")
				.say("who", "officer_reactor")
				.ask("harlo", "fine", null, s -> s.has("m4.passed")).ask("hale", "isb", null, s -> s.has("trust.hale")).ask("patrol", "bad")
				.say("fine", "officer_reactor").does(Chapter1::officerSatisfied)
				.say("isb", "officer_reactor").does(Chapter1::officerSatisfied)
				.say("bad", "officer_reactor").does(Chapter1::officerAlarm)
				.build());

		add(Dialog.of("checkpoint_closed")
				.say("start", "guard")
				.build());

		add(Dialog.of("m4_tech")
				.say("start", "tech")
				.ask("sick", "q2").ask("who", "bad")
				.say("bad", "tech").does(s -> Chapter1.techSuspects(s, 0.35F)).then("q2")
				.say("q2", "tech")
				.ask("password", "good", null, s -> s.has("know.password")).ask("changed", "bluff").ask("refuse", "hostile")
				.say("good", "tech").does(s -> Chapter1.techLeaves(s))
				.say("bluff", "tech").does(s -> Chapter1.techWatches(s))
				.say("hostile", "tech").does(s -> Chapter1.techAlarm(s))
				.build());

		add(Dialog.of("m4_console")
				.say("start", "terminal").does(Chapter1::download)
				.build());

		add(Dialog.of("m4_coolant")
				.say("start", "funke").does(s -> s.set("opt.coolant"))
				.build());

		add(Dialog.of("tech_idle")
				.say("start", "tech")
				.build());

		// --- Mission 5: the discovery ---------------------------------------------------------------
		add(Dialog.of("m5_recording")
				.radio("start", "kestrel").then("hatch")
				.radio("hatch", "kestrel").does(s -> {
					s.note("recording_kestrel");
					Chapter1.done(s, "m5.recording");
				})
				.build());

		add(Dialog.of("m5_trap")
				.say("start", "hale").then("take")
				.say("take", "hale").does(Chapter1::trapped)
				.build());

		add(Dialog.of("m5_r2")
				.say("start", "r2v7").does(Chapter1::freeAstromech)
				.build());

		add(Dialog.of("m5_kestrel")
				.say("start", "kestrel").thenIf(s -> s.has("trust.hale"), "trusted", "refused")
				.say("trusted", "kestrel").then("go")
				.say("refused", "kestrel").then("go")
				.say("go", "kestrel").does(Chapter1::kestrelFree)
				.build());

		add(Dialog.of("m5_prisoner")
				.say("start", "prisoner").does(s -> s.set("opt.prisoner"))
				.build());

		// --- Mission 6: the finale ------------------------------------------------------------------
		add(Dialog.of("m6_plan")
				.say("start", "kestrel").then("artefact")
				.say("artefact", "kestrel").then("pickup")
				.say("pickup", "kestrel").thenIf(s -> s.has("opt.armour2"), "escort", "fire")
				.say("escort", "kestrel").then("ready")
				.say("fire", "kestrel").then("ready")
				.say("ready", "kestrel")
				.ask("go", Dialog.END, s -> Chapter1.done(s, "m6.plan"))
				.build());

		add(Dialog.of("m6_ilyra")
				.radio("start", "ilyra").then("crystal")
				.radio("crystal", "ilyra").then("ready")
				.radio("ready", "ilyra").does(s -> {
					s.note("ilyra_message");
					s.set("know.ilyra");
				})
				.build());

		add(Dialog.of("m6_saber")
				.say("start", "funke")
				.ask("take", Dialog.END, Scenes::saberBegin).ask("wait", Dialog.END)
				.build());

		add(Dialog.of("m6_shadow")
				.say("start", "shadow").then("threat")
				.say("threat", "shadow").does(Finale::duelStarts)
				.build());

		add(Dialog.of("m6_epilogue")
				.say("start", "kestrel").then("r2")
				.say("r2", "r2v7").then("signature")
				.say("signature", "kestrel").then("mira")
				.say("mira", "mira").then("knows")
				.say("knows", "kestrel").then("dax")
				.say("dax", "dax").thenIf(s -> s.has("opt.prisoner"), "tamsin", "rorrak")
				.say("tamsin", "prisoner").then("rorrak")
				.say("rorrak", "rorrak").does(s -> Chapter1.done(s, "m6.epilogue"))
				.build());

		// --- Phase 22: after the chapter, on the way to the fleet --------------------------------------
		add(Dialog.of("after_dax")
				.say("start", "dax").thenIf(s -> s.has("trust.hale"), "hale_trusted", "hale_refused")
				.say("hale_trusted", "dax")
				.ask("ship", "ship").ask("next", "next").ask("bye", Dialog.END)
				.say("hale_refused", "dax")
				.ask("ship", "ship").ask("next", "next").ask("bye", Dialog.END)
				.say("ship", "dax")
				.ask("next", "next").ask("bye", Dialog.END)
				.say("next", "dax")
				.ask("ship", "ship").ask("bye", Dialog.END)
				.build());

		add(Dialog.of("after_kestrel")
				.say("start", "kestrel").thenIf(s -> s.has("opt.armour2"), "armour", "plain")
				.say("armour", "kestrel")
				.ask("tracker", "tracker").ask("shadow", "shadow").ask("bye", Dialog.END)
				.say("plain", "kestrel")
				.ask("tracker", "tracker").ask("shadow", "shadow").ask("bye", Dialog.END)
				.say("tracker", "kestrel")
				.ask("shadow", "shadow").ask("bye", Dialog.END)
				.say("shadow", "kestrel")
				.ask("tracker", "tracker").ask("bye", Dialog.END)
				.build());

		add(Dialog.of("after_mira")
				.say("start", "mira").thenIf(s -> s.has("opt.prisoner"), "tamsin", "alone")
				.say("tamsin", "mira").then("saber")
				.say("alone", "mira").then("saber")
				.say("saber", "mira")
				.build());

		add(Dialog.of("after_rorrak")
				.say("start", "rorrak").then("dax")
				.radio("dax", "dax")
				.build());

		add(Dialog.of("after_tamsin")
				.say("start", "prisoner").then("promise")
				.say("promise", "prisoner")
				.build());

		add(Dialog.of("falcon_crew")
				.say("start", "dax")
				.build());

		add(Dialog.of("dax_idle")
				.say("start", "dax")
				.build());

		add(Dialog.of("rorrak_idle")
				.say("start", "rorrak").then("dax")
				.say("dax", "dax")
				.build());
	}
}
