"""Makes the Android launcher icon layers and the Web favicons from art/app_icon/source.png.

The source is one picture on a transparent background (any square size; 1024 px is plenty). The
script writes the adaptive icon's foreground per density, fitted so every drawn pixel lies inside
the 66 dp safe circle of the 108 dp layer (launcher masks never cut it), the Android 13+ themed-icon
monochrome layer (the same silhouette from the source's alpha), and the Web favicons. The adaptive
icon's background is a colour (`ic_launcher_background` in app/src/main/res/values/colors.xml).
Replace the source and rerun: python3 tools/art/make_app_icon.py
Needs Pillow and NumPy.
"""

from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art/app_icon/source.png"
ANDROID_RES = ROOT / "app/src/main/res"
WEB_RESOURCES = ROOT / "web-app/src/webMain/resources"
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LAYER_DP = 108       # an adaptive icon layer
SAFE_DP = 66         # the circle no launcher mask ever cuts into
NOISE = 16           # alpha below this is not drawing
FAVICONS = {"favicon-32.png": 32, "favicon-192.png": 192}
FAVICON_MARGIN = 0.04  # empty border around a favicon, as a share of its side


def drawn(source: Image.Image) -> Image.Image:
    """The source cropped to what it actually draws."""
    box = source.getchannel("A").point(lambda a: 255 if a >= NOISE else 0).getbbox()
    if box is None:
        raise SystemExit(f"{SOURCE} draws nothing")
    return source.crop(box)


def safe_scale(picture: Image.Image) -> float:
    """How much to scale a picture (in its pixels) so its farthest drawn pixel touches the safe circle."""
    alpha = np.asarray(picture.getchannel("A")) >= NOISE
    ys, xs = np.nonzero(alpha)
    # Distance from the picture's centre to each drawn pixel's far corner.
    cx, cy = picture.width / 2, picture.height / 2
    dx = np.maximum(np.abs(xs - cx), np.abs(xs + 1 - cx))
    dy = np.maximum(np.abs(ys - cy), np.abs(ys + 1 - cy))
    radius = float(np.sqrt(dx * dx + dy * dy).max())
    return (SAFE_DP / 2) / radius  # dp per source pixel


def layer(picture: Image.Image, dp_per_pixel: float, density: float) -> Image.Image:
    side = round(LAYER_DP * density)
    scale = dp_per_pixel * density
    size = (max(1, round(picture.width * scale)), max(1, round(picture.height * scale)))
    scaled = picture.resize(size, Image.LANCZOS)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(scaled, ((side - size[0]) // 2, (side - size[1]) // 2))
    return canvas


def monochrome(foreground: Image.Image) -> Image.Image:
    """The themed-icon layer: the system tints it and reads only its alpha."""
    silhouette = Image.new("RGBA", foreground.size, (255, 255, 255, 0))
    silhouette.putalpha(foreground.getchannel("A"))
    return silhouette


def favicon(picture: Image.Image, side: int) -> Image.Image:
    inner = side * (1 - 2 * FAVICON_MARGIN)
    scale = inner / max(picture.size)
    size = (max(1, round(picture.width * scale)), max(1, round(picture.height * scale)))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(picture.resize(size, Image.LANCZOS), ((side - size[0]) // 2, (side - size[1]) // 2))
    return canvas


def main() -> None:
    picture = drawn(Image.open(SOURCE).convert("RGBA"))
    dp_per_pixel = safe_scale(picture)
    for name, density in DENSITIES.items():
        folder = ANDROID_RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        foreground = layer(picture, dp_per_pixel, density)
        foreground.save(folder / "ic_launcher_foreground.png", optimize=True)
        monochrome(foreground).save(folder / "ic_launcher_monochrome.png", optimize=True)
    for name, side in FAVICONS.items():
        favicon(picture, side).save(WEB_RESOURCES / name, optimize=True)
    largest = round(LAYER_DP * max(DENSITIES.values()) * SAFE_DP / LAYER_DP)
    if max(picture.size) < largest:
        print(f"note: the source draws {max(picture.size)} px; {largest} px or more keeps xxxhdpi sharp")


if __name__ == "__main__":
    main()
