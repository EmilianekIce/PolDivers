"""Builds the website's data files from the Android app's sources, so both share one truth.

Usage: python3 build_web_data.py <site-dir>
  - copies the app assets (icons, planet art, campaign headers, JSON data, fonts) to <site>/assets
  - writes <site>/assets/index.json: file lists of icons / planets / campaigns, effect-name and
    effect-icon tables taken from PlanetEffects.kt and GameArt.kt
"""
import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app/src/main/assets")
FONTS = os.path.join(ROOT, "app/src/main/res/font")
SRC = os.path.join(ROOT, "app/src/main/java/com/poldivers/app")


def kotlin_list(text, name):
    m = re.search(name + r"\s*=\s*listOf\((.*?)\)", text, re.S)
    return re.findall(r'"([^"]*)"', m.group(1)) if m else []


def kotlin_map(text, name):
    m = re.search(name + r"\s*=\s*linkedMapOf\((.*?)\n\s*\)", text, re.S)
    return dict(re.findall(r'"([^"]*)"\s+to\s+"([^"]*)"', m.group(1))) if m else {}


def main(site):
    out = os.path.join(site, "assets")
    os.makedirs(out, exist_ok=True)
    for d in ("icons", "planets", "campaigns"):
        shutil.copytree(os.path.join(ASSETS, d), os.path.join(out, d), dirs_exist_ok=True)
    for f in os.listdir(ASSETS):
        if f.endswith(".json"):
            shutil.copy(os.path.join(ASSETS, f), out)
    os.makedirs(os.path.join(out, "fonts"), exist_ok=True)
    for f in os.listdir(FONTS):
        shutil.copy(os.path.join(FONTS, f), os.path.join(out, "fonts"))
    shutil.copy(os.path.join(ROOT, "app/src/main/res/drawable-nodpi/sector_map.webp"), out)
    res = os.path.join(ROOT, "app/src/main/res")
    for d in os.listdir(res):
        if d.startswith("drawable"):
            for f in os.listdir(os.path.join(res, d)):
                if f.startswith("faction_"):
                    shutil.copy(os.path.join(res, d, f), out)
    shutil.copy(os.path.join(res, "mipmap-xxxhdpi/ic_launcher_foreground.png"), os.path.join(out, "logo.png"))

    effects = open(os.path.join(SRC, "data/hd2/PlanetEffects.kt"), encoding="utf-8").read()
    art = open(os.path.join(SRC, "core/art/GameArt.kt"), encoding="utf-8").read()
    index = {
        "icons": sorted(os.listdir(os.path.join(ASSETS, "icons"))),
        "planets": sorted(os.listdir(os.path.join(ASSETS, "planets"))),
        "campaigns": sorted(os.listdir(os.path.join(ASSETS, "campaigns"))),
        "effectKinds": {
            "hidden": re.findall(r'"([^"]*)"', re.search(r"HIDDEN = setOf\((.*?)\)", effects).group(1)),
            "enemy": kotlin_list(effects, "ENEMY_KEYS"),
            "hazard": kotlin_list(effects, "HAZARD_KEYS"),
            "support": kotlin_list(effects, "SUPPORT_KEYS"),
            "site": kotlin_list(effects, "SITE_KEYS"),
        },
        "effectPolish": kotlin_map(effects, "POLISH"),
        "effectIcons": kotlin_map(art, "EFFECT_ICONS"),
    }
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))
    print(f"assets -> {out}: {len(index['icons'])} icons, {len(index['planets'])} planets, "
          f"{len(index['effectPolish'])} effect names, {len(index['effectIcons'])} effect icons")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(ROOT), "Web"))
