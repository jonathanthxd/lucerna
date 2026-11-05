// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package us.z1x.lucerna.gl.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.KHRDebug;
import us.z1x.lucerna.gl.GLDebug;
import us.z1x.lucerna.gl.LucernaRenderSystem;

public class ProgramCreator {
    private static final Logger LOGGER = LogManager.getLogger(ProgramCreator.class);

    public static void bindAttributes(int program) {
        LucernaRenderSystem.bindAttributeLocation(program, 11, "mc_Entity");
        LucernaRenderSystem.bindAttributeLocation(program, 12, "mc_midTexCoord");
        LucernaRenderSystem.bindAttributeLocation(program, 13, "at_tangent");
        LucernaRenderSystem.bindAttributeLocation(program, 14, "at_midBlock");
    }

    public static int create(String name, GlShader... shaders) {
        int program = GL20.glCreateProgram();

        bindAttributes(program);

        for (GlShader shader : shaders) {
            GL20.glAttachShader(program, shader.getHandle());
        }

        GL20.glLinkProgram(program);

        GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name);

        // https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
        for (GlShader shader : shaders) {
            LucernaRenderSystem.detachShader(program, shader.getHandle());
        }

        String log = LucernaRenderSystem.getProgramInfoLog(program);

        if (!log.isEmpty()) {
            LOGGER.warn("Program link log for {}: {}", name, log);
        }

        int result = GL20.glGetProgrami(program, GL20C.GL_LINK_STATUS);

        if (result != GL20C.GL_TRUE) {
            throw new RuntimeException("Shader program linking failed, see log for details");
        }

        return program;
    }
}
