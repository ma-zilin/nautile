#!/usr/bin/env python3
"""G2R4 设置页浅/深色截图与 uiautomator 证据核对（只读，不写业务）。

用法：
  python3 g2r4_check.py --shot x.png --xml x.xml --theme light --statusbar 128

判定（阈值依据：文字包围盒含大块背景，按“暗/亮像素计数”而非分位数）：
  1) 设置页文本节点（设置/返回/默认页/预测性返回）bounds.top >= 状态栏高度；
  2) 文本区存在与主题相反的笔画像素（浅色：暗像素>=50；深色：亮像素>=50），
     且包围盒背景中位亮度符合主题（浅色>0.7，深色<0.4）；
  3) 状态栏区域存在与主题相应的图标像素（浅色：暗像素占比>=0.002；深色：亮像素占比>=0.002）。
退出码：0 全部通过；1 有阻断；2 输入/解析错误。
"""
import argparse
import sys
import xml.etree.ElementTree as ET

from PIL import Image


def luminance(px):
    return (0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]) / 255.0


def region_count(img, x1, y1, x2, y2, pred):
    w, h = img.size
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(w, x2), min(h, y2)
    n = 0
    for y in range(y1, y2):
        for x in range(x1, x2):
            if pred(luminance(img.getpixel((x, y)))):
                n += 1
    return n, max(0, (x2 - x1)) * max(0, (y2 - y1))


def median_lum(img, x1, y1, x2, y2):
    vals = []
    w, h = img.size
    for y in range(max(0, y1), min(h, y2)):
        for x in range(max(0, x1), min(w, x2)):
            vals.append(luminance(img.getpixel((x, y))))
    vals.sort()
    return vals[len(vals) // 2] if vals else None


def parse_bounds(s):
    parts = s.replace("][", ",").strip("[]").split(",")
    return tuple(int(v) for v in parts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shot", required=True)
    ap.add_argument("--xml", required=True)
    ap.add_argument("--theme", choices=["light", "dark"], required=True)
    ap.add_argument("--statusbar", type=int, required=True)
    args = ap.parse_args()

    img = Image.open(args.shot).convert("RGB")
    root = ET.parse(args.xml).getroot()
    labels = ["设置", "返回", "默认页", "预测性返回"]
    found = {}
    for node in root.iter("node"):
        text = (node.get("text") or "").strip()
        if text in labels and text not in found:
            found[text] = parse_bounds(node.get("bounds"))

    print(f"IMAGE={args.shot} size={img.size} theme={args.theme} statusbar={args.statusbar}px")
    print("FOUND=" + ", ".join(f"{k}:{v}" for k, v in found.items()))

    blocking = []
    missing = [l for l in labels if l not in found]
    if missing:
        blocking.append(f"缺少文本节点: {missing}")

    # 1) 顶部安全区：文本不压状态栏
    for label, (x1, y1, x2, y2) in found.items():
        if y1 < args.statusbar:
            blocking.append(f"{label} 顶部 y={y1} < 状态栏 {args.statusbar}")
    if found:
        top = min(v[1] for v in found.values())
        print(f"CONTENT_TOP_MIN={top} (statusbar={args.statusbar})")

    # 2) 文本可读性：包围盒内是否存在与主题相反的笔画像素
    for label, (x1, y1, x2, y2) in found.items():
        if args.theme == "light":
            count, total = region_count(img, x1, y1, x2, y2, lambda v: v < 0.5)
            bg = median_lum(img, x1, y1, x2, y2)
            print(f"TEXT {label} box=({x1},{y1},{x2},{y2}) dark_px={count}/{total} bg_p50={bg:.3f}")
            if count < 50:
                blocking.append(f"{label} 暗像素 {count} < 50（浅底无明显深色笔画）")
            if bg is not None and bg <= 0.7:
                blocking.append(f"{label} 包围盒背景中位亮度 {bg:.3f} <= 0.7（浅色主题背景非浅色）")
        else:
            count, total = region_count(img, x1, y1, x2, y2, lambda v: v > 0.5)
            bg = median_lum(img, x1, y1, x2, y2)
            print(f"TEXT {label} box=({x1},{y1},{x2},{y2}) light_px={count}/{total} bg_p50={bg:.3f}")
            if count < 50:
                blocking.append(f"{label} 亮像素 {count} < 50（深底无明显浅色笔画，疑似黑底黑字）")
            if bg is not None and bg >= 0.4:
                blocking.append(f"{label} 包围盒背景中位亮度 {bg:.3f} >= 0.4（深色主题背景非深色）")

    # 3) 状态栏图标明暗
    sb_h = min(args.statusbar, img.size[1])
    if args.theme == "light":
        dark, total = region_count(img, 0, 0, img.size[0], sb_h, lambda v: v < 0.5)
        ratio = dark / total if total else 0.0
        bg = median_lum(img, 0, sb_h, img.size[0], sb_h + 8) if sb_h + 8 <= img.size[1] else None
        print(f"STATUSBAR dark_px={dark}/{total} ratio={ratio:.4f} content_bg_p50={bg}")
        if ratio < 0.002:
            blocking.append("浅色主题状态栏深色图标像素占比 < 0.002（图标未转深色）")
        if bg is not None and bg <= 0.7:
            blocking.append(f"浅色主题内容背景中位亮度 {bg:.3f} <= 0.7")
    else:
        light, total = region_count(img, 0, 0, img.size[0], sb_h, lambda v: v > 0.5)
        ratio = light / total if total else 0.0
        bg = median_lum(img, 0, sb_h, img.size[0], sb_h + 8) if sb_h + 8 <= img.size[1] else None
        print(f"STATUSBAR light_px={light}/{total} ratio={ratio:.4f} content_bg_p50={bg}")
        if ratio < 0.002:
            blocking.append("深色主题状态栏浅色图标像素占比 < 0.002（图标未转浅色）")
        if bg is not None and bg >= 0.4:
            blocking.append(f"深色主题内容背景中位亮度 {bg:.3f} >= 0.4")

    if blocking:
        print("RESULT=FAIL")
        for b in blocking:
            print("  - " + b)
        sys.exit(1)
    print("RESULT=PASS")
    sys.exit(0)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(2)
