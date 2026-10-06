package ru.mobecome;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

/** Состояние админа, который сейчас превращён в моба. */
final class Session {
    EntityType type;
    LivingEntity disguise;
    boolean origAllowFlight;
    long lmbReady;
    long rmbReady;
    long lastRight;
    /** true, пока плагин сам наносит урон (чтобы не отменять его как ванильный удар игрока). */
    boolean own;

    Session(EntityType type) {
        this.type = type;
    }
}
