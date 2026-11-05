package us.z1x.lucerna.pipeline.transform;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.match.AutoHintedMatcher;
import io.github.douira.glsl_transformer.ast.query.match.Matcher;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.util.Type;

// patches terrain programs to read the celeritas chunk vertices that argentum draws
class CeleritasTerrainTransformer {
    public static void transform(
            ASTParser t,
            TranslationUnit tree,
            Root root,
            Parameters parameters) {
        switch (parameters.type) {
            case FRAGMENT:
                transformFragment(t, tree, root, parameters);
                break;
            case GEOMETRY:
                transformShared(t, tree, root, parameters);
                break;
            case VERTEX:
                transformVertex(t, tree, root, parameters);
                break;
            default:
                throw new IllegalStateException("Unexpected Sodium terrain patching shader type: " + parameters.type);
        }
    }

    private static final AutoHintedMatcher<Expression> glTextureMatrix0 = new AutoHintedMatcher<>(
            "gl_TextureMatrix[0]", Matcher.expressionPattern);
    private static final AutoHintedMatcher<Expression> glTextureMatrix1 = new AutoHintedMatcher<>(
            "gl_TextureMatrix[1]", Matcher.expressionPattern);

    public static void transformVertex(
            ASTParser t,
            TranslationUnit tree,
            Root root,
            Parameters parameters) {
        String in = tree.getVersionStatement().version.number >= 130 ? "in" : "attribute";

        tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
                in + " vec3 a_PosId;",
                in + " vec4 a_Color;",
                in + " vec2 a_TexCoord;",
                in + " vec2 iris_LightCoord;",
                in + " vec4 iris_DrawParams;",
                in + " vec3 iris_Normal;",
                "uniform vec3 u_RegionOffset;");
        tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
                // the draw id packs the section position inside its region as x << 5 | z << 2 | y
                "vec4 iris_getVertexPosition() { float id = iris_DrawParams.x; return vec4(a_PosId + u_RegionOffset + vec3(mod(floor(id / 32.0), 8.0), mod(id, 4.0), mod(floor(id / 4.0), 8.0)) * 16.0, 1.0); }",
                "vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");

        transformShared(t, tree, root, parameters);

        root.replaceReferenceExpressions(t, "gl_Vertex", "iris_getVertexPosition()");
        root.replaceReferenceExpressions(t, "gl_MultiTexCoord0", "vec4(a_TexCoord, 0.0, 1.0)");
        root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(iris_LightCoord, 0.0, 1.0)");
        root.replaceReferenceExpressions(t, "gl_MultiTexCoord2", "vec4(iris_LightCoord, 0.0, 1.0)");
        root.rename("gl_Color", "a_Color");
        root.rename("gl_Normal", "iris_Normal");
        root.rename("ftransform", "iris_ftransform");
    }

    // applies the cutout alpha test that the fixed function pipeline would do
    public static void transformFragment(
            ASTParser t,
            TranslationUnit tree,
            Root root,
            Parameters parameters) {
        transformShared(t, tree, root, parameters);

        String output = findFragmentOutput(root);

        if (output != null) {
            tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uniform float iris_currentAlphaTest;");
            tree.appendMain(t, "if (" + output + ".a < iris_currentAlphaTest) discard;");
        }
    }

    // the first colour output, packs written against modern iris declare their own out variables
    static String findFragmentOutput(Root root) {
        if (root.identifierIndex.has("gl_FragData")) {
            return "gl_FragData[0]";
        } else if (root.identifierIndex.has("gl_FragColor")) {
            return "gl_FragColor";
        }

        for (DeclarationExternalDeclaration declaration : root.nodeIndex.get(DeclarationExternalDeclaration.class)) {
            if (declaration.getDeclaration() instanceof TypeAndInitDeclaration variable && variable.getType().getTypeQualifier() != null
                    && variable.getType().getTypeQualifier().getChildren().stream().anyMatch(part -> part instanceof StorageQualifier storage && storage.storageType == StorageQualifier.StorageType.OUT)) {
                // the alpha test and the glint layers write rgba, so only a plain vec4 output works
                boolean vec4 = variable.getType().getTypeSpecifier() instanceof BuiltinNumericTypeSpecifier numeric && numeric.type == Type.F32VEC4;
                return vec4 && variable.getMembers().get(0).getArraySpecifier() == null ? variable.getMembers().get(0).getName().getName() : null;
            }
        }

        return null;
    }

    private static void transformShared(
            ASTParser t,
            TranslationUnit tree,
            Root root,
            Parameters parameters) {
        tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
                "uniform mat4 iris_ModelViewMatrix;",
                "uniform mat4 iris_ModelViewMatrixInverse;",
                "uniform mat4 iris_ProjectionMatrix;",
                "uniform mat4 iris_ProjectionMatrixInverse;",
                "uniform mat3 iris_NormalMatrix;",
                "uniform mat4 iris_LightmapTextureMatrix;");
        root.rename("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        root.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        root.rename("gl_ProjectionMatrix", "iris_ProjectionMatrix");
        root.rename("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");
        root.replaceReferenceExpressions(t, "gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        root.rename("gl_NormalMatrix", "iris_NormalMatrix");

        root.replaceExpressionMatches(t, glTextureMatrix0, "mat4(1.0)");
        root.replaceExpressionMatches(t, glTextureMatrix1, "iris_LightmapTextureMatrix");
    }
}
