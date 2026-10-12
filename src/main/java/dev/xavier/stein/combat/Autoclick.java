/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Screens;

/**
 * Autoclick: clicks at the chosen rate ({@link CombatConfig#autoclickCps}), while holding the left
 * button (the default) or constant, without holding anything, while it is on ({@link
 * CombatConfig#autoclickMode}).
 *
 * <p><b>Like an external autoclicker:</b> each click is a press and a release in the mouse event
 * queue ({@link Keys#click}), the same one the physical mouse feeds. The game reads them in the
 * next tick, in order with the others: the hit or the block, the arm, the packets in the usual
 * order, the game's own hit delay and the HUD's CPS counter (which counts the events). No hit
 * called from outside. While holding, the physical button stays pressed so the game knows the
 * finger is there; the real press is already a click, and the first one from here comes one
 * interval later.
 *
 * <p><b>Rate:</b> each click's time comes from the clock (nanoTime), checked every frame: the CPS
 * is the chosen one at any FPS, with no variation at all; with more than one click per tick (CPS
 * above 20), the game processes the tick's clicks one after the other, as it would the mouse's. A
 * stall (a stuck frame, a reload) does not become a burst: the rate starts over from where it is.
 *
 * <p><b>When:</b> only with no screen open, and last in Combat's order — it waits for armor (Auto
 * Armor), the refill (Auto Refill), food and potions (Smart Effect) and the blocking sword (Auto
 * Protect): while they handle the hand or the inventory, no click goes out (the hit would go with
 * the wrong item, in the middle of a swap).
 */
final class Autoclick {

    /**
     * A delay longer than this (a stall) restarts the rate, instead of sending the late clicks at
     * once.
     */
    private static final long STALL_NS = 100_000_000L;

    /**
     * At most this many clicks in one frame (very low FPS with high CPS): the rest of the delay is
     * forgiven.
     */
    private static final int MAX_PER_FRAME = 8;

    private final SteinCombat mod;

    /**
     * The time (nanoTime) of the next click; 0: stopped (the finger is not on the button, or
     * something is ahead).
     */
    private long next;

    Autoclick(SteinCombat mod) {
        this.mod = mod;
    }

    /** Every frame, on the game thread. */
    void frame() {
        CombatConfig c = mod.config;
        boolean always = c.autoclickMode == 1;
        boolean trigger = always || Keys.isMouseDown(0);
        if (!c.autoclick
                || Screens.isOpen()
                || Entities.self() == null
                || !trigger
                || mod.handBusy()) {
            next = 0;
            return;
        }
        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, c.autoclickCps);
        if (next == 0) {
            if (!always) {
                next = now + interval; // holding: the real press is the first click
                return;
            }
            next = now;
        }
        if (now - next > STALL_NS) {
            next = now;
        }
        for (int n = 0; now >= next && n < MAX_PER_FRAME; n++) {
            Keys.click(0);
            next += interval;
        }
        if (now >= next) {
            next = now + interval;
        }
    }
}
