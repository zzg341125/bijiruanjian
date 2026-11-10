from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable-nodpi" / "ic_launcher_foreground.png"
PREVIEW = ROOT / "docs" / "quyang-icon-preview.png"
BACKGROUND = (246, 222, 174)
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def clean_transparent_rgb(
    image: Image.Image,
    alpha_cutoff: int = 0,
) -> Image.Image:
    """Keep hidden edge pixels matching the launcher background, never blue."""
    image = image.convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, alpha = pixels[x, y]
            is_blue = blue > red + 20 and green > red + 10
            if alpha <= alpha_cutoff or is_blue:
                pixels[x, y] = (*BACKGROUND, 0)
    return image


def make_full_bleed(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    pixels = image.load()
    edge_band = max(4, image.width // 24)
    for y in range(image.height):
        for x in range(image.width):
            if min(x, y, image.width - 1 - x, image.height - 1 - y) > edge_band:
                continue
            red, green, blue, _ = pixels[x, y]
            if red > 225 and green > 215 and blue > 185:
                pixels[x, y] = (*BACKGROUND, 255)
    canvas = Image.new("RGBA", image.size, (*BACKGROUND, 255))
    canvas.alpha_composite(image)
    return canvas.convert("RGBA")


master = clean_transparent_rgb(Image.open(SOURCE))
master.save(SOURCE, format="PNG", optimize=True)
master.save(PREVIEW, format="PNG", optimize=True)

for density, size in LEGACY_SIZES.items():
    resized = master.resize((size, size), Image.Resampling.LANCZOS)
    icon = make_full_bleed(resized)
    output_dir = RES / f"mipmap-{density}"
    icon.save(output_dir / "ic_launcher.png", format="PNG", optimize=True)
    icon.save(output_dir / "ic_launcher_round.png", format="PNG", optimize=True)
