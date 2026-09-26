"""Cuts the sector border artwork (res/drawable-nodpi/sector_map.webp) into a label map
(assets/sector_regions.png): every pixel holds the id of the sector cell it lies in, 0 outside.
The app assigns cells to sectors by the planets inside them and colours them by owner.

Usage: python3 build_sector_regions.py   (needs pillow, numpy, scipy; run from Android/tools)
"""
import numpy as np
from PIL import Image
from scipy import ndimage

N = 500
SRC = "../app/src/main/res/drawable-nodpi/sector_map.webp"
DST = "../app/src/main/assets/sector_regions.png"

alpha = np.array(Image.open(SRC).convert("RGBA").resize((N, N), Image.LANCZOS))[:, :, 3]
line = alpha > 40
yy, xx = np.mgrid[0:N, 0:N]
r = np.hypot(xx - (N - 1) / 2, yy - (N - 1) / 2) / (N / 2)
inside = (~line) & (r < 0.985)
lab, n = ndimage.label(inside)
sizes = ndimage.sum(inside, lab, range(1, n + 1))
for i, size in enumerate(sizes, 1):
    if size < 30:  # anti-aliasing specks
        lab[lab == i] = 0
ids = np.unique(lab)
ids = ids[ids > 0]
remap = np.zeros(lab.max() + 1, dtype=np.int32)
remap[ids] = np.arange(1, len(ids) + 1)
lab = remap[lab]
# Border pixels go to the nearest cell so the fill reaches the lines.
_, (iy, ix) = ndimage.distance_transform_edt(lab == 0, return_indices=True)
grown = lab[iy, ix]
grown[r >= 0.995] = 0
Image.fromarray(grown.astype(np.uint8), "L").save(DST, optimize=True)
print(f"{len(ids)} cells -> {DST}")
