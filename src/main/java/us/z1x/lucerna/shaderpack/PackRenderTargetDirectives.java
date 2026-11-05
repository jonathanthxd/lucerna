package us.z1x.lucerna.shaderpack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Vector4f;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.texture.InternalTextureFormat;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PackRenderTargetDirectives {
    public static final ImmutableList<String> LEGACY_RENDER_TARGETS = ImmutableList.of(
        "gcolor",
        "gdepth",
        "gnormal",
        "composite",
        "gaux1",
        "gaux2",
        "gaux3",
        "gaux4"
    );

    public static final Set<Integer> BASELINE_SUPPORTED_RENDER_TARGETS;

    static {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();

        for (int i = 0; i < LucernaLimits.MAX_COLOR_BUFFERS; i++) {
            builder.add(i);
        }

        BASELINE_SUPPORTED_RENDER_TARGETS = builder.build();
    }

    private final Int2ObjectMap<RenderTargetSettings> renderTargetSettings;

    PackRenderTargetDirectives(Set<Integer> supportedRenderTargets) {
        this.renderTargetSettings = new Int2ObjectOpenHashMap<>();

        supportedRenderTargets.forEach(
                (index) -> renderTargetSettings.put(index.intValue(), new RenderTargetSettings()));
    }

    public IntList getBuffersToBeCleared() {
        IntList buffersToBeCleared = new IntArrayList();

        renderTargetSettings.forEach((index, settings) -> {
            if (settings.shouldClear()) {
                buffersToBeCleared.add(index.intValue());
            }
        });

        return buffersToBeCleared;
    }

    public Map<Integer, RenderTargetSettings> getRenderTargetSettings() {
        return Collections.unmodifiableMap(renderTargetSettings);
    }

    public void acceptDirectives(DirectiveHolder directives) {
        Optional.ofNullable(renderTargetSettings.get(7)).ifPresent(colortex7 -> {
            directives.acceptCommentStringDirective("GAUX4FORMAT", format -> {
                if ("RGBA32F".equals(format)) {
                    colortex7.requestedFormat = InternalTextureFormat.RGBA32F;
                } else if ("RGB32F".equals(format)) {
                    colortex7.requestedFormat = InternalTextureFormat.RGB32F;
                } else if ("RGB16".equals(format)) {
                    colortex7.requestedFormat = InternalTextureFormat.RGB16;
                } else {
                    Lucerna.logger.warn("Ignoring GAUX4FORMAT directive /* GAUX4FORMAT:{}*/ because {} must be RGBA32F, RGB32F, or RGB16. Use `const int colortex7Format = {};` + instead.", format, format, format);
                }
            });
        });

        // any gdepth uniform, even unsampled or mistyped, upgrades colortex1 from RGBA to RGBA32F
        Optional.ofNullable(renderTargetSettings.get(1)).ifPresent(gdepth -> {
            directives.acceptUniformDirective("gdepth", () -> {
                if (gdepth.requestedFormat == InternalTextureFormat.RGBA) {
                    gdepth.requestedFormat = InternalTextureFormat.RGBA32F;
                }
            });
        });

        renderTargetSettings.forEach((index, settings) -> {
            acceptBufferDirectives(directives, settings, "colortex" + index);

            if (index < LEGACY_RENDER_TARGETS.size()) {
                acceptBufferDirectives(directives, settings, LEGACY_RENDER_TARGETS.get(index));
            }
        });
    }

    private void acceptBufferDirectives(DirectiveHolder directives, RenderTargetSettings settings, String bufferName) {
        directives.acceptConstStringDirective(bufferName + "Format", format -> {
            Optional<InternalTextureFormat> internalFormat = InternalTextureFormat.fromString(format);

            if (internalFormat.isPresent()) {
                settings.requestedFormat = internalFormat.get();
            } else {
                Lucerna.logger.warn("Unrecognized internal texture format {} specified for {}Format, ignoring.", format, bufferName);
            }
        });

        directives.acceptConstBooleanDirective(bufferName + "Clear",
                shouldClear -> settings.clear = shouldClear);

        // still applies when clear is false since this is the initial color
        directives.acceptConstVec4Directive(bufferName + "ClearColor",
                clearColor -> settings.clearColor = clearColor);
    }

    public static final class RenderTargetSettings {
        private InternalTextureFormat requestedFormat;
        private boolean clear;
        private Vector4f clearColor;

        public RenderTargetSettings() {
            this.requestedFormat = InternalTextureFormat.RGBA;
            this.clear = true;
            this.clearColor = null;
        }

        public InternalTextureFormat getInternalFormat() {
            return requestedFormat;
        }

        public boolean shouldClear() {
            return clear;
        }

        public Optional<Vector4f> getClearColor() {
            return Optional.ofNullable(clearColor);
        }

        @Override
        public String toString() {
            return "RenderTargetSettings{" +
                    "requestedFormat=" + requestedFormat +
                    ", clear=" + clear +
                    ", clearColor=" + clearColor +
                    '}';
        }
    }
}
