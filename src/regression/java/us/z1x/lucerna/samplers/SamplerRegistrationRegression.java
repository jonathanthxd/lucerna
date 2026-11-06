package us.z1x.lucerna.samplers;

import java.nio.file.*;
import java.util.*;
import java.util.function.IntSupplier;
import com.google.gson.Gson;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import us.z1x.lucerna.compat.farterrain.FarTerrainUniforms;
import us.z1x.lucerna.gl.program.ProgramSamplers;
import us.z1x.lucerna.gl.program.SamplerFixture;
import us.z1x.lucerna.gl.sampler.SamplerHolder;

public final class SamplerRegistrationRegression {
    private static int checks;
    private static final List<Map<String,Object>> nativeCases = new ArrayList<>();
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Sampler check " + checks); }
    private static void fails(Runnable action, String message) {
        try { action.run(); throw new AssertionError("Expected " + message); }
        catch (IllegalStateException | IllegalArgumentException expected) { check(expected.getMessage().contains(message)); }
    }
    private static void register(SamplerHolder holder, boolean fullscreen) {
        LucernaSamplers.addRenderTargetSamplers(holder, 8, index -> () -> 100 + index, List.of(), fullscreen);
    }
    private static void nativeCase(String name, Map<String,Integer> units) {
        nativeCases.add(Map.of("name",name,"units",units));
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("lucerna.experimentalFarTerrain", "true");
        // Exact failed ordering from tfl.1, including an active DH uniform and unit 0 available.
        var old = new SamplerFixture(16, Set.of(1,2), "colortex0", "dhDepthTex0", "dhDepthTex1");
        FarTerrainUniforms.addSamplers(old.builder);
        check(old.assignments().get("dhDepthTex0") == 0);
        fails(() -> old.builder.addDefaultSampler(() -> 100,"colortex0"), "Texture unit 0 is already used");

        for (String pass : List.of("deferred", "composite", "final", "compute")) {
            var fixture = new SamplerFixture(16, Set.of(1,2), "colortex0", "colortex4", "dhDepthTex0", "dhDepthTex1", "depthtex0", "noisetex");
            register(fixture.builder, true);
            fixture.builder.addDynamicSampler(() -> 201, "noisetex");
            fixture.builder.addDynamicSampler(() -> 202, "depthtex0");
            var units = fixture.assignments();
            check(units.get("colortex0") == 0);
            check(units.get("colortex4") == 3);
            check(units.get("dhDepthTex0") > 0 && units.get("dhDepthTex1") > 0);
            check(new HashSet<>(units.values()).size() == units.size());
            check(units.values().stream().noneMatch(u -> u == 1 || u == 2 || u >= 16));
            fixture.builder.build();
            nativeCase(pass + "-dh", units);
        }
        Map<String,Integer> baseline = null;
        for (boolean enabled : new boolean[]{false,true}) {
            System.setProperty("lucerna.experimentalFarTerrain", Boolean.toString(enabled));
            var fixture = new SamplerFixture(8, Set.of(1,2), "colortex0", "colortex4", "noisetex");
            register(fixture.builder, true); fixture.builder.addDynamicSampler(() -> 203,"noisetex");
            var units=fixture.assignments();
            check(units.equals(Map.of("colortex0",0,"colortex4",3,"noisetex",4)));
            if (baseline != null) check(units.equals(baseline));
            baseline = units;
            nativeCase("no-dh-uniforms-flag-" + enabled, units);
        }
        System.setProperty("lucerna.experimentalFarTerrain", "true");
        var optimized = new SamplerFixture(8, Set.of(1,2), "dhDepthTex0", "dhDepthTex1");
        register(optimized.builder,true);
        check(optimized.assignments().equals(Map.of("dhDepthTex0",3,"dhDepthTex1",4)));
        nativeCase("optimized-out-colortex0",optimized.assignments());
        var aliases=new SamplerFixture(8,Set.of(1,2),"colortex0","gcolor","dhDepthTex0","dhDepthTex1");
        register(aliases.builder,true);
        check(aliases.assignments().get("colortex0")==0 && aliases.assignments().get("gcolor")==0);
        check(aliases.assignments().get("dhDepthTex0")==3);
        nativeCase("legacy-alias",aliases.assignments());
        var custom=new SamplerFixture(8,Set.of(1,2),"colortex0","customtex0","dhDepthTex0","dhDepthTex1");
        var textures=new Object2ObjectOpenHashMap<String,IntSupplier>();
        textures.put("customtex0",()->501); textures.put("colortex0",()->502);
        register(ProgramSamplers.customTextureSamplerInterceptor(custom.builder,textures),true);
        check(custom.assignments().get("colortex0")==0);
        check(custom.assignments().get("customtex0")==3);
        check(custom.assignments().get("dhDepthTex0")==4);
        check(custom.assignments().get("dhDepthTex1")==5);
        nativeCase("custom-textures",custom.assignments());
        var world=new SamplerFixture(8,Set.of(0,1,2),"colortex4","dhDepthTex0","dhDepthTex1","tex","lightmap");
        register(world.builder,false);
        world.builder.addExternalSampler(0,"tex");world.builder.addExternalSampler(1,"lightmap");
        check(world.assignments().get("tex")==0 && world.assignments().get("lightmap")==1);
        check(world.assignments().get("colortex4")==3 && world.assignments().get("dhDepthTex0")==4);
        nativeCase("world-reserved",world.assignments());
        var override=new SamplerFixture(8,Set.of(0,1,2),"colortex4","dhDepthTex0","dhDepthTex1","tex");
        textures.clear();textures.put("tex",()->503);
        var intercepted=ProgramSamplers.customTextureSamplerInterceptor(override.builder,textures);
        register(intercepted,false);intercepted.addExternalSampler(0,"tex");
        check(override.assignments().get("tex")==6);
        nativeCase("world-external-zero-override",override.assignments());
        var full=new SamplerFixture(8,Set.of(1,2),"colortex0","dhDepthTex0","dhDepthTex1","a","b","c","overflow");
        register(full.builder,true);
        for(String name:List.of("a","b","c")) check(full.builder.addDynamicSampler(()->99,name));
        check(full.assignments().get("c")==7);
        check(!full.builder.addDynamicSampler(()->99,"optimizedOut"));
        fails(()->full.builder.addDynamicSampler(()->99,"overflow"),"usable limit=8");
        check(full.assignments().values().stream().allMatch(u->u<8));
        var tiny=new SamplerFixture(4,Set.of(1,2),"colortex0","dhDepthTex0","dhDepthTex1");
        fails(()->register(tiny.builder,true),"dhDepthTex1");
        fails(()->new SamplerFixture(4,Set.of(-1)),"reserved");
        fails(()->new SamplerFixture(4,Set.of(4)),"reserved");
        check(SamplerFixture.effectiveLimit(8,48,32)==8);
        check(SamplerFixture.effectiveLimit(16,8,32)==8);
        check(SamplerFixture.effectiveLimit(64,192,32)==32);
        check(SamplerFixture.effectiveLimit(16,192,32)==16);
        // Active uniforms captured by compiling/linking the user's original Mellow 3.5 ZIP.
        // Replay their allocation on both an 8-unit legacy budget and a 16-unit budget.
        int mellowCollisions = 0;
        var resource = SamplerRegistrationRegression.class.getResourceAsStream("/mellow-3.5-active-samplers.json");
        if (resource == null) throw new AssertionError("Missing original Mellow sampler fixture");
        var probe = new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        check(probe.getAsJsonArray("failures").size()==0);
        for (var entry : probe.getAsJsonArray("programs")) {
            var program = entry.getAsJsonObject();
            boolean dh = program.get("dh").getAsBoolean();
            System.setProperty("lucerna.experimentalFarTerrain", Boolean.toString(dh));
            Set<String> names = new LinkedHashSet<>();
            program.getAsJsonObject("samplers").entrySet().forEach(value -> names.add(value.getKey()));
            String[] uniforms = names.toArray(String[]::new);
            if (dh && (names.contains("dhDepthTex0") || names.contains("dhDepthTex1"))) {
                var broken = new SamplerFixture(16, Set.of(1,2), uniforms);
                FarTerrainUniforms.addSamplers(broken.builder);
                fails(() -> broken.builder.addDefaultSampler(() -> 100,"colortex0","gcolor"), "Texture unit 0 is already used");
                mellowCollisions++;
            }
            for (int capacity : new int[]{8,16}) {
                var fixture = new SamplerFixture(capacity, Set.of(1,2), uniforms);
                LucernaSamplers.addRenderTargetSamplers(fixture.builder,16,index->()->100+index,List.of(),true);
                LucernaSamplers.addNoiseSampler(fixture.builder,()->200);
                LucernaSamplers.addCompositeSamplers(fixture.builder,()->201,()->202,()->203);
                Map<String,Integer> assignments = fixture.assignments();
                check(assignments.keySet().equals(names));
                check(assignments.values().stream().allMatch(unit->unit<capacity && unit!=1 && unit!=2));
                check(new HashSet<>(assignments.values()).size()==assignments.size());
                fixture.builder.build();
                if(capacity==16)nativeCase("mellow-"+program.get("name").getAsString()+"-dh-"+dh,assignments);
            }
        }
        check(mellowCollisions==4);
        System.out.println("Original Mellow active-signature replay: 34 programs x 2 capacities; tfl.1 collisions="+mellowCollisions);
        if(args.length>0) { Path report=Path.of(args[0]); Files.createDirectories(report.getParent()); Files.writeString(report,new Gson().toJson(nativeCases)); }
        System.out.println("Sampler allocation/registration checks passed: " + checks + "; old tfl.1 collision reproduced; native cases=" + nativeCases.size());
    }
}
