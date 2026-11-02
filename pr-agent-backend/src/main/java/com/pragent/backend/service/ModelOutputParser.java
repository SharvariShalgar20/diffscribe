package com.pragent.backend.service;

import com.pragent.backend.dto.ChangeType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the model's five labeled lines (TYPE / TITLE / WHAT / WHY / TEST) and
 * builds the final title and markdown description itself. The model never
 * writes JSON or headings, so malformed JSON and missing headings can't happen.
 */
final class ModelOutputParser {

    record Parsed(ChangeType type, String summary, String what, String why, String howToTest) {}

    private enum Field { TYPE, TITLE, WHAT, WHY, TEST }

    static final int MAX_TITLE_LENGTH = 72;
    static final String WHY_FALLBACK = "Not evident from the diff.";

    // Tolerates markdown around the label ("**TYPE**:"), and a few label variants.
    private static final Pattern FIELD_LINE = Pattern.compile(
            "^[\\s*_`#>-]*(TYPE|TITLE|WHAT(?:\\s+CHANGED)?|WHY|TEST|HOW\\s+TO\\s+TEST)[\\s*_`]*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TYPE_WORD = Pattern.compile(
            "\\b(feat(?:ure)?|fix|refactor|docs?|tests?|chore)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern TYPE_PREFIX = Pattern.compile(
            "^(feat|fix|refactor|docs|test|chore)(\\([^)]*\\))?!?:\\s*", Pattern.CASE_INSENSITIVE);

    private ModelOutputParser() {}

    static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ModelOutputException("The model returned an empty answer.");
        }

        Map<Field, List<String>> fields = new EnumMap<>(Field.class);
        Field current = null;
        for (String line : raw.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("```")) {
                continue;
            }
            Matcher m = FIELD_LINE.matcher(trimmed);
            if (m.matches()) {
                current = toField(m.group(1));
                fields.computeIfAbsent(current, k -> new ArrayList<>())
                        .add(m.group(2).replaceFirst("^[\\s*_`]+", ""));
            } else if (current != null) {
                fields.get(current).add(trimmed);
            }
        }

        String typeValue = firstLine(fields.get(Field.TYPE));
        if (typeValue.isEmpty()) {
            throw new ModelOutputException("The answer has no TYPE line.");
        }
        ChangeType type = parseType(typeValue);

        String summary = cleanSummary(firstLine(fields.get(Field.TITLE)));
        if (summary.isEmpty()) {
            throw new ModelOutputException("The answer has no TITLE line.");
        }

        String what = cleanBody(fields.get(Field.WHAT));
        if (what.isEmpty()) {
            throw new ModelOutputException("The answer has no WHAT line.");
        }

        String why = cleanBody(fields.get(Field.WHY));
        if (why.isEmpty()) {
            why = WHY_FALLBACK;
        }

        String howToTest = cleanBody(fields.get(Field.TEST));
        if (howToTest.isEmpty()) {
            throw new ModelOutputException("The answer has no TEST line.");
        }

        return new Parsed(type, summary, what, why, howToTest);
    }

    static String formatTitle(ChangeType type, String summary) {
        String prefix = type.toValue() + ": ";
        String s = summary;
        if (s.length() > 1 && Character.isUpperCase(s.charAt(0)) && Character.isLowerCase(s.charAt(1))) {
            s = Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        int max = MAX_TITLE_LENGTH - prefix.length();
        if (s.length() > max) {
            s = s.substring(0, max);
            int lastSpace = s.lastIndexOf(' ');
            if (lastSpace > max / 2) {
                s = s.substring(0, lastSpace);
            }
            s = s.replaceAll("[\\s.,;:\\-]+$", "");
        }
        return prefix + s;
    }

    static String formatDescription(Parsed parsed) {
        return "## What changed\n" + parsed.what()
                + "\n\n## Why\n" + parsed.why()
                + "\n\n## How to test\n" + parsed.howToTest();
    }

    private static Field toField(String label) {
        String upper = label.toUpperCase(Locale.ROOT);
        if (upper.startsWith("WHAT")) {
            return Field.WHAT;
        }
        if (upper.startsWith("HOW")) {
            return Field.TEST;
        }
        return Field.valueOf(upper);
    }

    private static ChangeType parseType(String value) {
        Matcher m = TYPE_WORD.matcher(value);
        if (!m.find()) {
            throw new ModelOutputException(
                    "TYPE is not one of feat, fix, refactor, docs, test, chore: '" + value + "'.");
        }
        String t = m.group(1).toLowerCase(Locale.ROOT);
        if (t.startsWith("feat")) return ChangeType.FEAT;
        if (t.startsWith("fix")) return ChangeType.FIX;
        if (t.startsWith("refactor")) return ChangeType.REFACTOR;
        if (t.startsWith("doc")) return ChangeType.DOCS;
        if (t.startsWith("test")) return ChangeType.TEST;
        return ChangeType.CHORE;
    }

    private static String cleanSummary(String value) {
        String s = value.strip();
        s = s.replaceAll("^[\"'`*_]+|[\"'`*_]+$", "").strip();
        s = TYPE_PREFIX.matcher(s).replaceFirst("").strip();
        s = s.replaceAll("[.\\s]+$", "");
        return s;
    }

    private static String firstLine(List<String> lines) {
        if (lines == null) {
            return "";
        }
        for (String l : lines) {
            if (!l.isBlank()) {
                return l.strip();
            }
        }
        return "";
    }

    private static String cleanBody(List<String> lines) {
        if (lines == null) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String l : lines) {
            String t = l.strip();
            if (t.matches("#{1,6}\\s.*")) {
                continue; // the model must not add headings, we add the three
            }
            kept.add(t);
        }
        return String.join("\n", kept).strip().replaceAll("\n{3,}", "\n\n");
    }
}