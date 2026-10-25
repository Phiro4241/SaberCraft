"""Writes the SaberCraft advancements (Phase 11) to src/main/resources/data/sabercraft/advancement/.

Every advancement has "minecraft:impossible" criteria: the mod awards them on the server when the
real event happens (SaberAdvancements.java), so a data pack trigger can never award them by
mistake. Several criteria mean "all of these" (for example every saber ability once); vanilla
saves which ones are done, so progress survives a restart.

    python3 tools/gen_advancements.py
"""
import json
import os

OUT = "src/main/resources/data/sabercraft/advancement"

SABER = {"id": "minecraft:netherite_sword"}


def blaster(kind):
    return {"id": "minecraft:bow", "components": {"minecraft:custom_model_data": {"strings": ["sabercraft:" + kind]}}}


def droid(kind):
    return {"id": "minecraft:iron_nugget", "components": {"minecraft:item_model": f"sabercraft:droid/{kind}_icon"}}


DROIDS = ["r2d2", "c3po", "bb8", "chopper", "mouse", "probe", "b1", "k2so"]

# name: (parent, icon, frame, criteria, hidden)
ADVANCEMENTS = {
    "root": (None, SABER, "task", ["joined"], False),
    "force_awakens": ("root", SABER, "task", ["saber"], False),
    "first_shot": ("root", blaster("dl44"), "task", ["shot"], False),
    "sharpshooter": ("first_shot", blaster("dc15a"), "goal", ["hit"], False),
    "duel_begins": ("force_awakens", {"id": "minecraft:iron_sword"}, "task", ["duel"], False),
    "dark_lord": ("duel_begins", {"id": "minecraft:wither_skeleton_skull"}, "challenge", ["won"], False),
    "chosen_one": ("force_awakens", {"id": "minecraft:nether_star"}, "challenge",
                   ["guard", "spin", "push", "dash", "grip", "pull", "jump"], False),
    "spin_speed": ("force_awakens", {"id": "minecraft:breeze_rod"}, "goal", ["two_hits"], False),
    "finisher": ("duel_begins", {"id": "minecraft:blaze_powder"}, "goal", ["finisher"], False),
    "droid_friend": ("root", droid("r2d2"), "task", ["droid"], False),
    "loyal_companion": ("droid_friend", droid("bb8"), "challenge", DROIDS, False),
}


def write():
    os.makedirs(OUT, exist_ok=True)

    for name, (parent, icon, frame, criteria, hidden) in ADVANCEMENTS.items():
        display = {
            "icon": icon,
            "title": {"translate": f"advancements.sabercraft.{name}.title"},
            "description": {"translate": f"advancements.sabercraft.{name}.description"},
            "frame": frame,
            "show_toast": parent is not None,
            "announce_to_chat": parent is not None,
            "hidden": hidden,
        }

        if parent is None:
            display["background"] = "sabercraft:gui/advancements/backgrounds/space"

        data = {}

        if parent is not None:
            data["parent"] = "sabercraft:" + parent

        data["display"] = display
        data["criteria"] = {c: {"trigger": "minecraft:impossible"} for c in criteria}
        data["requirements"] = [[c] for c in criteria]

        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")

    print("wrote", len(ADVANCEMENTS), "advancements")


if __name__ == "__main__":
    write()
