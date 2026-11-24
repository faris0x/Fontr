# Fontr

Fontr replaces Minecraft 1.8.9's bitmap font renderer with TTF/OTF fonts on
Ornithe/Fabric, with first-class OneConfig settings and Arabic shaping.

## Requirements

- OneClient / Ornithe 1.8.9 (Fabric Loader 0.20.1+)
- Java 21+ (OneClient ships Java 25)
- OneConfig 1.9+ (provided by OneClient)

## Build

```sh
cd ~/projects/Fontr
JAVA_HOME=/home/faris/.local/share/PrismLauncher/java/java-runtime-epsilon ./gradlew build
```

The mod jar is written to `build/libs/fontr-1.0.0+mc1.8.9.jar`.

## Install

1. Put `fontr-1.0.0+mc1.8.9.jar` in the instance's `mods/` folder.
2. Put the font at `.minecraft/config/Fontr/ArabicPixelSmall.ttf`.
3. Launch OneClient and open **OneConfig → Visuals → Fontr**.

The default font path is `Fontr/ArabicPixelSmall.ttf`, relative to
`.minecraft/config/`. You can also pick a different `.ttf`/`.otf` with the
OneConfig file field.

## Settings

- **Enabled** — turn Fontr on/off.
- **Font file** — TTF/OTF path.
- **Font size** — glyph size in game pixels (pixel fonts usually prefer 8).
- **Antialiasing** — off for pixel fonts.
- **Pixel snap** — integer positions + nearest filtering.
- **Arabic shaping** — ICU presentation-form shaping. Required for fonts
  without GSUB (including the supplied Arabic PixelSmall font).
- **Drop shadow** — use Minecraft's normal shadow pass.
- **Fallback** — font for codepoints missing from the main font.
- **Allow resource pack font override** — when enabled, a resource pack that
  supplies `textures/font/ascii.png`, `font/glyph_sizes.bin` or a unicode page
  wins and Fontr defers to vanilla for text. Off by default so Fontr always
  takes effect.
- **Debug logging**.

## Font licensing

`Arabic PixelSmall` is free for personal use only; commercial use is
prohibited. This repository does not include the font. The personal build loads
it from `.minecraft/config/Fontr/`. Do not redistribute it.

## Notes

- OneConfig's own settings UI uses Compose/Skia and remains on its own font.
  Fontr affects in-game text (chat, HUD, nametags, signs, tooltips, etc.).
- If Cera's HD-font batching interferes, disable `hd_fonts` in
  `config/cera.json` and restart.
- The mixin targets Ornithe's `TextRenderer` (`C_23014920`) and its
  `drawLayer`/width/layout methods.
