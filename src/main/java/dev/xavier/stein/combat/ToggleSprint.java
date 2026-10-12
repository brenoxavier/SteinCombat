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
 * Toggle sprint: sprinting without holding the game's sprint key — the mod keeps it pressed. With
 * "Sprint in all directions" (an option), also backwards and sideways, through the Loader. No
 * automatic jump and no faster flight.
 */
final class ToggleSprint {

    /** Toggle sprint on (the key, or the Panel row). Not saved: it starts off every session. */
    boolean toggled;

    /** The mod is holding the game's sprint key. */
    private boolean holding;

    /**
     * Every tick: the game's sprint key stays pressed; when turned off, it goes back to the
     * keyboard's state.
     */
    void tick() {
        boolean want = toggled && Players.self() != null && !Screens.isOpen();
        if (want) {
            Keys.game(Keys.GameKey.SPRINT).hold(true);
            holding = true;
        } else if (holding) {
            holding = false;
            Keys.game(Keys.GameKey.SPRINT).hold(false);
        }
    }
}
