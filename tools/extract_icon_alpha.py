"""Extract a note icon from a baked checkerboard background."""

from argparse import ArgumentParser
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


def extract(source: Path, destination: Path) -> None:
    image = Image.open(source).convert("RGB")
    candidate = Image.new("L", image.size, 1)
    candidate_pixels = candidate.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue = image.getpixel((x, y))
            if min(red, green, blue) > 165 and max(red, green, blue) - min(red, green, blue) < 20:
                candidate_pixels[x, y] = 255

    for corner in (
        (0, 0),
        (image.width - 1, 0),
        (0, image.height - 1),
        (image.width - 1, image.height - 1),
    ):
        ImageDraw.floodfill(candidate, corner, 0, thresh=0)

    alpha = candidate.point(lambda value: 0 if value == 0 else 255)
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.7))
    bounds = alpha.getbbox()
    if bounds is None:
        raise RuntimeError("Could not find icon foreground")

    rgba = image.convert("RGBA")
    rgba.putalpha(alpha)
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
    final = square.resize((1024, 1024), Image.Resampling.LANCZOS)
    destination.parent.mkdir(parents=True, exist_ok=True)
    final.save(destination, format="PNG", optimize=True)


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    extract(args.source, args.destination)
