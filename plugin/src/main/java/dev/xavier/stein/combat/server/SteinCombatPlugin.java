/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat.server;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Combat's clans, on the server (Spigot 1.8.8, Java 8): the user's request was "Clan, prefix [3
 * letter tag] Nick in everyone's Tab list". Each clan is a team of the main scoreboard with the
 * "[ABC] " prefix ({@link ClanTeams}): every player's game (with or without the mod) shows the tag
 * in the Tab list and above the head, and the team has no friendly fire — members of the same clan
 * do not hurt each other. The Combat mod reads the {@code sc_<TAG>} team as proof of a clan.
 *
 * <p>Commands in {@link ClanCommand}; data in plugins/SteinCombat/clans.yml ({@link ClanStore}). If
 * another plugin gives each player a scoreboard of their own, the tag does not show.
 */
public final class SteinCombatPlugin extends JavaPlugin implements Listener {

    private ClanStore store;
    private ClanCommand command;

    @Override
    public void onEnable() {
        Messages msg;
        try {
            msg = new Messages();
        } catch (IOException e) {
            getLogger().severe("cannot read the messages: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        store = new ClanStore(new File(getDataFolder(), "clans.yml"), getLogger());
        store.load();
        command = new ClanCommand(store, msg);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            seen(p);
        }
        ClanTeams.syncAll(store.all());
        getLogger().info(store.size() + " clan(s)");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.save();
        }
    }

    /** Joined: the member's nick may have changed. */
    private void seen(Player p) {
        Clan c = store.clanOf(p.getUniqueId());
        if (c != null && !p.getName().equals(c.members.get(p.getUniqueId()))) {
            c.members.put(p.getUniqueId(), p.getName());
            store.save();
            ClanTeams.sync(c);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        seen(e.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        command.run(sender, args);
        return true;
    }
}
