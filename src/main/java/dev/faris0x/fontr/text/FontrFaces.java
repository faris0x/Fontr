// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Finds a font's bold/italic faces next to the selected regular file. */
public final class FontrFaces {
    public final Path bold;
    public final Path italic;
    public final Path boldItalic;

    private FontrFaces(Path bold, Path italic, Path boldItalic) {
        this.bold = bold;
        this.italic = italic;
        this.boldItalic = boldItalic;
    }

    public static FontrFaces resolve(Path regular, Path boldOverride, Path italicOverride,
                                     Path boldItalicOverride) {
        Path directory = regular.toAbsolutePath().getParent();
        String stem = normalize(stripExtension(regular.getFileName().toString()));
        List<Path> candidates = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile).forEach(candidates::add);
        } catch (Throwable ignored) {
        }

        Path bold = boldOverride != null ? boldOverride : find(candidates, regular, stem, true, false);
        Path italic = italicOverride != null ? italicOverride : find(candidates, regular, stem, false, true);
        Path boldItalic = boldItalicOverride != null ? boldItalicOverride
            : find(candidates, regular, stem, true, true);
        return new FontrFaces(bold, italic, boldItalic);
    }

    private static Path find(List<Path> candidates, Path regular, String stem, boolean bold, boolean italic) {
        Path best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Path candidate : candidates) {
            String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".ttf") || name.endsWith(".otf"))) {
                continue;
            }
            if (candidate.toAbsolutePath().equals(regular.toAbsolutePath())) {
                continue;
            }
            String normalized = normalize(stripExtension(name));
            if (!familyMatches(stem, normalized)) {
                continue;
            }
            boolean candidateBold = containsBold(normalized);
            boolean candidateItalic = containsItalic(normalized);
            if (candidateBold != bold || candidateItalic != italic) {
                continue;
            }
            if (containsLight(normalized)) {
                continue;
            }
            int rank = rank(normalized);
            if (rank < bestRank) {
                bestRank = rank;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean familyMatches(String stem, String candidate) {
        int limit = Math.min(Math.min(stem.length(), candidate.length()), 6);
        if (limit < 4) {
            return false;
        }
        return stem.regionMatches(0, candidate, 0, limit);
    }

    private static boolean containsBold(String name) {
        return name.contains("bold") || name.contains("black") || name.contains("heavy")
            || name.contains("semibold") || name.contains("demi");
    }

    private static boolean containsItalic(String name) {
        return name.contains("italic") || name.contains("oblique");
    }

    private static boolean containsLight(String name) {
        return name.contains("light") || name.contains("thin") || name.contains("extralight");
    }

    private static int rank(String name) {
        if (name.contains("regular") && !containsBold(name)) {
            return 90;
        }
        if (name.contains("semibold") || name.contains("demi")) {
            return 20;
        }
        if (name.contains("bold")) {
            return 10;
        }
        if (name.contains("black") || name.contains("heavy")) {
            return 30;
        }
        return 50;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
