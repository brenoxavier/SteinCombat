/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Screens;

/**
 * Toggle sprint: sprinting without holding the game's sprint key — the mod keeps it pressed, and
 * the game decides when sprinting happens (forward only, with its own conditions). No automatic
 * jump; flying sprints as with the key held (vanilla).
 */
final class ToggleSprint {

    /** The mod is holding the game's sprint key. */
    private boolean holding;

    /**
     * Every tick: with toggle sprint on ({@link CombatConfig#toggleSprint}), the game's sprint key
     * stays pressed; when turned off, it goes back to the keyboard's state.
     */
    void tick(boolean on) {
        boolean want = on && Players.self() != null && !Screens.isOpen();
        if (want) {
            Keys.game(Keys.GameKey.SPRINT).hold(true);
            holding = true;
        } else if (holding) {
            holding = false;
            Keys.game(Keys.GameKey.SPRINT).hold(false);
        }
    }
}
