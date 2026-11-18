import random
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

source_png, source_xml, font_path, out_dir = sys.argv[1:5]
out = Path(out_dir)
out.mkdir(parents=True, exist_ok=True)

SS = 2
THICK = 3


def parse(xml):
    frames = []
    for m in re.finditer(r'<SubTexture\s+([^>]*?)/>', xml):
        attrs = dict(re.findall(r'(\w+)="([^"]*)"', m.group(1)))
        frames.append(attrs)
    return frames


def pack(name, frames, images):
    unique = []
    index = {}
    for frame, image in zip(frames, images):
        key = image.tobytes() + bytes(str(image.size), "ascii")
        if key not in index:
            index[key] = len(unique)
            unique.append(image)
        frame["region"] = index[key]
    width = 2048
    x = y = row = 0
    spots = []
    for image in unique:
        if x + image.width > width:
            x = 0
            y += row + 10
            row = 0
        spots.append((x, y))
        x += image.width + 10
        row = max(row, image.height)
    sheet = Image.new("RGBA", (min(width, max(sx + im.width for (sx, _), im in zip(spots, unique))), y + row), (0, 0, 0, 0))
    for (sx, sy), image in zip(spots, unique):
        sheet.paste(image, (sx, sy))
    lines = ['<?xml version="1.0" encoding="utf-8"?>', f'<TextureAtlas imagePath="{name}.png">']
    for frame in frames:
        sx, sy = spots[frame["region"]]
        image = unique[frame["region"]]
        extra = ""
        if "frameX" in frame:
            extra = f' frameX="{frame["frameX"]}" frameY="{frame["frameY"]}" frameWidth="{frame["frameWidth"]}" frameHeight="{frame["frameHeight"]}"'
        lines.append(f'\t<SubTexture name="{frame["name"]}" x="{sx}" y="{sy}" width="{image.width}" height="{image.height}"{extra}/>')
    lines.append("</TextureAtlas>")
    sheet.save(out / f"{name}.png", optimize=True)
    (out / f"{name}.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


atlas = Image.open(source_png).convert("RGBA")
original = parse(Path(source_xml).read_text(encoding="utf-8"))
for word, name in [("story mode", "story-mode"), ("freeplay", "freeplay"), ("donate", "donate"), ("options", "options")]:
    frames = [dict(f) for f in original if f["name"].startswith(word + " ")]
    images = [atlas.crop((int(f["x"]), int(f["y"]), int(f["x"]) + int(f["width"]), int(f["y"]) + int(f["height"]))) for f in frames]
    pack(name, frames, images)


def render(text, size, fill, stroke, stroke_width, seed, wobble):
    rng = random.Random(seed)
    base = random.Random(len(text) * 7919 + sum(map(ord, text)))
    font = ImageFont.truetype(font_path, size * SS)
    canvas_w = int(size * SS * (len(text) * 0.95 + 2))
    canvas_h = int(size * SS * 2.2)
    strokes = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    fills = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    pen = size * SS * 0.5
    baseline = canvas_h * 0.62
    for char in text:
        if char == " ":
            pen += size * SS * 0.42
            continue
        small = char == "O"
        scale = (0.74 if small else base.uniform(0.94, 1.06))
        rotation = base.uniform(-7, 7) + rng.uniform(-wobble, wobble)
        lift = base.uniform(-5, 5) * SS + rng.uniform(-wobble, wobble) * SS * 0.8
        glyph_font = ImageFont.truetype(font_path, max(8, int(size * SS * scale)))
        left, top, right, bottom = glyph_font.getbbox(char, stroke_width=(stroke_width + THICK) * SS)
        gw, gh = right - left + stroke_width * SS * 2, bottom - top + stroke_width * SS * 2
        pad = stroke_width * SS * 2
        layer_s = Image.new("RGBA", (gw + pad * 2, gh + pad * 2), (0, 0, 0, 0))
        layer_f = Image.new("RGBA", (gw + pad * 2, gh + pad * 2), (0, 0, 0, 0))
        ImageDraw.Draw(layer_s).text((pad - left, pad - top), char, font=glyph_font, fill=stroke, stroke_width=(stroke_width + THICK) * SS, stroke_fill=stroke)
        ImageDraw.Draw(layer_f).text((pad - left, pad - top), char, font=glyph_font, fill=fill, stroke_width=THICK * SS, stroke_fill=fill)
        layer_s = layer_s.rotate(rotation, resample=Image.BICUBIC, expand=True)
        layer_f = layer_f.rotate(rotation, resample=Image.BICUBIC, expand=True)
        glyph_h = (bottom - top)
        y = int(baseline - glyph_h - (glyph_h * 0.18 if small else 0) + lift - (layer_s.height - glyph_h) / 2)
        x = int(pen - (layer_s.width - (right - left)) / 2)
        strokes.alpha_composite(layer_s, (max(0, x), max(0, y)))
        fills.alpha_composite(layer_f, (max(0, x), max(0, y)))
        advance = glyph_font.getlength(char)
        pen += advance * (0.92 if small else 1.0) + THICK * SS
    strokes.alpha_composite(fills)
    return strokes


def frames_for(text, size, fill, stroke, stroke_width, wobble, count, seed):
    renders = [render(text, size, fill, stroke, stroke_width, seed + i, wobble) for i in range(count)]
    box = None
    for image in renders:
        b = image.getbbox()
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    box = (box[0] - 2, box[1] - 2, box[2] + 2, box[3] + 2)
    result = []
    for image in renders:
        cropped = image.crop(box)
        result.append(cropped.resize((max(1, cropped.width // SS), max(1, cropped.height // SS)), Image.LANCZOS))
    return result


for word, name in [("CONVERTER", "converter"), ("GENERATOR", "generator"), ("ONLINE", "online")]:
    basic = frames_for(word, 104, (0, 0, 0, 255), (255, 255, 255, 255), 8, 1.2, 3, 11)
    white = frames_for(word, 146, (255, 255, 255, 255), (0, 0, 0, 255), 10, 2.2, 3, 41)
    frames = []
    images = []
    for i in range(9):
        frames.append({"name": f"{name} basic{i:04d}"})
        images.append(basic[i // 3])
    for i in range(3):
        frames.append({"name": f"{name} white{i:04d}"})
        images.append(white[i])
    pack(name, frames, images)
    print(name, basic[0].size, white[0].size)
