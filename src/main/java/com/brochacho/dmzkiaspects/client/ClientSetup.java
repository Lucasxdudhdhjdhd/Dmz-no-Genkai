package com.brochacho.dmzkiaspects.client;

import com.brochacho.dmzkiaspects.DMZKiAspectsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registro de cosas de cliente (overlay de HUD, keybind). Va en el bus de
 * MOD (eventos de arranque), a diferencia de KiAspectsScreenIntegration que
 * usa el bus de Forge (eventos de juego). Gateado a Dist.CLIENT: nunca se
 * carga en un servidor dedicado.
 *
 * NOTA DE VERIFICACIÓN: RegisterGuiOverlaysEvent y su API
 * (registerAboveAll/registerBelowAll) son de la reescritura del sistema de
 * overlays de Forge 1.20.1. Si tu versión exacta de Forge 47.x difiere,
 * ajustá esta llamada — no pude compilar contra Forge real para
 * confirmarlo en este entorno.
 */
@Mod.EventBusSubscriber(modid = DMZKiAspectsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("ki_aspects_hud", new KiAspectsHudOverlay());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KiAspectsKeybinds.TOGGLE_HUD);
    }
}
