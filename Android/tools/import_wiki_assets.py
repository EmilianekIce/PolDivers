#!/usr/bin/env python3
"""
Converts images from a helldivers.wiki.gg image dump into the app's bundled assets.

  python3 import_wiki_assets.py <folder-with-svg-and-png> <Android/app/src/main/assets>

- icons/<Name>.webp      every icon SVG (and a few PNG icons), 128 px, transparent
- campaigns/<Name>.webp  Galactic War campaign headers (+ phase variants), 1280 px wide
- planets/<Name>.webp    planet artwork (<Name>_Planet_Icon.png), 160 px

Needs: pip install cairosvg pillow
"""
import io
import os
import re
import sys

import cairosvg
from PIL import Image

SKIP = re.compile(
    r"(_Sector_map|_Background|^ASMT_|^Automaton_[^_]{1,3}\.svg$|^Automaton_(HASH|PERCENT|apostrophe|at_sign|backslash|caret|colon|"
    r"comma|dot|equal|exlemnationpoint|grave_accent|greaterthen|leftsquarebraket|lessthen|minus|pipe|plus|question|quote|"
    r"rightsquarebraket|semicolon|slash|star|tilde|underscore)\.svg$|^Illuminate_[^_]{1,3}\.svg$|^Terminid_[^_]{1,3}\.svg$|"
    r"Discord|JavaScript|Playstation|Xbox|Sony_|Testing_vector|OOjs|^QQ|JokeIcon|Gamerscore|^XP|SVGMap|Super_Earth_Map|"
    r"Sol_System_map|Sectorview|_Rank_Icon|Armor_Passive|Weapon_Level|_Title_Icon|_Currency_Icon|Summer)",
    re.I,
)
PNG_ICONS = [
    "Stratagem_Permit.png", "Super_Credit.png", "Invasion_Fleet_Enemy_Icon.png",
    "Automaton_Logo.png", "Illuminate_Emblem.png", "Cyborg_Emblem_2.png",
]
ICON_PX = 128
PLANET_PX = 160
HEADER_W = 1280


def planet_key(name: str):
    """Acamar_IV_Planet_Icon.png -> Acamar_IV; Keid_Planet_Icon_Exostorm_C2.png -> Keid__Exostorm_C2;
    Crimsica_Gloom_Planet_Icon.png -> Crimsica__Gloom."""
    m = re.match(r"^(.+?)_Gloom_Planet_Icon\.png$", name, re.I)
    if m:
        return m.group(1) + "__Gloom"
    m = re.match(r"^(.+?)_Planet_Icon(?:_(.+))?\.png$", name, re.I)
    if not m:
        return None
    return m.group(1) + ("__" + m.group(2) if m.group(2) else "")


def remove_black_background(img: Image.Image, threshold: int = 32) -> Image.Image:
    """Some planet renders sit on an opaque black square. Planets are centred discs, so fit the
    disc (radius from the lit pixels, robust to stray specks) and keep only a soft-edged circle --
    the dark night side stays intact, the square goes."""
    import numpy as np

    img = img.convert("RGBA")
    a = np.asarray(img).astype(np.float32)
    h, w = a.shape[:2]
    corner_opaque = a[0, 0, 3] > 0 or a[0, -1, 3] > 0 or a[-1, 0, 3] > 0 or a[-1, -1, 3] > 0
    if not corner_opaque:
        return img
    lum = a[..., :3].max(axis=2)
    ys, xs = np.nonzero((lum > threshold) & (a[..., 3] > 0))
    if len(xs) == 0:
        return img
    cx, cy = (w - 1) / 2, (h - 1) / 2
    dist = np.hypot(xs - cx, ys - cy)
    radius = min(np.percentile(dist, 99.5) + 1.0, min(w, h) / 2)
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx - cx, yy - cy)
    mask = np.clip(radius + 0.5 - d, 0, 1)  # ~1 px anti-aliased rim
    a[..., 3] = a[..., 3] * mask
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def save_webp(img: Image.Image, path: str, quality: int):
    img.save(path, "WEBP", quality=quality, method=6)


def main(src: str, dst: str):
    icons = os.path.join(dst, "icons")
    campaigns = os.path.join(dst, "campaigns")
    os.makedirs(icons, exist_ok=True)
    os.makedirs(campaigns, exist_ok=True)
    planets = os.path.join(dst, "planets")
    done = failed = 0
    for name in sorted(os.listdir(src)):
        path = os.path.join(src, name)
        stem, ext = os.path.splitext(name)
        try:
            if ext.lower() == ".svg" and not SKIP.search(name):
                png = cairosvg.svg2png(url=path, output_width=ICON_PX * 2)
                img = Image.open(io.BytesIO(png)).convert("RGBA")
                img.thumbnail((ICON_PX, ICON_PX), Image.LANCZOS)
                save_webp(img, os.path.join(icons, stem + ".webp"), 90)
            elif name in PNG_ICONS:
                img = Image.open(path).convert("RGBA")
                img.thumbnail((ICON_PX, ICON_PX), Image.LANCZOS)
                save_webp(img, os.path.join(icons, stem + ".webp"), 90)
            elif planet_key(name):
                os.makedirs(planets, exist_ok=True)
                img = remove_black_background(Image.open(path))
                img.thumbnail((PLANET_PX, PLANET_PX), Image.LANCZOS)
                save_webp(img, os.path.join(planets, planet_key(name) + ".webp"), 85)
            elif name.startswith("Galactic_War_Campaigns_Header_"):
                img = Image.open(path).convert("RGB")
                if img.width > HEADER_W:
                    img = img.resize((HEADER_W, round(img.height * HEADER_W / img.width)), Image.LANCZOS)
                key = stem.replace("Galactic_War_Campaigns_Header_", "")
                save_webp(img, os.path.join(campaigns, key + ".webp"), 82)
            else:
                continue
            done += 1
        except Exception as e:  # a few wiki SVGs use features cairo cannot render
            failed += 1
            print("skip", name, e)
    print(f"converted {done}, failed {failed}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
