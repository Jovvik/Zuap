package de.infynyty.zuap;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

public class CantonResolver {
    private static CantonResolver self = new CantonResolver();

    private CantonResolver() {
        cityToCanton = new HashMap<>();
        try (final var reader = Files.newBufferedReader(Path.of("cantons.csv"))) {
            reader.lines().forEach(line -> {
                final var split = line.split(",");
                cityToCanton.put(removeDiacritics(split[0]), removeDiacritics(split[1]));
            });
        } catch (IOException e) {
            // bleeeh
        }
    }

    private static String removeDiacritics(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        return s;
    }

    private final Map<String, String> cityToCanton;

    public static CantonResolver getInstance() {
        return self;
    }

    @Nullable
    public String resolveCanton(final String city) {
        final String cityNormalized = removeDiacritics(city).trim();
        var canton = cityToCanton.get(cityNormalized);
        if (canton != null) {
            return canton;
        }
        for (final var entry : cityToCanton.entrySet()) {
            if (cityNormalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (final var maybeCanton : cityToCanton.values()) {
            if (cityNormalized.contains(maybeCanton)) {
                return maybeCanton;
            }
        }
        return null;
    }
}
