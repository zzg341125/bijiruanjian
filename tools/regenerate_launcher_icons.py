from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = ROOT / "docs" / "quyang-icon-source.png"
MASTER = RES / "drawable-nodpi" / "quyang_launcher_art.png"
ADAPTIVE_BACKGROUND = RES / "drawable-nodpi" / "smartisan_notes_launcher.png"
ADAPTIVE_BACKGROUND_FRAME = RES / "drawable-nodpi" / "quyang_launcher_background.png"
ADAPTIVE_FOREGROUND = RES / "drawable-nodpi" / "quyang_launcher_foreground.png"
PREVIEW = ROOT / "docs" / "quyang-icon-preview.png"
ARTWORK_SCALE = 0.92
FRAME_HORIZONTAL_STRETCH = 1.16
ADAPTIVE_ART_SCALE = 0.82
BACKGROUND_COLOR = (251, 247, 237, 255)
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def transparent_square_icon(image: Image.Image) -> Image.Image:
    """Remove the solid background, crop the 3D note, and fit it to a square."""
    rgba = image.convert("RGBA")
    for corner in (
        (0, 0),
        (rgba.width - 1, 0),
        (0, rgba.height - 1),
        (rgba.width - 1, rgba.height - 1),
    ):
        ImageDraw.floodfill(rgba, corner, (0, 0, 0, 0), thresh=28)
    alpha = rgba.getchannel("A")
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.55))
    rgba.putalpha(alpha)
    bounds = alpha.getbbox()
    if bounds is None:
        raise RuntimeError("Could not find icon artwork")
    cropped = rgba.crop(bounds)
    square = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    side = round(square.width * ARTWORK_SCALE)
    inset = (square.width - side) // 2
    square.alpha_composite(
        cropped.resize((side, side), Image.Resampling.LANCZOS),
        (inset, inset),
    )
    return square


def square_frame(icon: Image.Image) -> Image.Image:
    """Compensate for the pencil overhang so the raised note frame itself is square."""
    widened = icon.resize(
        (round(icon.width * FRAME_HORIZONTAL_STRETCH), icon.height),
        Image.Resampling.LANCZOS,
    )
    side = round(icon.width * ARTWORK_SCALE)
    height = round(widened.height * side / widened.width)
    square = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    square.alpha_composite(
        widened.resize((side, height), Image.Resampling.LANCZOS),
        ((square.width - side) // 2, (square.height - height) // 2),
    )
    return square


master = square_frame(transparent_square_icon(Image.open(SOURCE)))
foreground = Image.new("RGBA", master.size, (0, 0, 0, 0))
foreground_side = round(master.width * ADAPTIVE_ART_SCALE)
foreground_inset = (foreground.width - foreground_side) // 2
foreground.alpha_composite(
    master.resize((foreground_side, foreground_side), Image.Resampling.LANCZOS),
    (foreground_inset, foreground_inset),
)
background = Image.new("RGBA", master.size, BACKGROUND_COLOR)
legacy = background.copy()
legacy.alpha_composite(master)
foreground.save(ADAPTIVE_FOREGROUND, format="PNG", optimize=True)
background.save(ADAPTIVE_BACKGROUND_FRAME, format="PNG", optimize=True)
legacy.save(MASTER, format="PNG", optimize=True)
legacy.save(ADAPTIVE_BACKGROUND, format="PNG", optimize=True)
legacy.save(PREVIEW, format="PNG", optimize=True)

for density, size in LEGACY_SIZES.items():
    icon = legacy.resize((size, size), Image.Resampling.LANCZOS)
    output_dir = RES / f"mipmap-{density}"
    icon.save(output_dir / "ic_launcher.png", format="PNG", optimize=True)
    round_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(round_mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    round_icon = icon.copy()
    round_icon.putalpha(round_mask)
    round_icon.save(output_dir / "ic_launcher_round.png", format="PNG", optimize=True)
