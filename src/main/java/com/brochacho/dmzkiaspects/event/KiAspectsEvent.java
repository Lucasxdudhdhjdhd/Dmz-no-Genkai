package com.brochacho.dmzkiaspects.event;

import com.dragonminez.common.stats.techniques.KiAttackData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Eventos propios de DMZ Ki Aspects.
 *
 * Estos NO son eventos de DragonMineZ (esos son DMZEvent): son eventos que
 * este addon dispara sobre el bus de Forge para que otro código (una GUI,
 * otro addon, o una mixin de Fase 3 que reemplace el Scouter/Ki Sense de
 * DMZ) pueda reaccionar a cambios de Presencia sin acoplarse directamente
 * a KiAspectsCapability.
 */
public abstract class KiAspectsEvent extends Event {

    private final ServerPlayer player;

    protected KiAspectsEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * Se dispara cada vez que el valor de Presencia efectivamente cambia
     * (tras el clamp a 0.0-1.0). Útil para sincronizar HUD/cliente o para
     * que un scouter (Fase 3, con mixin) sepa cuándo refrescar su lectura.
     */
    public static class PresenceChangeEvent extends KiAspectsEvent {
        private final double oldPresence;
        private final double newPresence;

        public PresenceChangeEvent(ServerPlayer player, double oldPresence, double newPresence) {
            super(player);
            this.oldPresence = oldPresence;
            this.newPresence = newPresence;
        }

        public double getOldPresence() {
            return oldPresence;
        }

        public double getNewPresence() {
            return newPresence;
        }

        public double getDelta() {
            return newPresence - oldPresence;
        }
    }

    /**
     * Se dispara en cada tick que el jugador mantiene presionada la tecla
     * de Cargar Ki (C) mientras este addon está usando esa entrada para
     * subir/bajar la Presencia. "raising" indica la dirección: true = está
     * subiendo (mostrando más poder), false = está bajando (ocultándolo,
     * mientras agacha/sneak).
     */
    public static class KiChargeEvent extends KiAspectsEvent {
        private final boolean raisingPresence;

        public KiChargeEvent(ServerPlayer player, boolean raisingPresence) {
            super(player);
            this.raisingPresence = raisingPresence;
        }

        public boolean isRaisingPresence() {
            return raisingPresence;
        }
    }

    /**
     * Se dispara cuando el jugador EMPIEZA a lanzar una técnica de expulsión
     * (Kamehameha, Galick Gun, Final Flash, Spirit Bomb...). Sirve para
     * efectos de carga, sonidos o UI. Viene de DMZEvent.KiAttackCastEvent.
     */
    public static class ExpulsionCastEvent extends KiAspectsEvent {
        private final KiAttackData attack;

        public ExpulsionCastEvent(ServerPlayer player, KiAttackData attack) {
            super(player);
            this.attack = attack;
        }

        public KiAttackData getAttack() {
            return attack;
        }
    }

    /**
     * Se dispara cuando una técnica de expulsión SALE. Trae cuánto del Ki
     * Completo está sacando y el multiplicador de daño que se aplicará.
     * Los listeners pueden ajustar {@link #setDamageBonus(double)}
     * (por ejemplo 1.0 para anular el bonus, o subirlo con un pasivo propio).
     * Viene de DMZEvent.KiAttackFireEvent.
     */
    public static class ExpulsionFireEvent extends KiAspectsEvent {
        private final KiAttackData attack;
        private final double overchargeProgress;
        private final double outputFraction;
        private double damageBonus;

        public ExpulsionFireEvent(ServerPlayer player, KiAttackData attack,
                                  double overchargeProgress, double outputFraction, double damageBonus) {
            super(player);
            this.attack = attack;
            this.overchargeProgress = overchargeProgress;
            this.outputFraction = outputFraction;
            this.damageBonus = damageBonus;
        }

        public KiAttackData getAttack() {
            return attack;
        }

        /** 0.0 = sin sobrecarga, 1.0 = sobrecarga máxima de DMZ. */
        public double getOverchargeProgress() {
            return overchargeProgress;
        }

        /** Fracción del Ki Completo que saca esta técnica (0.0-1.0). */
        public double getOutputFraction() {
            return outputFraction;
        }

        /** Multiplicador sobre el daño de DMZ (1.0 = sin cambio). */
        public double getDamageBonus() {
            return damageBonus;
        }

        public void setDamageBonus(double damageBonus) {
            this.damageBonus = Math.max(0.0D, damageBonus);
        }
    }

    /**
     * Se dispara cuando el Límite Actual (Potencial) cambia: por ruptura de
     * límite (Poder Real lo alcanzó), por subir de nivel, por crecimiento
     * pasivo en el tiempo, o por un otorgamiento manual/especial
     * (ver {@link com.brochacho.dmzkiaspects.capability.KiAspectsCapability#grantPotentialFlat}
     * y {@code #grantPotentialPercent}).
     */
    public static class PotentialLimitChangeEvent extends KiAspectsEvent {
        private final double oldLimit;
        private final double newLimit;
        private final String reason;

        public PotentialLimitChangeEvent(ServerPlayer player, double oldLimit, double newLimit, String reason) {
            super(player);
            this.oldLimit = oldLimit;
            this.newLimit = newLimit;
            this.reason = reason;
        }

        public double getOldLimit() {
            return oldLimit;
        }

        public double getNewLimit() {
            return newLimit;
        }

        public double getDelta() {
            return newLimit - oldLimit;
        }

        /** "breakthrough", "level_up", "time", o la razón que se le haya pasado a un grant manual. */
        public String getReason() {
            return reason;
        }
    }

    /**
     * Se dispara cuando el Kaioken del jugador (grupo de stack "kaioken" de
     * DMZ) pasa de inactivo a activo, o cambia de intensidad (x2 -> x10,
     * etc.), o se desactiva. Trae la evaluación completa (multiplicador
     * crudo, multiplicador efectivo tras el beneficio reducido, y qué tan
     * peligroso es en este momento) para que un HUD o efecto visual pueda
     * reaccionar sin recalcular nada.
     */
    public static class KaiokenStateEvent extends KiAspectsEvent {
        private final boolean active;
        private final double rawMultiplier;
        private final double effectiveMultiplier;
        private final double benefitFactor;
        private final double dangerFactor;

        public KaiokenStateEvent(ServerPlayer player, boolean active, double rawMultiplier,
                                 double effectiveMultiplier, double benefitFactor, double dangerFactor) {
            super(player);
            this.active = active;
            this.rawMultiplier = rawMultiplier;
            this.effectiveMultiplier = effectiveMultiplier;
            this.benefitFactor = benefitFactor;
            this.dangerFactor = dangerFactor;
        }

        public boolean isActive() {
            return active;
        }

        /** El multiplicador tal cual lo eligió el jugador (x2, x10, ...). */
        public double getRawMultiplier() {
            return rawMultiplier;
        }

        /** El multiplicador que realmente se aplica al poder en uso, ya reducido si queda poco potencial. */
        public double getEffectiveMultiplier() {
            return effectiveMultiplier;
        }

        /** 0.0-1.0: qué fracción del extra prometido por el multiplicador se está obteniendo de verdad. */
        public double getBenefitFactor() {
            return benefitFactor;
        }

        /** 0.0-1.0: qué tan peligroso es sostener este Kaioken ahora mismo. */
        public double getDangerFactor() {
            return dangerFactor;
        }
    }

    /**
     * Se dispara cada vez que el Kaioken inflige un pulso de daño/drenaje
     * corporal por forzar el cuerpo más allá de lo seguro.
     */
    public static class KaiokenStrainEvent extends KiAspectsEvent {
        private final double rawMultiplier;
        private final double dangerFactor;
        private final float damageDealt;

        public KaiokenStrainEvent(ServerPlayer player, double rawMultiplier, double dangerFactor, float damageDealt) {
            super(player);
            this.rawMultiplier = rawMultiplier;
            this.dangerFactor = dangerFactor;
            this.damageDealt = damageDealt;
        }

        public double getRawMultiplier() {
            return rawMultiplier;
        }

        public double getDangerFactor() {
            return dangerFactor;
        }

        public float getDamageDealt() {
            return damageDealt;
        }
    }

    /**
     * Se dispara cuando un Zenkai (exclusivo Saiyan) convierte Potencial
     * Oculto en Poder Real permanente. limitGrowth es 0.0 salvo que la
     * herida haya sido extrema, en cuyo caso también empujó el Límite Actual.
     */
    public static class ZenkaiBoostEvent extends KiAspectsEvent {
        private final double oldPoderReal;
        private final double newPoderReal;
        private final double limitGrowth;
        private final boolean extreme;

        public ZenkaiBoostEvent(ServerPlayer player, double oldPoderReal, double newPoderReal,
                                double limitGrowth, boolean extreme) {
            super(player);
            this.oldPoderReal = oldPoderReal;
            this.newPoderReal = newPoderReal;
            this.limitGrowth = limitGrowth;
            this.extreme = extreme;
        }

        public double getOldPoderReal() {
            return oldPoderReal;
        }

        public double getNewPoderReal() {
            return newPoderReal;
        }

        public double getGain() {
            return newPoderReal - oldPoderReal;
        }

        /** > 0.0 si además empujó el Límite Actual por herida extrema. */
        public double getLimitGrowth() {
            return limitGrowth;
        }

        public boolean isExtreme() {
            return extreme;
        }
    }

    /**
     * Se dispara cuando un desbloqueo de potencial (Guru, Anciano Kaioshin,
     * u otro origen vía {@code KiAspectsCapability.unlockPotential}) convierte
     * Potencial Oculto en Poder Real de forma directa. A diferencia del
     * Zenkai, esto NUNCA toca el Límite Actual ni requiere estar malherido:
     * solo hace usable el potencial que ya existía.
     */
    public static class PotentialUnlockEvent extends KiAspectsEvent {
        private final double oldPoderReal;
        private final double newPoderReal;
        private final String source;

        public PotentialUnlockEvent(ServerPlayer player, double oldPoderReal, double newPoderReal, String source) {
            super(player);
            this.oldPoderReal = oldPoderReal;
            this.newPoderReal = newPoderReal;
            this.source = source;
        }

        public double getOldPoderReal() {
            return oldPoderReal;
        }

        public double getNewPoderReal() {
            return newPoderReal;
        }

        public double getGain() {
            return newPoderReal - oldPoderReal;
        }

        /** "guru", "old_kai", "comando_admin", etc. */
        public String getSource() {
            return source;
        }
    }
}
