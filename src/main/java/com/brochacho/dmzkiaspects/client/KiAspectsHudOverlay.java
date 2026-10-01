package com.brochacho.dmzkiaspects.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

/**
 * Panel de HUD compacto, en la esquina superior izquierda, con los aspectos
 * que pediste que sean siempre visibles: Presencia, Poder Real, Límite
 * Actual y Potencial Oculto. Muestra también un aviso corto si el Kaioken
 * está activo y en zona peligrosa.
 *
 * APAGADO por defecto (ver {@link #enabled}): DMZ ya tiene su propio HUD
 * (Alternative HUD / Xenoverse HUD, configurables desde su menú de Config)
 * y este addon no debe imponerse sobre eso. El jugador lo activa con la
 * tecla configurable "Mostrar/Ocultar 5 Aspectos" (ver KiAspectsKeybinds).
 *
 * NOTA DE VERIFICACIÓN: la API de overlays de Forge (paquete
 * net.minecraftforge.client.gui.overlay, IGuiOverlay/ForgeGui/
 * RegisterGuiOverlaysEvent) cambió de forma durante el ciclo de 1.20.1. Si
 * tu build de Forge 47.x usa una forma distinta (paquetes o firmas), esta
 * es la clase a ajustar; no pude compilar contra Forge real para
 * confirmarlo en este entorno.
 */
public final class KiAspectsHudOverlay implements IGuiOverlay {

    /** Apagado por defecto: opt-in vía tecla, no cambia el HUD de DMZ a menos que el jugador lo pida. */
    public static boolean enabled = false;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        ClientKiAspectsCache.Snapshot snap = ClientKiAspectsCache.get();
        Font font = mc.font;

        int x = 4;
        int y = 4;
        int lineHeight = 10;
        int white = 0xFFFFFF;

        guiGraphics.drawString(font, "§65 Aspectos del Ki", x, y, white, true);
        y += lineHeight;
        guiGraphics.drawString(font, String.format(Locale.ROOT, "§bPresencia: §f%.0f%%", snap.presencia() * 100.0), x, y, white, true);
        y += lineHeight;
        guiGraphics.drawString(font, String.format(Locale.ROOT, "§bPoder Real: §f%.0f", snap.poderReal()), x, y, white, true);
        y += lineHeight;
        guiGraphics.drawString(font, String.format(Locale.ROOT, "§bLímite Actual: §f%.0f", snap.limiteActual()), x, y, white, true);
        y += lineHeight;
        guiGraphics.drawString(font, String.format(Locale.ROOT, "§bPotencial Oculto: §f%.0f  §7(%.0f%% usado)",
                snap.potencialOculto(), snap.potencialUsadoFraccion() * 100.0), x, y, white, true);

        if (snap.kaiokenActive()) {
            y += lineHeight;
            int color = snap.kaiokenDangerFactor() > 0.6D ? 0xFF5555 : 0xFFAA00;
            guiGraphics.drawString(font, String.format(Locale.ROOT,
                    "§cKaioken x%.0f §7(efectivo x%.2f, peligro %.0f%%)",
                    snap.kaiokenRawMultiplier(), snap.kaiokenEffectiveMultiplier(), snap.kaiokenDangerFactor() * 100.0),
                    x, y, color, true);
        }
    }
}
