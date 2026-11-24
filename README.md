# Fontr

Fontr replaces the Minecraft 1.8.9 bitmap font with a TTF or OTF file on
Ornithe/Fabric. It generates high resolution font sheets and delivers them
through the resource manager, so vanilla text, OneConfig, PolyCompose,
VanillaHud, EvergreenHUD, Argentum and Cera read the same glyphs.

## What Fontr does

Fontr builds an OSL resource pack named `FontrPack` that sits at the top of the
resource stack. The pack contains the resources that every 1.8.9 text renderer
already reads:

| Resource | Contents |
|---|---|
| `assets/minecraft/textures/font/ascii.png` | ASCII and Latin-1 sheet, 4096 by default, 8192 optional |
| `assets/minecraft/textures/font/unicode_page_XX.png` | 256 by 256 pages generated on demand for blocks the font covers |
| `assets/minecraft/font/glyph_sizes.bin` | Per codepoint advances merged with the pack's original widths |

The sheet uses Minecraft's legacy cell grid. Cell `0xFE` contains the box
character used by vanilla. Each glyph is fitted from the font's own metrics, and
cap height and baseline come from the font's letters and descenders. Unicode
pages are fitted per block, with marks excluded from the metrics, so Arabic and
other scripts can use the full cell. Missing codepoints fall back to the
configured system font, then to the original resource pack cell.

Font textures upload with linear filtering and a capped mip chain. Transparent
pixels are whitened to avoid dark fringing.

Arabic text is shaped with ICU and reordered to visual right to left order
before drawing. The transform applies to vanilla draw, width and trim calls, to
PolyCompose `RenderContext` text and measurement calls including `mcText`,
`mcTextLive` and `mcTextOutline`, to Compose paragraphs, and to the
`PolyTessellator` text entry points. Paired punctuation is mirrored during the
reorder, so parentheses and brackets keep their orientation.

## What works

- Global TTF and OTF replacement for the vanilla bitmap font on Minecraft 1.8.9.
- 4096 and 8192 ASCII sheets, with lazy unicode pages at vanilla's 256 by 256
  page size.
- True bold and italic sheets when matching face files exist. Fontr looks for
  sibling face files next to the regular font and accepts explicit bold, italic
  and bold italic files. When no face file exists it bakes AWT synthetic faces.
- Styled vanilla ASCII text and styled Argentum batched text when the real
  faces are available.
- Arabic shaping and RTL order in vanilla text, PolyCompose HUD text, Compose
  paragraphs, and EvergreenHUD lyrics.
- EvergreenHUD lyric rows are mirrored for Arabic, and long Arabic words are
  kept as single marks so a highlighted word keeps its RTL letter order.
- Argentum chat and nametag integration through runtime mixins, including
  optional real styles.
- OneConfig settings page under Visuals when OneConfig is installed.
- Settings changes regenerate resources without restarting the game.
- Generated sheets are cached under `config/Fontr/cache/`.

## What does not work

- Only Minecraft 1.8.9 on Ornithe with OSL is supported. Other versions and
  other loaders are untested and unsupported.
- `accented.png` and `nonlatin_european.png` are not generated. Those
  characters still come from the selected resource pack until a page is
  generated for their block.
- Hebrew and other RTL scripts have shaping and reordering helpers in place but
  are not a tested target.
- Arabic alignment inside HUD components follows the component's own text
  alignment. Letter order is correct while the box alignment can stay
  left aligned.
- EvergreenHUD karaoke keeps a whole Arabic word as one mark. The wipe inside
  that single word still advances along x as EvergreenHUD implements it. Word to
  word progression follows mirrored positions and timestamps.
- Variable fonts expose only their default instance to AWT. Choosing another
  weight requires a static face file.
- The Full-width glyphs switch can misalign VanillaHud text that was captured
  with vanilla positions. Leave it off unless you need full ink width.
- Cera must keep `hd_fonts` disabled. Cera's HD font path re-reads the sheet and
  undoes the override.
- Argentum integration uses internal class names. A future Argentum release can
  break it, and the integration then disables itself while normal text still
  works.
- OneConfig is optional. Without OneConfig there is no settings page and the
  engine uses its built-in defaults, including `Fontr/font.ttf`.
- The mod does not choose fonts per server or per world. One font file is active
  at a time.

## What was tested

Development and testing happened on Linux with a OneClient 1.8.9 Ornithe
instance using Prism Launcher. The fonts used during testing were
`ArabicPixelSmall.ttf` and `CascadiaCode-VariableFont_wght.ttf`.

Tested in the running game:

- vanilla chat, tab list, scoreboard and tooltips
- OneConfig screens and HUD text
- EvergreenHUD media and lyrics text, including Arabic lyrics
- Argentum chat and nametags
- Latin text, Arabic text with punctuation, and mixed Latin and Arabic runs
- 4096 sheets, lazy unicode pages, cache reuse, and resource reload after a
  settings change

Tested offline:

- sheet generation, cell fitting, baselines and advances
- merged `glyph_sizes.bin` output
- Arabic shaping, visual reordering and punctuation mirroring
- lyric row mirroring and word splitting decisions

Not tested:

- Windows, macOS, or any client other than OneClient
- Minecraft versions other than 1.8.9
- 8192 sheets on low VRAM hardware
- Hebrew, CJK, or other scripts beyond Latin and Arabic
- shader packs, Sodium-like renderers, or other font mods running at the same
  time
- multiplayer servers and anticheat environments
- OneConfig-less clients, Cera combinations other than `hd_fonts` off, or future
  Argentum releases
- every HUD mod available for 1.8.9

## Requirements

- Minecraft 1.8.9 with Ornithe and Fabric Loader 0.20.1 or newer
- Ornithe Standard Libraries
- Java 21 or newer
- OneConfig 1.9 or newer for the settings page

## Install

1. Put the Fontr jar in the instance's `mods/` folder.
2. Put font files in `.minecraft/config/Fontr/`, for example
   `config/Fontr/MyFont.ttf`.
3. Open the OneConfig mods manager, Visuals, Fontr, and select the font.

The first launch generates sheets for the selected settings. Later launches
reuse the cache, and changing a setting regenerates the affected sheets.

## Settings

- Enabled: turns Fontr on or off.
- Font file: TTF or OTF path, relative to `.minecraft/config/` or absolute.
- Bold font, Italic font, Bold-italic font: optional explicit face files. Empty
  values use auto-detection.
- Sheet resolution: 4096 recommended, 8192 maximum.
- Glyph scale: scales the fitted glyph size. Values above 1.0 can clip.
- Script scale: scales unicode blocks relative to their fitted size, from 0.8
  to 2.0.
- Script offset: moves unicode blocks up or down inside the line.
- Script height: stretches unicode blocks vertically around their baseline.
- Letter spacing: extra pixels between glyphs, with negative values tightening
  text.
- Embolden: strokes glyph outlines for thin fonts.
- Full-width glyphs: draws glyphs at their natural ink width. This can misalign
  VanillaHud captured text.
- Antialiasing: smooths glyph edges in the sheet. Disable it for pixel fonts.
- Styled sheet resolution: 2048 recommended, 4096 maximum.
- Use real bold and italic faces: generates styled sheets from the font's own
  faces.
- Fallback: None, System SansSerif, or System Monospaced.
- Mipmaps: builds a mip chain for the font textures.
- Arabic shaping and RTL: joins Arabic letters and reorders RTL runs.
- Real styles in Argentum text: uses the styled sheets in Argentum chat and
  nametags.
- Apply to OneConfig UI: registers the font with PolyCompose so the mod menu and
  HUD match.
- Debug logging: logs sheet generation, cache keys and resource hits.

## Build

```sh
cd ~/projects/Fontr
JAVA_HOME=/path/to/jdk21 ./gradlew build
```

The jar is written to `build/libs/fontr-1.0.0+mc1.8.9.jar`.

## Third-party notices

Fontr ships no Argentum, OneConfig, PolyCompose, EvergreenHUD, VanillaHud or Cera
code. Argentum support is compiled against signature-only stubs under
`src/main/java/dev/rdh`, and those stubs are excluded from the jar. The runtime
mixins target public or internal entry points of installed mods without copying
their implementation.

`Arabic PixelSmall` is free for personal use only. It is not included in this
repository. Do not redistribute it.

## License

GPL-3.0-only. See [LICENSE](LICENSE).

## AI Declaration

I use large language models through the OpenCode coding assistant while
developing Fontr. I use the assistant for design discussion, implementation
drafts, test construction, refactoring, and documentation editing. I review and
accept every change, run the game and offline checks, and remain responsible for
correctness, licensing, and provenance. I do not list an AI system as an author,
and I do not include generated datasets or third-party copyrighted text.
