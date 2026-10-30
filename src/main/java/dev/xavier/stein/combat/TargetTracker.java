/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Targeting;

import java.util.Locale;

/**
 * Who the target element shows: the last player you hit, while in range and for some seconds after
 * the hit; without a recent hit, the player in your crosshair (the crosshair's ray up to the
 * range).
 */
final class TargetTracker {

    /**
     * How long the crosshair target stays after the crosshair leaves it: sweeping across someone
     * does not flash the panel.
     */
    private static final long AIM_LINGER = 400_000_000L;

    /** How often the crosshair's ray is cast. */
    private static final long AIM_EVERY = 50_000_000L;

    /** {@code -Dsteinhud.targetLog=true}: who comes in and why they leave. */
    static final boolean LOG = Boolean.getBoolean("steinhud.targetLog");

    private final TargetConfig c;
    private final Log log;

    /** The last player hit (EntityPlayer), or null, and when the hit was (nanoTime). */
    private Object hitTarget;

    private long lastHit;

    /** The crosshair's player, or null, when it was last in it, and when the ray was cast. */
    private Object aimTarget;

    private long lastAim;
    private long aimChecked;

    TargetTracker(TargetConfig c, Log log) {
        this.c = c;
        this.log = log;
    }

    /** You hit someone (the attack click, before it goes to the server). */
    void hit(Object entity) {
        if (LOG) {
            log.info(
                    "target: hit on "
                            + Entities.name(entity)
                            + " (player: "
                            + Entities.isPlayer(entity)
                            + ")");
        }
        if (!Entities.isPlayer(entity) || entity == Entities.self()) {
            return;
        }
        long now = System.nanoTime();
        if (entity != hitTarget
                && hitTarget != null
                && inRange(hitTarget)
                && now - lastHit < (long) (c.switchSeconds * 1e9)) {
            return; // the current target still holds the place
        }
        hitTarget = entity;
        lastHit = now;
    }

    /** Alive, still in the client's world and in range. */
    private boolean inRange(Object t) {
        Object me = Entities.self();
        return me != null
                && Entities.isAlive(t)
                && Entities.player(Entities.name(t)) == t
                && Entities.distance(me, t) <= c.range;
    }

    /**
     * The player on the crosshair's line, up to the range (blocks stop the ray); cast at most every
     * {@link #AIM_EVERY}.
     */
    private void aim(long now) {
        if (!c.aim || now - aimChecked < AIM_EVERY) {
            return;
        }
        aimChecked = now;
        Object me = Entities.self();
        if (me == null) {
            return;
        }
        Targeting.Ray ray =
                Targeting.raycast(
                        Targeting.query()
                                .reach(c.range)
                                .living()
                                .visibleOnly()
                                .cutAtBlock()
                                .exclude(me));
        for (Targeting.Hit h : ray.hits) {
            if (Entities.isPlayer(h.entity)) {
                if (LOG && h.entity != aimTarget) {
                    log.info(
                            String.format(
                                    Locale.ROOT,
                                    "target: aiming at %s, %.1f blocks",
                                    Entities.name(h.entity),
                                    h.distance));
                }
                aimTarget = h.entity;
                lastAim = now;
                return;
            }
        }
    }

    /** Who goes on screen: the last one hit, if still valid; otherwise the crosshair's. */
    Object choose(long now) {
        if (hitTarget != null
                && (!inRange(hitTarget) || now - lastHit >= (long) (c.seconds * 1e9))) {
            if (LOG) {
                log.info(
                        "target "
                                + Entities.name(hitTarget)
                                + " left: in range "
                                + inRange(hitTarget)
                                + ", seconds "
                                + (now - lastHit) / 1e9);
            }
            hitTarget = null;
        }
        if (hitTarget != null) {
            return hitTarget;
        }
        aim(now);
        if (aimTarget != null && (!c.aim || now - lastAim >= AIM_LINGER || !inRange(aimTarget))) {
            aimTarget = null;
        }
        return aimTarget;
    }
}
