package com.brochacho.dmzkiaspects.capability;

import com.brochacho.dmzkiaspects.DMZKiAspectsMod;
import com.brochacho.dmzkiaspects.calc.KiAspectsCalculator;
import com.brochacho.dmzkiaspects.config.KiAspectsConfig;
import com.brochacho.dmzkiaspects.event.KiAspectsEvent;
import com.brochacho.dmzkiaspects.kaioken.KaiokenTracker;
import com.brochacho.dmzkiaspects.network.KiAspectsNetwork;
import com.brochacho.dmzkiaspects.network.SyncKiAspectsS2C;
import com.brochacho.dmzkiaspects.technique.ExpulsionTracker;
import com.brochacho.dmzkiaspects.zenkai.ZenkaiTracker;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.server.util.GravityLogic;
import com.dragonminez.common.stats.techniques.KiAttackData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;

/**
 * Registro, ciclo de vida y sincronización de KiAspectsCapability.
 *
 * El ciclo de vida (registro, adjuntar a Player, clonar en muerte) está
 * calcado del patrón de com.dragonminez.common.stats.StatsCapability.
 *
 * La sincronización con el sistema de stats de DMZ se hace escuchando
 * los eventos públicos que DMZ ya dispara (DMZEvent), en vez de mixins:
 *   - DMZEvent.StatChangeEvent  -> cambió una stat base (fuerza, ki, etc.)
 *   - DMZEvent.FormChangeEvent  -> el jugador transformó/destransformó
 *   - DMZEvent.PlayerDataLoadEvent -> se cargaron los datos del jugador
 *
 * En cualquiera de esos casos, recalculamos los 5 Aspectos del Ki a partir
 * del StatsData actual del jugador.
 */
@Mod.EventBusSubscriber(modid = DMZKiAspectsMod.MODID)
public class KiAspectsCapability {

    public static final Capability<KiAspectsData> INSTANCE =
            CapabilityManager.get(new CapabilityToken<>() {});

    private KiAspectsCapability() {
    }

    // ---------------------------------------------------------------
    // Registro / ciclo de vida (igual patrón que StatsCapability)
    // ---------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(KiAspectsData.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            KiAspectsProvider provider = new KiAspectsProvider(player);
            event.addCapability(KiAspectsProvider.ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Solo copiamos datos si es un respawn real (muerte), no en el clone
        // "falso" que dispara Forge al volver del End.
        if (!event.isWasDeath()) {
            return;
        }
        get(event.getOriginal()).ifPresent(oldData ->
                get(event.getEntity()).ifPresent(newData -> newData.copyFrom(oldData)));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            recalculate(serverPlayer);
            syncPresenceFromDmz(serverPlayer);
            syncToClient(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            recalculate(serverPlayer);
        }
    }

    /**
     * Cada tick del jugador:
     *  1. Si está manteniendo Cargar Ki (C), sube o baja la Presencia
     *     (agachado = bajar/ocultar, de pie = subir/mostrar) y empuja el
     *     resultado al Power Release real de DMZ, para que el resto del
     *     mod (vuelo, HUD, etc.) vea el mismo número.
     *  2. Acumula Presión de Adaptación si está entrenando en condiciones
     *     extremas (zona de gravedad alta de DMZ) y ya cerca de su Límite
     *     Actual — la única vía "de verdad" para hacer crecer el Límite,
     *     junto con dominar por completo una forma (ver recalculate()).
     *  3. Si tiene el Kaioken de DMZ activo, evalúa beneficio/peligro y
     *     aplica pulsos de daño si se está forzando demasiado.
     *  4. Vigila si está malherido para el Zenkai (exclusivo Saiyan).
     *  5. Cada 100 ticks (5s), recalcula Ki de Combate/Completo/Real/Límite
     *     como red de seguridad por si algo cambió sin pasar por un
     *     DMZEvent que ya estemos escuchando.
     *  6. Cada 4 ticks (5 veces por segundo), manda al cliente una foto de
     *     los 5 Aspectos para el HUD y el panel del menú de Stats (V).
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        handleKiChargePresence(serverPlayer);
        handleAdaptationPressure(serverPlayer);
        handleKaioken(serverPlayer);
        handleZenkaiWatch(serverPlayer);

        if (serverPlayer.tickCount % 100 == 0) {
            recalculate(serverPlayer);
        }
        if (serverPlayer.tickCount % 4 == 0) {
            syncToClient(serverPlayer);
        }
    }

    /** Manda al cliente la foto actual de los 5 Aspectos + estado de Kaioken, para el HUD y el menú de Stats. */
    private static void syncToClient(ServerPlayer player) {
        get(player).ifPresent(ki -> KiAspectsNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncKiAspectsS2C(ki, KaiokenTracker.get(player.getUUID()))));
    }

    /**
     * Presión de Adaptación (Fase 6): mientras el jugador entrena en la zona
     * de gravedad "Pushing Hard"/"Overloaded" de DMZ (GravityLogic) Y ya usa
     * la mayoría de su Límite Actual, acumula presión; si no, decae despacio.
     * Al llenarse, el Límite sube un poco — lento a propósito.
     */
    private static void handleAdaptationPressure(ServerPlayer player) {
        get(player).ifPresent(ki -> {
            int zone = GravityLogic.getTrainingZone(player);
            boolean trainingExtreme = zone >= KiAspectsConfig.ADAPTATION_MIN_ZONE
                    && ki.getPotencialUsadoFraccion() >= KiAspectsConfig.ADAPTATION_MIN_USAGE_FRACTION;

            double oldLimit = ki.getLimiteActual();
            double delta = ki.tickAdaptationPressure(trainingExtreme);
            if (delta > 0.0D) {
                postLimitChange(player, oldLimit, ki.getLimiteActual(), "adaptation_training");
            }
        });
    }

    /**
     * Usa Status#isChargingKi() de DMZ (activado por la tecla "Cargar Ki",
     * por defecto C) como disparador para subir/bajar la Presencia de este
     * addon, sin necesidad de un keybind propio ni de mixins:
     *   - Cargando Ki + de pie   -> sube Presencia (mostrar más poder)
     *   - Cargando Ki + agachado -> baja Presencia (ocultar poder)
     */
    private static void handleKiChargePresence(ServerPlayer player) {
        LazyOptional<StatsData> statsOpt = player.getCapability(StatsCapability.INSTANCE);
        StatsData stats = statsOpt.orElse(null);
        if (stats == null || !stats.getStatus().isChargingKi()) {
            return;
        }

        get(player).ifPresent(ki -> {
            boolean raising = !player.isShiftKeyDown();

            MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.KiChargeEvent(player, raising));

            double oldPresence = ki.getPresencia();
            ki.stepPresence(raising);
            double newPresence = ki.getPresencia();

            if (newPresence != oldPresence) {
                MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.PresenceChangeEvent(player, oldPresence, newPresence));
                pushPresenceToDmz(player, stats, ki.getPresencia());
            }
        });
    }

    /** Inicializa nuestra Presencia a partir del Power Release que DMZ ya tenía guardado. */
    private static void syncPresenceFromDmz(ServerPlayer player) {
        LazyOptional<StatsData> statsOpt = player.getCapability(StatsCapability.INSTANCE);
        statsOpt.ifPresent(stats -> get(player).ifPresent(ki ->
                ki.syncPresenceFromDmzRelease(stats.getResources().getPowerRelease())));
    }

    /** Escribe nuestra Presencia de vuelta en el Power Release real de DMZ (0.0-1.0 -> 0-100). */
    private static void pushPresenceToDmz(ServerPlayer player, StatsData stats, double presencia) {
        Resources resources = stats.getResources();
        resources.setRelease((int) Math.round(presencia * 100.0D));
    }

    // ---------------------------------------------------------------
    // Sincronización con StatsCapability / eventos de DragonMineZ
    // ---------------------------------------------------------------

    @SubscribeEvent
    public static void onDmzStatChange(DMZEvent.StatChangeEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
            recalculate(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onDmzFormChange(DMZEvent.FormChangeEvent event) {
        recalculate(event.getPlayer());
    }

    @SubscribeEvent
    public static void onDmzPlayerDataLoad(DMZEvent.PlayerDataLoadEvent event) {
        recalculate(event.getPlayer());
        syncPresenceFromDmz(event.getPlayer());
    }

    // ---------------------------------------------------------------
    // Técnicas de expulsión (Fase 3)
    // ---------------------------------------------------------------

    /** Empieza a lanzarse una técnica: si es de expulsión, avisamos con nuestro propio evento. */
    @SubscribeEvent
    public static void onDmzKiAttackCast(DMZEvent.KiAttackCastEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        KiAttackData attack = event.getKiAttack();
        if (KiAspectsCalculator.isExpulsion(attack.getKiType())) {
            MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.ExpulsionCastEvent(player, attack));
        }
    }

    /**
     * Sale una técnica. Calculamos cuánto del Ki Completo saca según el tipo
     * y la sobrecarga de DMZ (chargeMultiplier), dejamos que otros listeners
     * ajusten el bonus, y guardamos el contexto para aplicarlo al impactar.
     */
    @SubscribeEvent
    public static void onDmzKiAttackFire(DMZEvent.KiAttackFireEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        KiAttackData attack = event.getKiAttack();
        KiAttackData.KiType type = attack.getKiType();

        if (!KiAspectsCalculator.isExpulsion(type)) {
            // Una técnica normal reemplaza a la expulsión anterior como "última".
            ExpulsionTracker.clear(player.getUUID());
            return;
        }

        double overcharge = KiAspectsCalculator.overchargeProgress(event.getChargeMultiplier());
        double fraction = KiAspectsCalculator.outputFraction(type, overcharge);
        double bonus = KiAspectsConfig.APPLY_KI_COMPLETO_DAMAGE_BONUS
                ? KiAspectsCalculator.damageBonus(type, overcharge)
                : 1.0D;

        // Si está en Kaioken, el multiplicador también se aplica al poder que
        // esta expulsión está sacando de su Ki Completo (ver KaiokenTracker).
        KaiokenTracker.State kaioken = KaiokenTracker.get(player.getUUID());
        if (kaioken.active()) {
            bonus *= kaioken.evaluation().effectiveMultiplier();
        }

        KiAspectsEvent.ExpulsionFireEvent fireEvent =
                new KiAspectsEvent.ExpulsionFireEvent(player, attack, overcharge, fraction, bonus);
        MinecraftForge.EVENT_BUS.post(fireEvent);

        ExpulsionTracker.begin(player, new ExpulsionTracker.Context(
                attack.getId(), type, overcharge, fraction, fireEvent.getDamageBonus(),
                player.level().getGameTime() + KiAspectsConfig.EXPULSION_CONTEXT_TICKS));
    }

    /**
     * Impacto de Ki: si viene de una expulsión reciente del atacante, se
     * escala el daño por el bonus de Ki Completo. Los golpes melee y strike
     * no se tocan.
     */
    @SubscribeEvent
    public static void onDmzDamageModify(DMZEvent.DamageModifyEvent event) {
        if (event.getSourceType() != DMZEvent.DamageSourceType.KI) {
            return;
        }
        if (!(event.getAttacker() instanceof ServerPlayer player)) {
            return;
        }
        ExpulsionTracker.Context ctx = ExpulsionTracker.get(player);
        if (ctx != null && ctx.damageBonus() != 1.0D) {
            event.setAmount(event.getAmount() * ctx.damageBonus());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ExpulsionTracker.clear(event.getEntity().getUUID());
        KaiokenTracker.clear(event.getEntity().getUUID());
        ZenkaiTracker.reset(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------
    // Kaioken (Fase 5): multiplicador real sobre el poder en uso, con
    // beneficio reducido y daño creciente según cuánto Potencial Oculto
    // quede y qué tan cerca esté el jugador de su Límite Actual.
    // ---------------------------------------------------------------

    @SubscribeEvent
    public static void onDmzStackFormChange(DMZEvent.StackFormChangeEvent event) {
        if (!isKaiokenGroup(event.getNewGroup()) && !isKaiokenGroup(event.getOldGroup())) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        boolean nowActive = isKaiokenGroup(event.getNewGroup()) && !event.isUntransform();

        if (!nowActive) {
            KaiokenTracker.State previous = KaiokenTracker.get(player.getUUID());
            KaiokenTracker.clear(player.getUUID());
            if (previous.active()) {
                MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.KaiokenStateEvent(
                        player, false, previous.evaluation().rawMultiplier(), 1.0D, 1.0D, 0.0D));
            }
        }
        // La activación en sí se reporta desde handleKaioken() en el primer
        // tick con datos reales (ahí ya tenemos la evaluación completa).
    }

    private static boolean isKaiokenGroup(String group) {
        return group != null && group.equalsIgnoreCase(KiAspectsConfig.KAIOKEN_STACK_GROUP);
    }

    /**
     * Mientras el grupo de stack activo sea "kaioken", evalúa beneficio y
     * peligro cada tick (ver KiAspectsCalculator#evaluateKaioken), acumula
     * desgaste, y descarga pulsos de daño real cuando se cruza el umbral.
     * El multiplicador de combate normal (STR/SKP/PWR) ya lo aplica DMZ
     * mismo vía su propio getStackFormMultiplier; acá solo modelamos el
     * costo corporal y el reporte de los 5 Aspectos, que DMZ no tiene.
     */
    private static void handleKaioken(ServerPlayer player) {
        LazyOptional<StatsData> statsOpt = player.getCapability(StatsCapability.INSTANCE);
        StatsData stats = statsOpt.orElse(null);
        if (stats == null) {
            return;
        }
        String group = stats.getCharacter().getActiveStackFormGroup();
        if (!isKaiokenGroup(group)) {
            return; // clear() ya lo maneja onDmzStackFormChange / el chequeo de más arriba
        }
        String form = stats.getCharacter().getActiveStackForm();

        get(player).ifPresent(ki -> {
            boolean wasActive = KaiokenTracker.isActive(player.getUUID());

            KiAspectsCalculator.KaiokenEvaluation eval = KiAspectsCalculator.evaluateKaioken(
                    form, ki.getPoderReal(), ki.getLimiteActual(), ki.getPotencialOculto());

            if (!wasActive) {
                MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.KaiokenStateEvent(
                        player, true, eval.rawMultiplier(), eval.effectiveMultiplier(),
                        eval.benefitFactor(), eval.dangerFactor()));
            }

            double strainDelta = KiAspectsCalculator.kaiokenStrainPerTick(eval);
            KaiokenTracker.State state = KaiokenTracker.update(player.getUUID(), eval, strainDelta);

            if (state.strain() >= KiAspectsConfig.KAIOKEN_STRAIN_DAMAGE_THRESHOLD) {
                double damageFraction = KiAspectsCalculator.kaiokenPulseDamageFraction(eval);
                float damage = (float) (player.getMaxHealth() * damageFraction);
                player.hurt(player.level().damageSources().magic(), damage);
                KaiokenTracker.consumeStrain(player.getUUID(), KiAspectsConfig.KAIOKEN_STRAIN_DAMAGE_THRESHOLD);
                MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.KaiokenStrainEvent(
                        player, eval.rawMultiplier(), eval.dangerFactor(), damage));
            }
        });
    }

    // ---------------------------------------------------------------
    // Zenkai (Fase 5): exclusivo Saiyan. Sobrevivir malherido convierte
    // Potencial Oculto en Poder Real permanente; si la herida fue
    // extrema, también empuja el Límite Actual.
    // ---------------------------------------------------------------

    private static void handleZenkaiWatch(ServerPlayer player) {
        LazyOptional<StatsData> statsOpt = player.getCapability(StatsCapability.INSTANCE);
        StatsData stats = statsOpt.orElse(null);
        if (stats == null || !isZenkaiRace(stats.getCharacter().getRaceName())) {
            return;
        }

        float maxHealth = player.getMaxHealth();
        float healthFraction = maxHealth <= 0.0F ? 1.0F : player.getHealth() / maxHealth;

        if (player.isDeadOrDying() || healthFraction > KiAspectsConfig.ZENKAI_HEALTH_THRESHOLD) {
            ZenkaiTracker.reset(player.getUUID());
            return;
        }

        ZenkaiTracker.Streak streak = ZenkaiTracker.tick(player.getUUID(), healthFraction);
        if (streak.ticks() < KiAspectsConfig.ZENKAI_REQUIRED_TICKS) {
            return;
        }
        ZenkaiTracker.reset(player.getUUID());

        get(player).ifPresent(ki -> {
            if (!ki.canTriggerZenkai(player.level().getGameTime())) {
                return;
            }

            boolean extreme = streak.minHealthFraction() <= KiAspectsConfig.ZENKAI_EXTREME_HEALTH_THRESHOLD;
            double oldPoderReal = ki.getPoderReal();
            double oldLimit = ki.getLimiteActual();

            KiAspectsData.ZenkaiResult result = ki.applyZenkaiBoost(player.level().getGameTime(), extreme);
            if (result.poderRealGain() <= 0.0D) {
                return;
            }

            player.heal((float) (maxHealth * KiAspectsConfig.ZENKAI_HEAL_FRACTION));
            MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.ZenkaiBoostEvent(
                    player, oldPoderReal, ki.getPoderReal(), result.limitGrowth(), extreme));

            if (result.limitGrowth() > 0.0D) {
                postLimitChange(player, oldLimit, ki.getLimiteActual(), "zenkai_extreme");
            }
        });
    }

    private static boolean isZenkaiRace(String raceName) {
        return raceName != null && raceName.equalsIgnoreCase(KiAspectsConfig.ZENKAI_RACE);
    }

    // ---------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------

    /**
     * Recalcula los 5 Aspectos del jugador a partir de su StatsData actual
     * de DMZ, y de paso evalúa las dos vías de crecimiento del Límite que
     * no dependen del tick (ruptura de límite y maestría máxima) y la
     * detección de desbloqueos de potencial (Guru/Anciano Kaioshin).
     */
    public static void recalculate(@Nonnull ServerPlayer player) {
        LazyOptional<StatsData> statsOpt = player.getCapability(StatsCapability.INSTANCE);
        StatsData stats = statsOpt.orElse(null);
        if (stats == null) {
            return;
        }

        get(player).ifPresent(ki -> {
            ki.recalculateFrom(stats);
            applyBreakthroughSafety(player, ki);
            applyPotentialUnlockIfDetected(player, ki, stats);
            applyMasteryGrowthIfDetected(player, ki, stats);
        });
    }

    /**
     * Red de seguridad (NO es una vía "oficial" de crecimiento): si el Poder
     * Real recién calculado desde los stats crudos de DMZ superó el Límite
     * Actual registrado —por ejemplo, un salto grande de stats por fuera de
     * este addon—, el Límite se expande lo justo para no dejar el estado en
     * una situación imposible (Poder Real > Límite Actual).
     */
    private static void applyBreakthroughSafety(ServerPlayer player, KiAspectsData ki) {
        double oldLimit = ki.getLimiteActual();
        double delta = ki.applyBreakthroughIfNeeded();
        if (delta > 0.0D) {
            postLimitChange(player, oldLimit, ki.getLimiteActual(), "breakthrough_safety");
        }
    }

    /**
     * Guru / Anciano Kaioshin (skill "Potential Unlock" de DMZ) se detectan
     * como una subida del Release Limit (Resources#getReleaseLimit). Según
     * la teoría del addon, esto SOLO convierte Potencial Oculto en Poder
     * Real — nunca toca el Límite Actual.
     */
    private static void applyPotentialUnlockIfDetected(ServerPlayer player, KiAspectsData ki, StatsData stats) {
        double oldPoderReal = ki.getPoderReal();
        double gained = ki.detectAndApplyPotentialUnlock(stats.getResources().getReleaseLimit());
        if (gained > 0.0D) {
            MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.PotentialUnlockEvent(
                    player, oldPoderReal, ki.getPoderReal(), "guru_o_anciano_kaioshin"));
        }
    }

    /**
     * Dominar por completo la forma activa (FormMasteries#hasMaxMastery de
     * DMZ contra el máximo configurado de esa forma) es, junto con la
     * Presión de Adaptación, la otra vía "ganada" de subir el Límite Actual.
     * Se otorga una única vez por forma (ver KiAspectsData#masteryRewardsGranted).
     */
    private static void applyMasteryGrowthIfDetected(ServerPlayer player, KiAspectsData ki, StatsData stats) {
        var character = stats.getCharacter();
        if (!character.hasActiveForm()) {
            return;
        }
        var activeFormData = character.getActiveFormData();
        if (activeFormData == null) {
            return;
        }
        Double maxMastery = activeFormData.getMaxMastery();
        if (maxMastery == null) {
            return;
        }
        String group = character.getActiveFormGroup();
        String form = character.getActiveForm();
        if (group == null || form == null) {
            return;
        }
        if (!character.getFormMasteries().hasMaxMastery(group, form, maxMastery)) {
            return;
        }

        double oldLimit = ki.getLimiteActual();
        double delta = ki.applyMasteryBreakthroughIfNeeded(group, form);
        if (delta > 0.0D) {
            postLimitChange(player, oldLimit, ki.getLimiteActual(), "mastery:" + group + ":" + form);
        }
    }

    /**
     * API pública para que otro código (una quest, un comando de admin, una
     * integración más fina con Guru/Anciano Kaioshin) otorgue un desbloqueo
     * de potencial directamente. Igual que la detección automática: SOLO
     * convierte Potencial Oculto en Poder Real, nunca toca el Límite Actual.
     */
    public static double unlockPotential(ServerPlayer player, double fraction, String source) {
        return get(player).map(ki -> {
            double oldPoderReal = ki.getPoderReal();
            double gained = ki.applyPotentialUnlock(fraction);
            if (gained > 0.0D) {
                MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.PotentialUnlockEvent(player, oldPoderReal, ki.getPoderReal(), source));
            }
            return gained;
        }).orElse(0.0D);
    }

    /**
     * API pública para que otro código (quests, comandos, eventos
     * especiales) otorgue crecimiento real del Límite Actual, sin pasar por
     * la Presión de Adaptación ni la maestría. Dispara PotentialLimitChangeEvent
     * igual que las demás vías de crecimiento.
     */
    public static double grantPotentialFlat(ServerPlayer player, double amount, String reason) {
        return get(player).map(ki -> {
            double oldLimit = ki.getLimiteActual();
            double delta = ki.grantPotentialFlat(amount);
            if (delta > 0.0D) {
                postLimitChange(player, oldLimit, ki.getLimiteActual(), reason);
            }
            return delta;
        }).orElse(0.0D);
    }

    /** Igual que {@link #grantPotentialFlat} pero en porcentaje del Límite Actual (ej. 0.10 = +10%). */
    public static double grantPotentialPercent(ServerPlayer player, double percent, String reason) {
        return get(player).map(ki -> {
            double oldLimit = ki.getLimiteActual();
            double delta = ki.grantPotentialPercent(percent);
            if (delta > 0.0D) {
                postLimitChange(player, oldLimit, ki.getLimiteActual(), reason);
            }
            return delta;
        }).orElse(0.0D);
    }

    /** Fuerza el Límite Actual a un valor exacto (comandos de debug/QA). */
    public static double setPotentialLimitDebug(ServerPlayer player, double value) {
        return get(player).map(ki -> {
            double oldLimit = ki.getLimiteActual();
            double delta = ki.setLimiteActualDebug(value);
            if (delta != 0.0D) {
                postLimitChange(player, oldLimit, ki.getLimiteActual(), "debug_set");
            }
            return delta;
        }).orElse(0.0D);
    }

    private static void postLimitChange(ServerPlayer player, double oldLimit, double newLimit, String reason) {
        MinecraftForge.EVENT_BUS.post(new KiAspectsEvent.PotentialLimitChangeEvent(player, oldLimit, newLimit, reason));
    }

    public static LazyOptional<KiAspectsData> get(Entity entity) {
        return entity.getCapability(INSTANCE);
    }
}
