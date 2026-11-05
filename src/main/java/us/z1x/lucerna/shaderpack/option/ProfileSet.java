package us.z1x.lucerna.shaderpack.option;

import org.jetbrains.annotations.Nullable;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.shaderpack.option.values.OptionValues;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class ProfileSet {
    private final LinkedHashMap<String, Profile> orderedProfiles; // cycle order
    private final List<Profile> sortedProfiles; // match order

    public ProfileSet(LinkedHashMap<String, Profile> orderedProfiles) {
        List<Profile> sorted = new ArrayList<>(orderedProfiles.values());

        Comparator<Profile> lowToHigh = Comparator.comparing(p -> p.precedence);
        Comparator<Profile> highToLow = lowToHigh.reversed();

        // most constrained profiles first, so a profile with one extra constraint matches before its base
        sorted.sort(highToLow);

        this.sortedProfiles = sorted;
        this.orderedProfiles = orderedProfiles;
    }

    public void forEach(BiConsumer<String, Profile> action) {
        orderedProfiles.forEach(action);
    }

    public ProfileResult scan(OptionSet options, OptionValues values) {
        if (sortedProfiles.size() <= 0) {
            return new ProfileResult(null, null, null);
        }

        for (int i = 0; i < sortedProfiles.size(); i++) {
            Profile current = sortedProfiles.get(i);

            if (current.matches(options, values)) {
                Profile next = sortedProfiles.get(Math.floorMod(i + 1, sortedProfiles.size()));
                Profile prev = sortedProfiles.get(Math.floorMod(i - 1, sortedProfiles.size()));

                return new ProfileResult(current, next, prev);
            }
        }

        // fallback when no profile matches
        Profile next = sortedProfiles.get(0);
        Profile prev = sortedProfiles.get(sortedProfiles.size() - 1);

        return new ProfileResult(null, next, prev);
    }

    public static ProfileSet fromTree(Map<String, List<String>> tree, OptionSet optionSet) {
        LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();

        for (String name : tree.keySet()) {
            profiles.put(name, parse(name, new ArrayList<>(), tree, optionSet));
        }

        return new ProfileSet(profiles);
    }

    private static Profile parse(String name, List<String> parents, Map<String, List<String>> tree, OptionSet optionSet) throws IllegalArgumentException {
        Profile.Builder builder = new Profile.Builder(name);
        List<String> options = tree.get(name);

        if (options == null) {
            throw new IllegalArgumentException("Profile \"" + name + "\" does not exist!");
        }

        for (String option : options) {
            if (option.startsWith("!program.")) {
                builder.disableProgram(option.substring("!program.".length()));
            } else if (option.startsWith("profile.")) {
                String dependency = option.substring("profile.".length());

                if (parents.contains(dependency)) {
                    throw new IllegalArgumentException("Error parsing profile \"" + name
                            + "\", recursively included by: " + String.join(", ", parents));
                }

                parents.add(dependency);
                builder.addAll(parse(dependency, parents, tree, optionSet));
            } else if (option.startsWith("!")) {
                builder.option(option.substring(1), "false");
            } else if (option.contains("=")) {
                int splitPoint = option.indexOf("=");
                builder.option(option.substring(0, splitPoint), option.substring(splitPoint + 1));
            } else if (option.contains(":")) {
                int splitPoint = option.indexOf(":");
                builder.option(option.substring(0, splitPoint), option.substring(splitPoint + 1));
            } else if (optionSet.isBooleanOption(option)) {
                builder.option(option, "true");
            } else {
                Lucerna.logger.warn("Invalid pack option: {}", option);
            }
        }

        return builder.build();
    }

    public int size() {
        return sortedProfiles.size();
    }

    public static class ProfileResult {
        public final Optional<Profile> current;
        public final Profile next;
        public final Profile previous;

        private ProfileResult(@Nullable Profile current, Profile next, Profile previous) {
            this.current = Optional.ofNullable(current);
            this.next = next;
            this.previous = previous;
        }
    }
}
