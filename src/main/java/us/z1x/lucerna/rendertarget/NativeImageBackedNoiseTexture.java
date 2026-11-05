package us.z1x.lucerna.rendertarget;

import net.minecraft.client.render.texture.DynamicTexture;
import org.lwjgl.opengl.GL11C;
import us.z1x.lucerna.gl.LucernaRenderSystem;

import java.util.Random;

public class NativeImageBackedNoiseTexture extends DynamicTexture {
    public NativeImageBackedNoiseTexture(int size) {
        super(size, size);

        Random random = new Random(0);
        int[] pixels = getPixels();

        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = random.nextInt() | (255 << 24);
        }

        upload();

        LucernaRenderSystem.texParameteri(getGlId(), GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        LucernaRenderSystem.texParameteri(getGlId(), GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
    }
}
