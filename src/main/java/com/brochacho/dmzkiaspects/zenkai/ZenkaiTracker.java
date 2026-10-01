package com.brochacho.dmzkiaspects.zenkai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado en memoria (no persistido) de la racha de vida baja que dispara el
 * Zenkai. No hace falta guardarlo entre sesiones: si el jugador se
 * desconecta a mitad de la racha, simplemente tiene que volver a pasar el
 * tiempo requerido malherido al reconectar. No es explotable.
 *
 * También guarda la fracción de vida MÍNIMA alcanzada durante la racha,
 * para distinguir una herida "extrema" (casi muerte real) de simplemente
 * cruzar el umbral por un momento.
 */
public final class ZenkaiTracker {

    /** Racha en curso para un jugador. */
    public record Streak(int ticks, double minHealthFraction) {
        public static final Streak NONE = new Streak(0, 1.0D);
    }

    private static final Map<UUID, Streak> STREAKS = new ConcurrentHashMap<>();

    private ZenkaiTracker() {
    }

    /** Registra un tick por debajo del umbral con la fracción de vida actual, y devuelve la racha actualizada. */
    public static Streak tick(UUID player, double healthFraction) {
        Streak previous = STREAKS.getOrDefault(player, Streak.NONE);
        Streak next = new Streak(previous.ticks() + 1, Math.min(previous.minHealthFraction(), healthFraction));
        STREAKS.put(player, next);
        return next;
    }

    public static Streak get(UUID player) {
        return STREAKS.getOrDefault(player, Streak.NONE);
    }

    /** Se llama cuando la vida vuelve a estar por encima del umbral, o tras disparar el Zenkai. */
    public static void reset(UUID player) {
        STREAKS.remove(player);
    }
}
