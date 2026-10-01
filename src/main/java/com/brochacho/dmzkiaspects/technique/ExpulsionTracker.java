package com.brochacho.dmzkiaspects.technique;

import com.dragonminez.common.stats.techniques.KiAttackData.KiType;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recuerda, por jugador, la última técnica de expulsión que lanzó.
 *
 * Por qué existe: DMZEvent.DamageModifyEvent no dice qué técnica causó el
 * daño, solo que el tipo de fuente es KI. Para saber si ese impacto viene
 * de una expulsión sobrecargada, guardamos el contexto al dispararse
 * (KiAttackFireEvent) y lo consultamos al impactar, dentro de una ventana
 * de ticks.
 *
 * LIMITACIÓN CONOCIDA (Fase 4): la atribución es por jugador y por tiempo,
 * no por proyectil. Si el jugador lanza dos técnicas dentro de la ventana,
 * los impactos de la primera se atribuyen a la segunda. Enlazarlo por
 * proyectil exige un mixin sobre la entidad del proyectil de DMZ.
 */
public final class ExpulsionTracker {

    /** Datos de la expulsión activa. */
    public record Context(String techniqueId, KiType type, double overchargeProgress,
                          double outputFraction, double damageBonus, long expiresAtTick) {
    }

    private static final Map<UUID, Context> ACTIVE = new ConcurrentHashMap<>();

    private ExpulsionTracker() {
    }

    public static void begin(ServerPlayer player, Context context) {
        ACTIVE.put(player.getUUID(), context);
    }

    /** Devuelve el contexto vigente o null si no hay o ya venció (y lo limpia). */
    public static Context get(ServerPlayer player) {
        Context ctx = ACTIVE.get(player.getUUID());
        if (ctx == null) {
            return null;
        }
        if (player.level().getGameTime() > ctx.expiresAtTick()) {
            ACTIVE.remove(player.getUUID());
            return null;
        }
        return ctx;
    }

    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
