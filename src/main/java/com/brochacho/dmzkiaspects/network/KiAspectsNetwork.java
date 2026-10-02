package com.brochacho.dmzkiaspects.network;

import com.brochacho.dmzkiaspects.DMZKiAspectsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal de red del addon. Solo se usa para mandar al cliente una foto de
 * los 5 Aspectos del jugador local, así el HUD y el panel del menú de
 * Stats (V) pueden mostrar algo sin tener que adivinar nada del lado cliente.
 *
 * No hay paquetes cliente -> servidor: todo lo que el jugador puede "hacer"
 * (cargar ki, entrenar, etc.) ya pasa por la lógica normal de DMZ; este
 * addon solo observa y reporta.
 */
public final class KiAspectsNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(DMZKiAspectsMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    private KiAspectsNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, SyncKiAspectsS2C.class,
                SyncKiAspectsS2C::encode, SyncKiAspectsS2C::new, SyncKiAspectsS2C::handle);
    }
}
