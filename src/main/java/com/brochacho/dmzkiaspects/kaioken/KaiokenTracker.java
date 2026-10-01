package com.brochacho.dmzkiaspects.kaioken;

import com.brochacho.dmzkiaspects.calc.KiAspectsCalculator.KaiokenEvaluation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado en memoria (no persistido: es un efecto de combate momentáneo, no
 * uno de los 5 Aspectos) del Kaioken activo de cada jugador.
 *
 * Guarda la última evaluación (multiplicador crudo/efectivo, peligro) para
 * que el comando de debug y el resto del addon la lean sin recalcular, y el
 * desgaste acumulado que dispara los pulsos de daño.
 */
public final class KaiokenTracker {

    /** Snapshot del estado de Kaioken de un jugador en un tick dado. */
    public record State(boolean active, KaiokenEvaluation evaluation, double strain) {
        public static final State INACTIVE = new State(false, null, 0.0D);
    }

    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private KaiokenTracker() {
    }

    /** Actualiza la evaluación de este tick y devuelve el nuevo desgaste acumulado (sin descontar). */
    public static State update(UUID player, KaiokenEvaluation evaluation, double strainDelta) {
        double previousStrain = ACTIVE.getOrDefault(player, State.INACTIVE).strain();
        State next = new State(true, evaluation, previousStrain + strainDelta);
        ACTIVE.put(player, next);
        return next;
    }

    /** Descuenta el umbral del desgaste acumulado tras un pulso de daño, conservando el resto. */
    public static void consumeStrain(UUID player, double amount) {
        State current = ACTIVE.get(player);
        if (current == null) {
            return;
        }
        ACTIVE.put(player, new State(current.active(), current.evaluation(), Math.max(0.0D, current.strain() - amount)));
    }

    public static State get(UUID player) {
        return ACTIVE.getOrDefault(player, State.INACTIVE);
    }

    public static boolean isActive(UUID player) {
        return get(player).active();
    }

    /** Se llama cuando el Kaioken se desactiva (StackFormChangeEvent de untransform, logout, etc). */
    public static void clear(UUID player) {
        ACTIVE.remove(player);
    }
}
