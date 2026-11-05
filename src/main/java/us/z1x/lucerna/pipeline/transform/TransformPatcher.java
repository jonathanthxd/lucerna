package us.z1x.lucerna.pipeline.transform;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.EnumASTTransformer;
import io.github.douira.glsl_transformer.cst.core.SemanticException;
import io.github.douira.glsl_transformer.cst.token_filter.ChannelFilter;
import io.github.douira.glsl_transformer.cst.token_filter.TokenChannel;
import io.github.douira.glsl_transformer.cst.token_filter.TokenFilter;
import io.github.douira.glsl_transformer.util.LRUCache;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import us.z1x.lucerna.gbufferoverrides.matching.InputAvailability;
import us.z1x.lucerna.pipeline.PatchedShaderPrinter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// caches on source and parameters, so parameter objects need equals and must not change after use
// the source must have no preprocessor directives except #extension and #pragma
public class TransformPatcher {
    private static EnumASTTransformer<Parameters, PatchShaderType> transformer;
    private static final boolean USE_CACHE = true;
    private static final Map<CacheKey, Map<PatchShaderType, String>> cache =  new LRUCache<>(400);

    private static class CacheKey {
        final Parameters parameters;
        final String vertex;
        final String geometry;
        final String fragment;

        public CacheKey(Parameters parameters, String vertex, String geometry, String fragment) {
            this.parameters = parameters;
            this.vertex = vertex;
            this.geometry = geometry;
            this.fragment = fragment;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fragment == null) ? 0 : fragment.hashCode());
            result = prime * result + ((geometry == null) ? 0 : geometry.hashCode());
            result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
            result = prime * result + ((vertex == null) ? 0 : vertex.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (fragment == null) {
                if (other.fragment != null)
                    return false;
            } else if (!fragment.equals(other.fragment))
                return false;
            if (geometry == null) {
                if (other.geometry != null)
                    return false;
            } else if (!geometry.equals(other.geometry))
                return false;
            if (parameters == null) {
                if (other.parameters != null)
                    return false;
            } else if (!parameters.equals(other.parameters))
                return false;
            if (vertex == null) {
                if (other.vertex != null)
                    return false;
            } else if (!vertex.equals(other.vertex))
                return false;
            return true;
        }
    }

    static TokenFilter<Parameters> parseTokenFilter = new ChannelFilter<Parameters>(TokenChannel.PREPROCESSOR) {
        @Override
        public boolean isTokenAllowed(Token token) {
            if (!super.isTokenAllowed(token)) {
                throw new SemanticException("Unparsed preprocessor directives such as '" + token.getText()
                        + "' may not be present at this stage of shader processing!");
            }
            return true;
        }
    };

    static {
        transformer = new EnumASTTransformer<Parameters, PatchShaderType>(PatchShaderType.class) {
            @Override
            public TranslationUnit parseTranslationUnit(String input) throws RecognitionException {
                // parse #version with a regex first so the parser uses the right version
                Matcher matcher = VERSION_PATTERN.matcher(input);
                if (!matcher.find()) {
                    throw new IllegalArgumentException("No #version directive found in source code! See debugging.md for more information.");
                }
                Version version = Version.fromNumber(Integer.parseInt(matcher.group(1)));
                transformer.getLexer().version = version;

                return super.parseTranslationUnit(input);
            }
        };
        transformer.setTransformation((trees, parameters) -> {
            for (PatchShaderType type : PatchShaderType.values()) {
                TranslationUnit tree = trees.get(type);
                if (tree == null) {
                    continue;
                }
                tree.outputOptions.enablePrintInfo();

                parameters.type = type;
                Root root = tree.getRoot();

                // check for illegal references to internal iris shader interfaces
                Optional<Identifier> violation = root.identifierIndex.prefixQueryFlat("iris_").findAny();
                if (!violation.isPresent()) {
                    violation = root.identifierIndex.prefixQueryFlat("irisMain").findAny();
                }
                violation.ifPresent(id -> {
                    throw new SemanticException(
                            "Detected a potential reference to unstable and internal Lucerna shader interfaces (iris_ and irisMain). This isn't currently supported. Violation: "
                                    + id.getName() + ". See debugging.md for more information.");
                });

                Root.indexBuildSession(tree, () -> {
                    CompatibilityTransformer.transformCore(transformer, tree, root, parameters);
                    renameCustomTextures(root, parameters.textureRenames);
                    switch (parameters.patch) {
                        case ATTRIBUTES:
                            AttributeTransformer.transform(transformer, tree, root, (AttributeParameters) parameters);
                            break;
                        case CELERITAS_TERRAIN:
                            CeleritasTerrainTransformer.transform(transformer, tree, root, parameters);
                            break;
                        case INSTANCED_ENTITY:
                            InstancedEntityTransformer.transform(transformer, tree, root, parameters);
                            break;
                        case COMPOSITE:
                            CompositeTransformer.transform(transformer, tree, root);
                            break;
                    }
                    CompatibilityTransformer.transformEach(transformer, tree, root, parameters);
                });
            }

            // the compatibility transformer does a grouped transformation
            CompatibilityTransformer.transformGrouped(transformer, trees, parameters);
        });
        transformer.setParseTokenFilter(parseTokenFilter);
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile("^.*#version\\s+(\\d+)", Pattern.DOTALL);

    private static synchronized Map<PatchShaderType, String> transform(String vertex, String geometry, String fragment, Parameters parameters) {
        if (vertex == null && geometry == null && fragment == null) {
            return null;
        }

        CacheKey key;
        Map<PatchShaderType, String> result = null;
        if (USE_CACHE) {
            key = new CacheKey(parameters, vertex, geometry, fragment);
            if (cache.containsKey(key)) {
                result = cache.get(key);
            }
        }

        if (result == null) {
            transformer.setPrintType(PatchedShaderPrinter.PRETTY_PRINT_SHADERS ? PrintType.INDENTED : PrintType.SIMPLE);
            EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
            // a compute source travels in the vertex slot
            if (parameters.patch == Patch.COMPUTE) {
                inputs.put(PatchShaderType.COMPUTE, vertex);
            } else {
                inputs.put(PatchShaderType.VERTEX, vertex);
                inputs.put(PatchShaderType.GEOMETRY, geometry);
                inputs.put(PatchShaderType.FRAGMENT, fragment);
            }
            result = transformer.transform(inputs, parameters);
            if (USE_CACHE) {
                cache.put(key, result);
            }
        }

        return result;
    }

    public static Map<PatchShaderType, String> patchAttributes(String vertex, String geometry, String fragment, InputAvailability inputs, Map<String, String> textureRenames) {
        return transform(vertex, geometry, fragment, withRenames(new AttributeParameters(Patch.ATTRIBUTES, geometry != null, inputs), textureRenames));
    }

    public static Map<PatchShaderType, String> patchCeleritasTerrain(String vertex, String geometry, String fragment, Map<String, String> textureRenames) {
        return transform(vertex, geometry, fragment, withRenames(new Parameters(Patch.CELERITAS_TERRAIN), textureRenames));
    }

    public static Map<PatchShaderType, String> patchInstancedEntity(String vertex, String fragment, Map<String, String> textureRenames) {
        return transform(vertex, null, fragment, withRenames(new Parameters(Patch.INSTANCED_ENTITY), textureRenames));
    }

    public static Map<PatchShaderType, String> patchComposite(String vertex, String geometry, String fragment, Map<String, String> textureRenames) {
        return transform(vertex, geometry, fragment, withRenames(new Parameters(Patch.COMPOSITE), textureRenames));
    }

    public static String patchCompute(String compute, Map<String, String> textureRenames) {
        return transform(compute, null, null, withRenames(new Parameters(Patch.COMPUTE), textureRenames)).get(PatchShaderType.COMPUTE);
    }

    private static Parameters withRenames(Parameters parameters, Map<String, String> textureRenames) {
        parameters.textureRenames = textureRenames;
        return parameters;
    }

    private static void renameCustomTextures(Root root, Map<String, String> textureRenames) {
        textureRenames.forEach((declaration, name) -> {
            String[] parts = declaration.split(" ");

            for (Identifier id : root.identifierIndex.get(parts[1])) {
                TypeAndInitDeclaration initDeclaration = id.getParent() instanceof DeclarationMember ? id.getAncestor(TypeAndInitDeclaration.class) : null;

                if (initDeclaration != null && initDeclaration.getType().getTypeSpecifier() instanceof BuiltinFixedTypeSpecifier specifier
                        && specifier.type.name().equals(parts[0])) {
                    root.rename(parts[1], name);
                    break;
                }
            }
        });
    }
}
