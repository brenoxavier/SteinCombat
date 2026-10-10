/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.loader.internal;

/**
 * Compile-time copy of the signatures of the Loader's engine combat switches, which stein-api.jar
 * leaves out (the {@code internal} package is only for the official Stein mods). It is never
 * packed: in the game, the Loader's own class answers. Keep in step with the Loader's {@code
 * dev.xavier.stein.loader.internal.Combat}.
 */
public final class Combat {

    private Combat() {}

    /** Sprinting in all directions while the sprint key is held. */
    public static void setSprintAllDirections(boolean on) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** Removes 1.8's ten-tick hit lock after a missed click. */
    public static void setNoHitDelay(boolean on) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** Sprinting survives the hit (no 1.8 "sprint reset"). */
    public static void setSprintOnHit(boolean on) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    /** With sprinting through the hit, the hit does not slow the player down either. */
    public static void setNoHitSlowdown(boolean on) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
