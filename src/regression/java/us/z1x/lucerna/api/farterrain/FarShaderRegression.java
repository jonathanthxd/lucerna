package us.z1x.lucerna.api.farterrain;

import java.util.Map;
import us.z1x.lucerna.shaderpack.*;
import us.z1x.lucerna.shaderpack.include.*;
import us.z1x.lucerna.shaderpack.loading.ProgramId;
import us.z1x.lucerna.pipeline.transform.*;

/** Real loader/AST transformations on synthetic GLSL; no GL compilation or game execution. */
public final class FarShaderRegression {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static void invalidRaw(String declaration) throws Exception {
        var method = ShaderPack.class.getDeclaredMethod("readRawTexture", String[].class, byte[].class,
            us.z1x.lucerna.shaderpack.texture.TextureFilteringData.class);
        method.setAccessible(true);
        try {
            method.invoke(null, declaration.split(" "), new byte[1],
                new us.z1x.lucerna.shaderpack.texture.TextureFilteringData(false, false));
            throw new AssertionError("Accepted malformed raw texture: " + declaration);
        } catch (java.lang.reflect.InvocationTargetException error) {
            check(error.getCause() instanceof java.io.IOException);
        }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("lucerna.prettyPrintShaders", "true");
        System.setProperty("lucerna.experimentalFarTerrain", "true");
        check(ShaderPackSourceNames.POTENTIAL_STARTS.contains("dh_terrain.vsh"));
        check(ShaderPackSourceNames.POTENTIAL_STARTS.contains("dh_water.fsh"));
        String vertex = "#version 120\nvarying float material; void main(){ material=float(dhMaterialId); gl_Position=gl_ProjectionMatrix*gl_ModelViewMatrix*gl_Vertex; }";
        String fragment = "#version 120\nvarying float material; /* DRAWBUFFERS:04 */ void main(){gl_FragData[0]=vec4(material);gl_FragData[1]=vec4(1.0);}";
        Map<String, String> sources = Map.of("/dh_terrain.vsh", vertex, "/dh_terrain.fsh", fragment,
            "/world0/dh_terrain.vsh", vertex, "/world0/dh_terrain.fsh", fragment,
            "/world0/dh_water.vsh", vertex, "/world0/dh_water.fsh", fragment);
        ProgramSet base = new ProgramSet(AbsolutePackPath.fromAbsolutePath("/"), p -> sources.get(p.getPathString()), ShaderProperties.empty(), null);
        check(base.get(ProgramId.DhTerrain).isPresent());
        check(base.get(ProgramId.DhWater).isEmpty());
        check(new ProgramFallbackResolver(base).resolve(ProgramId.DhWater).isEmpty());
        int[] outputs = base.get(ProgramId.DhTerrain).orElseThrow().getDirectives().getDrawBuffers();
        check(outputs.length == 2 && outputs[0] == 0 && outputs[1] == 4);
        ProgramSet dimension = new ProgramSet(AbsolutePackPath.fromAbsolutePath("/world0"), p -> sources.get(p.getPathString()), ShaderProperties.empty(), null);
        check(dimension.get(ProgramId.DhWater).isPresent());
        Map<PatchShaderType, String> transformed = TransformPatcher.patchFarTerrain(vertex, null, fragment, Map.of());
        check(transformed.get(PatchShaderType.VERTEX).contains("dhMaterialId"));
        check(transformed.get(PatchShaderType.VERTEX).contains("gl_MultiTexCoord3"));
        check(transformed.get(PatchShaderType.FRAGMENT).contains("lucernaDhProjectionInverse"));
        check(transformed.get(PatchShaderType.FRAGMENT).contains("lucernaNativeProjection"));
        check(transformed.get(PatchShaderType.FRAGMENT).contains("discard"));
        check(TransformPatcher.patchFarTerrain(vertex, null, fragment, Map.of()).equals(transformed));
        System.setProperty("lucerna.experimentalFarTerrain", "false");
        ProgramSet disabled = new ProgramSet(AbsolutePackPath.fromAbsolutePath("/"), p -> sources.get(p.getPathString()), ShaderProperties.empty(), null);
        check(disabled.get(ProgramId.DhTerrain).isEmpty());
        invalidRaw("noise.bin TEXTURE_3D");
        invalidRaw("noise.bin TEXTURE_3D R8 16 16 RED UNSIGNED_BYTE");
        invalidRaw("noise.bin TEXTURE_2D R8 -1 16 RED UNSIGNED_BYTE");
        invalidRaw("noise.bin TEXTURE_2D R8 nope 16 RED UNSIGNED_BYTE");
        invalidRaw("noise.bin TEXTURE_UNKNOWN R8 16 RED UNSIGNED_BYTE");
        System.out.println("Far terrain loader/AST and malformed texture checks passed: " + checks);
    }
}
