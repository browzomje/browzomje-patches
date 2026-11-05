package app.browzomje.extension.pinterest;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class MutedPinWords {
    private static final String[] TEXT_FIELDS = {"grid_title", "title", "description"};
    private static volatile MutedPinWords cached = new MutedPinWords("", null);

    private final String input;
    private final Pattern pattern;

    private MutedPinWords(String input, Pattern pattern) {
        this.input = input;
        this.pattern = pattern;
    }

    static MutedPinWords forInput(String input) {
        MutedPinWords current = cached;
        if (current.input.equals(input)) return current;
        StringBuilder alternatives = new StringBuilder();
        for (String line : input.split("\\R")) {
            String phrase = normalize(line).trim();
            if (phrase.isEmpty()) continue;
            if (alternatives.length() > 0) alternatives.append('|');
            alternatives.append(Pattern.quote(phrase));
        }
        Pattern pattern = alternatives.length() == 0 ? null : Pattern.compile(
                "(?<![\\p{L}\\p{M}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{M}\\p{N}])");
        current = new MutedPinWords(input, pattern);
        cached = current;
        return current;
    }

    boolean isEmpty() {
        return pattern == null;
    }

    boolean matches(Object model) {
        if (pattern == null || AdDetector.fieldValueBySerializedName(model, "grid_title") == null
                && AdDetector.fieldValueBySerializedName(model, "images") == null) return false;
        for (String field : TEXT_FIELDS) {
            Object text = AdDetector.fieldValueBySerializedName(model, field);
            if (text instanceof String && pattern.matcher(normalize((String) text)).find()) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
