package us.z1x.lucerna.pipeline;

import net.minecraft.client.render.Culler;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import us.z1x.lucerna.gbufferoverrides.matching.InputAvailability;
import us.z1x.lucerna.gbufferoverrides.matching.SpecialCondition;
import us.z1x.lucerna.gbufferoverrides.state.RenderTargetStateListener;
import us.z1x.lucerna.shaderpack.CloudSetting;
import us.z1x.lucerna.uniforms.FrameUpdateNotifier;

import java.util.List;
import java.util.OptionalInt;

public interface WorldRenderingPipeline {
    void beginLevelRendering();
    void renderShadows(WorldRenderer worldRenderer, Entity camera, Culler playerCuller);
    void addDebugText(List<String> messages);
    OptionalInt getForcedShadowRenderDistanceChunksForDisplay();

    WorldRenderingPhase getPhase();

    void beginTerrainRendering();
    void endTerrainRendering();
    void setOverridePhase(WorldRenderingPhase phase);
    void setPhase(WorldRenderingPhase phase);
    void setInputs(InputAvailability availability);
    void setSpecialCondition(SpecialCondition special);
    void syncProgram();

    boolean usesExtendedVertexAttributes();
    RenderTargetStateListener getRenderTargetStateListener();

    int getCurrentNormalTexture();
    int getCurrentSpecularTexture();

    void onBindTexture(int id);

    void beginHand();

    void beginTranslucents();
    void finalizeLevelRendering();
    void destroy();

    CeleritasTerrainPipeline getCeleritasTerrainPipeline();
    FrameUpdateNotifier getFrameUpdateNotifier();

    boolean shouldDisableVanillaEntityShadows();
    boolean shouldDisableDirectionalShading();
    CloudSetting getCloudSetting();
    boolean shouldRenderUnderwaterOverlay();
    boolean shouldRenderVignette();
    boolean shouldRenderSun();
    boolean shouldRenderMoon();
    boolean shouldWriteRainAndSnowToDepthBuffer();
    boolean shouldRenderParticlesBeforeDeferred();
    boolean allowConcurrentCompute();

    float getSunPathRotation();
}
