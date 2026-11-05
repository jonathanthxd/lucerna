package us.z1x.lucerna.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import us.z1x.lucerna.gbufferoverrides.matching.SpecialCondition;
import us.z1x.lucerna.layer.GbufferPrograms;

@Mixin(ItemRenderer.class)
public class MixinItemGlint {
    // wrap the whole method, a return injector misses the early returns other glint mods add and leaves the glint condition set
    @WrapMethod(method = "renderEnchantmentGlint")
    private void lucerna$renderGlint(BakedModel model, Operation<Void> original) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        original.call(model);
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
