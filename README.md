# Fontr

Fontr replaces Minecraft 1.8.9's bitmap font with any TTF/OTF, on Ornithe/Fabric.
It generates high-resolution font sheets and serves them through the resource
manager, so every text renderer in the game uses them: vanilla chat, nametags,
signs, tooltips, the F3 screen, Argentum's batched text, and Cera.

## How it works

Fontr does not hook `TextRenderer`. Instead it replaces the three resources every
1.8.9 renderer already reads:

| Resource | Generated from |
|---|---|
| `textures/font/ascii.png` | 16x16 grid at 4096x4096 by default (256 px cells, 32x supersampling) |
| `textures/font/unicode_page_XX.png` | 256x256 pages, generated lazily for the blocks the font covers |
| `font/glyph_sizes.bin` | per-codepoint advances, merged with the pack's original widths |

Because the data is replaced at the resource level, it does not matter which
renderer asks for it. Vanilla uploads the sheet with `GL_NEAREST`; Fontr switches
font textures to `GL_LINEAR` + a capped mip chain so the extra resolution is
actually visible.

Cap height is normalised to the vanilla 7px cap in an 8px line. Fallback glyphs
(codepoints missing from the selected font) are rasterised from the configured
system fallback; if that also lacks a glyph, the pack's original cell is kept.

## Requirements

- Ornithe 1.8.9 (Fabric Loader 0.20.1+) with Ornithe Standard Libraries
- Java 21+ (OneClient ships Java 25)
- OneConfig 1.9+ is optional; when present the settings page appears in the
  OneClient/OneConfig mods manager

## Build

```sh
cd ~/projects/Fontr
JAVA_HOME=/home/faris/.local/share/PrismLauncher/java/java-runtime-epsilon ./gradlew build
```

The jar is written to `build/libs/fontr-1.0.0+mc1.8.9.jar`.

## Install

1. Put `fontr-1.0.0+mc1.8.9.jar` in the instance's `mods/` folder.
2. Put fonts under `.minecraft/config/Fontr/`, e.g. `config/Fontr/MyFont.ttf`.
3. Open **OneConfig -> Visuals -> Fontr** and select the font.

Generated sheets are cached in `config/Fontr/cache/<key>/`, so only the first
launch (or the first launch after changing a setting) pays the generation cost.
Changing a setting reloads resources automatically.

## Settings

- **Enabled** — turn Fontr on/off.
- **Font file** — TTF/OTF path, relative to `.minecraft/config/` or absolute.
- **Sheet resolution** — `4096` (32x supersampling, ~85 MB VRAM) or `8192`
  (64x, ~340 MB VRAM including mipmaps).
- **Glyph scale** — cap height within the 8px line. `1.0` matches Mojangles.
- **Antialiasing** — off for pixel fonts.
- **Fallback** — system font used for codepoints missing from the main font.
- **Apply to OneConfig UI** — also register the TTF with PolyCompose so the
  mod menu and OneConfig HUD match.
- **Debug logging** — logs cache keys, generated pages and resource hits.

## Font licensing

`Arabic PixelSmall` is free for personal use only; commercial use is prohibited.
This repository does not include the font. Do not redistribute it.

## Notes

- Unicode pages stay at vanilla's 256x256 layout so Argentum, Cera and vanilla
  all read them identically. Only the ASCII sheet (which covers Latin-1 and is
  the vast majority of UI text) is supersampled.
- Keep Cera's `hd_fonts` off. Fontr supplies its own HD sheet; Cera's HD-font
  path would re-read the pack's sheet and undo the override.
- Each 4096 ASCII sheet is uploaded once per resource reload; the mip chain is
  capped at level 4 to keep VRAM reasonable.
