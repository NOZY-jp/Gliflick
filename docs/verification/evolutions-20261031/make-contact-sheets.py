"""Arrange actual emulator captures for review; does not synthesize application UI."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).parent
captures = root / "evolutions"
font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 22)
weapons = ["spectre", "hunter", "sentry", "tesla_mine", "oscillator", "meteor",
           "chopper", "minefield", "blade_trap", "orbiter", "guardian"]
sheet = Image.new("RGB", (1260, 1460), "#efefe5")
draw = ImageDraw.Draw(sheet)
for i, weapon in enumerate(weapons):
    image = Image.open(captures / f"evolved-{weapon}.png").convert("RGB")
    w, h = image.size
    crop = image.crop((int(w * .12), max(0, int(h * .5 - w * .4)),
                       int(w * .94), min(h, int(h * .5 + w * .4))))
    crop.thumbnail((404, 325), Image.Resampling.LANCZOS)
    x, y = i % 3 * 420, i // 3 * 365
    sheet.paste(crop, (x + (420 - crop.width) // 2, y + 38))
    draw.text((x + 16, y + 8), weapon.replace("_", " ").title(), font=font, fill="#464646")
    draw.rectangle((x, y, x + 419, y + 364), outline="#c7c7bc")
sheet.save(root / "evolved-actors.png")

cards = ["spectre", "chopper", "guardian"]
sheet = Image.new("RGB", (1080, 780), "#efefe5")
draw = ImageDraw.Draw(sheet)
for i, weapon in enumerate(cards):
    image = Image.open(captures / f"choices-{weapon}.png").convert("RGB")
    image.thumbnail((352, 742), Image.Resampling.LANCZOS)
    sheet.paste(image, (i * 360 + (360 - image.width) // 2, 34))
    draw.text((i * 360 + 12, 5), weapon.title(), font=font, fill="#464646")
sheet.save(root / "evolution-choices.png")
print("Saved actor and choice contact sheets")
