/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat.server;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Every clan, in plugins/SteinCombat/clans.yml: {@code clans.<TAG>.leader} and {@code
 * clans.<TAG>.members.<uuid>: <nick>}. The file's shape is the server owner's data: it never
 * changes.
 */
final class ClanStore {

    private final File file;
    private final Logger log;

    /** The clans by tag, in alphabetical order (the order of "/clan lista"). */
    private final Map<String, Clan> clans = new TreeMap<>();

    /** Each member's clan tag. */
    private final Map<UUID, String> clanOf = new HashMap<>();

    ClanStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    Clan get(String tag) {
        return clans.get(tag);
    }

    Collection<Clan> all() {
        return clans.values();
    }

    int size() {
        return clans.size();
    }

    boolean isEmpty() {
        return clans.isEmpty();
    }

    boolean exists(String tag) {
        return clans.containsKey(tag);
    }

    /** The tag of the player's clan, or null. */
    String tagOf(UUID player) {
        return clanOf.get(player);
    }

    /** The player's clan, or null. */
    Clan clanOf(UUID player) {
        String tag = clanOf.get(player);
        return tag == null ? null : clans.get(tag);
    }

    void add(Clan c) {
        clans.put(c.tag, c);
    }

    void join(Clan c, UUID player, String nick) {
        c.members.put(player, nick);
        clanOf.put(player, c.tag);
    }

    void leave(Clan c, UUID player) {
        c.members.remove(player);
        clanOf.remove(player);
    }

    /** The clan is gone: its members are free again. */
    void remove(Clan c) {
        for (UUID u : c.members.keySet()) {
            clanOf.remove(u);
        }
        c.members.clear();
        clans.remove(c.tag);
    }

    void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection all = y.getConfigurationSection("clans");
        if (all == null) {
            return;
        }
        for (String tag : all.getKeys(false)) {
            ConfigurationSection s = all.getConfigurationSection(tag);
            try {
                Clan c = new Clan(tag, UUID.fromString(s.getString("leader")));
                ConfigurationSection m = s.getConfigurationSection("members");
                if (m != null) {
                    for (String id : m.getKeys(false)) {
                        UUID u = UUID.fromString(id);
                        c.members.put(u, m.getString(id));
                        clanOf.put(u, tag);
                    }
                }
                clans.put(tag, c);
            } catch (RuntimeException e) {
                log.warning("clan " + tag + " unreadable: " + e);
            }
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            y.set("clans." + c.tag + ".leader", c.leader.toString());
            for (Map.Entry<UUID, String> m : c.members.entrySet()) {
                y.set("clans." + c.tag + ".members." + m.getKey(), m.getValue());
            }
        }
        try {
            file.getParentFile().mkdirs();
            y.save(file);
        } catch (IOException e) {
            log.warning("could not write " + file + ": " + e);
        }
    }
}
