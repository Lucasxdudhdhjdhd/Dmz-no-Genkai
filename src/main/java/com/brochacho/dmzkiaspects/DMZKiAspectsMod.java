package com.brochacho.dmzkiaspects;

import com.brochacho.dmzkiaspects.command.KiAspectsDebugCommand;
import com.brochacho.dmzkiaspects.network.KiAspectsNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * DMZ Ki Aspects
 * -----------------------------------------------------------------------
 * Addon standalone para DragonMineZ (Forge 1.20.1) que implementa el sistema
 * de datos de los "5 Aspectos del Ki":
 *
 *   1. Ki de Combate   -> poder máximo cuerpo a cuerpo en la forma actual
 *   2. Presencia       -> % del Ki de Combate detectable por scouter/Ki Sense
 *   3. Ki Completo     -> 100% del poder de la transformación actual
 *   4. Poder Real      -> poder de la transformación más fuerte desbloqueada
 *   5. Potencial/Límite Actual -> techo alcanzable a este nivel + potencial oculto
 *
 * Fase 1: solo estructura de datos, capability, sincronización con
 * StatsCapability de DMZ y comando de debug. Sin mixins todavía.
 */
@Mod(DMZKiAspectsMod.MODID)
public class DMZKiAspectsMod {

    public static final String MODID = "dmzkiaspects";

    public DMZKiAspectsMod() {
        // La capability, la integración de cliente (HUD, pantalla de Stats)
        // y el registro de teclas se auto-registran vía @Mod.EventBusSubscriber
        // (ver KiAspectsCapability y el paquete client). Acá solo enganchamos
        // lo que necesita registro manual: comandos y el canal de red.
        MinecraftForge.EVENT_BUS.register(KiAspectsDebugCommand.class);
        KiAspectsNetwork.register();
    }
}
