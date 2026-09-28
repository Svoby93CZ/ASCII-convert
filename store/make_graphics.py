#!/usr/bin/env python3
"""Renders the Google Play graphics: the 512x512 icon and the 1024x500 feature graphics.

The icon is drawn from the launcher icon's vector layers, the feature graphics show the same
ASCII donut as the home screen. Needs Chromium or Google Chrome for rendering:

    python3 store/make_graphics.py            # finds chromium / google-chrome on PATH
    CHROME=/path/to/chrome python3 store/make_graphics.py
"""
import html
import math
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store"
RES = ROOT / "app" / "src" / "main" / "res"
FONTS = RES / "font"

# Feature graphic texts per store listing language.
FEATURE_TEXTS = {
    "cs": ("Proměňte fotky v ASCII art", "Braille · Barvy z fotky · Obrysy · Kamera"),
    "en": ("Turn photos into ASCII art", "Braille · Photo colors · Outlines · Camera"),
}


def vector_layer(name: str) -> str:
    """Converts a launcher icon layer (VectorDrawable with linear gradients) to SVG elements."""
    xml = (RES / "drawable" / f"{name}.xml").read_text()
    elements = []
    for index, path in enumerate(re.findall(r"<path(.*?)</path>|<path(.*?)/>", xml, re.S)):
        body = path[0] or path[1]
        data = re.search(r'android:pathData="([^"]+)"', body).group(1)
        gradient = re.search(
            r'startX="([\d.]+)"\s+android:startY="([\d.]+)"\s+android:endX="([\d.]+)"\s+android:endY="([\d.]+)"(.*?)</gradient>',
            body,
            re.S,
        )
        if gradient:
            x1, y1, x2, y2, items = gradient.groups()
            stops = "".join(
                f'<stop offset="{offset}" stop-color="#{color[3:]}"/>'
                for offset, color in re.findall(r'android:offset="([\d.]+)"\s+android:color="(#\w+)"', items)
            )
            gradient_id = f"{name}{index}"
            elements.append(
                f'<linearGradient id="{gradient_id}" gradientUnits="userSpaceOnUse" '
                f'x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stops}</linearGradient>'
                f'<path d="{data}" fill="url(#{gradient_id})"/>'
            )
        else:
            color = re.search(r'android:fillColor="#(\w{2})(\w{6})"', body)
            opacity = int(color.group(1), 16) / 255
            elements.append(f'<path d="{data}" fill="#{color.group(2)}" fill-opacity="{opacity:.3f}"/>')
    return "".join(elements)


def icon_svg(size: int) -> str:
    # The visible part of an adaptive icon is the central 72x72 of its 108x108 canvas.
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
        f'{vector_layer("ic_launcher_background")}{vector_layer("ic_launcher_foreground")}</svg>'
    )


def donut(columns=60, rows=26, time=0.8, cell_aspect=600 / 1320):
    """The home screen donut (engine Donut.kt) with the colours of DonutHero.kt."""
    shades = ".,-~:;=!*#$@"
    a, b = 1 + time * 1.1, time * 0.55
    cos_a, sin_a, cos_b, sin_b = math.cos(a), math.sin(a), math.cos(b), math.sin(b)
    chars = [[" "] * columns for _ in range(rows)]
    colors = [[None] * columns for _ in range(rows)]
    depth = [[0.0] * columns for _ in range(rows)]
    scale_x = columns * 5 * 3 / (8 * 3)
    scale_y = scale_x * cell_aspect
    dark, bright = (0x14, 0x73, 0x5A), (0xB9, 0xFF, 0xD8)
    theta = 0.0
    while theta < 2 * math.pi:
        cos_t, sin_t = math.cos(theta), math.sin(theta)
        circle_x, circle_y = 2 + cos_t, sin_t
        phi = 0.0
        while phi < 2 * math.pi:
            cos_p, sin_p = math.cos(phi), math.sin(phi)
            x = circle_x * (cos_b * cos_p + sin_a * sin_b * sin_p) - circle_y * cos_a * sin_b
            y = circle_x * (sin_b * cos_p - sin_a * cos_b * sin_p) + circle_y * cos_a * cos_b
            ooz = 1 / (5 + cos_a * circle_x * sin_p + circle_y * sin_a)
            column = int(columns / 2 + scale_x * ooz * x)
            row = int(rows / 2 - scale_y * ooz * y)
            light = (cos_p * cos_t * sin_b - cos_a * cos_t * sin_p - sin_a * sin_t
                     + cos_b * (cos_a * sin_t - cos_t * sin_a * sin_p))
            if light > 0 and 0 <= column < columns and 0 <= row < rows and ooz > depth[row][column]:
                depth[row][column] = ooz
                chars[row][column] = shades[min(int(light * 8), len(shades) - 1)]
                t = min(max(light / math.sqrt(2), 0), 1) ** 0.8
                colors[row][column] = "#%02x%02x%02x" % tuple(round(d + (e - d) * t) for d, e in zip(dark, bright))
            phi += 0.02
        theta += 0.07
    # Crop to the drawn cells.
    used_rows = [r for r in range(rows) if any(c != " " for c in chars[r])]
    used_columns = [c for c in range(columns) if any(chars[r][c] != " " for r in range(rows))]
    return [
        [(chars[r][c], colors[r][c]) for c in range(used_columns[0], used_columns[-1] + 1)]
        for r in range(used_rows[0], used_rows[-1] + 1)
    ]


def donut_html(cells, font_size):
    lines = []
    for row in cells:
        line = "".join(
            f'<span style="color:{color}">{html.escape(char)}</span>' if color else " " for char, color in row
        )
        lines.append(line)
    return f'<pre class="donut" style="font-size:{font_size:.2f}px">' + "\n".join(lines) + "</pre>"


def feature_html(language: str) -> str:
    tagline, features = FEATURE_TEXTS[language]
    cells = donut()
    # Fit the donut into 440 px of height; JetBrains Mono cells are 0.6 x 1.32 em.
    font_size = min(440 / (len(cells) * 1.32), 400 / (len(cells[0]) * 0.6))
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Mono; src: url('{(FONTS / "jetbrains_mono_regular.ttf").as_uri()}'); }}
@font-face {{ font-family: Mono; font-weight: bold; src: url('{(FONTS / "jetbrains_mono_bold.ttf").as_uri()}'); }}
html, body {{ margin: 0; width: 1024px; height: 500px; overflow: hidden; }}
body {{
  font-family: Mono; color: #cfe9db;
  font-variant-ligatures: none; font-feature-settings: "calt" 0, "liga" 0;
  background:
    repeating-linear-gradient(to bottom, rgba(255,255,255,0.045) 0 2px, transparent 2px 12px),
    linear-gradient(135deg, #1b2b23 0%, #060b08 100%);
  display: flex; align-items: center; justify-content: space-between; padding: 0 56px 0 64px;
  box-sizing: border-box;
}}
.text {{ display: flex; flex-direction: column; gap: 18px; }}
.icon {{ width: 88px; height: 88px; border-radius: 22px; overflow: hidden; box-shadow: 0 0 0 1px rgba(157,255,196,0.18); }}
.icon svg {{ display: block; width: 88px; height: 88px; }}
h1 {{
  margin: 6px 0 0; font-size: 64px; font-weight: bold; letter-spacing: -1px; line-height: 1;
  background: linear-gradient(120deg, #9dffc4, #22d3b8); -webkit-background-clip: text; color: transparent;
}}
.tagline {{ font-size: 28px; color: #e3f5eb; }}
.features {{ font-size: 18px; color: #7fb89a; }}
.donut {{ margin: 0; font-family: Mono; line-height: 1.32; letter-spacing: 0; color: #14735a; }}
</style></head><body>
<div class="text">
  <div class="icon">{icon_svg(88)}</div>
  <h1>ASCII Studio</h1>
  <div class="tagline">{html.escape(tagline)}</div>
  <div class="features">{html.escape(features)}</div>
</div>
{donut_html(cells, font_size)}
</body></html>"""


def find_chrome() -> str:
    candidates = [os.environ.get("CHROME")] + [
        shutil.which(name) for name in ("chromium", "chromium-browser", "google-chrome", "google-chrome-stable")
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    raise SystemExit("Chromium or Google Chrome is needed: set CHROME=/path/to/chrome")


def render(chrome: str, page: str, width: int, height: int, output: Path):
    with tempfile.TemporaryDirectory() as temp:
        source = Path(temp) / "page.html"
        source.write_text(page, encoding="utf-8")
        subprocess.run(
            [
                chrome, "--headless", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
                "--force-device-scale-factor=1", f"--window-size={width},{height}",
                "--virtual-time-budget=3000", f"--screenshot={output}", source.as_uri(),
            ],
            check=True,
            capture_output=True,
        )
    print(f"wrote {output.relative_to(ROOT)}")


def main():
    chrome = find_chrome()
    icon_page = f'<!doctype html><html><body style="margin:0">{icon_svg(512)}</body></html>'
    render(chrome, icon_page, 512, 512, STORE / "icon-512.png")
    for language in FEATURE_TEXTS:
        render(chrome, feature_html(language), 1024, 500, STORE / f"feature-graphic-{language}.png")


if __name__ == "__main__":
    main()
