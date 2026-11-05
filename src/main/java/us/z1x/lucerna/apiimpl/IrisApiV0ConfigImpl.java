package us.z1x.lucerna.apiimpl;

import net.irisshaders.iris.api.v0.IrisApiConfig;
import us.z1x.lucerna.Lucerna;
import us.z1x.lucerna.config.LucernaConfig;

import java.io.IOException;

public class IrisApiV0ConfigImpl implements IrisApiConfig {
    @Override
    public boolean areShadersEnabled() {
        return Lucerna.getLucernaConfig().areShadersEnabled();
    }

    @Override
    public void setShadersEnabledAndApply(boolean enabled) {
        LucernaConfig config = Lucerna.getLucernaConfig();

        config.setShadersEnabled(enabled);

        try {
            config.save();
        } catch (IOException e) {
            Lucerna.logger.error("Error saving configuration file!", e);
        }

        try {
            Lucerna.reloadInBackground();
        } catch (IOException e) {
            Lucerna.logger.error("Error reloading shader pack while applying changes!", e);
        }
    }
}
