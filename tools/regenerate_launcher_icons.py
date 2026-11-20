from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = ROOT / "docs" / "quyang-icon-source.png"
MASTER = RES / "drawable-nodpi" / "quyang_launcher_art.png"
ADAPTIVE_BACKGROUND = RES / "drawable-nodpi" / "smartisan_notes_launcher.png"
PREVIEW = ROOT / "docs" / "quyang-icon-preview.png"
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


master = Image.open(SOURCE).convert("RGBA").resize(
    (1024, 1024),
    Image.Resampling.LANCZOS,
)
master.save(MASTER, format="PNG", optimize=True)
master.save(ADAPTIVE_BACKGROUND, format="PNG", optimize=True)
master.save(PREVIEW, format="PNG", optimize=True)

for density, size in LEGACY_SIZES.items():
    icon = master.resize((size, size), Image.Resampling.LANCZOS)
    output_dir = RES / f"mipmap-{density}"
    icon.save(output_dir / "ic_launcher.png", format="PNG", optimize=True)
    round_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(round_mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    round_icon = icon.copy()
    round_icon.putalpha(round_mask)
    round_icon.save(output_dir / "ic_launcher_round.png", format="PNG", optimize=True)
