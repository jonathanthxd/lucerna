package us.z1x.lucerna.mixin.gui;

import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.gui.CeleritasVideoOptionsController;
import org.embeddedt.embeddium.impl.gui.frame.tab.Tab;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.structure.OptionPage;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.gui.option.LucernaOptionPage;
import us.z1x.lucerna.gui.screen.ShaderPackScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = CeleritasVideoOptionsController.class, remap = false)
public class MixinCeleritasVideoOptionsController {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<OptionPage> lucerna$addOptionPage(List<OptionPage> pages) {
        List<OptionPage> withShaders = new ArrayList<>(pages);
        withShaders.add(LucernaOptionPage.create());

        return withShaders;
    }

    @Inject(method = "createExtraTabs", at = @At("HEAD"))
    private void lucerna$addShaderPackTab(Map<String, List<Tab<?>>> tabs, CallbackInfo ci) {
        tabs.computeIfAbsent(Lucerna.MODID, id -> new ArrayList<>()).add(Tab.createBuilder()
                .setId(OptionIdentifier.create(Lucerna.MODID, "shader_packs"))
                .setTitle(TextComponent.translatable("options.lucerna.shaderPackSelection"))
                .setOnSelectFunction(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.openScreen(new ShaderPackScreen(minecraft.screen));

                    // open the screen instead of selecting a page
                    return false;
                })
                .build());
    }
}
