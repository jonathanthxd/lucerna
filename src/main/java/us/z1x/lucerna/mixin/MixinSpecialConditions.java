package us.z1x.lucerna.mixin;

import net.minecraft.client.render.block.entity.BeaconRenderer;
import net.minecraft.client.render.entity.layer.EnderDragonEyesLayer;
import net.minecraft.client.render.entity.layer.EndermanEyesLayer;
import net.minecraft.client.render.entity.layer.SpiderEyesLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.z1x.lucerna.gbufferoverrides.matching.SpecialCondition;
import us.z1x.lucerna.layer.GbufferPrograms;

@Mixin({BeaconRenderer.class, SpiderEyesLayer.class, EndermanEyesLayer.class, EnderDragonEyesLayer.class})
public class MixinSpecialConditions {
    @Inject(method = "render", at = @At("HEAD"))
    private void lucerna$beginSpecialCondition(CallbackInfo ci) {
        GbufferPrograms.setupSpecialRenderCondition((Object) this instanceof BeaconRenderer ? SpecialCondition.BEACON_BEAM : SpecialCondition.ENTITY_EYES);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lucerna$endSpecialCondition(CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
