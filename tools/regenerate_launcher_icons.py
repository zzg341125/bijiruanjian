from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = ROOT / "docs" / "quyang-icon-source.png"
MASTER = RES / "drawable-nodpi" / "quyang_launcher_art.png"
PREVIEW = ROOT / "docs" / "quyang-icon-preview.png"
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def crop_rounded_icon(image: Image.Image) -> Image.Image:
    """Crop transparent artwork, or remove a baked checkerboard first."""
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    if alpha.getextrema()[0] == 255:
        source = image.convert("RGB")
        mask = Image.new("L", source.size, 0)
        mask_pixels = mask.load()
        for y in range(source.height):
            for x in range(source.width):
                red, green, blue = source.getpixel((x, y))
                if min(red, green, blue) > 165 and max(red, green, blue) - min(red, green, blue) < 20:
                    mask_pixels[x, y] = 255
        for corner in (
            (0, 0),
            (source.width - 1, 0),
            (0, source.height - 1),
            (source.width - 1, source.height - 1),
        ):
            ImageDraw.floodfill(mask, corner, 0, thresh=0)
        alpha = mask.point(lambda value: 0 if value == 0 else 255)
        rgba.putalpha(alpha)
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.55))
    rgba.putalpha(alpha)
    bounds = alpha.getbbox()
    if bounds is None:
        raise RuntimeError("Could not find icon artwork")
    cropped = rgba.crop(bounds)
    side = max(cropped.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(
        cropped,
        (
            (side - cropped.width) // 2,
            (side - cropped.height) // 2,
        ),
    )
    return square.resize((1024, 1024), Image.Resampling.LANCZOS)


master = crop_rounded_icon(Image.open(SOURCE))
master.save(MASTER, format="PNG", optimize=True)
master.save(PREVIEW, format="PNG", optimize=True)

for density, size in LEGACY_SIZES.items():
    icon = master.resize((size, size), Image.Resampling.LANCZOS)
    output_dir = RES / f"mipmap-{density}"
    icon.save(output_dir / "ic_launcher.png", format="PNG", optimize=True)
    icon.save(output_dir / "ic_launcher_round.png", format="PNG", optimize=True)
