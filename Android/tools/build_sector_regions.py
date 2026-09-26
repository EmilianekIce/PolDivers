"""Cuts the sector border artwork (res/drawable-nodpi/sector_map.webp) into vector polygons
(assets/sector_regions.json): one outline per sector cell, in map units ([-1, 1], y up).
The app assigns cells to sectors by the planets inside them, fills enemy sectors and strokes
the outlines -- as vectors they stay sharp at any zoom.

Usage: python3 build_sector_regions.py   (needs pillow, numpy, scipy, scikit-image)
"""
import json

import numpy as np
from PIL import Image
from scipy import ndimage
from skimage import measure

SRC = "../app/src/main/res/drawable-nodpi/sector_map.webp"
DST = "../app/src/main/assets/sector_regions.json"

img = Image.open(SRC).convert("RGBA")
N = img.size[0]
alpha = np.array(img)[:, :, 3]
line = alpha > 40
yy, xx = np.mgrid[0:N, 0:N]
r = np.hypot(xx - (N - 1) / 2, yy - (N - 1) / 2) / (N / 2)
inside = (~line) & (r < 0.985)
lab, n = ndimage.label(inside)
sizes = ndimage.sum(inside, lab, range(1, n + 1))
keep = [i for i, size in enumerate(sizes, 1) if size >= 120]  # drop anti-aliasing specks
remap = np.zeros(lab.max() + 1, dtype=np.int32)
remap[keep] = np.arange(1, len(keep) + 1)
lab = remap[lab]
# Border pixels go to the nearest cell so neighbouring cells meet in the middle of the line.
_, (iy, ix) = ndimage.distance_transform_edt(lab == 0, return_indices=True)
grown = lab[iy, ix]
grown[r >= 0.99] = 0

cells = []
for cell in range(1, len(keep) + 1):
    mask = np.pad((grown == cell).astype(float), 1)
    contours = measure.find_contours(mask, 0.5)
    if not contours:
        continue
    contour = max(contours, key=len)
    poly = measure.approximate_polygon(contour, tolerance=0.6)
    pts = []
    for row, col in poly[:-1]:
        x = (col - 1 + 0.5) / N * 2 - 1
        y = 1 - (row - 1 + 0.5) / N * 2
        pts += [round(float(x), 4), round(float(y), 4)]
    cells.append(pts)

with open(DST, "w") as f:
    json.dump(cells, f, separators=(",", ":"))
print(f"{len(cells)} cells, {sum(len(c) for c in cells) // 2} points -> {DST}")
