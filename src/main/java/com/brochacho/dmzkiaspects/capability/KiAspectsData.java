package com.brochacho.dmzkiaspects.capability;

import com.brochacho.dmzkiaspects.calc.KiAspectsCalculator;
import com.brochacho.dmzkiaspects.config.KiAspectsConfig;
import com.dragonminez.common.stats.StatsData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Contenedor de datos de los 5 Aspectos del Ki.
 *
 * Relaciones (según la teoría del addon):
 *   Presencia        ⊆ Ki de Combate
 *   Ki de Combate    ⊆ Ki Completo
 *   Ki Completo      ⊆ Poder Real
 *   Poder Real       ≤ Límite Actual (Potencial)
 *   (Límite Actual - Poder Real) = Potencial Oculto
 *
 * Kaioken (multiplicador temporal y arriesgado sobre el poder en uso, ver
 * {@link com.brochacho.dmzkiaspects.kaioken.KaiokenTracker} y
 * {@link com.brochacho.dmzkiaspects.calc.KiAspectsCalculator#evaluateKaioken})
 * no persiste nada acá: es puramente momentáneo.
 *
 * Todo lo que sí toca el Potencial Oculto de forma permanente (Fase 6):
 *   - Zenkai (near-death, exclusivo Saiyan): convierte potencial en Poder
 *     Real y, si la herida fue extrema, también sube el Límite Actual.
 *     Ver {@link #applyZenkaiBoost} / {@code zenkaiBonusPower}.
 *   - Desbloqueos de potencial (Guru, Anciano Kaioshin, "Potential Unlock"):
 *     SOLO convierten potencial en Poder Real, nunca suben el Límite.
 *     Ver {@link #applyPotentialUnlock} / {@code unlockedPotentialPower}.
 *   - El Límite Actual sube lento y solo por: ruptura de límite (red de
 *     seguridad, ver {@link #applyBreakthroughIfNeeded}), entrenamiento
 *     extremo prolongado cerca del techo (Presión de Adaptación, ver
 *     {@link #tickAdaptationPressure}), o dominar por completo una
 *     transformación (ver {@link #applyMasteryBreakthroughIfNeeded}).
 */
public class KiAspectsData {

    // ---------------------------------------------------------------

    /** Cuánto sube/baja la Presencia por tick mientras se mantiene Cargar Ki (C). */
    public static final double PRESENCE_STEP_PER_TICK = 0.0025D; // ~0-100% en 20s

    private final Player player;

    /** 1. Ki de Combate: máximo poder cuerpo a cuerpo en la transformación actual. */
    private double kiDeCombate;

    /** 2. Presencia: 0.0 a 1.0, fracción del Ki de Combate que se "muestra" hacia afuera. */
    private double presencia;

    /** 3. Ki Completo: 100% del poder de la transformación actual. */
    private double kiCompleto;

    /** 4. Poder Real: poder de la transformación más fuerte ya desbloqueada. */
    private double poderReal;

    /** 5. Potencial / Límite Actual: techo alcanzable en el nivel/edad actual. */
    private double limiteActual;

    /** Bonus permanente al Poder Real por Zenkais disparados (potencial oculto ya convertido). Persistente. */
    private double zenkaiBonusPower;

    /** Game time (StatsData/level) del último Zenkai disparado, para el cooldown. */
    private long lastZenkaiTick = Long.MIN_VALUE / 2;

    /** Cuántos Zenkai disparó este jugador en total (informativo). */
    private int zenkaiTriggerCount;

    // --- Fase 6: corrección del Límite Actual / Potencial Oculto ---

    /**
     * Último Release Limit de DMZ visto (Resources#getReleaseLimit, 0-100),
     * para detectar cuándo Guru/Anciano Kaioshin otorgan un desbloqueo de
     * potencial. -1 = todavía no observado (evita regalar de golpe un
     * desbloqueo retroactivo si el personaje ya traía Release Limit alto
     * de antes de instalar este addon).
     */
    private int lastKnownReleaseLimit = -1;

    /** Bonus permanente al Poder Real por desbloqueos de potencial (Guru, Kaioshin, manual). Nunca toca el Límite. */
    private double unlockedPotentialPower;

    /** Presión de Adaptación acumulada (0.0-1.0) por entrenar en condiciones extremas cerca del Límite. */
    private double adaptationPressure;

    /** Claves "grupo:forma" que ya otorgaron su crecimiento de Límite por maestría máxima (una sola vez cada una). */
    private final Set<String> masteryRewardsGranted = new HashSet<>();

    /** Informativo: "grupo:forma" de la que sale el Poder Real (se recalcula, no se guarda). */
    private String strongestForm = "base:base";

    public KiAspectsData(Player player) {
        this.player = player;
        this.presencia = 1.0D; // por defecto el jugador no oculta su ki
    }

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public double getKiDeCombate() {
        return kiDeCombate;
    }

    public void setKiDeCombate(double kiDeCombate) {
        this.kiDeCombate = Math.max(0.0D, kiDeCombate);
    }

    public double getPresencia() {
        return presencia;
    }

    /** Fuerza el rango 0.0 - 1.0 (0% a 100% de Ki de Combate mostrado). */
    public void setPresencia(double presencia) {
        this.presencia = Math.min(1.0D, Math.max(0.0D, presencia));
    }

    public double getKiCompleto() {
        return kiCompleto;
    }

    public void setKiCompleto(double kiCompleto) {
        this.kiCompleto = Math.max(0.0D, kiCompleto);
    }

    public double getPoderReal() {
        return poderReal;
    }

    public void setPoderReal(double poderReal) {
        this.poderReal = Math.max(0.0D, poderReal);
    }

    public double getLimiteActual() {
        return limiteActual;
    }

    public void setLimiteActual(double limiteActual) {
        this.limiteActual = Math.max(0.0D, limiteActual);
    }

    /** Informativo: "grupo:forma" de la que sale el Poder Real. */
    public String getStrongestForm() {
        return strongestForm;
    }

    /** Cuánto del Poder Real actual viene de Zenkais ya disparados (potencial convertido, permanente). */
    public double getZenkaiBonusPower() {
        return zenkaiBonusPower;
    }

    public int getZenkaiTriggerCount() {
        return zenkaiTriggerCount;
    }

    /** Valor derivado: cuánto Potencial Oculto le queda al jugador. */
    public double getPotencialOculto() {
        return Math.max(0.0D, limiteActual - poderReal);
    }

    /** Valor derivado: qué fracción del Límite Actual ya está expresada como Poder Real (0.0-1.0+). */
    public double getPotencialUsadoFraccion() {
        return limiteActual <= 0.0D ? 0.0D : poderReal / limiteActual;
    }

    /** Valor derivado: Ki de Combate efectivamente detectable por un scouter/Ki Sense. */
    public double getKiDetectablePorScouter() {
        return kiDeCombate * presencia;
    }

    // ---------------------------------------------------------------
    // Cálculo base
    // ---------------------------------------------------------------

    /**
     * Recalcula Ki de Combate, Ki Completo, Poder Real y Límite Actual a
     * partir del StatsData de DragonMineZ. NO toca Presencia (la maneja
     * {@link KiAspectsCapability}). Se llama con StatChangeEvent,
     * FormChangeEvent, PlayerDataLoadEvent y como red de seguridad cada
     * 100 ticks.
     *
     * Fase 3 (fórmulas en {@link KiAspectsCalculator}):
     *   - Ki de Combate: (STR+SKP+PWR) escalados por la forma activa.
     *   - Ki Completo: 100% de la forma actual = Ki de Combate / fracción de combate.
     *   - Poder Real: la forma más fuerte DESBLOQUEADA, aunque no esté activa.
     *
     * Fase 4: el Límite Actual YA NO se recalcula acá (no es un derivado en
     * vivo de Poder Real). Es un valor persistente que solo sube por
     * ruptura de límite, nivel, tiempo o un otorgamiento manual —
     * ver {@link #applyBreakthroughIfNeeded()}, {@link #tickAdaptationPressure(boolean)},
     * {@link #applyMasteryBreakthroughIfNeeded(String, String)} y {@link KiAspectsCapability}.
     */
    public void recalculateFrom(StatsData stats) {
        if (stats == null) {
            return;
        }

        // 1. Ki de Combate (forma actual)
        setKiDeCombate(KiAspectsCalculator.combatKi(stats));

        // 2. Presencia: gestionada aparte, no se toca acá.

        // 3. Ki Completo (forma actual al 100%)
        setKiCompleto(KiAspectsCalculator.fullKi(this.kiDeCombate));

        // 4. Poder Real (mejor forma desbloqueada; nunca menor al Ki Completo actual)
        //    + el bonus permanente que el Zenkai haya convertido hasta ahora.
        KiAspectsCalculator.RealPower real = KiAspectsCalculator.realPower(stats, this.kiCompleto);
        setPoderReal(real.value() + zenkaiBonusPower);
        this.strongestForm = real.formGroup() + ":" + real.formName();

        // 5. Límite Actual: ver arriba, no se toca en este método.
    }

    // ---------------------------------------------------------------
    // Presencia: sincronización con Power Release + control por tecla
    // ---------------------------------------------------------------

    /**
     * Sincroniza la Presencia desde el Power Release actual de DMZ
     * (0-100 -> 0.0-1.0). Se usa una sola vez, al cargar/loguear al
     * jugador, para que este addon arranque "superpuesto" al valor que
     * DMZ ya tenía guardado en vez de resetearlo a 100%.
     */
    public void syncPresenceFromDmzRelease(int dmzPowerReleasePercent) {
        setPresencia(dmzPowerReleasePercent / 100.0D);
    }

    /**
     * Sube o baja la Presencia en un paso fijo ({@link #PRESENCE_STEP_PER_TICK}).
     *
     * @param raise true para subir (mostrar más poder), false para bajar (ocultarlo)
     * @return el delta realmente aplicado (puede ser menor al step si se saturó en 0.0 o 1.0)
     */
    public double stepPresence(boolean raise) {
        double before = this.presencia;
        double delta = raise ? PRESENCE_STEP_PER_TICK : -PRESENCE_STEP_PER_TICK;
        setPresencia(before + delta);
        return this.presencia - before;
    }

    /** Battle Power "visible": lo que un scouter/Ki Sense debería leer, según la Presencia. */
    public double getVisibleBattlePower() {
        return getKiDetectablePorScouter();
    }

    // ---------------------------------------------------------------
    // Potencial Oculto: crecimiento del Límite Actual (Fase 4)
    // ---------------------------------------------------------------

    /**
     * Ruptura de límite: si el Poder Real alcanzó o superó el Límite Actual
     * registrado (incluye el caso inicial, con Límite Actual en 0), el
     * Límite se expande a Poder Real * margen de ruptura. Así el jugador
     * nunca queda "por encima" de su propio techo, y cada vez que su Poder
     * Real crece de verdad (entrenamiento, nueva forma) el techo se corre
     * un poco más allá para dejar Potencial Oculto por descubrir.
     *
     * @return cuánto subió el Límite Actual (0.0 si no hubo ruptura)
     */
    public double applyBreakthroughIfNeeded() {
        if (poderReal <= limiteActual) {
            return 0.0D;
        }
        double before = limiteActual;
        setLimiteActual(poderReal * KiAspectsConfig.POTENTIAL_BREAKTHROUGH_MARGIN);
        return limiteActual - before;
    }

    // Nota Fase 6: el crecimiento por nivel de DMZ y por tiempo jugado que
    // había acá en la Fase 4 se retiró — la corrección de esta fase dice que
    // el Límite Actual solo debe subir por entrenamiento extremo prolongado
    // cerca del techo (ver tickAdaptationPressure) o por maestría máxima de
    // una forma (ver applyMasteryBreakthroughIfNeeded), más esta ruptura de
    // límite como red de seguridad.

    /**
     * Otorgamiento manual/especial: suma una cantidad fija al Límite Actual.
     * Pensado para que un evento especial, una quest, o un comando de admin
     * lo llamen vía {@code KiAspectsCapability.grantPotentialFlat(...)}.
     *
     * @return cuánto subió el Límite Actual (siempre == amount si amount > 0)
     */
    public double grantPotentialFlat(double amount) {
        if (amount <= 0.0D) {
            return 0.0D;
        }
        double before = limiteActual;
        setLimiteActual(limiteActual + amount);
        return limiteActual - before;
    }

    /** Igual que {@link #grantPotentialFlat(double)} pero como porcentaje del Límite Actual (ej. 0.10 = +10%). */
    public double grantPotentialPercent(double percent) {
        return grantPotentialFlat(limiteActual * percent);
    }

    /**
     * Fuerza el Límite Actual a un valor exacto (comandos de debug). Nunca
     * lo deja por debajo del Poder Real actual, para no dejar el estado en
     * una situación imposible según la teoría del addon.
     */
    public double setLimiteActualDebug(double value) {
        double before = limiteActual;
        setLimiteActual(Math.max(value, poderReal));
        return limiteActual - before;
    }

    // ---------------------------------------------------------------
    // Zenkai (Fase 5): conversión permanente de Potencial Oculto en
    // Poder Real, exclusiva Saiyan (el filtro de raza lo aplica
    // KiAspectsCapability antes de llamar acá).
    // ---------------------------------------------------------------

    /** Si ya pasó el cooldown desde el último Zenkai disparado. */
    public boolean canTriggerZenkai(long currentGameTime) {
        return currentGameTime - lastZenkaiTick >= KiAspectsConfig.ZENKAI_COOLDOWN_TICKS;
    }

    /** Resultado de disparar un Zenkai: cuánto Poder Real ganó y, si la herida fue extrema, cuánto subió el Límite Actual. */
    public record ZenkaiResult(double poderRealGain, double limitGrowth) {
        public static final ZenkaiResult NONE = new ZenkaiResult(0.0D, 0.0D);
    }

    /**
     * Dispara un Zenkai: convierte una fracción del Potencial Oculto actual
     * en Poder Real permanente (sumado a {@code zenkaiBonusPower}, que
     * sobrevive a todos los recálculos futuros). Si la herida fue extrema
     * (ver {@code extremeWound}), además empuja el Límite Actual hacia
     * arriba en una fracción del Potencial Oculto restante.
     *
     * No valida raza ni cooldown: eso lo hace la capability antes de llamar,
     * para mantener esta clase libre de dependencias del ciclo de vida del
     * jugador. Sí es seguro llamarlo varias veces: si no queda Potencial
     * Oculto, simplemente no hace nada y devuelve {@link ZenkaiResult#NONE}.
     */
    public ZenkaiResult applyZenkaiBoost(long currentGameTime, boolean extremeWound) {
        double available = getPotencialOculto();
        if (available <= 0.0D) {
            lastZenkaiTick = currentGameTime;
            return ZenkaiResult.NONE;
        }

        double gain = available * KiAspectsConfig.ZENKAI_CONVERSION_FRACTION;
        this.zenkaiBonusPower += gain;
        setPoderReal(this.poderReal + gain);
        this.lastZenkaiTick = currentGameTime;
        this.zenkaiTriggerCount++;

        double limitGrowth = 0.0D;
        if (extremeWound) {
            double remainingPotential = getPotencialOculto();
            limitGrowth = remainingPotential * KiAspectsConfig.ZENKAI_EXTREME_LIMIT_GROWTH_FRACTION;
            if (limitGrowth > 0.0D) {
                setLimiteActual(this.limiteActual + limitGrowth);
            }
        }

        return new ZenkaiResult(gain, limitGrowth);
    }

    // ---------------------------------------------------------------
    // Desbloqueos de potencial (Guru, Anciano Kaioshin, "Potential Unlock"):
    // SOLO convierten Potencial Oculto en Poder Real. Nunca tocan el Límite.
    // ---------------------------------------------------------------

    /**
     * Detecta si el Release Limit de DMZ (Resources#getReleaseLimit, 0-100)
     * subió desde la última vez que lo vimos —lo que en DMZ pasa cuando el
     * jugador habla con Guru/Anciano Kaioshin tras subir su skill "Potential
     * Unlock"— y, si es así, convierte una fracción del Potencial Oculto
     * restante en Poder Real permanente. Se llama en cada recálculo.
     *
     * @return cuánto Poder Real ganó (0.0 si el Release Limit no subió)
     */
    public double detectAndApplyPotentialUnlock(int currentReleaseLimit) {
        if (lastKnownReleaseLimit < 0) {
            // Primera vez que vemos a este jugador: solo fijamos la base.
            lastKnownReleaseLimit = currentReleaseLimit;
            return 0.0D;
        }
        if (currentReleaseLimit <= lastKnownReleaseLimit) {
            if (currentReleaseLimit != lastKnownReleaseLimit) {
                lastKnownReleaseLimit = currentReleaseLimit;
            }
            return 0.0D;
        }
        int pointsGained = currentReleaseLimit - lastKnownReleaseLimit;
        lastKnownReleaseLimit = currentReleaseLimit;

        double fraction = Math.min(1.0D, pointsGained * KiAspectsConfig.POTENTIAL_UNLOCK_FRACTION_PER_RELEASE_POINT);
        return applyPotentialUnlock(fraction);
    }

    /**
     * Convierte directamente una fracción del Potencial Oculto actual en
     * Poder Real permanente (sumado a {@code unlockedPotentialPower}), sin
     * tocar el Límite Actual. Pensado para ser llamado desde
     * {@link #detectAndApplyPotentialUnlock}, o manualmente desde
     * {@code KiAspectsCapability.unlockPotential(...)} para quests, comandos
     * de admin, u otros orígenes de "desbloqueo de potencial".
     *
     * @return cuánto Poder Real ganó (0.0 si no había Potencial Oculto disponible)
     */
    public double applyPotentialUnlock(double fraction) {
        double available = getPotencialOculto();
        if (available <= 0.0D || fraction <= 0.0D) {
            return 0.0D;
        }
        double gain = available * Math.min(1.0D, fraction);
        this.unlockedPotentialPower += gain;
        setPoderReal(this.poderReal + gain);
        return gain;
    }

    public double getUnlockedPotentialPower() {
        return unlockedPotentialPower;
    }

    // ---------------------------------------------------------------
    // Presión de Adaptación: entrenamiento extremo (gravedad/pesos altos,
    // ver GravityLogic#getTrainingZone de DMZ) prolongado y cerca del
    // Límite Actual. Es la vía "lenta y de verdad" para subir el Límite.
    // ---------------------------------------------------------------

    /**
     * Se llama una vez por tick de jugador con si en ESTE tick se cumplen
     * las condiciones de entrenamiento extremo (zona de gravedad alta Y
     * Potencial ya usado en su mayoría). Si se cumplen, acumula presión;
     * si no, la presión decae lentamente en vez de resetearse de golpe.
     * Al llenarse (1.0), el Límite Actual sube un poco y la presión se
     * reinicia — lo suficientemente lento como para sentirse ganado.
     *
     * @return cuánto subió el Límite Actual (0.0 la enorme mayoría de los ticks)
     */
    public double tickAdaptationPressure(boolean trainingUnderExtremeConditions) {
        if (trainingUnderExtremeConditions) {
            adaptationPressure = Math.min(1.0D, adaptationPressure + KiAspectsConfig.ADAPTATION_PRESSURE_PER_TICK);
        } else if (adaptationPressure > 0.0D) {
            adaptationPressure = Math.max(0.0D, adaptationPressure - KiAspectsConfig.ADAPTATION_DECAY_PER_TICK);
        }

        if (adaptationPressure < 1.0D) {
            return 0.0D;
        }

        adaptationPressure = 0.0D;
        double before = limiteActual;
        setLimiteActual(limiteActual + limiteActual * KiAspectsConfig.ADAPTATION_LIMIT_GROWTH_PERCENT);
        return limiteActual - before;
    }

    public double getAdaptationPressure() {
        return adaptationPressure;
    }

    // ---------------------------------------------------------------
    // Maestría máxima de una transformación: otra vía lenta y "ganada" de
    // hacer crecer el Límite Actual. Una sola vez por forma.
    // ---------------------------------------------------------------

    /**
     * Otorga el crecimiento de Límite Actual por dominar por completo una
     * forma (grupo:forma), una única vez por forma. Quien llama debe haber
     * confirmado ya que la forma está al máximo (Character#getFormMasteries
     * / FormMasteries#hasMaxMastery de DMZ); esta clase solo se encarga de
     * no otorgar el premio dos veces.
     *
     * @return cuánto subió el Límite Actual (0.0 si esta forma ya había otorgado su premio)
     */
    public double applyMasteryBreakthroughIfNeeded(String formGroup, String formName) {
        String key = formGroup + ":" + formName;
        if (!masteryRewardsGranted.add(key)) {
            return 0.0D;
        }
        double before = limiteActual;
        setLimiteActual(limiteActual + limiteActual * KiAspectsConfig.MASTERY_LIMIT_GROWTH_PERCENT);
        return limiteActual - before;
    }

    // ---------------------------------------------------------------
    // Persistencia / copia
    // ---------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("KiDeCombate", kiDeCombate);
        tag.putDouble("Presencia", presencia);
        tag.putDouble("KiCompleto", kiCompleto);
        tag.putDouble("PoderReal", poderReal);
        tag.putDouble("LimiteActual", limiteActual);
        tag.putDouble("ZenkaiBonusPower", zenkaiBonusPower);
        tag.putLong("LastZenkaiTick", lastZenkaiTick);
        tag.putInt("ZenkaiTriggerCount", zenkaiTriggerCount);
        tag.putInt("LastKnownReleaseLimit", lastKnownReleaseLimit);
        tag.putDouble("UnlockedPotentialPower", unlockedPotentialPower);
        tag.putDouble("AdaptationPressure", adaptationPressure);
        tag.putString("MasteryRewardsGranted", String.join(";", masteryRewardsGranted));
        return tag;
    }

    public void load(CompoundTag tag) {
        this.kiDeCombate = tag.getDouble("KiDeCombate");
        this.presencia = tag.contains("Presencia") ? tag.getDouble("Presencia") : 1.0D;
        this.kiCompleto = tag.getDouble("KiCompleto");
        this.poderReal = tag.getDouble("PoderReal");
        this.limiteActual = tag.getDouble("LimiteActual");
        this.zenkaiBonusPower = tag.getDouble("ZenkaiBonusPower");
        this.lastZenkaiTick = tag.contains("LastZenkaiTick") ? tag.getLong("LastZenkaiTick") : Long.MIN_VALUE / 2;
        this.zenkaiTriggerCount = tag.getInt("ZenkaiTriggerCount");
        this.lastKnownReleaseLimit = tag.contains("LastKnownReleaseLimit") ? tag.getInt("LastKnownReleaseLimit") : -1;
        this.unlockedPotentialPower = tag.getDouble("UnlockedPotentialPower");
        this.adaptationPressure = tag.getDouble("AdaptationPressure");
        this.masteryRewardsGranted.clear();
        String masteryStored = tag.getString("MasteryRewardsGranted");
        if (!masteryStored.isEmpty()) {
            this.masteryRewardsGranted.addAll(Arrays.asList(masteryStored.split(";")));
        }
    }

    public void copyFrom(KiAspectsData other) {
        this.kiDeCombate = other.kiDeCombate;
        this.presencia = other.presencia;
        this.kiCompleto = other.kiCompleto;
        this.poderReal = other.poderReal;
        this.limiteActual = other.limiteActual;
        this.zenkaiBonusPower = other.zenkaiBonusPower;
        this.lastZenkaiTick = other.lastZenkaiTick;
        this.zenkaiTriggerCount = other.zenkaiTriggerCount;
        this.lastKnownReleaseLimit = other.lastKnownReleaseLimit;
        this.unlockedPotentialPower = other.unlockedPotentialPower;
        this.adaptationPressure = other.adaptationPressure;
        this.masteryRewardsGranted.clear();
        this.masteryRewardsGranted.addAll(other.masteryRewardsGranted);
    }

    public Player getPlayer() {
        return player;
    }
}
