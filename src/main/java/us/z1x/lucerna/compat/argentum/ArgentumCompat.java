package us.z1x.lucerna.compat.argentum;

import dev.rdh.argentum.impl.Argentum;
import dev.rdh.argentum.impl.config.ArgentumConfig;

// these argentum renderers draw with their own programs, so they are off while a shader pack is loaded
// entity instancing stays on, it draws through the pack programs, see InstancedEntityPrograms
public final class ArgentumCompat {
    private static boolean applied;
    private static boolean instancingDisabled;
    private static boolean fasterClouds;
    private static boolean fasterWeather;
    private static boolean fasterEndPortals;

    private ArgentumCompat() {
    }

    public static void setShadersActive(boolean active) {
        ArgentumConfig config = Argentum.CONFIG;

        if (active == applied || config == null) {
            return;
        }

        if (active) {
            disable(config);
        } else {
            restore(config);
            instancingDisabled = false;
        }

        applied = active;
    }

    public static void beforeSave() {
        if (applied) {
            restore(Argentum.CONFIG);
        }
    }

    public static void afterSave() {
        if (applied) {
            disable(Argentum.CONFIG);
        }
    }

    private static void disable(ArgentumConfig config) {
        fasterClouds = config.fasterClouds;
        fasterWeather = config.fasterWeather;
        fasterEndPortals = config.fasterEndPortals;
        config.fasterClouds = false;
        config.fasterWeather = false;
        config.fasterEndPortals = false;

        if (instancingDisabled) {
            config.entityInstancing = false;
        }
    }

    // for shader packs whose entity program cannot draw argentum's instanced models
    public static void disableEntityInstancing() {
        ArgentumConfig config = Argentum.CONFIG;

        if (applied && !instancingDisabled && config.entityInstancing) {
            instancingDisabled = true;
            config.entityInstancing = false;
        }
    }

    private static void restore(ArgentumConfig config) {
        if (instancingDisabled) {
            config.entityInstancing = true;
        }

        config.fasterClouds = fasterClouds;
        config.fasterWeather = fasterWeather;
        config.fasterEndPortals = fasterEndPortals;
    }
}
