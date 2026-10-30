"""Rasterize Frame 3's grid and vector marks for the Android Canvas renderer."""

from pathlib import Path
import xml.etree.ElementTree as ET

import cairosvg


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs/verification/frame3-assets/grid.svg"
VECTOR_OUTPUT = ROOT / "docs/verification/figma-grid-frame-3.svg"
RESOURCE_DIR = ROOT / "app/src/main/res/drawable-nodpi"
WIDTH = 1080
HEIGHT = 4096
NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", NS)

source_root = ET.parse(SOURCE).getroot()
lines = [element for element in source_root.iter(f"{{{NS}}}line")]
grid = ET.Element(
    f"{{{NS}}}svg",
    {"width": str(WIDTH), "height": str(HEIGHT), "viewBox": f"0 0 {WIDTH} {HEIGHT}"},
)

horizontal_positions = []
for index, source_line in enumerate(lines, 1):
    line = dict(source_line.attrib)
    x1, x2 = float(line.get("x1", "0")), float(line.get("x2", str(WIDTH)))
    y1, y2 = float(line.get("y1", "0")), float(line.get("y2", "0"))
    line["id"] = f"grid-{index}"
    if abs(x1 - x2) < 0.01:
        line["y1"], line["y2"] = "0", str(HEIGHT)
    else:
        horizontal_positions.append(y1)
    ET.SubElement(grid, f"{{{NS}}}line", line)

# The Figma frame ends at 2040px. Continue its 198px grid interval below that
# edge so the bitmap still covers taller phones and the area behind the IME.
last_y = max(horizontal_positions)
while last_y + 198 < HEIGHT:
    last_y += 198
    ET.SubElement(
        grid,
        f"{{{NS}}}line",
        {
            "x1": "0",
            "y1": str(last_y),
            "x2": str(WIDTH),
            "y2": str(last_y),
            "stroke": "#E4E4D8",
            "stroke-width": "3",
        },
    )

ET.indent(grid, space="  ")
svg_bytes = ET.tostring(grid, encoding="utf-8", xml_declaration=True)
VECTOR_OUTPUT.write_bytes(svg_bytes)
RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
cairosvg.svg2png(
    bytestring=svg_bytes,
    write_to=str(RESOURCE_DIR / "menu_grid.png"),
    output_width=WIDTH,
    output_height=HEIGHT,
)
for source, name in (
    (ROOT / "app/src/main/res/raw/title_target.svg", "title_target.png"),
    (ROOT / "app/src/main/res/raw/title_navigation_marks.svg", "title_navigation_marks.png"),
    (ROOT / "app/src/main/res/raw/warder_barrier.svg", "warder_barrier.png"),
):
    cairosvg.svg2png(
        url=str(source),
        write_to=str(RESOURCE_DIR / name),
        scale=4,
    )
print(f"Wrote Frame 3 backdrop PNGs to {RESOURCE_DIR.relative_to(ROOT)}")
