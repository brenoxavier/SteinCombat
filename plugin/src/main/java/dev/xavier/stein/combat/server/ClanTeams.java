/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat.server;

import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * The clans on the main scoreboard: one team per clan, {@code sc_<TAG>} (the name the Combat mod
 * reads as proof of a clan), with the "[ABC] " prefix and no friendly fire. Every player's game,
 * with or without the mod, shows the tag in the Tab list and above the head.
 */
final class ClanTeams {

    /** The plugin's teams on the scoreboard: "sc_" + tag. */
    static final String TEAM = "sc_";

    private ClanTeams() {}

    private static Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private static String prefix(String tag) {
        return "§7[§b" + tag + "§7] §f";
    }

    /** One team per clan; the teams of clans that ended are removed. */
    static void syncAll(Iterable<Clan> clans) {
        Scoreboard sb = board();
        Set<String> wanted = new HashSet<>();
        for (Clan c : clans) {
            wanted.add(TEAM + c.tag);
            sync(c);
        }
        for (Team t : new ArrayList<>(sb.getTeams())) {
            if (t.getName().startsWith(TEAM) && !wanted.contains(t.getName())) {
                t.unregister();
            }
        }
    }

    /** The clan's team, with the tag and no friendly fire; its entries are the members' nicks. */
    static void sync(Clan c) {
        Scoreboard sb = board();
        Team t = sb.getTeam(TEAM + c.tag);
        if (t == null) {
            t = sb.registerNewTeam(TEAM + c.tag);
        }
        t.setPrefix(prefix(c.tag));
        t.setAllowFriendlyFire(false);
        t.setCanSeeFriendlyInvisibles(true);
        Set<String> names = new HashSet<>(c.members.values());
        for (String e : new ArrayList<>(t.getEntries())) {
            if (!names.contains(e)) {
                t.removeEntry(e);
            }
        }
        for (String n : names) {
            if (!t.hasEntry(n)) {
                t.addEntry(n);
            }
        }
    }

    /** The clan ended: its team goes. */
    static void remove(Clan c) {
        Team t = board().getTeam(TEAM + c.tag);
        if (t != null) {
            t.unregister();
        }
    }
}
