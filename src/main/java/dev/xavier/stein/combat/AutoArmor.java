/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Stacks;

/**
 * Auto Armor: replaces and swaps armor by itself, every tick, with the same clicks a player makes —
 * without opening the inventory, without pausing, without holding the player. It works with the
 * inventory open or closed, in the Esc menu, in the chat, in the Panel: the player's own inventory
 * window exists all the time. The only exception is an open container (chest, furnace, crafting
 * table): the server only accepts clicks in its window, which has no armor slots; when it closes,
 * Auto Armor acts in the same tick.
 *
 * <p>Each piece follows a rule ({@link Rule}) — the same for all in simple mode, or one per piece:
 *
 * <ul>
 *   <li><b>mode</b>: off; <b>replace when broken</b> (lets it break and puts the spare on in the
 *       tick the slot empties); <b>swap before</b> (with durability at the threshold or below it
 *       swaps for a spare that has more, and replaces it if it breaks anyway);
 *   <li><b>priority</b>: the spare with the highest protection first, or the lowest (use up the
 *       weak ones first);
 *   <li><b>unenchanted</b>: whether an unenchanted spare is accepted (off by default).
 * </ul>
 *
 * <p>A piece's protection ({@link #score}) counts everything: the material's armor points,
 * Protection, the specific protections and Unbreaking. The remaining durability only counts if the
 * rule asks ({@link Rule#durability}), and only as a tie-breaker between pieces that protect the
 * same; after it, the bag comes before the hotbar.
 *
 * <p>The clicks are shift-clicks: take the old one off and put the new one on, without going
 * through the cursor (the player may be holding an item in the inventory). With no free space, the
 * three clicks of a swap, only with the cursor empty.
 *
 * <p><b>Checked with the server.</b> The client applies the click at once; if the server refuses it
 * without sending the slot back (a plugin that cancels the click, an anticheat, lag), the client
 * keeps showing armor the server does not have — and Auto Armor, which only sees the client, thinks
 * all is well. So after acting, the real inventory is asked from the server ({@link
 * Inventory#resync}); if the swap did not take, the slot comes back empty and the piece is swapped
 * again. A piece that fails several times in a row waits longer and longer between attempts,
 * without ever stopping for good. Taking hits while the worn armor's durability does not move is a
 * sign of a client out of step with the server too.
 */
final class AutoArmor {

    /** {@code -Dsteincombat.debugNotices=true}: the notices (no item, swap, chest) in the log. */
    private static final boolean NOTICES = Boolean.getBoolean("steincombat.debugNotices");

    /** {@code -Dsteincombat.debugArmor=true}: every decision in the log. */
    private static final boolean DEBUG = Boolean.getBoolean("steincombat.debugArmor");

    static final int OFF = 0;
    static final int REPLACE = 1;
    static final int SWAP = 2;

    static final int DUR_OFF = 0;
    static final int DUR_HIGHEST = 1;
    static final int DUR_LOWEST = 2;

    static final String[] PIECE_KEYS = {
        "steincombat.armor.boots",
        "steincombat.armor.leggings",
        "steincombat.armor.chestplate",
        "steincombat.armor.helmet"
    };

    /** The game's enchantment ids. */
    private static final int PROTECTION = 0;

    private static final int FIRE_PROTECTION = 1;
    private static final int BLAST_PROTECTION = 3;
    private static final int PROJECTILE_PROTECTION = 4;
    private static final int UNBREAKING = 34;

    /** Ticks a piece waits after acting (for the server to confirm). */
    private static final int COOLDOWN = 4;

    /**
     * Half a second after acting, the real inventory is asked from the server (Inventory.resync).
     */
    private static final int VERIFY_TICKS = 10;

    /**
     * How long to wait for the inventory after asking; with no answer, it goes on with what it has.
     */
    private static final int RESYNC_TIMEOUT = 40;

    /**
     * Acting on the same piece again before this (10 s) counts as the swap not having taken. Longer
     * than the longest wait with the check: otherwise, after the longest wait, the count would
     * reset and the attempts would become fast again.
     */
    private static final int RETRY_WINDOW = 200;

    /**
     * The wait after acting doubles with each swap that did not take, up to 4 << 5 = 128 ticks (6.4
     * s).
     */
    private static final int MAX_BACKOFF = 5;

    /**
     * Hits in a row without the worn armor's durability going down, before asking for the real
     * inventory.
     */
    private static final int STILL_HITS = 3;

    /** Minimum interval between two requests because of unmoving durability (10 s). */
    private static final int STILL_RESYNC_GAP = 200;

    /** The rule of one piece (or of all, in simple mode). */
    static final class Rule {
        int mode = SWAP;

        /** Swap with this remaining durability, or less. */
        int threshold = 35;

        /** The spare with the highest protection first (false: the lowest). */
        boolean highest = true;

        /** Accept an unenchanted spare. */
        boolean unenchanted = false;

        /**
         * Durability tie-breaker: {@link #DUR_OFF} (ignored, the default), highest or lowest first.
         */
        int durability = DUR_OFF;

        /**
         * Do not let a piece with Protection of this level or more break (4 to 10; 0 off): at the
         * durability threshold it comes off — swapped for the spare, or kept in the inventory if
         * there is none —, even in replace mode.
         */
        int keepLevel = 0;

        void copyFrom(Rule o) {
            keepLevel = o.keepLevel;
            mode = o.mode;
            threshold = o.threshold;
            highest = o.highest;
            unenchanted = o.unenchanted;
            durability = o.durability;
        }
    }

    private final SteinCombat mod;
    private final int[] cooldown = new int[4];

    /** Already warned that there is no spare for the piece (warns again once there is one). */
    private final boolean[] warned = new boolean[4];

    private int now;

    /** The tick when the inventory will be asked for (-1 none set). */
    private int verifyAt = -1;

    /**
     * Waiting for the requested inventory until this tick (-1 not waiting): nothing is decided on
     * the old state.
     */
    private int awaitUntil = -1;

    private int lastResync = -STILL_RESYNC_GAP;
    private final int[] lastAct = {-RETRY_WINDOW, -RETRY_WINDOW, -RETRY_WINDOW, -RETRY_WINDOW};

    /** Swaps of the piece in a row that did not take. */
    private final int[] fails = new int[4];

    /** Already warned that the server is refusing the piece. */
    private final boolean[] warnedRefused = new boolean[4];

    /** A hit to check: in this tick the worn durability is compared with the one when it landed. */
    private int hitCheckAt = -1;

    private long hitWear;
    private int stillHits;

    /** Whether it acted in this tick (Smart Effect starts nothing in the same tick). */
    private boolean acted;

    AutoArmor(SteinCombat mod) {
        this.mod = mod;
    }

    /**
     * The server sent a whole window (SteinMod.onWindowItems): the inventory one is what the
     * request waited for.
     */
    void windowItems(int windowId) {
        if (windowId == 0 && awaitUntil >= 0) {
            awaitUntil = -1;
            log("real inventory arrived");
        }
    }

    /**
     * The player's health went down: the worn armor should wear. Checked a few ticks later, when
     * the slot arrives.
     */
    void hurt() {
        if (mod.config.autoArmor && hitCheckAt < 0 && wornWear() >= 0) {
            hitWear = wornWear();
            hitCheckAt = now + 10;
        }
    }

    /** The summed wear of the worn pieces that wear; -1 with none. */
    private static long wornWear() {
        long sum = -1;
        for (int piece = 0; piece < 4; piece++) {
            Object s = Inventory.armor(piece);
            if (s != null && Stacks.isDamageable(s)) {
                sum = Math.max(sum, 0) + Stacks.damage(s);
            }
        }
        return sum;
    }

    /** Asks for the real inventory; until it arrives (or the time runs out), nothing is decided. */
    private void resync(String why) {
        if (Inventory.resync()) {
            lastResync = now;
            awaitUntil = now + RESYNC_TIMEOUT;
            log("inventory asked from the server: " + why);
        }
    }

    /** One tick; returns true if it touched the inventory. */
    boolean tick() {
        acted = false;
        now++;
        CombatConfig c = mod.config;
        if (!c.autoArmor || Entities.self() == null) {
            verifyAt = -1;
            awaitUntil = -1;
            hitCheckAt = -1;
            return false;
        }
        if (Inventory.isContainerOpen()) {
            // The check that was set waits until it closes: the chest window has no armor.
            return false;
        }
        if (awaitUntil >= 0) {
            if (now < awaitUntil) {
                return false;
            }
            // No answer: go on with what the client has, and the next swap checks again.
            awaitUntil = -1;
            log("the requested inventory did not arrive");
        }
        if (verifyAt >= 0 && now >= verifyAt) {
            verifyAt = -1;
            resync("check the swap");
            return false;
        }
        if (hitCheckAt >= 0 && now >= hitCheckAt) {
            hitCheckAt = -1;
            long wear = wornWear();
            stillHits = wear >= 0 && wear == hitWear ? stillHits + 1 : 0;
            // Falling, fire and poison do not wear armor: only a few still hits in a row, with a
            // gap, ask.
            if (stillHits >= STILL_HITS && now - lastResync >= STILL_RESYNC_GAP) {
                stillHits = 0;
                resync("hits without the durability going down");
                return false;
            }
        }
        if (Inventory.cursor() != null && !Inventory.isScreenOpen()) {
            // With no screen open nobody holds an item on the cursor: it is left over from a swap
            // the server undid. Put it in a free space (with the inventory open it may be the
            // player dragging: then it is left alone).
            int free = Inventory.firstEmpty();
            if (free >= 0) {
                Inventory.pickup(Inventory.slotOf(free));
            }
            return true;
        }
        // From the helmet to the boots, as the screen shows them.
        for (int piece = Inventory.HELMET; piece >= Inventory.BOOTS; piece--) {
            if (cooldown[piece] > 0) {
                cooldown[piece]--;
                continue;
            }
            Rule r = c.armorRule(piece);
            if (r.mode == OFF) {
                continue;
            }
            Object worn = Inventory.armor(piece);
            if (worn == null) {
                replaceMissing(piece, r);
            } else if (Stacks.isDamageable(worn)
                    && Stacks.maxDamage(worn) - Stacks.damage(worn) <= r.threshold) {
                swapWorn(piece, r, worn);
            }
        }
        return acted;
    }

    /** The slot of the piece is empty: put on the best spare. */
    private void replaceMissing(int piece, Rule r) {
        // Replacing in swap mode: first a spare above the threshold (otherwise the almost broken
        // one would be put on and swapped again right after); only with none, any that still has
        // durability.
        int index = r.mode == SWAP ? best(piece, r.threshold + 1, r, false) : -1;
        if (index < 0) {
            index = best(piece, 1, r, false);
        }
        if (index < 0) {
            // Nothing better: the protected pieces kept at the threshold are used again until they
            // break (with no other piece, using up what there is beats having no armor).
            index = best(piece, 1, r, true);
        }
        act(piece, index, "steincombat.armor.equipped");
    }

    /** The worn piece is at the threshold: swap it, if the rule says so. */
    private void swapWorn(int piece, Rule r, Object worn) {
        boolean keep = isProtected(worn, r);
        if (DEBUG) {
            log(
                    "piece "
                            + piece
                            + " at the threshold ("
                            + (Stacks.maxDamage(worn) - Stacks.damage(worn))
                            + "), protected "
                            + keep
                            + ", mode "
                            + r.mode);
        }
        if (r.mode != SWAP && !keep) {
            return;
        }
        int index = best(piece, r.threshold + 1, r, false);
        if (index >= 0) {
            act(piece, index, "steincombat.armor.swapped");
        } else if (keep && !warned[piece]) {
            // No spare at all (and Auto Refill off, or the chest without fitting armor): the piece
            // stays on and is used until it breaks — no armor is worse. When a spare arrives (the
            // refill), it is swapped at once.
            warned[piece] = true;
            notice(Lang.tr("steincombat.armor.noSpareKeep", Lang.tr(PIECE_KEYS[piece])));
        }
    }

    private void act(int piece, int index, String doneKey) {
        if (index < 0) {
            if (!warned[piece]) {
                warned[piece] = true;
                notice(Lang.tr("steincombat.armor.none", Lang.tr(PIECE_KEYS[piece])));
            }
            return;
        }
        warned[piece] = false;
        if (!swap(piece, index)) {
            return;
        }
        acted = true;
        // Acting on the same piece again right after: the previous swap did not take on the server
        // (the check brought the slot back). The wait doubles each time, and goes back to normal
        // when a swap stays.
        fails[piece] = now - lastAct[piece] < RETRY_WINDOW ? fails[piece] + 1 : 0;
        lastAct[piece] = now;
        cooldown[piece] = COOLDOWN << Math.min(fails[piece], MAX_BACKOFF);
        boolean warnNow = false;
        if (fails[piece] == 0) {
            warnedRefused[piece] = false;
        } else if (fails[piece] >= 3 && !warnedRefused[piece]) {
            warnedRefused[piece] = true;
            warnNow = true;
        }
        log(
                "piece "
                        + piece
                        + " swapped"
                        + (fails[piece] > 0 ? ", attempt " + (fails[piece] + 1) : ""));
        if (verifyAt < 0) {
            verifyAt = now + VERIFY_TICKS;
        }
        notice(
                Lang.tr(
                        warnNow ? "steincombat.armor.refused" : doneKey,
                        Lang.tr(PIECE_KEYS[piece])));
    }

    private void log(String text) {
        if (DEBUG) {
            mod.log().info("[armor] " + text);
        }
    }

    /** Does the piece have Protection at the level the rule does not let break? */
    static boolean isProtected(Object s, Rule r) {
        return r.keepLevel > 0 && Stacks.enchantLevel(s, PROTECTION) >= r.keepLevel;
    }

    /**
     * Does the piece serve as a spare by the rule (the enchantment filter and durability above the
     * threshold)?
     */
    static boolean usable(Object s, int piece, Rule r) {
        if (s == null
                || Inventory.armorPiece(s) != piece
                || !r.unenchanted && !Stacks.isEnchanted(s)) {
            return false;
        }
        int max = Stacks.maxDamage(s);
        return max <= 0 || max - Stacks.damage(s) > r.threshold;
    }

    /** How many spares for the piece are in the inventory (hotbar and bag). */
    static int spares(int piece, Rule r) {
        int n = 0;
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            if (usable(Inventory.stack(i), piece, r)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Puts on the piece at the index. Empty slot: one shift-click. With the armor still on and a
     * free space: two shift-clicks (old off, new on), nothing going through the cursor. With no
     * space: the three clicks of a swap (the old one goes where the new one was), only with the
     * cursor empty.
     */
    private static boolean swap(int piece, int index) {
        int from = Inventory.slotOf(index);
        if (from < 0) {
            return false;
        }
        if (Inventory.armor(piece) == null) {
            return Inventory.quickMove(from);
        }
        if (Inventory.firstEmpty() >= 0) {
            return Inventory.unequip(piece) && Inventory.quickMove(from);
        }
        return Inventory.cursor() == null && Inventory.equip(index);
    }

    /**
     * The index of the piece's spare with at least {@code minLeft} durability that the rule picks:
     * the highest protection, or the lowest. The score always decides; only on a tie the bag comes
     * before the hotbar (what is on the hotbar is what the player wants at hand) — so the bag is
     * read first and only a better score changes the choice.
     *
     * @param allowKept also accept the protected piece that came off at the threshold (the last
     *     resort, with no other)
     */
    private static int best(int piece, int minLeft, Rule r, boolean allowKept) {
        int best = -1;
        long bestScore = 0;
        int bestLeft = 0;
        int bag = Inventory.ARMOR_START - Inventory.MAIN_START;
        for (int n = 0; n < Inventory.ARMOR_START; n++) {
            // The bag (9 to 35), then the hotbar (0 to 8).
            int i = n < bag ? Inventory.MAIN_START + n : n - bag;
            Object s = Inventory.stack(i);
            if (s == null
                    || Inventory.armorPiece(s) != piece
                    || !r.unenchanted && !Stacks.isEnchanted(s)) {
                continue;
            }
            int max = Stacks.maxDamage(s);
            int left = max - Stacks.damage(s);
            if (max > 0 && left < minLeft) {
                continue;
            }
            if (!allowKept && max > 0 && left <= r.threshold && isProtected(s, r)) {
                continue; // the protected piece that came off so as not to break does not go back
                // on
            }
            long score = score(s);
            boolean better = best < 0 || (r.highest ? score > bestScore : score < bestScore);
            if (!better && score == bestScore && r.durability != DUR_OFF) {
                // They protect the same: durability decides, if the rule asks.
                better = r.durability == DUR_HIGHEST ? left > bestLeft : left < bestLeft;
            }
            if (better) {
                bestScore = score;
                bestLeft = left;
                best = i;
            }
        }
        return best;
    }

    /**
     * How much the piece protects, counting everything. The material's armor points and those of
     * Protection (EPF, the game's formula: (6 + level squared) x 0.75 / 3) are worth the same —
     * each takes about 4% of the damage; the specific protections, a little; Unbreaking, a little
     * less. Durability does not count (it is a rule option, as a tie-breaker).
     */
    static long score(Object s) {
        int p = Stacks.enchantLevel(s, PROTECTION);
        long epf = p == 0 ? 0 : (long) Math.floor((6 + p * p) * 0.75 / 3.0);
        long special =
                Stacks.enchantLevel(s, FIRE_PROTECTION)
                        + Stacks.enchantLevel(s, BLAST_PROTECTION)
                        + Stacks.enchantLevel(s, PROJECTILE_PROTECTION);
        return Inventory.armorValue(s) * 1000L
                + epf * 1000L
                + special * 250L
                + Stacks.enchantLevel(s, UNBREAKING) * 120L;
    }

    /**
     * What happened (no item, swap done, empty chest...). Not on the action bar: only turning on
     * and off shows there (the user's request); with {@code -Dsteincombat.debugNotices=true}, in
     * the log.
     */
    private void notice(String text) {
        if (NOTICES) {
            mod.log().info("[Auto Armor] " + text);
        }
    }
}
