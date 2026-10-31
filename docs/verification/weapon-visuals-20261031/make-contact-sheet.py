"""Crop the central arena from captured app screenshots; never substitutes for capturing the app."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).parent
directory = root / (sys.argv[1] if len(sys.argv) > 1 else 'emulator')
weapons = ['spectre', 'hunter', 'sentry', 'tesla_mine', 'oscillator', 'meteor',
           'chopper', 'minefield', 'blade_trap', 'orbiter', 'guardian']
font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 22)
width, height = 420, 365
sheet = Image.new('RGB', (width * 3, height * 4), '#efefe5')
draw = ImageDraw.Draw(sheet)
for index, weapon in enumerate(weapons):
    image = Image.open(directory / f'weapon-{weapon}.png').convert('RGB')
    w, h = image.size
    crop = image.crop((int(w * .12), max(0, int(h * .5 - w * .4)),
                       int(w * .94), min(h, int(h * .5 + w * .4))))
    crop.thumbnail((width-16, height-40), Image.Resampling.LANCZOS)
    x, y = index % 3 * width, index // 3 * height
    sheet.paste(crop, (x + (width-crop.width)//2, y+38))
    draw.text((x+16, y+8), weapon.replace('_', ' ').title(), font=font, fill='#464646')
    draw.rectangle((x, y, x+width-1, y+height-1), outline='#c7c7bc')
sheet.save(root / f'{directory.name}-contact-sheet.png')
print(root / f'{directory.name}-contact-sheet.png')
