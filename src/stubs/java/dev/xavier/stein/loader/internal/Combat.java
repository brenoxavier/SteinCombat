/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.loader.internal;

/**
 * Compile-time copy of the signatures of the Loader's engine combat switches, which stein-api.jar
 * leaves out (the {@code internal} package is outside the public API). It is never packed: in the
 * game, the Loader's own class answers. Keep in step with the Loader's {@code
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
