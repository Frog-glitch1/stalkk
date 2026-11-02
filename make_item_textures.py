"""Generates the flashlight item textures (16x16) and the mod icon (128x128)."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/stalker"
(ROOT / "textures/item").mkdir(parents=True, exist_ok=True)


def flashlight(on):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    handle, handle_hi = (58, 58, 66), (92, 92, 102)
    head, head_hi = (120, 120, 130), (165, 165, 175)
    lens = (255, 245, 150) if on else (150, 175, 185)
    for t in range(0, 6):                              # handle (diagonal, 2 px thick)
        px[2 + t, 13 - t] = handle_hi
        px[3 + t, 13 - t] = handle
    px[4, 11] = (175, 45, 45)                          # switch
    for t in range(6, 9):                              # wider head
        px[2 + t, 13 - t] = head_hi
        px[3 + t, 13 - t] = head
        px[4 + t, 13 - t] = head
    for x in (11, 12, 13):                             # lens
        px[x, 4] = lens
    if on:
        for x, y, a in ((14, 3, 255), (15, 2, 200), (14, 1, 200), (13, 2, 255), (15, 4, 160), (12, 2, 160)):
            px[x, y] = (255, 240, 120, a)
    # dark outline
    out = img.copy()
    op = out.load()
    for x in range(16):
        for y in range(16):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and px[nx, ny][3] == 255 and px[nx, ny][:3] != (255, 240, 120):
                        op[x, y] = (18, 18, 22, 255)
    return out


flashlight(False).save(ROOT / "textures/item/flashlight.png")
flashlight(True).save(ROOT / "textures/item/flashlight_on.png")

# icon: the Stalker's face, big and wrong
face = Image.new("RGBA", (16, 16), (10, 10, 14, 255))
fp = face.load()
for x in range(4, 12):
    for y in range(1, 15):
        fp[x, y] = (186, 183, 174, 255)
for x in (4, 5, 10, 11):
    for y in (4, 5, 6):
        fp[x, y] = (6, 6, 8, 255)
for x, y in ((5, 5), (5, 6), (10, 5), (10, 6)):
    fp[x, y] = (232, 238, 255, 255)
for x in range(4, 12):
    fp[x, 11] = (226, 222, 205, 255) if x % 2 == 0 else (25, 15, 15, 255)
    fp[x, 12] = (14, 8, 8, 255)
fp[7, 8] = fp[8, 8] = (45, 42, 42, 255)
face.resize((128, 128), Image.NEAREST).save(ROOT / "icon.png")
print("ok")
