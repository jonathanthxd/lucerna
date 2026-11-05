package us.z1x.lucerna.shaderpack;

import org.joml.Vector2f;
import org.joml.Vector3i;
import us.z1x.lucerna.Lucerna;

public class ComputeDirectiveParser {
    public static void setComputeWorkGroups(ComputeSource source, ConstDirectiveParser.ConstDirective directive) {
        if (!directive.getValue().startsWith("ivec3")) {
            Lucerna.logger.error("Failed to process {}: value was not a valid ivec3 constructor", directive);
        }

        String ivec3Args = directive.getValue().substring("ivec3".length()).trim();

        if (!ivec3Args.startsWith("(") || !ivec3Args.endsWith(")")) {
            Lucerna.logger.error("Failed to process {}: value was not a valid ivec3 constructor", directive);
        }

        ivec3Args = ivec3Args.substring(1, ivec3Args.length() - 1);

        String[] parts = ivec3Args.split(",");

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        if (parts.length != 3) {
            Lucerna.logger.error("Failed to process {}: expected 3 arguments to a ivec3 constructor, got {}", directive, parts.length);
        }

        try {
            source.setWorkGroups(new Vector3i(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])));
        } catch (NumberFormatException e) {
            Lucerna.logger.error("Failed to process {}", directive, e);
        }
    }

    public static void setComputeWorkGroupsRelative(ComputeSource source, ConstDirectiveParser.ConstDirective directive) {
        if (!directive.getValue().startsWith("vec2")) {
            Lucerna.logger.error("Failed to process {}: value was not a valid vec2 constructor", directive);
        }

        String vec2Args = directive.getValue().substring("vec2".length()).trim();

        if (!vec2Args.startsWith("(") || !vec2Args.endsWith(")")) {
            Lucerna.logger.error("Failed to process {}: value was not a valid vec2 constructor", directive);
        }

        vec2Args = vec2Args.substring(1, vec2Args.length() - 1);

        String[] parts = vec2Args.split(",");

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        if (parts.length != 2) {
            Lucerna.logger.error("Failed to process {}: expected 2 arguments to a vec2 constructor, got {}", directive, parts.length);
        }

        try {
            source.setWorkGroupRelative(new Vector2f(
                Float.parseFloat(parts[0]),
                Float.parseFloat(parts[1])
            ));
        } catch (NumberFormatException e) {
            Lucerna.logger.error("Failed to process {}", directive, e);
        }
    }
}
