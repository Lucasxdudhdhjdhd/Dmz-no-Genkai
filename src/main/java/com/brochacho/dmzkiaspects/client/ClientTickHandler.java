package com.brochacho.dmzkiaspects.client;

import com.brochacho.dmzkiaspects.DMZKiAspectsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DMZKiAspectsMod.MODID, value = Dist.CLIENT)
public final class ClientTickHandler {

    private ClientTickHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (KiAspectsKeybinds.TOGGLE_HUD.consumeClick()) {
            KiAspectsHudOverlay.enabled = !KiAspectsHudOverlay.enabled;
        }
    }
}
