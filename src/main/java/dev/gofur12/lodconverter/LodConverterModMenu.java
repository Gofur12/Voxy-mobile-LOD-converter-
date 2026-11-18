package dev.gofur12.lodconverter;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Exposes the converter GUI as this mod's Config button in Mod Menu. */
public final class LodConverterModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DhConverterScreen::new;
    }
}
