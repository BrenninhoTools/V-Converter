import sys
from pathlib import Path
from PIL import Image

CELL = 150
COLUMNS = 10
ICONS = {
    "bf": (0, 1), "spooky": (2, 3), "pico": (4, 5), "mom": (6, 7), "tankman": (8, 9),
    "face": (10, 11), "dad": (12, 13), "bf-old": (14, 15), "gf": (16, 16),
    "parents-christmas": (17, 18), "monster": (19, 20), "bf-pixel": (21, 21),
    "senpai": (22, 22), "spirit": (23, 23),
}


def cell(grid, index):
    x = index % COLUMNS * CELL
    y = index // COLUMNS * CELL
    return grid.crop((x, y, x + CELL, y + CELL))


def main(source, target):
    grid = Image.open(source).convert("RGBA")
    out = Path(target)
    out.mkdir(parents=True, exist_ok=True)
    for name, (normal, losing) in ICONS.items():
        icon = Image.new("RGBA", (CELL * 2, CELL), (0, 0, 0, 0))
        icon.paste(cell(grid, normal), (0, 0))
        icon.paste(cell(grid, losing), (CELL, 0))
        icon.save(out / f"icon-{name}.png", optimize=True)
        print(name)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "assets/images/icons")
