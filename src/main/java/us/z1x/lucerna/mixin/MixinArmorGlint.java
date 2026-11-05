package us.z1x.lucerna.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import us.z1x.lucerna.gbufferoverrides.matching.SpecialCondition;
import us.z1x.lucerna.layer.GbufferPrograms;

@Mixin(AbstractArmorLayer.class)
public class MixinArmorGlint {
    @WrapMethod(method = "renderEnchantmentGlint")
    private void lucerna$renderGlint(LivingEntity entity, Model model, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw,
                                     float headPitch, float scale, Operation<Void> original) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
        original.call(entity, model, limbAngle, limbDistance, tickDelta, age, headYaw, headPitch, scale);
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
