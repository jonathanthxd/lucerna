package us.z1x.lucerna.shaderpack;

import com.google.common.collect.ImmutableList;
import org.joml.Vector4f;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.texture.InternalTextureFormat;
import us.z1x.lucerna.gui.option.LucernaVideoSettings;

import java.util.Optional;

public class PackShadowDirectives {
    // 2 for shadersmod and optifine parity, can go up to 8
    public static final int MAX_SHADOW_COLOR_BUFFERS = 2;

    private final OptionalBoolean shadowEnabled;

    private int resolution;
    // boxed so null means no fov
    private Float fov;
    private float distance;
    private float distanceRenderMul;
    private float entityShadowDistanceMul;
    private boolean explicitRenderDistance;
    private float intervalSize;

    private final boolean shouldRenderTerrain;
    private final boolean shouldRenderTranslucent;
    private final boolean shouldRenderEntities;
    private final boolean shouldRenderPlayer;
    private final boolean shouldRenderBlockEntities;
    private final OptionalBoolean cullingState;

    private final ImmutableList<DepthSamplingSettings> depthSamplingSettings;
    private final ImmutableList<SamplingSettings> colorSamplingSettings;

    public PackShadowDirectives(ShaderProperties properties) {
        this.resolution = 1024;

        // null fov means an orthographic projection sized by shadowDistance
        // with fov the projection is perspective and shadowDistance only sets the render distance
        this.fov = null;

        // 160 m half plane, about 10 chunks of render distance
        this.distance = 160.0f;

        // negative means no distance culling, matrix culling fails with the non-linear distortion packs use
        this.distanceRenderMul = -1.0f;
        this.entityShadowDistanceMul = 1.0f;
        this.explicitRenderDistance = false;

        // the shadow camera snaps to a 2 m grid and moves only with the sun or on a cell change
        this.intervalSize = 2.0f;

        this.shouldRenderTerrain = properties.getShadowTerrain().orElse(true);
        this.shouldRenderTranslucent = properties.getShadowTranslucent().orElse(true);
        this.shouldRenderEntities = properties.getShadowEntities().orElse(true);
        this.shouldRenderPlayer = properties.getShadowPlayer().orElse(false);
        this.shouldRenderBlockEntities = properties.getShadowBlockEntities().orElse(true);
        this.cullingState = properties.getShadowCulling();
        this.shadowEnabled = properties.getShadowEnabled();

        this.depthSamplingSettings = ImmutableList.of(new DepthSamplingSettings(), new DepthSamplingSettings());

        ImmutableList.Builder<SamplingSettings> colorSamplingSettings = ImmutableList.builder();

        for (int i = 0; i < MAX_SHADOW_COLOR_BUFFERS; i++) {
            colorSamplingSettings.add(new SamplingSettings());
        }

        this.colorSamplingSettings = colorSamplingSettings.build();
    }

    public PackShadowDirectives(PackShadowDirectives shadowDirectives) {
        this.resolution = shadowDirectives.resolution;
        this.fov = shadowDirectives.fov;
        this.distance = shadowDirectives.distance;
        this.distanceRenderMul = shadowDirectives.distanceRenderMul;
        this.entityShadowDistanceMul = shadowDirectives.entityShadowDistanceMul;
        this.explicitRenderDistance = shadowDirectives.explicitRenderDistance;
        this.intervalSize = shadowDirectives.intervalSize;
        this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain;
        this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent;
        this.shouldRenderEntities = shadowDirectives.shouldRenderEntities;
        this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer;
        this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities;
        this.cullingState = shadowDirectives.cullingState;
        this.depthSamplingSettings = shadowDirectives.depthSamplingSettings;
        this.colorSamplingSettings = shadowDirectives.colorSamplingSettings;
        this.shadowEnabled = shadowDirectives.shadowEnabled;
    }

    public int getResolution() {
        return LucernaVideoSettings.scaleShadowResolution(resolution);
    }

    public Float getFov() {
        return fov;
    }

    public float getDistance() {
        return distance;
    }

    public float getDistanceRenderMul() {
        return distanceRenderMul;
    }

    public float getEntityShadowDistanceMul() {
        return entityShadowDistanceMul;
    }

    public boolean isDistanceRenderMulExplicit() {
        return explicitRenderDistance;
    }

    public float getIntervalSize() {
        return intervalSize;
    }

    public boolean shouldRenderTerrain() {
        return shouldRenderTerrain;
    }

    public boolean shouldRenderTranslucent() {
        return shouldRenderTranslucent;
    }

    public boolean shouldRenderEntities() {
        return shouldRenderEntities;
    }

    public boolean shouldRenderPlayer() {
        return shouldRenderPlayer;
    }

    public boolean shouldRenderBlockEntities() {
        return shouldRenderBlockEntities;
    }

    public OptionalBoolean getCullingState() {
        return cullingState;
    }

    public OptionalBoolean isShadowEnabled() {
        return shadowEnabled;
    }

    public ImmutableList<DepthSamplingSettings> getDepthSamplingSettings() {
        return depthSamplingSettings;
    }

    public ImmutableList<SamplingSettings> getColorSamplingSettings() {
        return colorSamplingSettings;
    }

    public void acceptDirectives(DirectiveHolder directives) {
        directives.acceptCommentIntDirective("SHADOWRES", resolution -> this.resolution = resolution);
        directives.acceptConstIntDirective("shadowMapResolution", resolution -> this.resolution = resolution);

        directives.acceptCommentFloatDirective("SHADOWFOV", fov -> this.fov = fov);
        directives.acceptConstFloatDirective("shadowMapFov", fov -> this.fov = fov);

        directives.acceptCommentFloatDirective("SHADOWHPL", distance -> this.distance = distance);
        directives.acceptConstFloatDirective("shadowDistance", distance -> this.distance = distance);

        directives.acceptConstFloatDirective("entityShadowDistanceMul", distance -> this.entityShadowDistanceMul = distance);

        directives.acceptConstFloatDirective("shadowDistanceRenderMul", distanceRenderMul -> {
            this.distanceRenderMul = distanceRenderMul;
            this.explicitRenderDistance = true;
        });

        directives.acceptConstFloatDirective("shadowIntervalSize",
                intervalSize -> this.intervalSize = intervalSize);

        acceptHardwareFilteringSettings(directives, depthSamplingSettings);
        acceptDepthMipmapSettings(directives, depthSamplingSettings);
        acceptColorMipmapSettings(directives, colorSamplingSettings);
        acceptDepthFilteringSettings(directives, depthSamplingSettings);
        acceptColorFilteringSettings(directives, colorSamplingSettings);
        acceptBufferDirectives(directives, colorSamplingSettings);
    }

    private static void acceptHardwareFilteringSettings(DirectiveHolder directives, ImmutableList<DepthSamplingSettings> samplers) {
        directives.acceptConstBooleanDirective("shadowHardwareFiltering", hardwareFiltering -> {
            for (DepthSamplingSettings samplerSettings : samplers) {
                samplerSettings.setHardwareFiltering(hardwareFiltering);
            }
        });

        for (int i = 0; i < samplers.size(); i++) {
            String name = "shadowHardwareFiltering" + i;

            directives.acceptConstBooleanDirective(name, samplers.get(i)::setHardwareFiltering);
        }
    }

    private static void acceptDepthMipmapSettings(DirectiveHolder directives, ImmutableList<DepthSamplingSettings> samplers) {
        directives.acceptConstBooleanDirective("generateShadowMipmap", mipmap -> {
            for (SamplingSettings samplerSettings : samplers) {
                samplerSettings.setMipmap(mipmap);
            }
        });

        // legacy alias for shadowtex0Mipmap
        if (samplers.size() >= 1) {
            directives.acceptConstBooleanDirective("shadowtexMipmap", samplers.get(0)::setMipmap);
        }

        for (int i = 0; i < samplers.size(); i++) {
            String name = "shadowtex" + i + "Mipmap";

            directives.acceptConstBooleanDirective(name, samplers.get(i)::setMipmap);
        }
    }

    private static void acceptColorMipmapSettings(DirectiveHolder directives, ImmutableList<SamplingSettings> samplers) {
        directives.acceptConstBooleanDirective("generateShadowColorMipmap", mipmap -> {
            for (SamplingSettings samplerSettings : samplers) {
                samplerSettings.setMipmap(mipmap);
            }
        });

        for (int i = 0; i < samplers.size(); i++) {
            String name = "shadowcolor" + i + "Mipmap";
            directives.acceptConstBooleanDirective(name, samplers.get(i)::setMipmap);

            name = "shadowColor" + i + "Mipmap";
            directives.acceptConstBooleanDirective(name, samplers.get(i)::setMipmap);
        }
    }

    private static void acceptDepthFilteringSettings(DirectiveHolder directives, ImmutableList<DepthSamplingSettings> samplers) {
        if (samplers.size() >= 1) {
            directives.acceptConstBooleanDirective("shadowtexNearest", samplers.get(0)::setNearest);
        }

        for (int i = 0; i < samplers.size(); i++) {
            String name = "shadowtex" + i + "Nearest";

            directives.acceptConstBooleanDirective(name, samplers.get(i)::setNearest);

            name = "shadow" + i + "MinMagNearest";

            directives.acceptConstBooleanDirective(name, samplers.get(i)::setNearest);
        }
    }

    private static void acceptColorFilteringSettings(DirectiveHolder directives, ImmutableList<SamplingSettings> samplers) {
        for (int i = 0; i < samplers.size(); i++) {
            String name = "shadowcolor" + i + "Nearest";

            directives.acceptConstBooleanDirective(name, samplers.get(i)::setNearest);

            name = "shadowColor" + i + "Nearest";

            directives.acceptConstBooleanDirective(name, samplers.get(i)::setNearest);

            name = "shadowColor" + i + "MinMagNearest";

            directives.acceptConstBooleanDirective(name, samplers.get(i)::setNearest);
        }
    }

    private void acceptBufferDirectives(DirectiveHolder directives, ImmutableList<SamplingSettings> settings) {
        for (int i = 0; i < settings.size(); i++) {
            String bufferName = "shadowcolor" + i;
            int finalI = i;
            directives.acceptConstStringDirective(bufferName + "Format", format -> {
                Optional<InternalTextureFormat> internalFormat = InternalTextureFormat.fromString(format);

                if (internalFormat.isPresent()) {
                    settings.get(finalI).setFormat(internalFormat.get());
                } else {
                    Lucerna.logger.warn("Unrecognized internal texture format {} specified for {}Format, ignoring.", format, bufferName);
                }
            });

            directives.acceptConstBooleanDirective(bufferName + "Clear",
                shouldClear -> settings.get(finalI).setClear(shouldClear));

            // still applies when clear is false since this is the initial color
            directives.acceptConstVec4Directive(bufferName + "ClearColor",
                clearColor -> settings.get(finalI).setClearColor(clearColor));
        }
    }

    @Override
    public String toString() {
        return "PackShadowDirectives{" +
                "resolution=" + resolution +
                ", fov=" + fov +
                ", distance=" + distance +
                ", distanceRenderMul=" + distanceRenderMul +
                ", entityDistanceRenderMul=" + entityShadowDistanceMul +
                ", intervalSize=" + intervalSize +
                ", depthSamplingSettings=" + depthSamplingSettings +
                ", colorSamplingSettings=" + colorSamplingSettings +
                '}';
    }

    public static class SamplingSettings {
        private boolean mipmap;

        private boolean nearest;

        private boolean clear;

        private Vector4f clearColor;

        private InternalTextureFormat format;

        public SamplingSettings() {
            mipmap = false;
            nearest = false;
            clear = true;
            clearColor = new Vector4f(1.0F);
            format = InternalTextureFormat.RGBA;
        }

        protected void setMipmap(boolean mipmap) {
            this.mipmap = mipmap;
        }

        protected void setNearest(boolean nearest) {
            this.nearest = nearest;
        }

        protected void setClear(boolean clear) {
            this.clear = clear;
        }

        protected void setClearColor(Vector4f clearColor) {
            this.clearColor = clearColor;
        }

        protected void setFormat(InternalTextureFormat format) {
            this.format = format;
        }

        public boolean getMipmap() {
            return this.mipmap;
        }

        public boolean getNearest() {
            return this.nearest;
        }

        public boolean getClear() {
            return clear;
        }

        public Vector4f getClearColor() {
            return clearColor;
        }

        public InternalTextureFormat getFormat() {
            return this.format;
        }

        @Override
        public String toString() {
            return "SamplingSettings{" +
                    "mipmap=" + mipmap +
                    ", nearest=" + nearest +
                    ", clear=" + clear +
                    ", clearColor=" + clearColor +
                    ", format=" + format.name() +
                    '}';
        }
    }

    public static class DepthSamplingSettings extends SamplingSettings {
        private boolean hardwareFiltering;

        public DepthSamplingSettings() {
            hardwareFiltering = false;
        }

        private void setHardwareFiltering(boolean hardwareFiltering) {
            this.hardwareFiltering = hardwareFiltering;
        }

        public boolean getHardwareFiltering() {
            return hardwareFiltering;
        }

        @Override
        public String toString() {
            return "DepthSamplingSettings{" +
                    "mipmap=" + getMipmap() +
                    ", nearest=" + getNearest() +
                    ", hardwareFiltering=" + hardwareFiltering +
                    '}';
        }
    }
}
