package us.z1x.lucerna.pipeline;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Vector2i;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21C;
import us.z1x.lucerna.rendertarget.RenderTarget;
import us.z1x.lucerna.rendertarget.RenderTargets;
import us.z1x.lucerna.shaderpack.PackRenderTargetDirectives;
import us.z1x.lucerna.shaderpack.PackShadowDirectives;
import us.z1x.lucerna.shadows.ShadowRenderTargets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClearPassCreator {
    public static ImmutableList<ClearPass> createClearPasses(RenderTargets renderTargets, boolean fullClear,
                                                             PackRenderTargetDirectives renderTargetDirectives) {
        final int maxDrawBuffers = GL11.glGetInteger(GL21C.GL_MAX_DRAW_BUFFERS);

        // sort buffers by clear color to group glClear calls
        Map<Vector2i, Map<ClearPassInformation, IntList>> clearByColor = new HashMap<>();

        renderTargetDirectives.getRenderTargetSettings().forEach((bufferI, settings) -> {
            final int buffer = bufferI;

            if (fullClear || settings.shouldClear()) {
                Vector4f defaultClearColor;

                if (buffer == 0) {
                    // colortex0 defaults to the fog color with 1.0 alpha
                    defaultClearColor = null;
                } else if (buffer == 1) {
                    // colortex1 defaults to solid white with 1.0 alpha
                    defaultClearColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
                } else {
                    // other buffers default to solid black with 0.0 alpha
                    defaultClearColor = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
                }

                RenderTarget target = renderTargets.get(buffer);
                Vector4f clearColor = settings.getClearColor().orElse(defaultClearColor);
                clearByColor.computeIfAbsent(new Vector2i(target.getWidth(), target.getHeight()), size -> new HashMap<>()).computeIfAbsent(new ClearPassInformation(clearColor, target.getWidth(), target.getHeight()), color -> new IntArrayList()).add(buffer);
            }
        });

        List<ClearPass> clearPasses = new ArrayList<>();

        clearByColor.forEach((passSize, vector4fIntListMap) -> {
            vector4fIntListMap.forEach((clearInfo, buffers) -> {
                int startIndex = 0;

                while (startIndex < buffers.size()) {
                    // split into passes of max draw buffers so more than 8 buffers with one color work where the limit is 8
                    int[] clearBuffers = new int[Math.min(buffers.size() - startIndex, maxDrawBuffers)];

                    for (int i = 0; i < clearBuffers.length; i++) {
                        clearBuffers[i] = buffers.getInt(startIndex);
                        startIndex++;
                    }

                    // the depth buffer is cleared separately
                    clearPasses.add(new ClearPass(clearInfo.getColor(), clearInfo::getWidth, clearInfo::getHeight,
                        renderTargets.createClearFramebuffer(true, clearBuffers), GL21C.GL_COLOR_BUFFER_BIT));

                    clearPasses.add(new ClearPass(clearInfo.getColor(), clearInfo::getWidth, clearInfo::getHeight,
                        renderTargets.createClearFramebuffer(false, clearBuffers), GL21C.GL_COLOR_BUFFER_BIT));
                }
            });
        });

        return ImmutableList.copyOf(clearPasses);
    }

    public static ImmutableList<ClearPass> createShadowClearPasses(ShadowRenderTargets renderTargets, boolean fullClear,
                                                                   PackShadowDirectives renderTargetDirectives) {
        final int maxDrawBuffers = GL11.glGetInteger(GL21C.GL_MAX_DRAW_BUFFERS);

        // sort buffers by clear color to group glClear calls
        Map<Vector4f, IntList> clearByColor = new HashMap<>();

        for (int i = 0; i < renderTargetDirectives.getColorSamplingSettings().size(); i++) {
            PackShadowDirectives.SamplingSettings settings = renderTargetDirectives.getColorSamplingSettings().get(i);

            if (fullClear || settings.getClear()) {
                Vector4f clearColor = settings.getClearColor();
                clearByColor.computeIfAbsent(clearColor, color -> new IntArrayList()).add(i);
            }
        }

        List<ClearPass> clearPasses = new ArrayList<>();

        clearByColor.forEach((clearColor, buffers) -> {
            int startIndex = 0;

            while (startIndex < buffers.size()) {
                // split into passes of max draw buffers so more than 8 buffers with one color work where the limit is 8
                int[] clearBuffers = new int[Math.min(buffers.size() - startIndex, maxDrawBuffers)];

                for (int i = 0; i < clearBuffers.length; i++) {
                    clearBuffers[i] = buffers.getInt(startIndex);
                    startIndex++;
                }

                // the depth buffer is cleared separately
                clearPasses.add(new ClearPass(clearColor, renderTargets::getResolution, renderTargets::getResolution,
                    renderTargets.createFramebufferWritingToAlt(clearBuffers), GL21C.GL_COLOR_BUFFER_BIT));

                clearPasses.add(new ClearPass(clearColor, renderTargets::getResolution, renderTargets::getResolution,
                    renderTargets.createFramebufferWritingToMain(clearBuffers), GL21C.GL_COLOR_BUFFER_BIT));
            }
        });

        return ImmutableList.copyOf(clearPasses);
    }
}
