#!/usr/bin/env python3
"""One-time deterministic generation of independent development launcher icons.

Re-running this requires Pillow; building the APK does not: built PNGs ship in overlays.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def render(size: int):
    scale = 4
    n = size * scale
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    pad = int(n*.04)
    draw.rounded_rectangle((pad, pad, n-pad, n-pad), radius=int(n*.24), fill=(19, 35, 48, 255))
    # Distinct geometric playback glyph, not copied from SmartTube/SmarterTube artwork.
    draw.rounded_rectangle((int(n*.18), int(n*.30), int(n*.27), int(n*.70)), radius=int(n*.045), fill=(50, 205, 188, 255))
    draw.polygon([(int(n*.37), int(n*.26)), (int(n*.37), int(n*.74)), (int(n*.76), int(n*.50))], fill=(239, 249, 248, 255))
    return im.resize((size, size), Image.Resampling.LANCZOS)

if __name__ == '__main__':
    out = ROOT / 'overlays/dev-icons'
    out.mkdir(exist_ok=True)
    for density, px in SIZES.items():
        path = out / f'{density}.png'
        render(px).save(path, optimize=True)
        print(density, px, path)
