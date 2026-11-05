package us.z1x.lucerna.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.render.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

@Mixin(ItemInHandRenderer.class)
public class MixinItemInHandRenderer {
    @WrapWithCondition(method = "renderScreenEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;renderInWaterEffect(F)V"))
    private boolean lucerna$renderUnderwaterOverlay(ItemInHandRenderer renderer, float tickDelta) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        return pipeline == null || pipeline.shouldRenderUnderwaterOverlay();
    }
}
