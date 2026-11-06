package us.z1x.lucerna.gl.program;

import java.util.*;

/** Injects only uniform lookup and capacity; allocation is the production Builder. */
public final class SamplerFixture {
    public final ProgramSamplers.Builder builder;
    private final Map<String, Integer> locations = new LinkedHashMap<>();
    public SamplerFixture(int limit, Set<Integer> reserved, String... uniforms) {
        for (String name : uniforms) locations.put(name, locations.size());
        builder = new ProgramSamplers.Builder(37, reserved, limit, name -> locations.getOrDefault(name, -1));
    }
    public Map<String, Integer> assignments() {
        try {
            var field = ProgramSamplers.Builder.class.getDeclaredField("calls");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") List<GlUniform1iCall> calls = (List<GlUniform1iCall>) field.get(builder);
            Map<String, Integer> result = new LinkedHashMap<>();
            for (var call : calls) locations.forEach((name, location) -> {
                if (location == call.getLocation()) result.put(name, call.getValue());
            });
            return result;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public static int effectiveLimit(int fragment, int combined, int cache) {
        try {
            var method = us.z1x.lucerna.gl.sampler.SamplerLimits.class.getDeclaredMethod("effectiveTextureUnits", int.class, int.class, int.class);
            method.setAccessible(true);
            return (int) method.invoke(null, fragment, combined, cache);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
