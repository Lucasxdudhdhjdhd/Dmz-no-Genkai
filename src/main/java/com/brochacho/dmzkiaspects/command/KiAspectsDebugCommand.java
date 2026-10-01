package com.brochacho.dmzkiaspects.command;

import com.brochacho.dmzkiaspects.capability.KiAspectsCapability;
import com.brochacho.dmzkiaspects.capability.KiAspectsData;
import com.brochacho.dmzkiaspects.config.KiAspectsConfig;
import com.brochacho.dmzkiaspects.kaioken.KaiokenTracker;
import com.brochacho.dmzkiaspects.technique.ExpulsionTracker;
import com.brochacho.dmzkiaspects.zenkai.ZenkaiTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * /dmzkiaspects                                  -> tus 5 Aspectos
 * /dmzkiaspects <jugador>                        -> los de otro jugador (permiso 2)
 * /dmzkiaspects potencial [jugador]              -> detalle de Potencial Oculto / Límite Actual
 * /dmzkiaspects potencial <jugador> grant <cant> -> otorga Límite Actual en cantidad fija (permiso 2)
 * /dmzkiaspects potencial <jugador> grantpercent <pct> -> otorga Límite Actual en % (permiso 2, pct en 0-100)
 * /dmzkiaspects potencial <jugador> set <valor>  -> fuerza el Límite Actual (permiso 2, debug/QA)
 * /dmzkiaspects kaioken [jugador]                -> estado actual del Kaioken (multiplicador, beneficio, peligro)
 * /dmzkiaspects zenkai [jugador]                 -> Zenkai: disparos, bonus permanente, cooldown restante
 * /dmzkiaspects debug <jugador> recalcular       -> fuerza un recálculo inmediato (permiso 2)
 * /dmzkiaspects debug <jugador> set kicombate|kicompleto|poderreal <valor> -> fuerza un valor (permiso 2)
 * /dmzkiaspects debug <jugador> set presencia <0-100> -> fuerza la Presencia como % (permiso 2)
 */
public class KiAspectsDebugCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzkiaspects")
                .executes(ctx -> showFor(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> showFor(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador"))))
                .then(Commands.literal("potencial")
                        .executes(ctx -> showPotencial(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> showPotencial(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador")))
                                .then(Commands.literal("grant")
                                        .then(Commands.argument("cantidad", DoubleArgumentType.doubleArg(0.0))
                                                .executes(ctx -> grant(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "jugador"),
                                                        DoubleArgumentType.getDouble(ctx, "cantidad")))))
                                .then(Commands.literal("grantpercent")
                                        .then(Commands.argument("porcentaje", DoubleArgumentType.doubleArg(0.0, 1000.0))
                                                .executes(ctx -> grantPercent(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "jugador"),
                                                        DoubleArgumentType.getDouble(ctx, "porcentaje")))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("valor", DoubleArgumentType.doubleArg(0.0))
                                                .executes(ctx -> setLimit(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "jugador"),
                                                        DoubleArgumentType.getDouble(ctx, "valor")))))))
                .then(Commands.literal("kaioken")
                        .executes(ctx -> showKaioken(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> showKaioken(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador")))))
                .then(Commands.literal("zenkai")
                        .executes(ctx -> showZenkai(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> showZenkai(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador")))))
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.literal("recalcular")
                                        .executes(ctx -> debugRecalculate(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador"))))
                                .then(Commands.literal("set")
                                        .then(Commands.literal("kicombate")
                                                .then(Commands.argument("valor", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(ctx -> debugSet(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "jugador"), "kicombate",
                                                                DoubleArgumentType.getDouble(ctx, "valor")))))
                                        .then(Commands.literal("presencia")
                                                .then(Commands.argument("porcentaje", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                        .executes(ctx -> debugSet(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "jugador"), "presencia",
                                                                DoubleArgumentType.getDouble(ctx, "porcentaje")))))
                                        .then(Commands.literal("kicompleto")
                                                .then(Commands.argument("valor", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(ctx -> debugSet(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "jugador"), "kicompleto",
                                                                DoubleArgumentType.getDouble(ctx, "valor")))))
                                        .then(Commands.literal("poderreal")
                                                .then(Commands.argument("valor", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(ctx -> debugSet(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "jugador"), "poderreal",
                                                                DoubleArgumentType.getDouble(ctx, "valor")))))))));
    }

    private static int showFor(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        KiAspectsData data = data(target);

        ExpulsionTracker.Context exp = ExpulsionTracker.get(target);
        String expulsion = exp == null
                ? "ninguna activa"
                : String.format(Locale.ROOT, "%s (%s) -> %.0f%% del Ki Completo, daño x%.2f",
                exp.techniqueId(), exp.type().name(), exp.outputFraction() * 100.0, exp.damageBonus());

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] §f%s\n" +
                        "§b1. Ki de Combate: §f%.2f\n" +
                        "§b2. Presencia: §f%.1f%%  §7(Battle Power visible: %.2f)\n" +
                        "§b3. Ki Completo: §f%.2f\n" +
                        "§b4. Poder Real: §f%.2f  §7(forma: %s)\n" +
                        "§b5. Límite Actual: §f%.2f  §7(potencial oculto: %.2f, %.1f%% usado)\n" +
                        "§7Última expulsión: %s\n" +
                        "§7Detalle de potencial: /dmzkiaspects potencial%s",
                target.getName().getString(),
                data.getKiDeCombate(),
                data.getPresencia() * 100.0,
                data.getVisibleBattlePower(),
                data.getKiCompleto(),
                data.getPoderReal(),
                data.getStrongestForm(),
                data.getLimiteActual(),
                data.getPotencialOculto(),
                data.getPotencialUsadoFraccion() * 100.0,
                expulsion,
                source.hasPermission(2) ? " " + target.getGameProfile().getName() : ""
        )), false);

        return 1;
    }

    private static int showPotencial(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        KiAspectsData data = data(target);

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] Potencial de %s\n" +
                        "§b4. Poder Real:      §f%.2f  §7(forma: %s)\n" +
                        "§b5. Límite Actual:   §f%.2f\n" +
                        "§bPotencial Oculto:   §f%.2f\n" +
                        "§bUsado:              §f%.1f%%\n" +
                        "§bPresión de Adaptación: §f%.0f%%  §7(entrenar extremo con ≥%.0f%% usado la llena)\n" +
                        "§bDe desbloqueos (Guru/Kaioshin): §f%.2f  §7|  De Zenkai: §f%.2f  §7(x%d disparos)\n" +
                        "§7El Límite sube lento: entrenamiento extremo prolongado cerca del techo, o\n" +
                        "§7dominar por completo una forma (+%.0f%%). Guru/Kaioshin NUNCA suben el Límite,\n" +
                        "§7solo hacen usable el Potencial Oculto que ya tenías.",
                target.getName().getString(),
                data.getPoderReal(), data.getStrongestForm(),
                data.getLimiteActual(),
                data.getPotencialOculto(),
                data.getPotencialUsadoFraccion() * 100.0,
                data.getAdaptationPressure() * 100.0, KiAspectsConfig.ADAPTATION_MIN_USAGE_FRACTION * 100.0,
                data.getUnlockedPotentialPower(), data.getZenkaiBonusPower(), data.getZenkaiTriggerCount(),
                KiAspectsConfig.MASTERY_LIMIT_GROWTH_PERCENT * 100.0
        )), false);

        return 1;
    }

    private static int grant(CommandSourceStack source, ServerPlayer target, double cantidad) {
        double delta = KiAspectsCapability.grantPotentialFlat(target, cantidad, "comando_admin");
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] §fLímite Actual de %s +%.2f (nuevo: %.2f)",
                target.getName().getString(), delta, data(target).getLimiteActual())), true);
        return 1;
    }

    private static int grantPercent(CommandSourceStack source, ServerPlayer target, double porcentajeInput) {
        double delta = KiAspectsCapability.grantPotentialPercent(target, porcentajeInput / 100.0D, "comando_admin");
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] §fLímite Actual de %s +%.1f%% (+%.2f, nuevo: %.2f)",
                target.getName().getString(), porcentajeInput, delta, data(target).getLimiteActual())), true);
        return 1;
    }

    private static int setLimit(CommandSourceStack source, ServerPlayer target, double valor) {
        KiAspectsCapability.setPotentialLimitDebug(target, valor);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] §fLímite Actual de %s fijado en %.2f",
                target.getName().getString(), data(target).getLimiteActual())), true);
        return 1;
    }

    private static int showKaioken(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        KaiokenTracker.State state = KaiokenTracker.get(target.getUUID());

        if (!state.active()) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "§6[DMZ Ki Aspects] §f%s no tiene Kaioken activo.",
                    target.getName().getString())), false);
            return 1;
        }

        var eval = state.evaluation();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] Kaioken de %s\n" +
                        "§bMultiplicador elegido: §fx%.0f\n" +
                        "§bMultiplicador efectivo: §fx%.2f  §7(%.0f%% del beneficio prometido)\n" +
                        "§bPeligro: §f%.0f%%  §7(desgaste: %.0f%% hasta el próximo pulso de daño)\n" +
                        "§7El daño escala con el multiplicador elegido y crece fuerte si queda poco\n" +
                        "§7Potencial Oculto o estás cerca de tu Límite Actual. No se bloquea: en el\n" +
                        "§7peor caso rinde poco y duele mucho.",
                target.getName().getString(),
                eval.rawMultiplier(),
                eval.effectiveMultiplier(), eval.benefitFactor() * 100.0,
                eval.dangerFactor() * 100.0, state.strain() * 100.0
        )), false);

        return 1;
    }

    private static int showZenkai(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        KiAspectsData data = data(target);
        ZenkaiTracker.Streak streak = ZenkaiTracker.get(target.getUUID());
        long gameTime = target.level().getGameTime();
        boolean onCooldown = !data.canTriggerZenkai(gameTime);

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] Zenkai de %s  §7(exclusivo Saiyan)\n" +
                        "§bDisparos totales: §f%d\n" +
                        "§bBonus permanente a Poder Real: §f%.2f\n" +
                        "§bEstado: §f%s\n" +
                        "§bRacha de vida baja actual: §f%d ticks  §7(mínima vida vista: %.0f%%)\n" +
                        "§7Se dispara tras %d ticks seguidos bajo %.0f%% de vida; convierte %.0f%% del\n" +
                        "§7Potencial Oculto en Poder Real permanente, y si la vida mínima fue ≤%.0f%%\n" +
                        "§7también empuja el Límite Actual.",
                target.getName().getString(),
                data.getZenkaiTriggerCount(),
                data.getZenkaiBonusPower(),
                onCooldown ? "en cooldown" : "listo",
                streak.ticks(), streak.minHealthFraction() * 100.0,
                KiAspectsConfig.ZENKAI_REQUIRED_TICKS, KiAspectsConfig.ZENKAI_HEALTH_THRESHOLD * 100.0,
                KiAspectsConfig.ZENKAI_CONVERSION_FRACTION * 100.0,
                KiAspectsConfig.ZENKAI_EXTREME_HEALTH_THRESHOLD * 100.0
        )), false);

        return 1;
    }

    /**
     * Fuerza uno de los valores base para pruebas rápidas de balance. No
     * toca Límite Actual (para eso está "potencial set"): un valor forzado
     * acá puede autocorregirse parcialmente en el próximo recálculo (por
     * ejemplo, la ruptura de límite de seguridad si el Poder Real forzado
     * supera el Límite), así que es una herramienta de "probar ahora
     * mismo", no un valor permanentemente fijo.
     */
    private static int debugSet(CommandSourceStack source, ServerPlayer target, String aspecto, double valor) throws CommandSyntaxException {
        KiAspectsData data = data(target);
        double aplicado = valor;
        switch (aspecto) {
            case "kicombate" -> data.setKiDeCombate(valor);
            case "presencia" -> {
                data.setPresencia(valor / 100.0D);
                aplicado = data.getPresencia() * 100.0D;
            }
            case "kicompleto" -> data.setKiCompleto(valor);
            case "poderreal" -> data.setPoderReal(valor);
            default -> throw new IllegalArgumentException("Aspecto de debug desconocido: " + aspecto);
        }

        final double mostrar = aplicado;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] §f%s: %s = %.2f  §7(puede autocorregirse en el próximo recálculo)",
                target.getName().getString(), aspecto, mostrar)), true);
        return 1;
    }

    /** Fuerza un recálculo inmediato desde el StatsData actual de DMZ, sin esperar al próximo DMZEvent o tick 100. */
    private static int debugRecalculate(CommandSourceStack source, ServerPlayer target) {
        KiAspectsCapability.recalculate(target);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§6[DMZ Ki Aspects] §fRecalculado para %s.", target.getName().getString())), true);
        return 1;
    }

    private static KiAspectsData data(ServerPlayer target) {
        return KiAspectsCapability.get(target)
                .orElseThrow(() -> new IllegalStateException("El jugador no tiene KiAspectsData adjunta."));
    }
}

