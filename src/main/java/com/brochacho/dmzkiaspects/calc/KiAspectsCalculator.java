package com.brochacho.dmzkiaspects.calc;

import com.brochacho.dmzkiaspects.config.KiAspectsConfig;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.character.Stats;
import com.dragonminez.common.stats.techniques.KiAttackData.KiType;
import com.dragonminez.common.util.TransformationsHelper;

import java.util.List;
import java.util.Map;

/**
 * Fórmulas de los aspectos del Ki, sin estado y sin efectos secundarios.
 * Toda la lectura de DragonMineZ pasa por acá, para que un cambio de API del
 * mod base se arregle en un único archivo.
 */
public final class KiAspectsCalculator {

    private KiAspectsCalculator() {
    }

    /** Resultado del cálculo de Poder Real: el valor y de qué forma sale. */
    public record RealPower(double value, String formGroup, String formName) {
    }

    // ------------------------------------------------------------------
    // Ki de Combate / Ki Completo (forma ACTUAL)
    // ------------------------------------------------------------------

    /**
     * Ki de Combate = STR + SKP + PWR, cada uno escalado por el multiplicador
     * de la forma activa. getFormMultiplier(stat) de DMZ devuelve 1.0 sin
     * forma activa y NO incluye formas apiladas (Kaioken, Ultimate) ni
     * efectos: eso es deliberado, porque en la teoría del addon esos son
     * accesos a potencial oculto (Fase 4), no parte del poder de la forma.
     */
    public static double combatKi(StatsData stats) {
        Stats s = stats.getStats();
        return s.getStrength() * stats.getFormMultiplier("STR")
                + s.getStrikePower() * stats.getFormMultiplier("SKP")
                + s.getKiPower() * stats.getFormMultiplier("PWR");
    }

    /** Ki Completo = 100% del poder de la forma actual; el Ki de Combate es una parte de él. */
    public static double fullKi(double combatKi) {
        return combatKi / KiAspectsConfig.COMBAT_KI_FRACTION;
    }

    // ------------------------------------------------------------------
    // Poder Real (forma más fuerte DESBLOQUEADA, no necesariamente la activa)
    // ------------------------------------------------------------------

    /**
     * Recorre todos los grupos de formas de la raza y, con la lógica oficial
     * de DMZ (TransformationsHelper.getUnlockedForms), evalúa cada forma
     * desbloqueada con la misma fórmula del Ki Completo. Se queda con la
     * mayor. La forma base cuenta siempre.
     *
     * Usa los multiplicadores de la config de cada forma (sin ajuste de
     * maestría, que DMZ solo calcula para la forma activa); por eso el
     * resultado nunca baja del Ki Completo de la forma activa, que sí lo
     * incluye. Así se cumple siempre Ki Completo <= Poder Real.
     */
    public static RealPower realPower(StatsData stats, double currentFullKi) {
        Stats s = stats.getStats();
        int str = s.getStrength();
        int skp = s.getStrikePower();
        int pwr = s.getKiPower();

        RealPower best = new RealPower(fullKi(str + skp + pwr), "base", "base");

        String race = stats.getCharacter().getRaceName();
        Map<String, FormConfig> groups = ConfigManager.getAllFormsForRace(race);
        if (groups != null) {
            for (String groupKey : groups.keySet()) {
                List<FormConfig.FormData> unlocked = TransformationsHelper.getUnlockedForms(stats, race, groupKey);
                if (unlocked == null) {
                    continue;
                }
                for (FormConfig.FormData form : unlocked) {
                    double combat = str * multiplierOrOne(form.getStrMultiplier())
                            + skp * multiplierOrOne(form.getSkpMultiplier())
                            + pwr * multiplierOrOne(form.getPwrMultiplier());
                    double full = fullKi(combat);
                    if (full > best.value()) {
                        best = new RealPower(full, groupKey, String.valueOf(form.getName()));
                    }
                }
            }
        }

        if (currentFullKi > best.value()) {
            String group = stats.getCharacter().getActiveFormGroup();
            String name = stats.getCharacter().getActiveForm();
            best = new RealPower(currentFullKi, group == null ? "base" : group, name == null ? "base" : name);
        }
        return best;
    }

    private static double multiplierOrOne(Double value) {
        return value == null ? 1.0D : value;
    }

    // ------------------------------------------------------------------
    // Técnicas de Ki: cuánto del Ki Completo pueden sacar
    // ------------------------------------------------------------------

    public static boolean isExpulsion(KiType type) {
        return KiAspectsConfig.EXPULSION_TYPES.contains(type);
    }

    /**
     * Progreso de sobrecarga 0.0-1.0 a partir del chargeMultiplier de DMZ
     * (1.0 = carga normal, hasta 1.75 = sobrecarga máxima).
     */
    public static double overchargeProgress(float chargeMultiplier) {
        double range = KiAspectsConfig.OVERCHARGE_MAX_MULTIPLIER - 1.0D;
        return clamp01((chargeMultiplier - 1.0D) / range);
    }

    /**
     * Fracción del Ki Completo (0.0-1.0) que una técnica puede sacar.
     *  - Normal: solo la parte de combate (COMBAT_KI_FRACTION).
     *  - Expulsión: parte de esa base y sube hacia 100% conforme se sobrecarga.
     */
    public static double outputFraction(KiType type, double overchargeProgress) {
        double base = KiAspectsConfig.COMBAT_KI_FRACTION;
        if (!isExpulsion(type)) {
            return base;
        }
        return base + (1.0D - base) * clamp01(overchargeProgress);
    }

    /**
     * Multiplicador de daño a aplicar SOBRE el daño que DMZ ya calculó:
     * 1.0 en cualquier técnica normal o expulsión sin sobrecarga, y crece
     * hasta Ki Completo / Ki de Combate en sobrecarga máxima (con tope).
     */
    public static double damageBonus(KiType type, double overchargeProgress) {
        if (!isExpulsion(type)) {
            return 1.0D;
        }
        double bonus = outputFraction(type, overchargeProgress) / KiAspectsConfig.COMBAT_KI_FRACTION;
        return Math.min(bonus, KiAspectsConfig.MAX_EXPULSION_DAMAGE_BONUS);
    }

    private static double clamp01(double v) {
        return Math.max(0.0D, Math.min(1.0D, v));
    }

    // ------------------------------------------------------------------
    // Kaioken (Fase 5): multiplicador real, con beneficio y peligro
    // escalados por cuánto Potencial Oculto queda y qué tan cerca está
    // el jugador de su Límite Actual.
    // ------------------------------------------------------------------

    /** Resultado de evaluar un tick de Kaioken: cuánto beneficio real da y qué tan peligroso es. */
    public record KaiokenEvaluation(double rawMultiplier, double effectiveMultiplier,
                                    double benefitFactor, double dangerFactor, double dangerMultiplier) {
    }

    /**
     * Parsea el multiplicador desde el nombre de la forma de stack de DMZ
     * (ej. "x2", "x20", "x100" -> 2.0, 20.0, 100.0). Si no se puede leer,
     * asume 1.0 (sin efecto) en vez de romper.
     */
    public static double parseKaiokenMultiplier(String stackForm) {
        if (stackForm == null || stackForm.isEmpty()) {
            return 1.0D;
        }
        String digits = stackForm.toLowerCase(java.util.Locale.ROOT).replace("x", "").trim();
        try {
            double value = Double.parseDouble(digits);
            return value >= 1.0D ? value : 1.0D;
        } catch (NumberFormatException e) {
            return 1.0D;
        }
    }

    /**
     * Evalúa el Kaioken para un tick dado el estado actual de los 5 Aspectos.
     *
     * - benefitFactor: qué fracción del "extra" del multiplicador se
     *   consigue de verdad. 1.0 con margen amplio de Potencial Oculto,
     *   cae hacia KAIOKEN_MIN_BENEFIT_FACTOR (nunca a 0: no se bloquea)
     *   cuando casi no queda potencial.
     * - dangerFactor (0-1) combina cuánto Potencial Oculto queda (como
     *   fracción del Límite Actual) y qué tan cerca está el Poder Real
     *   del Límite Actual; alimenta dangerMultiplier, que escala tanto
     *   la velocidad de acumulación de desgaste como el daño del pulso.
     */
    public static KaiokenEvaluation evaluateKaioken(String stackForm, double poderReal,
                                                     double limiteActual, double potencialOculto) {
        double rawMultiplier = parseKaiokenMultiplier(stackForm);

        double marginFraction = limiteActual <= 0.0D ? 0.0D : clamp01(potencialOculto / limiteActual);
        double proximityToLimit = limiteActual <= 0.0D ? 1.0D : clamp01(poderReal / limiteActual);

        double benefitFactor = KiAspectsConfig.KAIOKEN_MIN_BENEFIT_FACTOR
                + (1.0D - KiAspectsConfig.KAIOKEN_MIN_BENEFIT_FACTOR)
                * clamp01(marginFraction / KiAspectsConfig.KAIOKEN_FULL_BENEFIT_MARGIN);
        double effectiveMultiplier = 1.0D + (rawMultiplier - 1.0D) * benefitFactor;

        double dangerFactor = clamp01((1.0D - marginFraction) * 0.5D + proximityToLimit * 0.5D);
        double dangerMultiplier = 1.0D + dangerFactor * (KiAspectsConfig.KAIOKEN_MAX_DANGER_MULTIPLIER - 1.0D);

        return new KaiokenEvaluation(rawMultiplier, effectiveMultiplier, benefitFactor, dangerFactor, dangerMultiplier);
    }

    /** Cuánto desgaste (hacia KAIOKEN_STRAIN_DAMAGE_THRESHOLD) se acumula en un tick con esta evaluación. */
    public static double kaiokenStrainPerTick(KaiokenEvaluation eval) {
        return KiAspectsConfig.KAIOKEN_STRAIN_BASE * (eval.rawMultiplier() - 1.0D) * eval.dangerMultiplier();
    }

    /** Daño (fracción de vida máxima) de un pulso de sobrecarga, ya con el tope de seguridad aplicado. */
    public static double kaiokenPulseDamageFraction(KaiokenEvaluation eval) {
        double base = KiAspectsConfig.KAIOKEN_DAMAGE_BASE_PERCENT_MAX_HEALTH
                * (eval.rawMultiplier() - 1.0D) / (KiAspectsConfig.KAIOKEN_DAMAGE_REFERENCE_MULTIPLIER - 1.0D)
                * eval.dangerMultiplier();
        return Math.min(base, KiAspectsConfig.KAIOKEN_MAX_DAMAGE_PERCENT_MAX_HEALTH);
    }
}
