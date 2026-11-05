package us.z1x.lucerna.mixin.argentum;

import dev.rdh.argentum.impl.render.terrain.ArgentumRenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.shadows.ShadowRenderingState;

@Mixin(value = ArgentumRenderSectionManager.class, remap = false)
public abstract class MixinArgentumRenderSectionManager extends RenderSectionManager {
    private MixinArgentumRenderSectionManager() {
        super(null, null, null, 0, null, 0, 0, 0);
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/RenderSectionManager;<init>(Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;Ljava/util/function/Supplier;Ljava/util/function/BiFunction;ILorg/embeddedt/embeddium/impl/gl/device/CommandList;IIIZ)V"), index = 8)
    private static boolean lucerna$enableShadowPass(boolean hasShadowPass) {
        return hasShadowPass || Lucerna.isShaderPackActive();
    }

    @Override
    public boolean isInShadowPass() {
        return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
    }
}
