from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = ROOT / "docs" / "quyang-icon-source.jpg"
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
    """Remove the white JPEG corners while keeping the supplied rounded artwork."""
    image = image.convert("RGB")
    mask = Image.new("L", image.size, 0)
    mask_pixels = mask.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue = image.getpixel((x, y))
            if not (
                min(red, green, blue) > 222
                and max(red, green, blue) - min(red, green, blue) < 42
            ):
                mask_pixels[x, y] = 255
    for corner in (
        (0, 0),
        (image.width - 1, 0),
        (0, image.height - 1),
        (image.width - 1, image.height - 1),
    ):
        ImageDraw.floodfill(mask, corner, 0, thresh=20)
    mask = mask.filter(ImageFilter.GaussianBlur(0.6))

    rgba = image.convert("RGBA")
    rgba.putalpha(mask)
    bounds = mask.getbbox()
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
