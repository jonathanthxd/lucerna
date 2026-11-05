package us.z1x.lucerna.samplers;

import com.google.common.collect.ImmutableSet;
import us.z1x.lucerna.gl.image.CustomImage;
import us.z1x.lucerna.gl.image.ImageHolder;
import us.z1x.lucerna.gl.texture.InternalTextureFormat;
import us.z1x.lucerna.rendertarget.RenderTarget;
import us.z1x.lucerna.rendertarget.RenderTargets;
import us.z1x.lucerna.shadows.ShadowRenderTargets;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class LucernaImages {
    public static void addRenderTargetImages(ImageHolder images, Supplier<ImmutableSet<Integer>> flipped,
                                             RenderTargets renderTargets) {
        for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
            final int index = i;

            // image bindings are affected by buffer flips
            IntSupplier textureID = () -> {
                ImmutableSet<Integer> flippedBuffers = flipped.get();
                RenderTarget target = renderTargets.get(index);

                if (flippedBuffers.contains(index)) {
                    return target.getAltTexture();
                } else {
                    return target.getMainTexture();
                }
            };

            final InternalTextureFormat internalFormat = renderTargets.get(i).getInternalFormat();
            final String name = "colorimg" + i;

            images.addTextureImage(textureID, internalFormat, name);
        }

        for (CustomImage image : renderTargets.getCustomImages()) {
            images.addTextureImage(image::getTexture, image.getInfo().internalFormat(), image.getInfo().name());
        }
    }

    public static boolean hasShadowImages(ImageHolder images) {
        return images.hasImage("shadowcolorimg0") || images.hasImage("shadowcolorimg1");
    }

    public static void addShadowColorImages(ImageHolder images, ShadowRenderTargets shadowRenderTargets) {
        for (int i = 0; i < shadowRenderTargets.getNumColorTextures(); i++) {
            final int index = i;

            IntSupplier textureID = () -> shadowRenderTargets.getColorTextureId(index);
            InternalTextureFormat format = shadowRenderTargets.getColorTextureFormat(index);

            images.addTextureImage(textureID, format, "shadowcolorimg" + i);
        }
    }
}
