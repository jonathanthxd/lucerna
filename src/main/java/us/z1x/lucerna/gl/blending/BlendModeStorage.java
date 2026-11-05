package us.z1x.lucerna.gl.blending;

import net.minecraft.client.render.platform.GlStateManager;
import us.z1x.lucerna.gl.LucernaRenderSystem;

public class BlendModeStorage {
    private static boolean originalBlendEnable;
    private static BlendMode originalBlend;
    private static boolean blendLocked;
    // draw buffers with their own blend state, which the global blend calls do not reset
    private static int overriddenBuffers;

    public static boolean isBlendLocked() {
        return blendLocked;
    }

    public static void overrideBlend(BlendMode override) {
        if (!blendLocked) {
            GlStateManager.Blend blendState = GlStateManager.BLEND;

            originalBlendEnable = blendState.state.enabled;
            originalBlend = new BlendMode(blendState.sfactorRGB, blendState.dfactorRGB, blendState.sfactorAlpha, blendState.dfactorAlpha);
        }

        blendLocked = false;

        if (override == null) {
            GlStateManager.disableBlend();
        } else {
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
        }

        blendLocked = true;
    }

    public static void overrideBufferBlend(int index, BlendMode override) {
        if (!blendLocked) {
            GlStateManager.Blend blendState = GlStateManager.BLEND;

            originalBlendEnable = blendState.state.enabled;
            originalBlend = new BlendMode(blendState.sfactorRGB, blendState.dfactorRGB, blendState.sfactorAlpha, blendState.dfactorAlpha);
        }

        overriddenBuffers |= 1 << index;

        if (override == null) {
            LucernaRenderSystem.disableBufferBlend(index);
        } else {
            LucernaRenderSystem.enableBufferBlend(index);
            LucernaRenderSystem.blendFuncSeparatei(index, override.getSrcRgb(), override.getDstRgb(), override.getSrcAlpha(), override.getDstAlpha());
        }

        blendLocked = true;
    }

    public static void deferBlendModeToggle(boolean enabled) {
        originalBlendEnable = enabled;
    }

    public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        originalBlend = new BlendMode(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void restoreBlend() {
        if (!blendLocked) {
            return;
        }

        blendLocked = false;

        if (originalBlendEnable) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }

        GlStateManager.blendFuncSeparate(originalBlend.getSrcRgb(), originalBlend.getDstRgb(),
                originalBlend.getSrcAlpha(), originalBlend.getDstAlpha());

        for (int index = 0; overriddenBuffers != 0; index++, overriddenBuffers >>>= 1) {
            if ((overriddenBuffers & 1) == 0) {
                continue;
            }

            if (originalBlendEnable) {
                LucernaRenderSystem.enableBufferBlend(index);
            } else {
                LucernaRenderSystem.disableBufferBlend(index);
            }

            LucernaRenderSystem.blendFuncSeparatei(index, originalBlend.getSrcRgb(), originalBlend.getDstRgb(), originalBlend.getSrcAlpha(), originalBlend.getDstAlpha());
        }
    }
}
