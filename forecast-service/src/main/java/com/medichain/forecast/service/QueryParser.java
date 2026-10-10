package com.medichain.forecast.service;

import com.medichain.forecast.client.InventoryDtos.MedicineDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an operator question into structured parameters (medicines, district, horizon).
 * Deterministic keyword matching: the numbers in the answer never come from a language model.
 */
public final class QueryParser {
    private static final Pattern DAYS = Pattern.compile("(\\d{1,3})\\s*-?\\s*(days?|d\\b|weeks?|wks?|months?)");

    private QueryParser() {
    }

    public record Parsed(String district, List<MedicineDto> medicines, Integer horizonDays) {
    }

    public static Parsed parse(String query, List<String> districts, List<MedicineDto> medicines) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);

        String district = null;
        for (String d : districts) {
            if (containsWord(q, d.toLowerCase(Locale.ROOT))) {
                district = d;
            }
        }

        Integer days = null;
        Matcher m = DAYS.matcher(q);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            days = unit.startsWith("w") ? n * 7 : unit.startsWith("m") ? n * 30 : n;
        } else if (q.contains("fortnight")) {
            days = 14;
        } else if (q.contains("week")) {
            days = 7;
        } else if (q.contains("month")) {
            days = 30;
        }

        List<MedicineDto> matched = new ArrayList<>();
        for (MedicineDto med : medicines) {
            List<String> keys = new ArrayList<>();
            keys.add(med.code().toLowerCase(Locale.ROOT));
            keys.add(med.name().toLowerCase(Locale.ROOT).split("[ +]")[0]);
            if (med.aliases() != null) {
                for (String a : med.aliases().split(",")) {
                    keys.add(a.trim().toLowerCase(Locale.ROOT));
                }
            }
            for (String k : keys) {
                if (!k.isEmpty() && containsWord(q, k)) {
                    matched.add(med);
                    break;
                }
            }
        }
        return new Parsed(district, matched, days);
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(word) + "(?![a-z0-9])").matcher(text).find();
    }
}
