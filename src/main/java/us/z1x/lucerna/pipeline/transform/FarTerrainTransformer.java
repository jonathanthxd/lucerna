package us.z1x.lucerna.pipeline.transform;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;

/** DH semantics over Lucerna's compatibility vertex arrays, not DH's packed integer VBO format. */
final class FarTerrainTransformer {
    static void transform(ASTParser parser, TranslationUnit tree, Root root, Parameters parameters) {
        if (parameters.type == PatchShaderType.FRAGMENT) {
            tree.parseAndInjectNodes(parser, ASTInjectionPoint.BEFORE_FUNCTIONS,
                "uniform mat4 lucernaDhProjectionInverse;",
                "uniform mat4 lucernaNativeProjection;",
                "uniform ivec2 lucernaViewport;",
                "uniform sampler2D lucernaNativeDepth;");
            // DH and vanilla use different projections; never compare their raw depth values.
            // Also protects water drawn after deferred from overwriting nearer vanilla geometry.
            tree.prependMain(parser,
                "vec2 lucernaUv = gl_FragCoord.xy / vec2(lucernaViewport);",
                "vec4 lucernaView = lucernaDhProjectionInverse * vec4(lucernaUv * 2.0 - 1.0, gl_FragCoord.z * 2.0 - 1.0, 1.0);",
                "vec4 lucernaClip = lucernaNativeProjection * lucernaView;",
                "float lucernaDepth = texture2D(lucernaNativeDepth, lucernaUv).r;",
                "if (lucernaDepth < 1.0 && lucernaClip.w > 0.0 && lucernaClip.z / lucernaClip.w * 0.5 + 0.5 > lucernaDepth + 0.000001) discard;");
            return;
        }
        if (parameters.type != PatchShaderType.VERTEX) return;
        root.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");
        boolean declared = root.identifierIndex.getStream("dhMaterialId")
            .anyMatch(id -> id.getParent() instanceof DeclarationMember);
        if (declared) throw new IllegalArgumentException("dhMaterialId is provided by Lucerna, do not redeclare it");
        tree.parseAndInjectNode(parser, ASTInjectionPoint.BEFORE_FUNCTIONS, "int dhMaterialId;");
        tree.prependMain(parser, "dhMaterialId = int(gl_MultiTexCoord3.x);");
    }
}
