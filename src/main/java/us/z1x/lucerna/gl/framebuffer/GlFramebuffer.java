package us.z1x.lucerna.gl.framebuffer;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;
import us.z1x.lucerna.gl.GlResource;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.texture.DepthBufferFormat;
import us.z1x.lucerna.texture.TextureInfoCache;

public class GlFramebuffer extends GlResource {
    private final Int2IntMap attachments;
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private boolean hasDepthAttachment;

    public GlFramebuffer() {
        super(LucernaRenderSystem.createFramebuffer());

        this.attachments = new Int2IntArrayMap();
        this.maxDrawBuffers = GL11.glGetInteger(GL30C.GL_MAX_DRAW_BUFFERS);
        this.maxColorAttachments = GL11.glGetInteger(GL30C.GL_MAX_COLOR_ATTACHMENTS);
        this.hasDepthAttachment = false;
    }

    public void addDepthAttachment(int texture) {
        int internalFormat = TextureInfoCache.INSTANCE.getInfo(texture).getInternalFormat();
        DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);

        int fb = getGlId();

        if (depthBufferFormat.isCombinedStencil()) {
            LucernaRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL30C.GL_TEXTURE_2D, texture, 0);
        } else {
            LucernaRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, texture, 0);
        }

        this.hasDepthAttachment = true;
    }

    public void addColorAttachment(int index, int texture) {
        int fb = getGlId();

        LucernaRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + index, GL30C.GL_TEXTURE_2D, texture, 0);
        attachments.put(index, texture);
    }

    public void noDrawBuffers() {
        LucernaRenderSystem.drawBuffers(getGlId(), new int[] { GL30C.GL_NONE });
    }

    public void drawBuffers(int[] buffers) {
        int[] glBuffers = new int[buffers.length];
        int index = 0;

        if (buffers.length > maxDrawBuffers) {
            throw new IllegalArgumentException("Cannot write to more than " + maxDrawBuffers + " draw buffers on this GPU");
        }

        for (int buffer : buffers) {
            if (buffer >= maxColorAttachments) {
                throw new IllegalArgumentException("Only " + maxColorAttachments + " color attachments are supported on this GPU, but an attempt was made to write to a color attachment with index " + buffer);
            }

            glBuffers[index++] = GL30C.GL_COLOR_ATTACHMENT0 + buffer;
        }

        LucernaRenderSystem.drawBuffers(getGlId(), glBuffers);
    }

    public void readBuffer(int buffer) {
        LucernaRenderSystem.readBuffer(getGlId(), GL30C.GL_COLOR_ATTACHMENT0 + buffer);
    }

    public boolean hasDepthAttachment() {
        return hasDepthAttachment;
    }

    public void bind() {
        LucernaRenderSystem.bindFramebuffer(GL30C.GL_FRAMEBUFFER, getGlId());
    }

    public void bindAsReadBuffer() {
        LucernaRenderSystem.bindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, getGlId());
    }

    protected void destroyInternal() {
        GL30.glDeleteFramebuffers(getGlId());
    }

    public boolean isComplete() {
        bind();
        int status = GL30.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);

        return status == GL30C.GL_FRAMEBUFFER_COMPLETE;
    }

    public int getId() {
        return getGlId();
    }
}
