from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable-nodpi" / "ic_launcher_foreground.png"
PREVIEW = ROOT / "docs" / "quyang-icon-preview.png"
BACKGROUND = (232, 211, 168)
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


master = clean_transparent_rgb(Image.open(SOURCE))
master.save(SOURCE, format="PNG", optimize=True)
master.save(PREVIEW, format="PNG", optimize=True)

for density, size in LEGACY_SIZES.items():
    resized = master.resize((size, size), Image.Resampling.LANCZOS)
    icon = clean_transparent_rgb(resized, alpha_cutoff=8)
    output_dir = RES / f"mipmap-{density}"
    icon.save(output_dir / "ic_launcher.png", format="PNG", optimize=True)
    icon.save(output_dir / "ic_launcher_round.png", format="PNG", optimize=True)
