package com.brochacho.dmzkiaspects.network;

import com.brochacho.dmzkiaspects.capability.KiAspectsData;
import com.brochacho.dmzkiaspects.kaioken.KaiokenTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> Cliente: foto de los 5 Aspectos + estado de Kaioken del
 * jugador local, para que el HUD y el panel del menú de Stats tengan algo
 * que mostrar. Se manda periódicamente desde KiAspectsCapability (cada
 * pocos ticks, no en cada uno, para no gastar ancho de banda de más).
 */
public class SyncKiAspectsS2C {

    private final double kiDeCombate;
    private final double presencia;
    private final double kiCompleto;
    private final double poderReal;
    private final double limiteActual;
    private final double potencialOculto;
    private final boolean kaiokenActive;
    private final double kaiokenRawMultiplier;
    private final double kaiokenEffectiveMultiplier;
    private final double kaiokenDangerFactor;

    public SyncKiAspectsS2C(KiAspectsData data, KaiokenTracker.State kaioken) {
        this.kiDeCombate = data.getKiDeCombate();
        this.presencia = data.getPresencia();
        this.kiCompleto = data.getKiCompleto();
        this.poderReal = data.getPoderReal();
        this.limiteActual = data.getLimiteActual();
        this.potencialOculto = data.getPotencialOculto();
        this.kaiokenActive = kaioken.active();
        this.kaiokenRawMultiplier = kaiokenActive ? kaioken.evaluation().rawMultiplier() : 1.0D;
        this.kaiokenEffectiveMultiplier = kaiokenActive ? kaioken.evaluation().effectiveMultiplier() : 1.0D;
        this.kaiokenDangerFactor = kaiokenActive ? kaioken.evaluation().dangerFactor() : 0.0D;
    }

    public SyncKiAspectsS2C(FriendlyByteBuf buf) {
        this.kiDeCombate = buf.readDouble();
        this.presencia = buf.readDouble();
        this.kiCompleto = buf.readDouble();
        this.poderReal = buf.readDouble();
        this.limiteActual = buf.readDouble();
        this.potencialOculto = buf.readDouble();
        this.kaiokenActive = buf.readBoolean();
        this.kaiokenRawMultiplier = buf.readDouble();
        this.kaiokenEffectiveMultiplier = buf.readDouble();
        this.kaiokenDangerFactor = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(kiDeCombate);
        buf.writeDouble(presencia);
        buf.writeDouble(kiCompleto);
        buf.writeDouble(poderReal);
        buf.writeDouble(limiteActual);
        buf.writeDouble(potencialOculto);
        buf.writeBoolean(kaiokenActive);
        buf.writeDouble(kaiokenRawMultiplier);
        buf.writeDouble(kaiokenEffectiveMultiplier);
        buf.writeDouble(kaiokenDangerFactor);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::applyOnClient));
        ctx.setPacketHandled(true);
    }

    /**
     * Vive en una clase separada indirectamente (vía DistExecutor) para que
     * el servidor dedicado nunca intente cargar clases de cliente al
     * procesar este paquete.
     */
    private void applyOnClient() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        com.brochacho.dmzkiaspects.client.ClientKiAspectsCache.update(new com.brochacho.dmzkiaspects.client.ClientKiAspectsCache.Snapshot(
                kiDeCombate, presencia, kiCompleto, poderReal, limiteActual, potencialOculto,
                kaiokenActive, kaiokenRawMultiplier, kaiokenEffectiveMultiplier, kaiokenDangerFactor
        ));
    }
}
