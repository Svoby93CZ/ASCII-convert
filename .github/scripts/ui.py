#!/usr/bin/env python3
"""Helpers for the emulator smoke test.

  ui.py find <window.xml> <label>…  print "x y" of the first node whose text or description matches
  ui.py summary <window.xml>        print the texts and descriptions visible on screen
  ui.py encode <screenshot.png>     print a small JPEG of the screenshot as base64 lines
  ui.py sample <out.jpg>            write a colourful test photo (used when downloading fails)
"""
import base64
import io
import re
import sys
import xml.etree.ElementTree as ElementTree


def nodes(path):
    try:
        return list(ElementTree.parse(path).getroot().iter("node"))
    except (ElementTree.ParseError, OSError):
        return []


def find(path, *labels):
    """Tries the labels in order and prints the centre of the first matching node."""
    for label in labels:
        if find_one(path, label):
            return


def find_one(path, label):
    exact = partial = None
    for node in nodes(path):
        for attribute in ("text", "content-desc"):
            value = (node.get(attribute) or "").strip()
            if not value:
                continue
            if value == label and exact is None:
                exact = node
            elif label.lower() in value.lower() and partial is None:
                partial = node
    node = exact if exact is not None else partial
    if node is None:
        return False
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return False
    x1, y1, x2, y2 = map(int, match.groups())
    print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
    return True


def summary(path):
    seen = []
    for node in nodes(path):
        for attribute in ("text", "content-desc"):
            value = (node.get(attribute) or "").strip()
            if value and value not in seen:
                seen.append(value)
    print("UI: " + (" | ".join(seen[:100]) if seen else "(no UI dump)"))


def encode(path):
    from PIL import Image

    image = Image.open(path).convert("RGB")
    image.thumbnail((480, 1066))
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=74)
    data = base64.b64encode(buffer.getvalue()).decode()
    # Long lines keep the whole walkthrough within the log window that tools can fetch.
    for start in range(0, len(data), 4000):
        print(data[start:start + 4000])


def sample(path):
    from PIL import Image, ImageDraw

    image = Image.new("RGB", (800, 600))
    draw = ImageDraw.Draw(image)
    for y in range(600):
        draw.line([(0, y), (800, y)], fill=(20 + y // 4, 40 + y // 5, 120 + y // 6))
    draw.ellipse([80, 120, 380, 420], fill=(240, 200, 40))
    draw.rectangle([430, 180, 720, 470], fill=(200, 40, 60))
    draw.polygon([(400, 80), (560, 300), (240, 300)], fill=(40, 180, 90))
    image.save(path, "JPEG", quality=92)


if __name__ == "__main__":
    command, *arguments = sys.argv[1:]
    {"find": find, "summary": summary, "encode": encode, "sample": sample}[command](*arguments)
