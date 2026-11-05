package us.z1x.lucerna.shaderpack.option;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import us.z1x.lucerna.shaderpack.OptionalBoolean;
import us.z1x.lucerna.shaderpack.option.values.OptionValues;
import us.z1x.lucerna.shaderpack.parsing.ParsedString;
import us.z1x.lucerna.shaderpack.transform.line.LineTransform;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OptionAnnotatedSource {
    private final ImmutableList<String> lines;

    private final ImmutableMap<Integer, BooleanOption> booleanOptions;
    private final ImmutableMap<Integer, StringOption> stringOptions;

    private final ImmutableMap<Integer, String> diagnostics;

    // plain #if references are ignored to match optifine, shader packs rely on this
    // a reference anywhere in the logical file (after includes) confirms a boolean define
    private final ImmutableMap<String, IntList> booleanDefineReferences;

    private static final ImmutableSet<String> VALID_CONST_OPTION_NAMES = ImmutableSet.of(
            "shadowMapResolution",
            "shadowDistance",
            "shadowDistanceRenderMul",
            "entityShadowDistanceMul",
            "shadowIntervalSize",
            "generateShadowMipmap",
            "generateShadowColorMipmap",
            "shadowHardwareFiltering",
            "shadowHardwareFiltering0",
            "shadowHardwareFiltering1",
            "shadowtex0Mipmap",
            "shadowtexMipmap",
            "shadowtex1Mipmap",
            "shadowcolor0Mipmap",
            "shadowColor0Mipmap",
            "shadowcolor1Mipmap",
            "shadowColor1Mipmap",
            "shadowtex0Nearest",
            "shadowtexNearest",
            "shadow0MinMagNearest",
            "shadowtex1Nearest",
            "shadow1MinMagNearest",
            "shadowcolor0Nearest",
            "shadowColor0Nearest",
            "shadowColor0MinMagNearest",
            "shadowcolor1Nearest",
            "shadowColor1Nearest",
            "shadowColor1MinMagNearest",
            "wetnessHalflife",
            "drynessHalflife",
            "eyeBrightnessHalflife",
            "centerDepthHalflife",
            "sunPathRotation",
            "ambientOcclusionLevel",
            "superSamplingLevel",
            "noiseTextureResolution"
    );

    public OptionAnnotatedSource(final String source) {
        this(ImmutableList.copyOf(source.split("\\R")));
    }

    public OptionAnnotatedSource(final ImmutableList<String> lines) {
        this.lines = lines;

        AnnotationsBuilder builder = new AnnotationsBuilder();

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            parseLine(builder, index, line);
        }

        this.booleanOptions = builder.booleanOptions.build();
        this.stringOptions = builder.stringOptions.build();
        this.diagnostics = builder.diagnostics.build();
        this.booleanDefineReferences = ImmutableMap.copyOf(builder.booleanDefineReferences);
    }

    private static void parseLine(AnnotationsBuilder builder, int index, String lineText) {
        if (!lineText.contains("#define")
                && !lineText.contains("const")
                && !lineText.contains("#ifdef")
                && !lineText.contains("#ifndef")) {
            return;
        }

        ParsedString line = new ParsedString(lineText.trim());

        if (line.takeLiteral("#ifdef") || line.takeLiteral("#ifndef")) {
            parseIfdef(builder, index, line);
        } else if (line.takeLiteral("const")) {
            parseConst(builder, index, line);
        } else if (line.currentlyContains("#define")) {
            parseDefineOption(builder, index, line);
        }
    }

    private static void parseIfdef(AnnotationsBuilder builder, int index, ParsedString line) {
        if (!line.takeSomeWhitespace()) {
            return;
        }

        String name = line.takeWord();

        line.takeSomeWhitespace();

        if (name == null || !line.isEnd()) {
            return;
        }

        builder.booleanDefineReferences
                .computeIfAbsent(name, n -> new IntArrayList()).add(index);
    }

    private static void parseConst(AnnotationsBuilder builder, int index, ParsedString line) {
        if (!line.takeSomeWhitespace()) {
            builder.diagnostics.put(index, "Expected whitespace after const and before type declaration");
            return;
        }

        boolean isString;

        if (line.takeLiteral("int") || line.takeLiteral("float")) {
            isString = true;
        } else if (line.takeLiteral("bool")) {
            isString = false;
        } else {
            builder.diagnostics.put(index, "Unexpected type declaration after const. " +
                    "Expected int, float, or bool. " +
                    "Vector const declarations cannot be configured using shader options.");
            return;
        }

        if (!line.takeSomeWhitespace()) {
            builder.diagnostics.put(index, "Expected whitespace after type declaration.");
            return;
        }

        String name = line.takeWord();

        if (name == null) {
            builder.diagnostics.put(index, "Expected name of option after type declaration, " +
                    "but an unexpected character was detected first.");
            return;
        }

        line.takeSomeWhitespace();

        if (!line.takeLiteral("=")) {
            builder.diagnostics.put(index, "Unexpected characters before equals sign in const declaration.");
            return;
        }

        line.takeSomeWhitespace();

        String value = line.takeWordOrNumber();

        if (value == null) {
            builder.diagnostics.put(index, "Unexpected non-whitespace characters after equals sign");
            return;
        }

        line.takeSomeWhitespace();

        if (!line.takeLiteral(";")) {
            builder.diagnostics.put(index, "Value between the equals sign and the semicolon wasn't parsed as a valid word or number.");
            return;
        }

        line.takeSomeWhitespace();

        String comment;

        if (line.takeComments()) {
            comment = line.takeRest().trim();
        } else if (!line.isEnd()) {
            builder.diagnostics.put(index, "Unexpected non-whitespace characters outside of comment after semicolon");
            return;
        } else {
            comment = null;
        }

        if (!isString) {
            boolean booleanValue;

            if ("true".equals(value)) {
                booleanValue = true;
            } else if ("false".equals(value)) {
                booleanValue = false;
            } else {
                builder.diagnostics.put(index, "Expected true or false as the value of a boolean const option, but got "
                        + value + ".");
                return;
            }

            if (!VALID_CONST_OPTION_NAMES.contains(name)) {
                builder.diagnostics.put(index, "This was a valid const boolean option declaration, but " + name +
                        " was not recognized as being a name of one of the configurable const options.");
                return;
            }

            builder.booleanOptions.put(index, new BooleanOption(OptionType.CONST, name, comment, booleanValue));
            return;
        }

        if (!VALID_CONST_OPTION_NAMES.contains(name)) {
            builder.diagnostics.put(index, "This was a valid const option declaration, but " + name +
                    " was not recognized as being a name of one of the configurable const options.");
            return;
        }

        StringOption option = StringOption.create(OptionType.CONST, name, comment, value);

        if (option != null) {
            builder.stringOptions.put(index, option);
        } else {
            builder.diagnostics.put(index, "Ignoring this const option because it is missing an allowed values list" +
                    "in a comment, but is not a boolean const option.");
        }
    }

    private static void parseDefineOption(AnnotationsBuilder builder, int index, ParsedString line) {
        boolean hasLeadingComment = line.takeComments();

        line.takeSomeWhitespace();

        if (!line.takeLiteral("#define")) {
            builder.diagnostics.put(index,
                    "This line contains an occurrence of \"#define\" " +
                            "but it wasn't in a place we expected, ignoring it.");
            return;
        }

        if (!line.takeSomeWhitespace()) {
            builder.diagnostics.put(index,
                    "This line properly starts with a #define statement but doesn't have " +
                            "any whitespace characters after the #define.");
            return;
        }

        String name = line.takeWord();

        if (name == null) {
            builder.diagnostics.put(index,
                    "Invalid syntax after #define directive. " +
                            "No alphanumeric or underscore characters detected.");
            return;
        }

        boolean tookWhitespace = line.takeSomeWhitespace();

        if (line.isEnd()) {
            builder.booleanOptions.put(index, new BooleanOption(OptionType.DEFINE, name, null, !hasLeadingComment));
            return;
        }

        if (line.takeComments()) {
            String comment = line.takeRest().trim();

            builder.booleanOptions.put(index, new BooleanOption(OptionType.DEFINE, name, comment, !hasLeadingComment));
            return;
        } else if (!tookWhitespace) {
            builder.diagnostics.put(index,
                    "Invalid syntax after #define directive. Only alphanumeric or underscore " +
                            "characters are allowed in option names.");

            return;
        }

        if (hasLeadingComment) {
            builder.diagnostics.put(index,
                    "Ignoring potential non-boolean #define option since it has a leading comment. " +
                            "Leading comments (//) are only allowed on boolean #define options.");
            return;
        }

        String value = line.takeWordOrNumber();

        if (value == null) {
            builder.diagnostics.put(index, "Ignoring this #define directive because it doesn't appear to be a boolean #define, " +
                    "and its potential value wasn't a valid number or a valid word.");
            return;
        }

        tookWhitespace = line.takeSomeWhitespace();

        if (line.isEnd()) {
            builder.diagnostics.put(index, "Ignoring this #define because it doesn't have a comment containing" +
                    " a list of allowed values afterwards, but it has a value so is therefore not a boolean.");
            return;
        } else if (!tookWhitespace) {
            if (!line.takeComments()) {
                builder.diagnostics.put(index,
                    "Invalid syntax after value #define directive. " +
                        "Invalid characters after number or word.");
                return;
            }
        } else if (!line.takeComments()) {
            builder.diagnostics.put(index,
                    "Invalid syntax after value #define directive. " +
                            "Only comments may come after the value.");
            return;
        }

        String comment = line.takeRest().trim();

        StringOption option = StringOption.create(OptionType.DEFINE, name, comment, value);

        if (option == null) {
            builder.diagnostics.put(index, "Ignoring this #define because it is missing an allowed values list" +
                    "in a comment, but is not a boolean define.");
            return;
        }

        builder.stringOptions.put(index, option);
    }

    public ImmutableMap<Integer, BooleanOption> getBooleanOptions() {
        return booleanOptions;
    }

    public ImmutableMap<Integer, StringOption> getStringOptions() {
        return stringOptions;
    }

    public ImmutableMap<String, IntList> getBooleanDefineReferences() {
        return booleanDefineReferences;
    }

    public OptionSet getOptionSet(Set<String> booleanDefineReferences) {
        OptionSet.Builder builder = OptionSet.builder();

        booleanOptions.values().forEach(option -> {
            if (booleanDefineReferences.contains(option.getName())) {
                builder.addBooleanOption(option);
            }
        });

        stringOptions.values().forEach(builder::addStringOption);

        return builder.build();
    }

    public LineTransform asTransform(OptionValues values) {
        return (index, line) -> edit(values, index, line);
    }

    private String edit(OptionValues values, int index, String existing) {
        BooleanOption booleanOption = booleanOptions.get(index);

        if (booleanOption != null) {
            OptionalBoolean value = values.getBooleanValue(booleanOption.getName());
            if (booleanOption.getType() == OptionType.DEFINE) {
                return setBooleanDefineValue(existing, value, booleanOption.getDefaultValue());
            } else if (booleanOption.getType() == OptionType.CONST) {
                if (value != OptionalBoolean.DEFAULT) {
                    // value is never default here, orElse only unwraps it
                    return editConst(existing, Boolean.toString(booleanOption.getDefaultValue()), Boolean.toString(value.orElse(booleanOption.getDefaultValue())));
                } else {
                    return existing;
                }
            } else {
                throw new AssertionError("Unknown option type " + booleanOption.getType());
            }
        }

        StringOption stringOption = stringOptions.get(index);

        if (stringOption != null) {
            return values.getStringValue(stringOption.getName()).map(value -> {
                if (stringOption.getType() == OptionType.DEFINE) {
                    return "#define " + stringOption.getName() + " " + value + " // OptionAnnotatedSource: Changed option";
                } else if (stringOption.getType() == OptionType.CONST) {
                    return editConst(existing, stringOption.getDefaultValue(), value);
                } else {
                    throw new AssertionError("Unknown option type " + stringOption.getType());
                }
            }).orElse(existing);
        }

        return existing;
    }

    private String editConst(String line, String currentValue, String newValue) {
        int equalsIndex = line.indexOf('=');

        if (equalsIndex == -1) {
            throw new IllegalStateException();
        }

        String firstPart = line.substring(0, equalsIndex);
        String secondPart = line.substring(equalsIndex);

        secondPart = secondPart.replaceFirst(Pattern.quote(currentValue), Matcher.quoteReplacement(newValue));

        return firstPart + secondPart;
    }

    private static boolean hasLeadingComment(String line) {
        return line.trim().startsWith("//");
    }

    private static String removeLeadingComment(String line) {
        ParsedString parsed = new ParsedString(line);

        parsed.takeSomeWhitespace();
        parsed.takeComments();

        return parsed.takeRest();
    }

    private static String setBooleanDefineValue(String line, OptionalBoolean newValue, boolean defaultValue) {
        if (hasLeadingComment(line) && newValue.orElse(defaultValue)) {
            return removeLeadingComment(line);
        } else if (!newValue.orElse(defaultValue)) {
            return "//" + line;
        } else {
            return line;
        }
    }

    private static class AnnotationsBuilder {
        private final ImmutableMap.Builder<Integer, BooleanOption> booleanOptions;
        private final ImmutableMap.Builder<Integer, StringOption> stringOptions;
        private final ImmutableMap.Builder<Integer, String> diagnostics;
        private final Map<String, IntList> booleanDefineReferences;

        private AnnotationsBuilder() {
            booleanOptions = ImmutableMap.builder();
            stringOptions = ImmutableMap.builder();
            diagnostics = ImmutableMap.builder();
            booleanDefineReferences = new HashMap<>();
        }
    }
}
