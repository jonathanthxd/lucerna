package us.z1x.lucerna.gl.blending;

import org.lwjgl.opengl.GL11;

import java.util.Optional;

public enum AlphaTestFunction {
    NEVER(GL11.GL_NEVER),
    LESS(GL11.GL_LESS),
    EQUAL(GL11.GL_EQUAL),
    LEQUAL(GL11.GL_LEQUAL),
    GREATER(GL11.GL_GREATER),
    NOTEQUAL(GL11.GL_NOTEQUAL),
    GEQUAL(GL11.GL_GEQUAL),
    ALWAYS(GL11.GL_ALWAYS);

    private final int glId;

    AlphaTestFunction(int glFormat) {
        this.glId = glFormat;
    }

    public static Optional<AlphaTestFunction> fromGlId(int glId) {
        switch (glId) {
            case GL11.GL_NEVER:
                return Optional.of(NEVER);
            case GL11.GL_LESS:
                return Optional.of(LESS);
            case GL11.GL_EQUAL:
                return Optional.of(EQUAL);
            case GL11.GL_LEQUAL:
                return Optional.of(LEQUAL);
            case GL11.GL_GREATER:
                return Optional.of(GREATER);
            case GL11.GL_NOTEQUAL:
                return Optional.of(NOTEQUAL);
            case GL11.GL_GEQUAL:
                return Optional.of(GEQUAL);
            case GL11.GL_ALWAYS:
                return Optional.of(ALWAYS);
            default:
                return Optional.empty();
        }
    }

    public static Optional<AlphaTestFunction> fromString(String name) {
        if ("GL_ALWAYS".equals(name)) {
            // shaders.properties documents GL_ALWAYS though the other names have no GL_ prefix
            return Optional.of(AlphaTestFunction.ALWAYS);
        }

        try {
            return Optional.of(AlphaTestFunction.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public int getGlId() {
        return glId;
    }
}
