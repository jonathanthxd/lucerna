package us.z1x.lucerna.gl.program;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL20C;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.sampler.SamplerBinding;
import us.z1x.lucerna.gl.sampler.SamplerHolder;
import us.z1x.lucerna.gl.sampler.SamplerLimits;
import us.z1x.lucerna.gl.state.ValueUpdateNotifier;
import us.z1x.lucerna.shaderpack.PackRenderTargetDirectives;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

public class ProgramSamplers {
    private static ProgramSamplers active;
    private final ImmutableList<SamplerBinding> samplerBindings;
    private final ImmutableList<ValueUpdateNotifier> notifiersToReset;
    private List<GlUniform1iCall> initializer;

    private ProgramSamplers(ImmutableList<SamplerBinding> samplerBindings, ImmutableList<ValueUpdateNotifier> notifiersToReset, List<GlUniform1iCall> initializer) {
        this.samplerBindings = samplerBindings;
        this.notifiersToReset = notifiersToReset;
        this.initializer = initializer;
    }

    public void update() {
        if (active != null) {
            active.removeListeners();
        }

        active = this;

        if (initializer != null) {
            for (GlUniform1iCall call : initializer) {
                LucernaRenderSystem.uniform1i(call.getLocation(), call.getValue());
            }

            initializer = null;
        }

        // the active texture must stay intact, changing it during render setup breaks rendering
        int activeTexture = GlStateManager.texture;

        for (SamplerBinding samplerBinding : samplerBindings) {
            samplerBinding.update();
        }

        GlStateManager.activeTexture(GL20C.GL_TEXTURE0 + activeTexture);
    }

    public void removeListeners() {
        active = null;

        for (ValueUpdateNotifier notifier : notifiersToReset) {
            notifier.setListener(null);
        }
    }

    public static void clearActiveSamplers() {
        if (active != null) {
            active.removeListeners();
        }
    }

    public static Builder builder(int program, Set<Integer> reservedTextureUnits) {
        return new Builder(program, reservedTextureUnits);
    }

    public static CustomTextureSamplerInterceptor customTextureSamplerInterceptor(SamplerHolder samplerHolder, Object2ObjectMap<String, IntSupplier> customTextureIds) {
        return customTextureSamplerInterceptor(samplerHolder, customTextureIds, ImmutableSet.of());
    }

    public static CustomTextureSamplerInterceptor customTextureSamplerInterceptor(SamplerHolder samplerHolder, Object2ObjectMap<String, IntSupplier> customTextureIds, ImmutableSet<Integer> flippedAtLeastOnceSnapshot) {
        return new CustomTextureSamplerInterceptor(samplerHolder, customTextureIds, flippedAtLeastOnceSnapshot);
    }

    public static final class Builder implements SamplerHolder {
        private final int program;
        private final ImmutableSet<Integer> reservedTextureUnits;
        private final ImmutableList.Builder<SamplerBinding> samplers;
        private final ImmutableList.Builder<ValueUpdateNotifier> notifiersToReset;
        private final List<GlUniform1iCall> calls;
        private int remainingUnits;
        private int nextUnit;

        private Builder(int program, Set<Integer> reservedTextureUnits) {
            this.program = program;
            this.reservedTextureUnits = ImmutableSet.copyOf(reservedTextureUnits);
            this.samplers = ImmutableList.builder();
            this.notifiersToReset = ImmutableList.builder();
            this.calls = new ArrayList<>();

            int maxTextureUnits = SamplerLimits.get().getMaxTextureUnits();

            for (int unit : reservedTextureUnits) {
                if (unit >= maxTextureUnits) {
                    throw new IllegalStateException("Cannot mark texture unit " + unit + " as reserved because that " +
                            "texture unit isn't available on this system! Only " + maxTextureUnits +
                            " texture units are available.");
                }
            }

            this.remainingUnits = maxTextureUnits - reservedTextureUnits.size();
            this.nextUnit = 0;

            while (reservedTextureUnits.contains(nextUnit)) {
                nextUnit += 1;
            }
        }

        @Override
        public void addExternalSampler(int textureUnit, String... names) {
            if (!reservedTextureUnits.contains(textureUnit)) {
                throw new IllegalArgumentException("Cannot add an externally-managed sampler for texture unit " +
                        textureUnit + " since it isn't in the set of reserved texture units.");
            }

            for (String name : names) {
                int location = LucernaRenderSystem.getUniformLocation(program, name);

                if (location == -1) {
                    continue;
                }

                calls.add(new GlUniform1iCall(location, textureUnit));
            }
        }

        @Override
        public boolean hasSampler(String name) {
            return LucernaRenderSystem.getUniformLocation(program, name) != -1;
        }

        @Override
        public boolean addDefaultSampler(IntSupplier sampler, String... names) {
            if (nextUnit != 0) {
                throw new IllegalStateException("Texture unit 0 is already used.");
            }

            return addDynamicSampler(sampler, true, null, names);
        }

        @Override
        public boolean addDynamicSampler(IntSupplier sampler, String... names) {
            return addDynamicSampler(sampler, false, null, names);
        }

        @Override
        public boolean addDynamicSampler(IntSupplier sampler, ValueUpdateNotifier notifier, String... names) {
            notifiersToReset.add(notifier);
            return addDynamicSampler(sampler, false, notifier, names);
        }

        private boolean addDynamicSampler(IntSupplier sampler, boolean used, ValueUpdateNotifier notifier, String... names) {
            for (String name : names) {
                int location = LucernaRenderSystem.getUniformLocation(program, name);

                if (location == -1) {
                    continue;
                }

                if (remainingUnits <= 0) {
                    throw new IllegalStateException("No more available texture units while activating sampler " + name);
                }

                calls.add(new GlUniform1iCall(location, nextUnit));

                used = true;
            }

            if (!used) {
                return false;
            }

            samplers.add(new SamplerBinding(nextUnit, sampler, notifier));

            remainingUnits -= 1;
            nextUnit += 1;

            while (remainingUnits > 0 && reservedTextureUnits.contains(nextUnit)) {
                nextUnit += 1;
            }

            return true;
        }

        public ProgramSamplers build() {
            return new ProgramSamplers(samplers.build(), notifiersToReset.build(), calls);
        }
    }

    public static final class CustomTextureSamplerInterceptor implements SamplerHolder {
        private final SamplerHolder samplerHolder;
        private final Object2ObjectMap<String, IntSupplier> customTextureIds;
        private final ImmutableSet<String> deactivatedOverrides;
        private boolean customTexturesAdded;

        private CustomTextureSamplerInterceptor(SamplerHolder samplerHolder, Object2ObjectMap<String, IntSupplier> customTextureIds, ImmutableSet<Integer> flippedAtLeastOnceSnapshot) {
            this.samplerHolder = samplerHolder;
            this.customTextureIds = customTextureIds;

            ImmutableSet.Builder<String> deactivatedOverrides = new ImmutableSet.Builder<>();

            for (int deactivatedOverride : flippedAtLeastOnceSnapshot) {
                deactivatedOverrides.add("colortex" + deactivatedOverride);

                if (deactivatedOverride < PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.size()) {
                    deactivatedOverrides.add(PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.get(deactivatedOverride));
                }
            }

            this.deactivatedOverrides = deactivatedOverrides.build();
        }

        // raw custom textures have samplers of their own, the patcher renames the pack's matching declarations to them
        // they go after the first sampler, which may have to be the default one on unit 0
        private <T> T addCustomTextures(T result) {
            if (!customTexturesAdded) {
                customTexturesAdded = true;
                customTextureIds.forEach((name, texture) -> {
                    if (name.startsWith("customtex")) {
                        samplerHolder.addDynamicSampler(texture, name);
                    }
                });
            }

            return result;
        }

        private IntSupplier getOverride(IntSupplier existing, String... names) {
            for (String name : names) {
                if (customTextureIds.containsKey(name) && !deactivatedOverrides.contains(name)) {
                    return customTextureIds.get(name);
                }
            }

            return existing;
        }

        @Override
        public void addExternalSampler(int textureUnit, String... names) {
            IntSupplier override = getOverride(null, names);

            if (override != null) {
                if (textureUnit == 0) {
                    samplerHolder.addDefaultSampler(override, names);
                } else {
                    samplerHolder.addDynamicSampler(override, names);
                }
            } else {
                samplerHolder.addExternalSampler(textureUnit, names);
            }

            addCustomTextures(null);
        }

        @Override
        public boolean hasSampler(String name) {
            return samplerHolder.hasSampler(name);
        }

        @Override
        public boolean addDefaultSampler(IntSupplier sampler, String... names) {
            sampler = getOverride(sampler, names);

            return addCustomTextures(samplerHolder.addDefaultSampler(sampler, names));
        }

        @Override
        public boolean addDynamicSampler(IntSupplier sampler, String... names) {
            sampler = getOverride(sampler, names);

            return addCustomTextures(samplerHolder.addDynamicSampler(sampler, names));
        }

        @Override
        public boolean addDynamicSampler(IntSupplier sampler, ValueUpdateNotifier notifier, String... names) {
            sampler = getOverride(sampler, names);

            return addCustomTextures(samplerHolder.addDynamicSampler(sampler, notifier, names));
        }
    }
}
