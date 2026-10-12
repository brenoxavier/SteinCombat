/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Targeting;

/**
 * What Combat does to my clan on screen and in a fight: hits on it are cancelled before reaching
 * the server (and can go through to whoever is behind), and the hide key makes it disappear. Both
 * apply only to my clan, and only with "My clan is allied" on; protecting and hiding marked allies
 * and allied clans was removed from the mod (the old path is written, as a comment, in both
 * methods). Marked allies outside my clan become ghosts with the hide key instead.
 */
final class ClanGuard {

    /** {@code -Dsteincombat.log=true}: every cancelled hit in the log. */
    private static final boolean LOG_HITS = Boolean.getBoolean("steincombat.log");

    private final SteinCombat mod;

    /**
     * My clan is hidden from the screen (the key toggles it). Not saved: it starts off every
     * session — opening the game without the allies on screen, without remembering why, would be
     * worse than pressing the key again.
     */
    boolean hiding;

    /** Sending the hit that goes through my clan (see {@link #behindClan}). */
    private boolean hittingThrough;

    ClanGuard(SteinCombat mod) {
        this.mod = mod;
    }

    /**
     * The hit cancelled before it reaches the server. <b>Only my clan</b>: marked allies and allied
     * clans are no longer protected — they can be hit like anyone.
     */
    boolean onAttackEntity(Object target) {
        if (!mod.config.protectAllies || !Entities.isPlayer(target)) {
            return false;
        }
        String nick = Entities.name(target);
        // Ally protection was removed from the mod: only my own clan's remains. What it was is
        // here, one line — swapping isClanmate for isAlly brings marked nicks and allied clans back
        // under protection:
        //     if (!mod.teams.isAlly(nick)) {
        if (!mod.teams.isClanmate(nick)) {
            return false;
        }
        // No action bar notice: whoever turned on "do not hit my clan" knows what they asked for,
        // and text flashing in the middle of a fight gets in the way more than it informs (and it
        // covers what the server writes there).
        Object through = mod.config.clanHitThrough && !hittingThrough ? behindClan(target) : null;
        if (through != null) {
            // The hit goes through: to whoever is behind, the same way as the click (the arm
            // already
            // swung). Targeting.attack does not come back here; the flag only guarantees it never
            // loops.
            hittingThrough = true;
            try {
                Targeting.attack(through);
            } finally {
                hittingThrough = false;
            }
        }
        if (LOG_HITS) {
            mod.log()
                    .info(
                            "hit on my clan cancelled ("
                                    + nick
                                    + ")"
                                    + (through == null
                                            ? ""
                                            : ", went through to " + Entities.name(through)));
        }
        return true;
    }

    /**
     * The hit that goes through my clan: in the line of aim, within the game's reach and before the
     * first block, the nearest one that is not me, not in my clan and alive — or null. The game's
     * own aiming math (each entity's box with the collision border), only without stopping at the
     * first: my clanmate in front is left out.
     */
    private Object behindClan(Object clanmate) {
        Targeting.Ray ray =
                Targeting.raycast(
                        Targeting.query()
                                .living()
                                .cutAtBlock()
                                .exclude(clanmate)
                                .exclude(Entities.self()));
        for (Targeting.Hit hit : ray.hits) {
            Object e = hit.entity;
            if (hit.distance >= ray.blockDistance) {
                break;
            }
            if (Entities.isPlayer(e) && mod.teams.isClanmate(Entities.name(e))) {
                continue; // another clanmate in line: the hit goes through them too
            }
            return e;
        }
        return null;
    }

    /**
     * My clan disappears from the screen while the key is on — body, armor, name and shadow. It is
     * not the game's invisibility: nothing is left, not even the armor.
     *
     * <p><b>Only my clan</b>, and only with "My clan is allied" on: hiding marked allies and allied
     * clans was removed from the mod. They are still there for everything else: in the Tab list, on
     * the minimap and in messages, and still in the ally color. Only the screen is clean; turning
     * the key off, everyone comes back.
     */
    boolean onHideEntity(Object entity) {
        if (!hiding || !Entities.isPlayer(entity)) {
            return false;
        }
        if (entity == Entities.self()) {
            return false; // I never disappear to myself (third person)
        }
        // Hiding allies was removed with the protection, and the same way: whoever is in my clan
        // disappears. What it was before is here, one line — marked nicks and allied clans
        // disappeared too:
        //     return mod.teams.isAlly(Entities.name(entity));
        return mod.teams.isClanmate(Entities.name(entity));
    }

    /**
     * With the hide key on, an ally who is <b>not</b> in my clan becomes a ghost: the 15%
     * silhouette the game already draws for invisible teammates, without armor and without the held
     * item.
     *
     * <p>It is the middle ground of hiding allies: whoever is fighting behind them can be seen, but
     * they are still there — with the name above the head, and <b>taking hits like anyone</b>.
     * Disappearing beats becoming a ghost, so my clan still disappears entirely.
     */
    boolean onGhostEntity(Object entity) {
        if (!hiding || !mod.config.ghostAllies || !Entities.isPlayer(entity)) {
            return false;
        }
        if (entity == Entities.self()) {
            return false; // I never become a ghost to myself (third person)
        }
        String nick = Entities.name(entity);
        return mod.teams.isAlly(nick) && !mod.teams.isClanmate(nick);
    }
}
