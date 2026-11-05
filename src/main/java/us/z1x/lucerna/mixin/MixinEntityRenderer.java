package us.z1x.lucerna.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.pipeline.WorldRenderingPipeline;

// priority 900 so this runs before argentum batches the blob shadows
@Mixin(value = EntityRenderer.class, priority = 900)
public class MixinEntityRenderer {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private void lucerna$disableVanillaShadows(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Lucerna.getPipelineManager().getPipelineNullable();

        if (pipeline != null && pipeline.shouldDisableVanillaEntityShadows()) {
            ci.cancel();
        }
    }
}
