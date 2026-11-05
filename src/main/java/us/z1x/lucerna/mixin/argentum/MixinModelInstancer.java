package us.z1x.lucerna.mixin.argentum;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.z1x.lucerna.compat.argentum.InstancedEntityPrograms;
import us.z1x.lucerna.pipeline.ShadowRenderer;

@Mixin(value = ModelInstancer.class, remap = false)
public abstract class MixinModelInstancer {
    @Shadow
    private boolean textureArraysSupported;

    @Unique
    private boolean lucerna$restoreTextureArrays;

    @Shadow
    protected abstract GlProgram<?> createProgram(ChunkShaderComponent.Factory<?> fogFactory);

    @Inject(method = "getProgram", at = @At("HEAD"), cancellable = true)
    private void lucerna$useShaderPackProgram(CallbackInfoReturnable<GlProgram<?>> cir) {
        if (!InstancedEntityPrograms.isActive()) {
            if (lucerna$restoreTextureArrays) {
                lucerna$restoreTextureArrays = false;
                textureArraysSupported = true;
            }

            return;
        }

        // shader packs sample a plain 2d texture, not argentum's texture arrays
        if (textureArraysSupported) {
            lucerna$restoreTextureArrays = true;
            textureArraysSupported = false;
        }

        GlProgram<?> program = InstancedEntityPrograms.get(ShadowRenderer.ACTIVE, this::lucerna$linkProgram);

        if (program != null) {
            cir.setReturnValue(program);
        }
    }

    @Unique
    private GlProgram<?> lucerna$linkProgram() {
        // the pack program declares every fog uniform, so the smooth fog component binds them all
        GlProgram<?> program = createProgram(ChunkFogMode.SMOOTH);
        program.bind();
        ((InstanceShaderInvoker) program.getInterface()).lucerna$initialize();
        program.unbind();

        return program;
    }

    @WrapOperation(method = "createProgram", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/shader/ShaderLoader;loadShader(Lorg/embeddedt/embeddium/impl/gl/shader/ShaderType;Ljava/lang/String;Lorg/embeddedt/embeddium/impl/gl/shader/ShaderConstants;)Lorg/embeddedt/embeddium/impl/gl/shader/GlShader;"))
    private GlShader lucerna$loadShaderPackSource(ShaderType type, String name, ShaderConstants constants, Operation<GlShader> original) {
        GlShader shader = InstancedEntityPrograms.createShader(type);
        return shader != null ? shader : original.call(type, name, constants);
    }

    @Inject(method = "flush", at = @At(value = "INVOKE", target = "Ldev/rdh/argentum/impl/render/entity/instancing/InstanceShader;setUniforms()V", shift = At.Shift.AFTER))
    private void lucerna$prepareShaderPackState(CommandList commandList, CallbackInfoReturnable<ModelInstancer.BatchStats> cir) {
        InstancedEntityPrograms.prepare(ShadowRenderer.ACTIVE);
    }

    @Inject(method = "flush", at = @At("RETURN"))
    private void lucerna$restoreShaderPackState(CommandList commandList, CallbackInfoReturnable<ModelInstancer.BatchStats> cir) {
        InstancedEntityPrograms.restore();
    }
}
