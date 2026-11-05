package us.z1x.lucerna.shaderpack.option.menu;

import com.google.common.collect.Lists;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.shaderpack.ShaderProperties;
import us.z1x.lucerna.shaderpack.option.ProfileSet;
import us.z1x.lucerna.shaderpack.option.ShaderPackOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OptionMenuContainer {
    public final OptionMenuElementScreen mainScreen;
    public final Map<String, OptionMenuElementScreen> subScreens = new HashMap<>();

    private final List<OptionMenuOptionElement> usedOptionElements = new ArrayList<>();
    private final List<String> usedOptions = new ArrayList<>();
    private final List<String> unusedOptions = new ArrayList<>();
    private final Map<List<OptionMenuElement>, Integer> unusedOptionDumpQueue = new HashMap<>();
    private final ProfileSet profiles;

    public OptionMenuContainer(ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, ProfileSet profiles) {
        this.profiles = profiles;

        // without a main screen list, all options go on the main screen
        this.mainScreen = new OptionMenuMainElementScreen(
            this, shaderProperties, shaderPackOptions,
            shaderProperties.getMainScreenOptions().orElseGet(() -> Collections.singletonList("*")),
            shaderProperties.getMainScreenColumnCount());

        this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getBooleanOptions().keySet());
        this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getStringOptions().keySet());

        Map<String, Integer> subScreenColumnCounts = shaderProperties.getSubScreenColumnCount();
        shaderProperties.getSubScreenOptions().forEach((screenKey, options) -> {
            subScreens.put(screenKey, new OptionMenuSubElementScreen(
                    screenKey, this, shaderProperties, shaderPackOptions, options, Optional.ofNullable(subScreenColumnCounts.get(screenKey))));
        });

        for (Map.Entry<List<OptionMenuElement>, Integer> entry : unusedOptionDumpQueue.entrySet()) {
            List<OptionMenuElement> elementsToInsert = new ArrayList<>();
            List<String> unusedOptionsCopy = Lists.newArrayList(this.unusedOptions);

            for (String optionId : unusedOptionsCopy) {
                try {
                    OptionMenuElement element = OptionMenuElement.create(optionId, this, shaderProperties, shaderPackOptions);
                    if (element != null) {
                        elementsToInsert.add(element);

                        if (element instanceof OptionMenuOptionElement) {
                            this.notifyOptionAdded(optionId, (OptionMenuOptionElement) element);
                        }
                    }
                } catch (IllegalArgumentException error) {
                    Lucerna.logger.warn(error);

                    elementsToInsert.add(OptionMenuElement.EMPTY);
                }
            }

            entry.getKey().addAll(entry.getValue(), elementsToInsert);
        }
    }

    public ProfileSet getProfiles() {
        return profiles;
    }

    // called for screens with a "*" element, unused options are added after all screens resolve
    public void queueForUnusedOptionDump(int index, List<OptionMenuElement> elementList) {
        this.unusedOptionDumpQueue.put(elementList, index);
    }

    public void notifyOptionAdded(String optionId, OptionMenuOptionElement option) {
        if (!usedOptions.contains(optionId)) {
            usedOptionElements.add(option);
            usedOptions.add(optionId);
        }

        unusedOptions.remove(optionId);
    }
}
