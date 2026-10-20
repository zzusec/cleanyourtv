"""Check the density fallbacks without Pillow or an Android runtime."""
from pathlib import Path
import struct
import unittest
import zlib


RES = Path(__file__).resolve().parents[1] / "launcher/app/src/main/res"
ICON_DP = {"av": 20, "ethernet": 22, "home_settings": 48, "input": 22,
           "livetv": 20, "lock": 24, "settings": 22, "wifi": 22}
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def rgba_png(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    compressed = bytearray()
    offset = 8
    while offset < len(data):
        length, kind = struct.unpack_from(">I4s", data, offset)
        payload = data[offset + 8:offset + 8 + length]
        if kind == b"IHDR":
            width, height, depth, color, _, _, interlace = struct.unpack(">2I5B", payload)
            if (depth, color, interlace) != (8, 6, 0):
                raise ValueError("expected non-interlaced RGBA8")
        elif kind == b"IDAT":
            compressed.extend(payload)
        offset += length + 12
    raw = zlib.decompress(compressed)
    stride = width * 4
    previous = bytearray(stride)
    pixels = []
    for y in range(height):
        start = y * (stride + 1)
        mode = raw[start]
        row = bytearray(raw[start + 1:start + 1 + stride])
        for x in range(stride):
            left = row[x - 4] if x >= 4 else 0
            up = previous[x]
            corner = previous[x - 4] if x >= 4 else 0
            if mode == 0:
                prediction = 0
            elif mode == 1:
                prediction = left
            elif mode == 2:
                prediction = up
            elif mode == 3:
                prediction = (left + up) // 2
            elif mode == 4:
                p = left + up - corner
                prediction = min((left, up, corner), key=lambda v: abs(p - v))
            else:
                raise ValueError("invalid PNG filter")
            row[x] = (row[x] + prediction) & 255
        pixels.append(row)
        previous = row
    return width, height, pixels


class LauncherIconsTest(unittest.TestCase):
    def test_icons_have_their_original_dp_size_and_are_not_cropped(self):
        for density, scale in DENSITIES.items():
            for name, dp in ICON_DP.items():
                with self.subTest(density=density, icon=name):
                    w, h, rows = rgba_png(RES / f"drawable-{density}/ic_{name}.png")
                    self.assertEqual((w, h), (round(dp * scale),) * 2)
                    filled = [(x, y) for y, row in enumerate(rows) for x in range(w)
                              if row[x * 4 + 3] > 128]
                    self.assertTrue(filled, "icon is blank")
                    xs, ys = zip(*filled)
                    self.assertGreater(min(xs), 0, "icon clipped at left edge")
                    self.assertGreater(min(ys), 0, "icon clipped at top edge")
                    self.assertLess(max(xs), w - 1, "icon clipped at right edge")
                    self.assertLess(max(ys), h - 1, "icon clipped at bottom edge")
                    self.assertGreater(max(xs) - min(xs), w * .4)
                    self.assertGreater(max(ys) - min(ys), h * .4)

    def test_settings_icon_has_a_hole_surrounded_by_the_gear(self):
        for density in DENSITIES:
            with self.subTest(density=density):
                w, h, rows = rgba_png(RES / f"drawable-{density}/ic_settings.png")
                self.assertEqual(rows[h // 2][(w // 2) * 4 + 3], 0)
                for x, y in ((w // 4, h // 2), (3 * w // 4, h // 2),
                             (w // 2, h // 4), (w // 2, 3 * h // 4)):
                    self.assertGreater(rows[y][x * 4 + 3], 128)


if __name__ == "__main__":
    unittest.main()
