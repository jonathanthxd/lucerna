package us.z1x.lucerna.shaderpack.preprocessor;

import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.shaderpack.StringPair;
import us.z1x.lucerna.shaderpack.option.ShaderPackOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PropertiesPreprocessor {
    // Derived from ShaderProcessor.glslPreprocessSource, which is derived from GlShader from Canvas, licenced under LGPL
    public static String preprocessSource(String source, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
        if (source.contains(PropertyCollectingListener.PROPERTY_MARKER)) {
            throw new RuntimeException("Some shader author is trying to exploit internal Lucerna implementation details, stop!");
        }

        List<String> booleanValues = getBooleanValues(shaderPackOptions);
        Map<String, String> stringValues = getStringValues(shaderPackOptions);

        try (Preprocessor pp = new Preprocessor()) {
            for (String value : booleanValues) {
                pp.addMacro(value);
            }

            for (StringPair envDefine : environmentDefines) {
                pp.addMacro(envDefine.getKey(), envDefine.getValue());
            }

            stringValues.forEach((name, value) -> {
                try {
                    pp.addMacro(name, value);
                } catch (LexerException e) {
                    Lucerna.logger.error("Failed to define the macro {}", name, e);
                }
            });

            return process(pp, source);
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException while processing macros", e);
        } catch (LexerException e) {
            throw new RuntimeException("Unexpected LexerException processing macros", e);
        }
    }

    private static String process(Preprocessor preprocessor, String source) {
        preprocessor.setListener(new PropertiesCommentListener());
        PropertyCollectingListener listener = new PropertyCollectingListener();
        preprocessor.setListener(listener);

        // trim lines to fix whitespace after line continuations, needed for voyager shader
        source = Arrays.stream(source.split("\\R")).map(String::trim)
            .map(line -> {
                if (line.startsWith("#")) {
                    // PropertyCollectingListener treats unknown directives as comments, as # is a comment in properties
                    return line;
                } else {
                    // hack: only macro lines get #define substitution in properties files
                    return "#warning IRIS_PASSTHROUGH " + line;
                }
            }).collect(Collectors.joining("\n")) + "\n";

        preprocessor.addInput(new StringLexerSource(source, true));
        preprocessor.addFeature(Feature.KEEPCOMMENTS);

        final StringBuilder builder = new StringBuilder();

        try {
            for (;;) {
                final Token tok = preprocessor.token();
                if (tok == null) break;
                if (tok.getType() == Token.EOF) break;
                builder.append(tok.getText());
            }
        } catch (final Exception e) {
            Lucerna.logger.error("Properties pre-processing failed", e);
        }

        source = builder.toString();

        return listener.collectLines() + source;
    }

    private static List<String> getBooleanValues(ShaderPackOptions shaderPackOptions) {
        List<String> booleanValues = new ArrayList<>();

        shaderPackOptions.getOptionSet().getBooleanOptions().forEach((string, value) -> {
            boolean trueValue = shaderPackOptions.getOptionValues().getBooleanValueOrDefault(string);

            if (trueValue) {
                booleanValues.add(string);
            }
        });

        return booleanValues;
    }

    private static Map<String, String> getStringValues(ShaderPackOptions shaderPackOptions) {
        Map<String, String> stringValues = new HashMap<>();

        shaderPackOptions.getOptionSet().getStringOptions().forEach(
                (optionName, value) -> stringValues.put(optionName, shaderPackOptions.getOptionValues().getStringValueOrDefault(optionName)));

        return stringValues;
    }
}
