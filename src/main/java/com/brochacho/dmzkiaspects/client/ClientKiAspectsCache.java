package com.brochacho.dmzkiaspects.client;

/**
 * Última foto de los 5 Aspectos (y del estado de Kaioken) del jugador local
 * que llegó del servidor. Solo para lectura desde el HUD y el panel del
 * menú de Stats; nunca se escribe desde el cliente salvo al recibir un
 * SyncKiAspectsS2C.
 */
public final class ClientKiAspectsCache {

    public record Snapshot(double kiDeCombate, double presencia, double kiCompleto, double poderReal,
                           double limiteActual, double potencialOculto,
                           boolean kaiokenActive, double kaiokenRawMultiplier,
                           double kaiokenEffectiveMultiplier, double kaiokenDangerFactor) {

        public static final Snapshot EMPTY =
                new Snapshot(0, 1.0, 0, 0, 0, 0, false, 1.0, 1.0, 0.0);

        public double potencialUsadoFraccion() {
            return limiteActual <= 0.0D ? 0.0D : poderReal / limiteActual;
        }
    }

    private static volatile Snapshot latest = Snapshot.EMPTY;

    private ClientKiAspectsCache() {
    }

    public static Snapshot get() {
        return latest;
    }

    public static void update(Snapshot snapshot) {
        latest = snapshot;
    }
}
