package com.brochacho.dmzkiaspects.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * Solo se carga en el cliente (referenciada únicamente desde clases
 * anotadas Dist.CLIENT: ClientSetup y ClientTickHandler), así que es seguro
 * tener acá un campo estático que construye un KeyMapping en el classloading.
 */
public final class KiAspectsKeybinds {

    /** Sin tecla por defecto (UNKNOWN): el jugador la asigna si quiere el HUD. No pisa ninguna tecla de DMZ. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.dmzkiaspects.toggle_hud",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.dmzkiaspects"
    );

    private KiAspectsKeybinds() {
    }
}
