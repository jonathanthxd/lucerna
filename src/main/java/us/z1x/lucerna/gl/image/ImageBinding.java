package us.z1x.lucerna.gl.image;

import org.lwjgl.opengl.GL42C;
import us.z1x.lucerna.gl.LucernaRenderSystem;

import java.util.function.IntSupplier;

public class ImageBinding {
    private final int imageUnit;
    private final int internalFormat;
    private final IntSupplier textureID;

    public ImageBinding(int imageUnit, int internalFormat, IntSupplier textureID) {
        this.textureID = textureID;
        this.imageUnit = imageUnit;
        this.internalFormat = internalFormat;
    }

    public void update() {
        // safe without a check, ImageLimits reports zero units when neither the ext nor gl 4.2 is present
        LucernaRenderSystem.bindImageTexture(imageUnit, textureID.getAsInt(), 0, true, 0, GL42C.GL_READ_WRITE, internalFormat);
    }
}
