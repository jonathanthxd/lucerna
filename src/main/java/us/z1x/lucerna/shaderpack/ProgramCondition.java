package us.z1x.lucerna.shaderpack;

import us.z1x.lucerna.shaderpack.option.values.OptionValues;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProgramCondition {
    private static final Pattern TOKEN = Pattern.compile("&&|\\|\\||[!()]|[\\w.]+");

    private final List<String> tokens = new ArrayList<>();
    private final OptionValues options;
    private int position;

    private ProgramCondition(String expression, OptionValues options) {
        this.options = options;

        Matcher matcher = TOKEN.matcher(expression);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
    }

    static boolean evaluate(String expression, OptionValues options) {
        return new ProgramCondition(expression, options).or();
    }

    private boolean accept(String token) {
        if (position < tokens.size() && tokens.get(position).equals(token)) {
            position++;
            return true;
        }

        return false;
    }

    private boolean or() {
        boolean value = and();

        while (accept("||")) {
            value |= and();
        }

        return value;
    }

    private boolean and() {
        boolean value = not();

        while (accept("&&")) {
            value &= not();
        }

        return value;
    }

    private boolean not() {
        if (accept("!")) {
            return !not();
        }

        if (accept("(")) {
            boolean value = or();
            accept(")");
            return value;
        }

        if (position == tokens.size()) {
            return true;
        }

        String token = tokens.get(position++);

        // packs also gate programs on [0 1] number options such as #define SSR 1
        if (options.getOptionSet().getStringOptions().containsKey(token)) {
            String value = options.getStringValueOrDefault(token);
            return !value.matches("0+(\\.0*)?|false");
        }

        return "true".equals(token) || (!"false".equals(token) && options.getBooleanValueOrDefault(token));
    }
}
