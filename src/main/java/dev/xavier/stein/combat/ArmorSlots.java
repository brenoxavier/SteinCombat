/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Stacks;

import java.util.Arrays;

/**
 * Auto Refill's view of the slots: spares, worn pieces and free places, in the bag and in the
 * chest.
 */
final class ArmorSlots {

    private ArmorSlots() {}

    /**
     * The pieces from the one with the fewest spares to the one with the most (on a tie, from the
     * helmet to the boots): every piece gets one spare before any gets a second — with few worn
     * pieces to swap, no piece goes without.
     */
    static Integer[] neediestFirst(CombatConfig c) {
        Integer[] order = {
            Inventory.HELMET, Inventory.CHESTPLATE, Inventory.LEGGINGS, Inventory.BOOTS
        };
        int[] spares = new int[4];
        for (int piece = 0; piece < 4; piece++) {
            spares[piece] = AutoArmor.spares(piece, c.armorRule(piece));
        }
        // A stable sort: the tie order stays.
        Arrays.sort(order, (a, b) -> Integer.compare(spares[a], spares[b]));
        return order;
    }

    /** The spares that fit, summed over the four pieces. */
    static int totalSpares(CombatConfig c) {
        int n = 0;
        for (int piece = 0; piece < 4; piece++) {
            n += AutoArmor.spares(piece, c.armorRule(piece));
        }
        return n;
    }

    /**
     * A worn armor piece in the bag or the hotbar (at its rule's threshold): the requested piece
     * first, then any; -1 with none. {@code piece} -1 accepts any piece.
     */
    static int worn(CombatConfig c, int piece) {
        int any = -1;
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            Object s = Inventory.stack(i);
            int p = s == null ? -1 : Inventory.armorPiece(s);
            if (p < 0 || !Stacks.isDamageable(s)) {
                continue;
            }
            if (Stacks.maxDamage(s) - Stacks.damage(s) <= c.armorRule(p).threshold) {
                if (piece < 0 || p == piece) {
                    return i;
                }
                if (any < 0) {
                    any = i;
                }
            }
        }
        return any;
    }

    /** The chest slot with the piece's armor that the rule picks (highest or lowest protection). */
    static int bestInChest(int piece, AutoArmor.Rule r) {
        int size = Inventory.containerSize();
        int best = -1;
        long bestScore = 0;
        for (int slot = 0; slot < size; slot++) {
            Object s = Inventory.slotStack(slot);
            if (!AutoArmor.usable(s, piece, r)) {
                continue;
            }
            long score = AutoArmor.score(s);
            if (best < 0 || (r.highest ? score > bestScore : score < bestScore)) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }

    /** An empty chest slot; -1 if it is full. */
    static int chestFree() {
        for (int i = 0; i < Inventory.containerSize(); i++) {
            if (Inventory.slotStack(i) == null) {
                return i;
            }
        }
        return -1;
    }

    static boolean chestEmpty() {
        for (int i = 0; i < Inventory.containerSize(); i++) {
            if (Inventory.slotStack(i) != null) {
                return false;
            }
        }
        return true;
    }

    /** A free space in the bag (9 to 35); with none, one in the hotbar. */
    static int freeSlot() {
        for (int i = Inventory.MAIN_START; i < Inventory.ARMOR_START; i++) {
            if (Inventory.stack(i) == null) {
                return i;
            }
        }
        for (int i = 0; i < Inventory.MAIN_START; i++) {
            if (Inventory.stack(i) == null) {
                return i;
            }
        }
        return -1;
    }
}
