package us.z1x.lucerna.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import us.z1x.lucerna.blockrendering.BlockRenderingSettings;
import us.z1x.lucerna.gbufferoverrides.matching.InputAvailability;
import us.z1x.lucerna.gbufferoverrides.matching.SpecialCondition;
import us.z1x.lucerna.gbufferoverrides.state.RenderTargetStateListener;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.shaderpack.CloudSetting;
import us.z1x.lucerna.uniforms.FrameUpdateNotifier;

import java.util.List;
import java.util.OptionalInt;

public class FixedFunctionWorldRenderingPipeline implements WorldRenderingPipeline {
    public FixedFunctionWorldRenderingPipeline() {
        BlockRenderingSettings.INSTANCE.setDisableDirectionalShading(shouldDisableDirectionalShading());
        BlockRenderingSettings.INSTANCE.setUseSeparateAo(false);
        BlockRenderingSettings.INSTANCE.setAmbientOcclusionLevel(1.0f);
        BlockRenderingSettings.INSTANCE.setUseExtendedVertexFormat(false);
        BlockRenderingSettings.INSTANCE.setBlockTypeIds(null);
    }

    @Override
    public void beginLevelRendering() {
        Minecraft.getInstance().getRenderTarget().bindWrite(true);
        LucernaRenderSystem.useProgram(0);
    }

    @Override
    public void renderShadows(WorldRenderer worldRenderer, Entity camera, Culler playerCuller) {
    }

    @Override
    public void addDebugText(List<String> messages) {
    }

    @Override
    public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
        return OptionalInt.empty();
    }

    @Override
    public WorldRenderingPhase getPhase() {
        return WorldRenderingPhase.NONE;
    }

    @Override
    public void beginTerrainRendering() {
    }

    @Override
    public void endTerrainRendering() {
    }

    @Override
    public void setOverridePhase(WorldRenderingPhase phase) {
    }

    @Override
    public void setPhase(WorldRenderingPhase phase) {
    }

    @Override
    public void setInputs(InputAvailability availability) {
    }

    @Override
    public void setSpecialCondition(SpecialCondition special) {
    }

    @Override
    public void syncProgram() {
    }

    @Override
    public boolean usesExtendedVertexAttributes() {
        return false;
    }

    @Override
    public RenderTargetStateListener getRenderTargetStateListener() {
        return RenderTargetStateListener.NOP;
    }

    @Override
    public int getCurrentNormalTexture() {
        return 0;
    }

    @Override
    public int getCurrentSpecularTexture() {
        return 0;
    }

    @Override
    public void onBindTexture(int id) {
    }

    @Override
    public void beginHand() {
    }

    @Override
    public void beginTranslucents() {
    }

    @Override
    public void finalizeLevelRendering() {
    }

    @Override
    public void destroy() {
    }

    @Override
    public CeleritasTerrainPipeline getCeleritasTerrainPipeline() {
        return null;
    }

    @Override
    public FrameUpdateNotifier getFrameUpdateNotifier() {
        return new FrameUpdateNotifier();
    }

    @Override
    public boolean shouldDisableVanillaEntityShadows() {
        return false;
    }

    @Override
    public boolean shouldDisableDirectionalShading() {
        return false;
    }

    @Override
    public CloudSetting getCloudSetting() {
        return CloudSetting.DEFAULT;
    }

    @Override
    public boolean shouldRenderUnderwaterOverlay() {
        return true;
    }

    @Override
    public boolean shouldRenderVignette() {
        return true;
    }

    @Override
    public boolean shouldRenderSun() {
        return true;
    }

    @Override
    public boolean shouldRenderMoon() {
        return true;
    }

    @Override
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return false;
    }

    @Override
    public boolean shouldRenderParticlesBeforeDeferred() {
        return false;
    }

    @Override
    public boolean allowConcurrentCompute() {
        return false;
    }

    @Override
    public float getSunPathRotation() {
        return 0;
    }
}
