"""Crop a rounded note reference and remove its white surroundings."""

from argparse import ArgumentParser
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


def crop_reference(source: Path, destination: Path) -> None:
    image = Image.open(source).convert("RGBA")
    side = int(min(image.size) * 0.926)
    left = int(image.width * 0.483) - side // 2
    top = int(image.height * 0.497) - side // 2
    square = image.crop((left, top, left + side, top + side))

    mask = Image.new("L", square.size, 0)
    draw = ImageDraw.Draw(mask)
    inset = int(side * 0.003)
    radius = int(side * 0.22)
    draw.rounded_rectangle(
        (inset, inset, side - inset, side - inset),
        radius=radius,
        fill=255,
    )
    mask = mask.filter(ImageFilter.GaussianBlur(radius=max(1, side // 512)))
    square.putalpha(mask)

    final = square.resize((1024, 1024), Image.Resampling.LANCZOS)
    final = final.filter(ImageFilter.UnsharpMask(radius=1.1, percent=115, threshold=2))
    destination.parent.mkdir(parents=True, exist_ok=True)
    final.save(destination, format="PNG", optimize=True)


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    crop_reference(args.source, args.destination)
