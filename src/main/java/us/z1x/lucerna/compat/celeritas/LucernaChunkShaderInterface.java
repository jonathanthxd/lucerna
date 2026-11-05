package us.z1x.lucerna.compat.celeritas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat3v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix3f;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL13;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gl.blending.BlendModeOverride;
import us.z1x.lucerna.gl.blending.BufferBlendOverride;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;
import us.z1x.lucerna.gl.program.ProgramImages;
import us.z1x.lucerna.gl.program.ProgramSamplers;
import us.z1x.lucerna.gl.program.ProgramUniforms;
import us.z1x.lucerna.mixin.GameRendererAccessor;
import us.z1x.lucerna.pipeline.CeleritasTerrainPipeline;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;
import us.z1x.lucerna.samplers.LucernaSamplers;
import us.z1x.lucerna.shadows.ShadowRenderingState;

import java.util.List;

public class LucernaChunkShaderInterface implements ChunkShaderInterface {
    @Nullable
    private final GlUniformMatrix4f uniformModelViewMatrix;
    @Nullable
    private final GlUniformMatrix4f uniformModelViewMatrixInverse;
    @Nullable
    private final GlUniformMatrix4f uniformProjectionMatrix;
    @Nullable
    private final GlUniformMatrix4f uniformProjectionMatrixInverse;
    @Nullable
    private final GlUniformFloat3v uniformRegionOffset;
    @Nullable
    private final GlUniformMatrix3f uniformNormalMatrix;
    @Nullable
    private final GlUniformFloat uniformAlphaTest;

    private final LucernaTerrainPass pass;
    private final BlendModeOverride blendModeOverride;
    private final float alpha;
    private final ProgramUniforms lucernaProgramUniforms;
    private final ProgramSamplers lucernaProgramSamplers;
    private final ProgramImages lucernaProgramImages;
    private final List<BufferBlendOverride> bufferBlendOverrides;
    private final boolean hasOverrides;
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();

    public LucernaChunkShaderInterface(int handle, ShaderBindingContext contextExt, CeleritasTerrainPipeline pipeline, LucernaTerrainPass pass,
                                       BlendModeOverride blendModeOverride, List<BufferBlendOverride> bufferOverrides, float alpha) {
        this.uniformModelViewMatrix = contextExt.bindUniformIfPresent("iris_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformModelViewMatrixInverse = contextExt.bindUniformIfPresent("iris_ModelViewMatrixInverse", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = contextExt.bindUniformIfPresent("iris_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrixInverse = contextExt.bindUniformIfPresent("iris_ProjectionMatrixInverse", GlUniformMatrix4f::new);
        this.uniformRegionOffset = contextExt.bindUniformIfPresent("u_RegionOffset", GlUniformFloat3v::new);
        this.uniformNormalMatrix = contextExt.bindUniformIfPresent("iris_NormalMatrix", GlUniformMatrix3f::new);
        this.uniformAlphaTest = contextExt.bindUniformIfPresent("iris_currentAlphaTest", GlUniformFloat::new);

        this.pass = pass;
        this.alpha = alpha;
        this.blendModeOverride = blendModeOverride;
        this.bufferBlendOverrides = bufferOverrides;
        this.hasOverrides = bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty();

        this.lucernaProgramUniforms = pipeline.initUniforms(handle);
        this.lucernaProgramSamplers = pass.isShadow() ? pipeline.initShadowSamplers(handle) : pipeline.initTerrainSamplers(handle);
        this.lucernaProgramImages = pass.isShadow() ? pipeline.initShadowImages(handle) : pipeline.initTerrainImages(handle);
    }

    @Override
    public void setupState(TerrainRenderPass terrainPass) {
        CeleritasTerrainPipeline pipeline = getCeleritasTerrainPipeline();

        if (pipeline != null) {
            GlFramebuffer framebuffer = pipeline.getPassInfo(pass).framebuffer();

            if (framebuffer != null) {
                framebuffer.bind();
            }
        }

        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            // no back face culling during the shadow pass
            GlStateManager.disableCull();
        }

        // see LucernaSamplers#addLevelSamplers
        Minecraft minecraft = Minecraft.getInstance();
        GlStateManager.activeTexture(GL13.GL_TEXTURE0 + LucernaSamplers.LIGHTMAP_TEXTURE_UNIT);
        GlStateManager.bindTexture(((GameRendererAccessor) minecraft.gameRenderer).lucerna$getLightmapTexture().getGlId());
        GlStateManager.activeTexture(GL13.GL_TEXTURE0 + LucernaSamplers.ALBEDO_TEXTURE_UNIT);
        GlStateManager.bindTexture(minecraft.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).getGlId());

        if (uniformAlphaTest != null) {
            uniformAlphaTest.setFloat(alpha);
        }

        if (blendModeOverride != null) {
            blendModeOverride.apply();
        }

        if (hasOverrides) {
            bufferBlendOverrides.forEach(BufferBlendOverride::apply);
        }

        lucernaProgramUniforms.update();
        lucernaProgramSamplers.update();
        lucernaProgramImages.update();
    }

    @Override
    public void restoreState() {
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();

        if (blendModeOverride != null || hasOverrides) {
            BlendModeOverride.restore();
        }

        // the gbuffer passes rebind their framebuffer before the next vanilla draw
        Minecraft.getInstance().getRenderTarget().bindWrite(false);
    }

    @Override
    public GlPrimitiveType getPrimitiveType() {
        return GlPrimitiveType.TRIANGLES;
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        if (this.uniformProjectionMatrix != null) {
            this.uniformProjectionMatrix.set(matrix);
        }

        if (this.uniformProjectionMatrixInverse != null) {
            this.uniformProjectionMatrixInverse.set(matrix.invert(inverse));
        }
    }

    @Override
    public void setModelViewMatrix(Matrix4fc modelView) {
        if (this.uniformModelViewMatrix != null) {
            this.uniformModelViewMatrix.set(modelView);
        }

        if (this.uniformModelViewMatrixInverse != null || this.uniformNormalMatrix != null) {
            modelView.invert(inverse);

            if (this.uniformModelViewMatrixInverse != null) {
                this.uniformModelViewMatrixInverse.set(inverse);
            }

            if (this.uniformNormalMatrix != null) {
                this.uniformNormalMatrix.set(normal.set(inverse).transpose());
            }
        }
    }

    @Override
    public void setRegionOffset(float x, float y, float z) {
        if (this.uniformRegionOffset != null) {
            this.uniformRegionOffset.set(x, y, z);
        }
    }

    @Override
    public void setTextureSlot(ChunkShaderTextureSlot slot, int val) {
        // unused, the pack samplers bind the albedo and lightmap units themselves
    }

    private static CeleritasTerrainPipeline getCeleritasTerrainPipeline() {
        WorldRenderingPipeline worldRenderingPipeline = Lucerna.getPipelineManager().getPipelineNullable();

        return worldRenderingPipeline != null ? worldRenderingPipeline.getCeleritasTerrainPipeline() : null;
    }
}
