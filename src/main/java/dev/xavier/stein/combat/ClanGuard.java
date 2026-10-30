/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Targeting;

/**
 * What Combat does to the allies on screen and in a fight — my clan, the marked nicks and the
 * allied clans alike ({@link Teams#isAlly}): hits on them are cancelled before reaching the server
 * (and can go through to whoever is behind), and the hide key takes them off the screen in one of
 * three styles ({@link CombatConfig#hideStyle}).
 */
final class ClanGuard {

    /** The ally disappears from the screen; the aim still finds them (and Ally Protect decides). */
    static final int DISAPPEAR = 0;

    /** The ally stays as a 15% silhouette, and hits go through them as if they were not there. */
    static final int GHOST = 1;

    /**
     * As if the ally did not exist: nothing on screen, and hits and right clicks go through them to
     * whatever is behind.
     */
    static final int GONE = 2;

    /** {@code -Dsteincombat.log=true}: every cancelled hit in the log. */
    private static final boolean LOG_HITS = Boolean.getBoolean("steincombat.log");

    private final SteinCombat mod;

    /**
     * The allies are hidden from the screen (the key toggles it). Not saved: it starts off every
     * session — opening the game without the allies on screen, without remembering why, would be
     * worse than pressing the key again.
     */
    boolean hiding;

    /** Sending the hit that goes through an ally (see {@link #behind}). */
    private boolean hittingThrough;

    /** The self-test checks the decisions without sending a hit to whoever is behind. */
    boolean dryRun;

    /** The empty name a ghost gets with "Hide ghost name tags" (built once the game is up). */
    private Object noName;

    ClanGuard(SteinCombat mod) {
        this.mod = mod;
    }

    /**
     * The hit cancelled before it reaches the server: on an ally with Ally Protect on, and always
     * on a ghost or gone ally — those two are not there for hits, so the hit goes to whoever is
     * behind them even without the protection.
     */
    boolean onAttackEntity(Object target) {
        if (!Entities.isPlayer(target)) {
            return false;
        }
        String nick = Entities.name(target);
        boolean passThrough = passesThrough(target, nick);
        if (!passThrough && !(mod.config.protectAllies && mod.teams.isAlly(nick))) {
            return false;
        }
        // No action bar notice: whoever turned on "do not hit my allies" knows what they asked
        // for, and text flashing in the middle of a fight gets in the way more than it informs (and
        // it covers what the server writes there).
        boolean through = passThrough || mod.config.clanHitThrough;
        Object behind = through && !hittingThrough && !dryRun ? behind(target) : null;
        if (behind != null) {
            // The hit goes through: to whoever is behind, the same way as the click (the arm
            // already swung). Targeting.attack does not come back here; the flag only guarantees
            // it never loops.
            hittingThrough = true;
            try {
                Targeting.attack(behind);
            } finally {
                hittingThrough = false;
            }
        }
        if (LOG_HITS) {
            mod.log()
                    .info(
                            "hit on an ally cancelled ("
                                    + nick
                                    + ")"
                                    + (behind == null
                                            ? ""
                                            : ", went through to " + Entities.name(behind)));
        }
        return true;
    }

    /**
     * A right click on a gone ally carries on as if nobody were there: the held item is used. The
     * other styles leave it alone (a right click does not hurt anyone).
     */
    boolean onInteractEntity(Object target) {
        return mod.config.hideStyle == GONE
                && Entities.isPlayer(target)
                && passesThrough(target, Entities.name(target));
    }

    /**
     * The hit that goes through the ally: in the line of aim, within the game's reach and before
     * the first block, the nearest one that is not me, not shielded and alive — or null. The game's
     * own aiming math (each entity's box with the collision border), only without stopping at the
     * first: the ally in front is left out.
     */
    private Object behind(Object ally) {
        Targeting.Ray ray =
                Targeting.raycast(
                        Targeting.query()
                                .living()
                                .cutAtBlock()
                                .exclude(ally)
                                .exclude(Entities.self()));
        for (Targeting.Hit hit : ray.hits) {
            Object e = hit.entity;
            if (hit.distance >= ray.blockDistance) {
                break;
            }
            if (Entities.isPlayer(e) && shielded(e, Entities.name(e))) {
                continue; // another ally in line: the hit goes through them too
            }
            return e;
        }
        return null;
    }

    /** Hits never land on them: protected, or hidden as a ghost or gone. */
    private boolean shielded(Object entity, String nick) {
        return mod.teams.isAlly(nick)
                && (mod.config.protectAllies
                        || hidden(entity) && mod.config.hideStyle != DISAPPEAR);
    }

    /** A hidden ally that hits go through: the ghost and gone styles. */
    private boolean passesThrough(Object entity, String nick) {
        return mod.config.hideStyle != DISAPPEAR && hidden(entity) && mod.teams.isAlly(nick);
    }

    /** The hide key is on and it is another player (never me, in third person). */
    private boolean hidden(Object entity) {
        return hiding && entity != Entities.self();
    }

    /**
     * Allies disappear from the screen while the key is on, in the disappear and gone styles —
     * body, armor, name and shadow. It is not the game's invisibility: nothing is left, not even
     * the armor. They are still in the Tab list, on the minimap and in messages, in the ally color.
     */
    boolean onHideEntity(Object entity) {
        if (!hiding || mod.config.hideStyle == GHOST || !Entities.isPlayer(entity)) {
            return false;
        }
        return entity != Entities.self() && mod.teams.isAlly(Entities.name(entity));
    }

    /**
     * Ghost style: with the hide key on, allies become the 15% silhouette the game already draws
     * for invisible teammates, without armor and without the held item — whoever is fighting behind
     * them can be seen, and hits go through them ({@link #onAttackEntity}).
     */
    boolean onGhostEntity(Object entity) {
        if (!hiding || mod.config.hideStyle != GHOST || !Entities.isPlayer(entity)) {
            return false;
        }
        return entity != Entities.self() && mod.teams.isAlly(Entities.name(entity));
    }

    /**
     * The name above a ghost's head, with "Hide ghost name tags": an empty name (the Loader keeps
     * the original for null), or null when the name stays.
     */
    Object ghostName(Object entity) {
        if (!mod.config.ghostHideName || !onGhostEntity(entity)) {
            return null;
        }
        if (noName == null) {
            noName = Chat.text("");
        }
        return noName;
    }
}
