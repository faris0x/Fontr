# Fontr

Fontr replaces Minecraft 1.8.9's bitmap font with any TTF/OTF, on Ornithe/Fabric.
It generates high-resolution font sheets and serves them through the resource
manager, so every text renderer in the game uses them: vanilla chat, nametags,
signs, tooltips, the F3 screen, Argentum's batched text, and Cera.

## How it works

Fontr does not hook any text renderer. It registers a required, fixed, top-of-stack
**OSL resource pack** containing the resources every 1.8.9 renderer already reads:

| Resource | Generated from |
|---|---|
| `textures/font/ascii.png` | 16x16 grid at 4096x4096 by default, filled using Minecraft's legacy cell layout |
| `textures/font/unicode_page_XX.png` | 256x256 pages, generated lazily for the blocks the font covers |
| `font/glyph_sizes.bin` | per-codepoint advances, merged with the pack's original widths |

Using a real pack (rather than intercepting Minecraft's resource manager) is what
makes the override visible to PolyCompose's `McResourceFont` (OneConfig HUD
"Default" font) and Cera, which read the OSL resource manager directly. Vanilla
uploads font sheets with `GL_NEAREST`; Fontr switches them to `GL_LINEAR` + a
capped mip chain so the extra resolution is actually visible.

Arabic text is rewritten before it reaches the renderer: a small argument-level
mixin joins letters with ICU and reorders right-to-left runs, and the same
transform is applied to the width/trim helpers so layout stays consistent.

The ASCII sheet follows Minecraft's legacy cell grid, so cell `0xFE` is `■`
(not `þ`) and the box-drawing range matches vanilla. Cap height and baseline are
computed from the font's own letters/descenders so glyphs are not clipped by the
8px line; outliers are squeezed per glyph. Fallback glyphs (codepoints missing
from the selected font) are rasterised from the configured system fallback; if
that also lacks a glyph, the pack's original cell is kept.

Real bold and italic faces are generated as separate sheets when available
(auto-detected next to the font or selected explicitly). Vanilla renders ASCII
styled text from those sheets and skips its synthetic double-draw/slant.
Argentum's batched chat/nametags are taught to use the same sheets through an
optional runtime mixin (no Argentum code is bundled or modified), so true styles
apply there too when the faces exist.

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
- **Bold / Italic / Bold-italic font** — optional; empty auto-detects faces.
- **Glyph scale** — scales the fitted size. `1.0` is the largest size that fits
  the 8px line without clipping; higher values may clip.
- **Letter spacing** — extra pixels between glyphs; negative tightens text.
- **Embolden** — strokes glyph outlines for thin fonts at 8px.
- **Antialiasing** — off for pixel fonts.
- **Styled sheet resolution** — 2048 (default) or 4096 for bold/italic sheets.
- **Mipmaps** — mip chain for font textures; turn off to compare sharpness.
- **Use real bold/italic faces** / **Real styles in Argentum text**.
- **Fallback** — system font used for codepoints missing from the main font.
- **Arabic shaping + RTL** — joins Arabic letters and reorders RTL runs.
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
- If no bold/italic face is found, Fontr falls back to vanilla's synthetic bold
  (draw twice) and slant. True styles require actual face files; variable fonts
  only expose their default instance to AWT.
- Argentum integration is best-effort: it targets Argentum's internal batcher at
  runtime and is compiled against signature-only stubs (no Argentum code is
  shipped). If a future Argentum changes the batcher, the feature disables itself.
- PolyCompose's accented/non-Latin European sheets (`accented.png`,
  `nonlatin_european.png`) are not regenerated yet; those characters still come
  from the selected resource pack.
- Each 4096 ASCII sheet is uploaded once per resource reload; the mip chain is
  capped at level 4 to keep VRAM reasonable.
