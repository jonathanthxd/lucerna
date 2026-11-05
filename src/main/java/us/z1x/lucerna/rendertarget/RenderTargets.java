package us.z1x.lucerna.rendertarget;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.render.platform.GlStateManager;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import us.z1x.lucerna.gl.LucernaRenderSystem;
import us.z1x.lucerna.gl.framebuffer.GlFramebuffer;
import us.z1x.lucerna.gl.image.CustomImage;
import us.z1x.lucerna.gl.texture.DepthBufferFormat;
import us.z1x.lucerna.gl.texture.DepthCopyStrategy;
import us.z1x.lucerna.shaderpack.PackDirectives;
import us.z1x.lucerna.shaderpack.PackRenderTargetDirectives;
import us.z1x.lucerna.shaderpack.ShaderStorageInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenderTargets {
    private final RenderTarget[] targets;
    private int currentDepthTexture;
    private DepthBufferFormat currentDepthFormat;

    private final DepthTexture noTranslucents;
    private final DepthTexture noHand;
    private final GlFramebuffer depthSourceFb;
    private final GlFramebuffer noTranslucentsDestFb;
    private final GlFramebuffer noHandDestFb;
    private DepthCopyStrategy copyStrategy;

    private final List<GlFramebuffer> ownedFramebuffers;
    private final Map<String, GlFramebuffer> gbufferFramebuffers = new HashMap<>();

    private int cachedWidth;
    private int cachedHeight;
    private boolean fullClearRequired;
    private boolean translucentDepthDirty;
    private boolean handDepthDirty;

    private int cachedDepthBufferVersion;

    private final List<CustomImage> customImages = new ArrayList<>();
    private final List<ShaderStorageInfo> bufferInfos = new ArrayList<>();
    private final IntList bufferIndices = new IntArrayList();
    private final IntList buffers = new IntArrayList();

    public RenderTargets(int width, int height, int depthTexture, int depthBufferVersion, DepthBufferFormat depthFormat, Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargets, PackDirectives packDirectives) {
        targets = new RenderTarget[renderTargets.size()];

        renderTargets.forEach((index, settings) -> {
            Vector2i dimensions = packDirectives.getTextureScaleOverride(index, width, height);
            targets[index] = RenderTarget.builder().setDimensions(dimensions.x, dimensions.y)
                    .setInternalFormat(settings.getInternalFormat())
                    .setPixelFormat(settings.getInternalFormat().getPixelFormat()).build();
        });

        this.currentDepthTexture = depthTexture;
        this.currentDepthFormat = depthFormat;
        this.copyStrategy = DepthCopyStrategy.fastest(currentDepthFormat.isCombinedStencil());

        this.cachedWidth = width;
        this.cachedHeight = height;
        this.cachedDepthBufferVersion = depthBufferVersion;

        packDirectives.getImages().forEach(info -> customImages.add(new CustomImage(info, width, height)));
        packDirectives.getBufferObjects().forEach((index, info) -> {
            bufferIndices.add((int) index);
            bufferInfos.add(info);
            buffers.add(GL15C.glGenBuffers());
        });
        allocateBuffers(false);

        this.ownedFramebuffers = new ArrayList<>();

        // clear every buffer once so none holds undefined data
        fullClearRequired = true;

        this.depthSourceFb = createFramebufferWritingToMain(new int[] {0});

        this.noTranslucents = new DepthTexture(width, height, currentDepthFormat);
        this.noHand = new DepthTexture(width, height, currentDepthFormat);

        this.noTranslucentsDestFb = createFramebufferWritingToMain(new int[] {0});
        this.noTranslucentsDestFb.addDepthAttachment(this.noTranslucents.getTextureId());

        this.noHandDestFb = createFramebufferWritingToMain(new int[] {0});
        this.noHandDestFb.addDepthAttachment(this.noHand.getTextureId());

        this.translucentDepthDirty = true;
        this.handDepthDirty = true;
    }

    public void destroy() {
        for (GlFramebuffer owned : ownedFramebuffers) {
            owned.destroy();
        }

        for (RenderTarget target : targets) {
            target.destroy();
        }

        noTranslucents.destroy();
        noHand.destroy();

        customImages.forEach(CustomImage::destroy);
        buffers.forEach(GL15C::glDeleteBuffers);
    }

    public List<CustomImage> getCustomImages() {
        return customImages;
    }

    // pack images marked for clearing start every frame empty, and the storage buffers keep fixed binding points
    public void prepareCustomResources() {
        customImages.forEach(CustomImage::clearIfNeeded);

        for (int i = 0; i < buffers.size(); i++) {
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, bufferIndices.getInt(i), buffers.getInt(i));
        }
    }

    private void allocateBuffers(boolean onlyRelative) {
        for (int i = 0; i < buffers.size(); i++) {
            ShaderStorageInfo info = bufferInfos.get(i);

            if (onlyRelative && !info.relative()) {
                continue;
            }

            long size = info.relative() ? info.size() * (long) Math.ceil(cachedWidth * info.scaleX()) * (long) Math.ceil(cachedHeight * info.scaleY()) : info.size();
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, buffers.getInt(i));
            GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, size, GL15C.GL_DYNAMIC_DRAW);
            GL43C.glClearBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, GL30C.GL_R8, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }

        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public int getRenderTargetCount() {
        return targets.length;
    }

    public RenderTarget get(int index) {
        return targets[index];
    }

    public GlFramebuffer getDepthSourceFb() {
        return depthSourceFb;
    }

    public int getDepthTexture() {
        return currentDepthTexture;
    }

    public DepthTexture getDepthTextureNoTranslucents() {
        return noTranslucents;
    }

    public DepthTexture getDepthTextureNoHand() {
        return noHand;
    }

    public boolean resizeIfNeeded(int newDepthBufferVersion, int newDepthTextureId, int newWidth, int newHeight, DepthBufferFormat newDepthFormat, PackDirectives packDirectives) {
        boolean recreateDepth = false;
        if (cachedDepthBufferVersion != newDepthBufferVersion) {
            recreateDepth = true;
            currentDepthTexture = newDepthTextureId;
            cachedDepthBufferVersion = newDepthBufferVersion;
        }

        boolean sizeChanged = newWidth != cachedWidth || newHeight != cachedHeight;
        boolean depthFormatChanged = newDepthFormat != currentDepthFormat;

        if (depthFormatChanged) {
            currentDepthFormat = newDepthFormat;
            copyStrategy = DepthCopyStrategy.fastest(currentDepthFormat.isCombinedStencil());
        }

        if (recreateDepth) {
            // minecraft recreates the depth texture on resize, so attach the new id
            // deleting the old texture detaches it from the framebuffers
            for (GlFramebuffer framebuffer : ownedFramebuffers) {
                if (framebuffer == noHandDestFb || framebuffer == noTranslucentsDestFb) {
                    // these framebuffers intentionally use a different depth attachment
                    continue;
                }

                if (framebuffer.hasDepthAttachment()) {
                    framebuffer.addDepthAttachment(newDepthTextureId);
                }
            }
        }

        if (depthFormatChanged || sizeChanged)  {
            noTranslucents.resize(newWidth, newHeight, newDepthFormat);
            noHand.resize(newWidth, newHeight, newDepthFormat);
            this.translucentDepthDirty = true;
            this.handDepthDirty = true;
        }

        if (sizeChanged) {
            cachedWidth = newWidth;
            cachedHeight = newHeight;

            for (int i = 0; i < targets.length; i++) {
                targets[i].resize(packDirectives.getTextureScaleOverride(i, newWidth, newHeight));
            }

            customImages.stream().filter(image -> image.getInfo().relative()).forEach(image -> image.resize(newWidth, newHeight));
            allocateBuffers(true);

            fullClearRequired = true;
        }

        return sizeChanged;
    }

    public void copyPreTranslucentDepth() {
        if (translucentDepthDirty) {
            translucentDepthDirty = false;
            GlStateManager.bindTexture(noTranslucents.getTextureId());
            depthSourceFb.bindAsReadBuffer();
            LucernaRenderSystem.copyTexImage2D(GL20C.GL_TEXTURE_2D, 0, currentDepthFormat.getGlInternalFormat(), 0, 0, cachedWidth, cachedHeight, 0);
        } else {
            copyStrategy.copy(depthSourceFb, getDepthTexture(), noTranslucentsDestFb, noTranslucents.getTextureId(),
                getCurrentWidth(), getCurrentHeight());
        }
    }

    public void copyPreHandDepth() {
        if (handDepthDirty) {
            handDepthDirty = false;
            GlStateManager.bindTexture(noHand.getTextureId());
            depthSourceFb.bindAsReadBuffer();
            LucernaRenderSystem.copyTexImage2D(GL20C.GL_TEXTURE_2D, 0, currentDepthFormat.getGlInternalFormat(), 0, 0, cachedWidth, cachedHeight, 0);
        } else {
            copyStrategy.copy(depthSourceFb, getDepthTexture(), noHandDestFb, noHand.getTextureId(),
                getCurrentWidth(), getCurrentHeight());
        }
    }

    public boolean isFullClearRequired() {
        return fullClearRequired;
    }

    public void onFullClear() {
        fullClearRequired = false;
    }

    public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
        return createFullFramebuffer(false, drawBuffers);
    }

    public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
        return createFullFramebuffer(true, drawBuffers);
    }

    public GlFramebuffer createClearFramebuffer(boolean alt, int[] clearBuffers) {
        ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

        if (!alt) {
            stageWritesToMain = invert(ImmutableSet.of(), clearBuffers);
        }

        return createColorFramebuffer(stageWritesToMain, clearBuffers);
    }

    private ImmutableSet<Integer> invert(ImmutableSet<Integer> base, int[] relevant) {
        ImmutableSet.Builder<Integer> inverted = ImmutableSet.builder();

        for (int i : relevant) {
            if (!base.contains(i)) {
                inverted.add(i);
            }
        }

        return inverted.build();
    }

    private GlFramebuffer createEmptyFramebuffer() {
        GlFramebuffer framebuffer = new GlFramebuffer();
        ownedFramebuffers.add(framebuffer);

        framebuffer.addDepthAttachment(currentDepthTexture);

        // before opengl 3.0 every framebuffer needs a color attachment
        framebuffer.addColorAttachment(0, get(0).getMainTexture());
        framebuffer.noDrawBuffers();

        return framebuffer;
    }

    public GlFramebuffer createGbufferFramebuffer(ImmutableSet<Integer> stageWritesToAlt, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        ImmutableSet<Integer> stageWritesToMain = invert(stageWritesToAlt, drawBuffers);

        // gbuffer programs that write the same targets share one framebuffer, so switching programs does not rebind it
        return gbufferFramebuffers.computeIfAbsent(Arrays.toString(drawBuffers) + stageWritesToMain, key -> {
            GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);

            framebuffer.addDepthAttachment(currentDepthTexture);

            return framebuffer;
        });
    }

    private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        ImmutableSet<Integer> stageWritesToMain = ImmutableSet.of();

        if (!clearsAlt) {
            stageWritesToMain = invert(ImmutableSet.of(), drawBuffers);
        }

        return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);
    }

    public GlFramebuffer createColorFramebufferWithDepth(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
        GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);

        framebuffer.addDepthAttachment(currentDepthTexture);

        return framebuffer;
    }

    public GlFramebuffer createColorFramebuffer(ImmutableSet<Integer> stageWritesToMain, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            throw new IllegalArgumentException("Framebuffer must have at least one color buffer");
        }

        GlFramebuffer framebuffer = new GlFramebuffer();
        ownedFramebuffers.add(framebuffer);

        int[] actualDrawBuffers = new int[drawBuffers.length];

        for (int i = 0; i < drawBuffers.length; i++) {
            actualDrawBuffers[i] = i;

            if (drawBuffers[i] >= getRenderTargetCount()) {
                throw new IllegalStateException("Render target with index " + drawBuffers[i] + " is not supported, only "
                        + getRenderTargetCount() + " render targets are supported.");
            }

            RenderTarget target = this.get(drawBuffers[i]);

            int textureId = stageWritesToMain.contains(drawBuffers[i]) ? target.getMainTexture() : target.getAltTexture();

            framebuffer.addColorAttachment(i, textureId);
        }

        framebuffer.drawBuffers(actualDrawBuffers);
        framebuffer.readBuffer(0);

        if (!framebuffer.isComplete()) {
            throw new IllegalStateException("Unexpected error while creating framebuffer");
        }

        return framebuffer;
    }

    public void destroyFramebuffer(GlFramebuffer framebuffer) {
        framebuffer.destroy();
        ownedFramebuffers.remove(framebuffer);
        gbufferFramebuffers.values().remove(framebuffer);
    }

    public int getCurrentWidth() {
        return cachedWidth;
    }

    public int getCurrentHeight() {
        return cachedHeight;
    }
}
