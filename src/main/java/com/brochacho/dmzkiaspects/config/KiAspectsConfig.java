package com.brochacho.dmzkiaspects.config;

import com.dragonminez.common.stats.techniques.KiAttackData.KiType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Constantes de balance del addon, centralizadas para poder pasarlas a un
 * ForgeConfigSpec en una fase futura sin tocar la lógica.
 */
public final class KiAspectsConfig {

    private KiAspectsConfig() {
    }

    /**
     * Qué fracción del Ki Completo representa el Ki de Combate (0 < x <= 1).
     * Ki Completo = Ki de Combate / COMBAT_KI_FRACTION.
     * Con 0.65, una técnica normal usa como máximo el 65% del poder total
     * de la forma actual; el 35% restante solo se accede con expulsión.
     */
    public static final double COMBAT_KI_FRACTION = 0.65D;

    /**
     * Tipos de técnica de Ki considerados "de expulsión": las que pueden
     * sacar el Ki Completo (Kamehameha, Galick Gun, Final Flash = WAVE/BEAM;
     * Spirit Bomb, Big Bang = GIANT_BALL; Final Explosion = EXPLOSION).
     * Las demás (bolas chicas/medianas, láser, disco, ráfaga, escudo, área)
     * usan solo una parte.
     */
    public static final Set<KiType> EXPULSION_TYPES = Collections.unmodifiableSet(
            EnumSet.of(KiType.WAVE, KiType.BEAM, KiType.GIANT_BALL, KiType.EXPLOSION));

    /** Si true, las expulsiones sobrecargadas reciben el bonus de daño por Ki Completo. */
    public static final boolean APPLY_KI_COMPLETO_DAMAGE_BONUS = true;

    /**
     * Tope de seguridad del bonus de daño (multiplicador sobre el daño que
     * DMZ ya calculó). Evita que un Ki de Combate muy bajo frente a un Ki
     * Completo alto genere daños absurdos por configuración errónea.
     */
    public static final double MAX_EXPULSION_DAMAGE_BONUS = 1.6D;

    /**
     * Ventana (en ticks) durante la que los impactos de Ki del jugador se
     * atribuyen a su última expulsión. Cubre el vuelo del proyectil y los
     * ticks de daño de un rayo sostenido.
     */
    public static final int EXPULSION_CONTEXT_TICKS = 100;

    /** Sobrecarga máxima de DMZ como multiplicador (KiAttackData.OVERCHARGE_MAX_PERCENT = 175). */
    public static final double OVERCHARGE_MAX_MULTIPLIER = 1.75D;

    // ------------------------------------------------------------------
    // Potencial Oculto / Límite Actual (Fase 4; vías de crecimiento
    // corregidas en Fase 6 más abajo — el crecimiento por nivel y por
    // tiempo jugado que hubo acá en la Fase 4 se retiró).
    // ------------------------------------------------------------------

    /**
     * Margen de aire entre Poder Real y Límite Actual cada vez que el
     * Límite se "rompe" (Poder Real lo alcanza o lo supera): el nuevo
     * Límite queda en Poder Real * este margen. Se usa tanto para el
     * primer cálculo (Límite arranca en 0) como para esta red de seguridad;
     * ya NO es una vía de crecimiento "oficial" desde la Fase 6 (ver más
     * abajo Presión de Adaptación y maestría máxima).
     */
    public static final double POTENTIAL_BREAKTHROUGH_MARGIN = 1.15D;

    // ------------------------------------------------------------------
    // Kaioken (Fase 5, rediseñado): SÍ es un multiplicador real sobre el
    // poder que se está usando en ese momento (Ki de Combate en combate
    // normal, Ki Completo en una expulsión), y se apila arriba de
    // cualquier transformación activa. El costo es daño/drenaje que
    // escala con el multiplicador y se dispara mucho más fuerte cuando
    // queda poco Potencial Oculto o el jugador está cerca de su Límite
    // Actual. Nunca se bloquea su uso: en el peor caso es solo muy caro
    // y poco rentable.
    // ------------------------------------------------------------------

    /** Grupo de stack form de DMZ que identifica al Kaioken ("race.dragonminez.stack.group.kaioken"). */
    public static final String KAIOKEN_STACK_GROUP = "kaioken";

    /**
     * Cuánto Potencial Oculto (como fracción del Límite Actual) hace falta
     * para obtener el 100% del beneficio del multiplicador. Por debajo de
     * esto, el beneficio se reduce linealmente hasta KAIOKEN_MIN_BENEFIT_FACTOR.
     */
    public static final double KAIOKEN_FULL_BENEFIT_MARGIN = 0.50D;

    /**
     * Piso del beneficio del multiplicador cuando no queda Potencial Oculto:
     * nunca cae a "sin efecto" (no se bloquea), pero rinde mucho menos.
     * Ej. 0.25 = incluso sin margen, se conserva un 25% del extra prometido.
     */
    public static final double KAIOKEN_MIN_BENEFIT_FACTOR = 0.25D;

    /**
     * Tope del factor de peligro (ver dangerFactor en KiAspectsCalculator):
     * a 0 margen y a 100% de Poder Real sobre el Límite, el desgaste y el
     * daño se multiplican por esto respecto al caso "seguro".
     */
    public static final double KAIOKEN_MAX_DANGER_MULTIPLIER = 8.0D;

    /** Fuerza base con la que se acumula el desgaste por tick, antes de aplicar el multiplicador de peligro. */
    public static final double KAIOKEN_STRAIN_BASE = 0.002D;

    /** Cuando el desgaste acumulado llega a esto, el cuerpo recibe un pulso de daño y el contador se descuenta. */
    public static final double KAIOKEN_STRAIN_DAMAGE_THRESHOLD = 1.0D;

    /** Daño base por pulso (multiplicador x2, margen seguro), como fracción de la vida máxima. */
    public static final double KAIOKEN_DAMAGE_BASE_PERCENT_MAX_HEALTH = 0.01D;

    /** Multiplicador de referencia contra el que se escala el daño base (DMZ llega hasta x100). */
    public static final double KAIOKEN_DAMAGE_REFERENCE_MULTIPLIER = 100.0D;

    /** Tope de seguridad: ningún pulso de Kaioken puede quitar más que esta fracción de la vida máxima de una vez. */
    public static final double KAIOKEN_MAX_DAMAGE_PERCENT_MAX_HEALTH = 0.12D;

    // ------------------------------------------------------------------
    // Zenkai (Fase 5, rediseñado): exclusivo Saiyan. Sobrevivir malherido
    // convierte una parte del Potencial Oculto en Poder Real de forma
    // permanente, y si la herida fue extrema también empuja un poco el
    // Límite Actual hacia arriba. Es la vía "correcta" y permanente, a
    // diferencia del préstamo temporal y arriesgado del Kaioken.
    // ------------------------------------------------------------------

    /** Raza de DMZ para la que aplica el Zenkai de este addon (Character#getRaceName()). */
    public static final String ZENKAI_RACE = "saiyan";

    /** Fracción de vida máxima por debajo de la cual el cuerpo empieza a "adaptarse". */
    public static final double ZENKAI_HEALTH_THRESHOLD = 0.15D;

    /** Ticks seguidos por debajo del umbral de vida necesarios para disparar el Zenkai (160 = 8s). */
    public static final int ZENKAI_REQUIRED_TICKS = 160;

    /** Ticks mínimos entre dos disparos de Zenkai, para que no se pueda abusar (6000 = 5 min). */
    public static final long ZENKAI_COOLDOWN_TICKS = 6000L;

    /** Fracción del Potencial Oculto restante que se convierte en Poder Real permanente por disparo. */
    public static final double ZENKAI_CONVERSION_FRACTION = 0.15D;

    /** Curación otorgada al dispararse, como fracción de la vida máxima (el cuerpo también sana al adaptarse). */
    public static final double ZENKAI_HEAL_FRACTION = 0.30D;

    /**
     * Si la vida mínima alcanzada durante la racha por debajo del umbral fue
     * igual o menor a esta fracción (herida "extrema"), el Zenkai también
     * empuja el Límite Actual hacia arriba, además de convertir potencial en
     * Poder Real.
     */
    public static final double ZENKAI_EXTREME_HEALTH_THRESHOLD = 0.05D;

    /** Cuánto sube el Límite Actual en una herida extrema, como fracción del Potencial Oculto restante. */
    public static final double ZENKAI_EXTREME_LIMIT_GROWTH_FRACTION = 0.05D;

    // ------------------------------------------------------------------
    // Fase 6 — corrección del Límite Actual / Potencial Oculto:
    //   - Guru / Anciano Kaioshin / "Potential Unlock": NO suben el Límite,
    //     solo convierten Potencial Oculto ya existente en Poder Real.
    //   - El Límite Actual sube lento, solo por entrenamiento extremo
    //     prolongado estando ya cerca del techo, o por dominar por completo
    //     una transformación (maestría máxima).
    // ------------------------------------------------------------------

    /**
     * Fracción del Potencial Oculto restante que se convierte en Poder Real
     * por cada punto de Release Limit que Guru/Anciano Kaioshin otorguen
     * (Resources#getReleaseLimit(), 0-100, vía el skill "Potential Unlock"
     * de DMZ). Ej. con 0.01, un desbloqueo de +20 puntos convierte 20% del
     * Potencial Oculto restante en Poder Real permanente. NO toca el Límite.
     */
    public static final double POTENTIAL_UNLOCK_FRACTION_PER_RELEASE_POINT = 0.01D;

    /**
     * Zona de entrenamiento mínima de DMZ (GravityLogic#getTrainingZone,
     * 0=None, 1=Comfortable, 2=Ideal, 3=Pushing Hard, 4=Overloaded) para que
     * cuente como "entrenamiento extremo" hacia la Presión de Adaptación.
     */
    public static final int ADAPTATION_MIN_ZONE = 3;

    /** Fracción mínima de Potencial usado (Poder Real / Límite Actual) para que el entrenamiento cuente. */
    public static final double ADAPTATION_MIN_USAGE_FRACTION = 0.85D;

    /** Cuánto sube la Presión de Adaptación (0.0-1.0) por tick mientras se entrena extremo y cerca del techo. */
    public static final double ADAPTATION_PRESSURE_PER_TICK = 1.0D / 72000.0D; // ~1h continua para llenarla

    /** Cuánto baja la Presión de Adaptación por tick cuando no se cumplen las condiciones. Mucho más lento que la subida. */
    public static final double ADAPTATION_DECAY_PER_TICK = 1.0D / 432000.0D; // ~6h para vaciarse del todo

    /** Cuánto sube el Límite Actual (como fracción de sí mismo) cada vez que la Presión de Adaptación se llena. */
    public static final double ADAPTATION_LIMIT_GROWTH_PERCENT = 0.04D;

    /** Cuánto sube el Límite Actual (como fracción de sí mismo) la primera vez que se domina por completo una forma. */
    public static final double MASTERY_LIMIT_GROWTH_PERCENT = 0.05D;
}
