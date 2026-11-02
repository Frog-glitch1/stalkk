"""
Generates stalker.png and stalker_eyes.png (64x64).

UV layout (u, v, w, h, d):
  head  (0, 0, 6,10,6)     jaw   (24,0, 5, 4,5)
  body  (0,16, 5,16,3)     arm   (16,16,2,22,2)
  hand  (32,16,3, 4,3)     claws (32,23,3, 5,1)
  leg   (44,16,2,20,2)
"""
import random
from pathlib import Path
from PIL import Image

random.seed(1337)
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/stalker/textures/entity"
OUT.mkdir(parents=True, exist_ok=True)

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px, ex = img.load(), eyes.load()

SKIN = (184, 181, 172)
clamp = lambda v: max(0, min(255, v))


def faces(u, v, w, h, d):
    return {
        "left": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "right": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
    }


def paint_box(u, v, w, h, d, base=SKIN, var=9):
    f = faces(u, v, w, h, d)
    for name, (x, y, fw, fh) in f.items():
        shade = {"bottom": -25, "top": 8}.get(name, 0)
        for i in range(x, x + fw):
            for j in range(y, y + fh):
                n = random.randint(-var, var) + shade
                px[i, j] = (clamp(base[0] + n), clamp(base[1] + n), clamp(base[2] + n - 2), 255)
    return f


def put(x, y, c):
    px[x, y] = (*c, 255)


# ---- head -----------------------------------------------------------------
head = paint_box(0, 0, 6, 10, 6)
fx, fy, fw, fh = head["front"]            # 6, 6, 6, 10
for x in range(fx, fx + fw):
    put(x, fy + 1, (140, 137, 130))        # heavy brow shadow
for x in (fx, fx + 1, fx + 4, fx + 5):     # hollow eye sockets (3 rows tall)
    for y in (fy + 2, fy + 3, fy + 4):
        put(x, y, (8, 8, 10))
for x, y in ((fx + 1, fy + 3), (fx + 1, fy + 4), (fx + 4, fy + 3), (fx + 4, fy + 4)):
    ex[x, y] = (232, 238, 255, 255)        # glowing pinprick pupils
for x in (fx + 2, fx + 3):
    put(x, fy + 5, (45, 42, 42))           # nose slits
for y in (fy + 6, fy + 7):                 # sunken cheeks
    put(fx, y, (150, 147, 140)); put(fx + 5, y, (150, 147, 140))
for k in range(fw):                        # upper teeth, then black throat
    put(fx + k, fy + 8, (226, 222, 205) if k % 2 == 0 else (25, 15, 15))
    put(fx + k, fy + 9, (14, 8, 8))

# ---- jaw ------------------------------------------------------------------
jaw = paint_box(24, 0, 5, 4, 5)
tx, ty, tw, th = jaw["top"]
for x in range(tx, tx + tw):
    for y in range(ty, ty + th):
        put(x, y, (30, 12, 12))            # mouth interior
jx, jy, jw, jh = jaw["front"]
for k in range(jw):
    put(jx + k, jy, (226, 222, 205) if k % 2 == 0 else (25, 15, 15))

# ---- torso (ribs + spine) -------------------------------------------------
body = paint_box(0, 16, 5, 16, 3)
bx, by, bw, bh = body["front"]
for row in (2, 4, 6, 8, 10):
    for x in range(bx, bx + bw):
        put(x, by + row, (120, 118, 112))
for row in range(1, bh - 1):
    put(bx + 2, by + row, (150, 148, 141))  # sternum
kx, ky, kw, kh = body["back"]
for row in range(0, kh, 2):
    put(kx + 2, ky + row, (95, 93, 88))     # vertebrae

# ---- limbs ----------------------------------------------------------------
paint_box(16, 16, 2, 22, 2)
paint_box(32, 16, 3, 4, 3, base=(160, 157, 150))
paint_box(32, 23, 3, 5, 1, base=(58, 56, 53), var=5)   # long dark claws
paint_box(44, 16, 2, 20, 2)

# ---- grime pass -------------------------------------------------------------
for i in range(64):
    for j in range(64):
        r, g, b, a = px[i, j]
        if a and random.random() < 0.05:
            d = random.randint(20, 45)
            px[i, j] = (clamp(r - d), clamp(g - d), clamp(b - d), a)

img.save(OUT / "stalker.png")
eyes.save(OUT / "stalker_eyes.png")
print("wrote", OUT)
