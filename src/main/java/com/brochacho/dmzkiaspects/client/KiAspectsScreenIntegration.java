package com.brochacho.dmzkiaspects.client;

import com.brochacho.dmzkiaspects.DMZKiAspectsMod;
import com.dragonminez.client.gui.character.CharacterStatsScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Suma un panel con los 5 Aspectos AL MENÚ de Stats (tecla V) de DMZ, no
 * encima como un tooltip flotante: se dibuja en el mismo espacio de
 * coordenadas ("UI space") que usa internamente ese menú, con la misma
 * textura de panel chico que usa DMZ, pegado justo debajo de su panel
 * derecho — así se mueve, escala y desliza exactamente igual que el resto
 * de la pantalla (zoom, animación de cambio de panel, etc.).
 *
 * ---------------------------------------------------------------------
 * CÓMO FUNCIONA, sin mixins:
 * ---------------------------------------------------------------------
 * DMZ maneja su menú con una clase base (ScaledScreen -> BaseMenuScreen)
 * que aplica un PoseStack.scale() propio y calcula "ancho/alto virtuales"
 * (getUiWidth/getUiHeight) y offsets de animación de panel
 * (getRightPanelSwitchOffset). Esos métodos son `protected`, y como no
 * extendemos ni mixineamos CharacterStatsScreen, no podemos llamarlos
 * directo. Los leemos por reflection (con setAccessible) UNA sola vez y
 * los cacheamos — así replicamos su transform exacto (mismo push/scale de
 * PoseStack, mismas coordenadas virtuales) para que nuestro panel quede
 * realmente integrado, no superpuesto en píxeles de pantalla fijos.
 *
 * Si en algún build de DMZ estos nombres cambian, la reflection falla UNA
 * vez, se marca `reflectionFailed = true` y de ahí en más se usa
 * {@link #renderFallback} (una caja de tooltip vanilla, prolija pero sin
 * la integración fina) — nunca crashea el juego por esto.
 * ---------------------------------------------------------------------
 */
@Mod.EventBusSubscriber(modid = DMZKiAspectsMod.MODID, value = Dist.CLIENT)
public final class KiAspectsScreenIntegration {

    private static final ResourceLocation MENU_SMALL =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/gui/menu/menusmall.png");

    /** Región del panel chico dentro del atlas de DMZ (la misma que usa CharacterStatsScreen para su panel superior). */
    private static final float PANEL_U = 0.0f;
    private static final float PANEL_V = 95.0f;
    private static final int PANEL_W = 145;
    private static final int PANEL_H = 58;
    private static final int ATLAS_SIZE = 256;

    private static Method mGetUiWidth;
    private static Method mGetUiHeight;
    private static Method mGetUiScale;
    private static Method mGetRightPanelSwitchOffset;
    private static boolean reflectionFailed = false;

    private KiAspectsScreenIntegration() {
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CharacterStatsScreen screen)) {
            return;
        }

        GuiGraphics gfx = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        ClientKiAspectsCache.Snapshot snap = ClientKiAspectsCache.get();

        if (!reflectionFailed && ensureReflection(screen)) {
            try {
                renderIntegrated(screen, gfx, font, snap, event.getPartialTick());
                return;
            } catch (Exception e) {
                reflectionFailed = true; // no insistir cada frame si algo dejó de calzar
            }
        }
        renderFallback(event, gfx, font, snap);
    }

    /** Busca y cachea los métodos protegidos de escala de UI de DMZ, subiendo por la jerarquía de clases. */
    private static boolean ensureReflection(CharacterStatsScreen screen) {
        if (mGetUiWidth != null) {
            return true;
        }
        try {
            Class<?> scaledScreenClass = findAncestor(screen.getClass(), "ScaledScreen");
            Class<?> baseMenuScreenClass = findAncestor(screen.getClass(), "BaseMenuScreen");
            if (scaledScreenClass == null || baseMenuScreenClass == null) {
                reflectionFailed = true;
                return false;
            }

            mGetUiWidth = scaledScreenClass.getDeclaredMethod("getUiWidth");
            mGetUiWidth.setAccessible(true);
            mGetUiHeight = scaledScreenClass.getDeclaredMethod("getUiHeight");
            mGetUiHeight.setAccessible(true);
            mGetUiScale = scaledScreenClass.getDeclaredMethod("getUiScale");
            mGetUiScale.setAccessible(true);
            mGetRightPanelSwitchOffset = baseMenuScreenClass.getDeclaredMethod("getRightPanelSwitchOffset", float.class);
            mGetRightPanelSwitchOffset.setAccessible(true);
            return true;
        } catch (Exception e) {
            reflectionFailed = true;
            return false;
        }
    }

    private static Class<?> findAncestor(Class<?> from, String simpleName) {
        Class<?> current = from;
        while (current != null) {
            if (current.getSimpleName().equals(simpleName)) {
                return current;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Dibuja de verdad "adentro" del menú: mismo scale, mismas coordenadas
     * virtuales y mismo offset de animación que usa DMZ para su panel
     * derecho, así el nuestro queda pegado justo debajo y se mueve con él.
     */
    private static void renderIntegrated(CharacterStatsScreen screen, GuiGraphics gfx, Font font,
                                          ClientKiAspectsCache.Snapshot snap, float partialTick) throws Exception {
        int uiWidth = (int) mGetUiWidth.invoke(screen);
        int uiHeight = (int) mGetUiHeight.invoke(screen);
        float uiScale = (float) mGetUiScale.invoke(screen);
        int rightOffset = (int) mGetRightPanelSwitchOffset.invoke(screen, partialTick);

        int centerY = uiHeight / 2;
        // El panel derecho de DMZ mide 141x213 y arranca en (uiWidth-158, centerY-105).
        // El nuestro va pegado justo debajo, con el mismo ancho aproximado.
        int panelX = uiWidth - 158 + rightOffset;
        int panelY = centerY - 105 + 213 + 4;

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.scale(uiScale, uiScale, 1.0f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        gfx.blit(MENU_SMALL, panelX, panelY, PANEL_U, PANEL_V, PANEL_W, PANEL_H, ATLAS_SIZE, ATLAS_SIZE);
        RenderSystem.disableBlend();

        int textX = panelX + 8;
        int textY = panelY + 7;
        int lineHeight = 9;
        int white = 0xFFFFFF;

        gfx.drawString(font, "§65 Aspectos del Ki", textX, textY, white, false);
        textY += lineHeight;
        gfx.drawString(font, String.format(Locale.ROOT, "§bPresencia §f%.0f%%   §bPoder Real §f%.0f",
                snap.presencia() * 100.0, snap.poderReal()), textX, textY, white, false);
        textY += lineHeight;
        gfx.drawString(font, String.format(Locale.ROOT, "§bLímite §f%.0f   §bPotencial §f%.0f",
                snap.limiteActual(), snap.potencialOculto()), textX, textY, white, false);
        textY += lineHeight;
        if (snap.kaiokenActive()) {
            gfx.drawString(font, String.format(Locale.ROOT, "§cKaioken x%.0f (x%.2f efectivo)",
                    snap.kaiokenRawMultiplier(), snap.kaiokenEffectiveMultiplier()), textX, textY, white, false);
        } else {
            gfx.drawString(font, String.format(Locale.ROOT, "§7Ki de Combate %.0f  Ki Completo %.0f",
                    snap.kiDeCombate(), snap.kiCompleto()), textX, textY, white, false);
        }

        pose.popPose();
    }

    /** Red de seguridad si la reflection falla: una caja de tooltip vanilla, en espacio de pantalla normal. */
    private static void renderFallback(ScreenEvent.Render.Post event, GuiGraphics gfx, Font font,
                                        ClientKiAspectsCache.Snapshot snap) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§65 Aspectos del Ki"));
        lines.add(Component.literal(String.format(Locale.ROOT, "§bPresencia: §f%.0f%%", snap.presencia() * 100.0)));
        lines.add(Component.literal(String.format(Locale.ROOT, "§bKi de Combate: §f%.0f", snap.kiDeCombate())));
        lines.add(Component.literal(String.format(Locale.ROOT, "§bKi Completo: §f%.0f", snap.kiCompleto())));
        lines.add(Component.literal(String.format(Locale.ROOT, "§bPoder Real: §f%.0f", snap.poderReal())));
        lines.add(Component.literal(String.format(Locale.ROOT, "§bLímite Actual: §f%.0f", snap.limiteActual())));
        lines.add(Component.literal(String.format(Locale.ROOT, "§bPotencial Oculto: §f%.0f", snap.potencialOculto())));
        if (snap.kaiokenActive()) {
            lines.add(Component.literal(String.format(Locale.ROOT, "§cKaioken x%.0f (x%.2f efectivo)",
                    snap.kaiokenRawMultiplier(), snap.kaiokenEffectiveMultiplier())));
        }

        int x = event.getScreen().width - 166;
        int y = 20;
        // renderTooltip quiere List<? extends FormattedCharSequence>, no List<Component>
        // directo (eso lo descubrió el CI, no lo tenía verificado de antes).
        List<FormattedCharSequence> visualLines = lines.stream()
                .map(Component::getVisualOrderText)
                .toList();
        gfx.renderTooltip(font, visualLines, x, y);
    }
}
