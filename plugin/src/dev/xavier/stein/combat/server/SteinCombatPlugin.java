/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat.server;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Clas do Stein Combat, no servidor (Spigot 1.8.8). Pedido do usuario: "Clan, Prefix [tag 3 letras] Nick no tab de
 * todo mundo". Cada cla e um time do placar principal com o prefixo "[ABC] ": o jogo de todos (com ou sem o mod)
 * mostra a tag no Tab e acima da cabeca, e o time nao tem fogo amigo — membros do mesmo cla nao se machucam.
 *
 * <p>Comandos (/clan ou /cla): criar TAG, convidar nick, aceitar, sair, expulsar nick, info [TAG], lista, desfazer
 * [TAG]. O lider convida e expulsa; saindo, a lideranca passa para o membro mais antigo; o ultimo a sair desfaz o cla.
 * Tudo em plugins/SteinCombat/clans.yml. Se outro plugin der a cada jogador um placar proprio, a tag nao aparece.
 */
public final class SteinCombatPlugin extends JavaPlugin implements Listener {

    private static final String PREFIX = "§7[Clã] §r";
    /** Convite vale 2 minutos. */
    private static final long INVITE_MS = 120_000L;
    /** Times do plugin no placar: "sc_" + tag. */
    private static final String TEAM = "sc_";

    private static final class Clan {
        final String tag;
        UUID leader;
        /** Membros por UUID, com o ultimo nick visto, na ordem de entrada. */
        final Map<UUID, String> members = new LinkedHashMap<UUID, String>();

        Clan(String tag, UUID leader) {
            this.tag = tag;
            this.leader = leader;
        }
    }

    private static final class Invite {
        final String tag;
        final long until;

        Invite(String tag, long until) {
            this.tag = tag;
            this.until = until;
        }
    }

    private final Map<String, Clan> clans = new TreeMap<String, Clan>();
    private final Map<UUID, String> clanOf = new HashMap<UUID, String>();
    private final Map<UUID, Invite> invites = new HashMap<UUID, Invite>();
    private File file;

    @Override
    public void onEnable() {
        file = new File(getDataFolder(), "clans.yml");
        load();
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            seen(p);
        }
        syncTeams();
        getLogger().info(clans.size() + " cla(s)");
    }

    @Override
    public void onDisable() {
        save();
    }

    // ---- dados ----

    private void load() {
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
                getLogger().warning("cla " + tag + " ilegivel: " + e);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            y.set("clans." + c.tag + ".leader", c.leader.toString());
            for (Map.Entry<UUID, String> m : c.members.entrySet()) {
                y.set("clans." + c.tag + ".members." + m.getKey(), m.getValue());
            }
        }
        try {
            getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            getLogger().warning("nao gravou " + file + ": " + e);
        }
    }

    // ---- times do placar: a tag para todos ----

    private static String prefix(String tag) {
        return "§7[§b" + tag + "§7] §f";
    }

    private static Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** Um time por cla, com a tag e sem fogo amigo; entradas = os nicks dos membros. Times de clas que acabaram saem. */
    private void syncTeams() {
        Scoreboard sb = board();
        Set<String> wanted = new HashSet<String>();
        for (Clan c : clans.values()) {
            wanted.add(TEAM + c.tag);
            syncTeam(c);
        }
        for (Team t : new ArrayList<Team>(sb.getTeams())) {
            if (t.getName().startsWith(TEAM) && !wanted.contains(t.getName())) {
                t.unregister();
            }
        }
    }

    private void syncTeam(Clan c) {
        Scoreboard sb = board();
        Team t = sb.getTeam(TEAM + c.tag);
        if (t == null) {
            t = sb.registerNewTeam(TEAM + c.tag);
        }
        t.setPrefix(prefix(c.tag));
        t.setAllowFriendlyFire(false);
        t.setCanSeeFriendlyInvisibles(true);
        Set<String> names = new HashSet<String>(c.members.values());
        for (String e : new ArrayList<String>(t.getEntries())) {
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

    /** Entrou: o nick do membro pode ter mudado. */
    private void seen(Player p) {
        String tag = clanOf.get(p.getUniqueId());
        if (tag == null) {
            return;
        }
        Clan c = clans.get(tag);
        if (c != null && !p.getName().equals(c.members.get(p.getUniqueId()))) {
            c.members.put(p.getUniqueId(), p.getName());
            save();
            syncTeam(c);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        seen(e.getPlayer());
    }

    // ---- comandos ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("lista")) {
            list(sender);
            return true;
        }
        if (sub.equals("desfazer") && args.length > 1 && sender.hasPermission("steincombat.admin")) {
            Clan c = clans.get(args[1].toUpperCase(Locale.ROOT));
            if (c == null) {
                sender.sendMessage(PREFIX + "§cNão existe o clã " + args[1] + ".");
            } else {
                dissolve(c);
                sender.sendMessage(PREFIX + "Clã [" + c.tag + "] desfeito.");
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "Só jogadores (no console: /clan lista, /clan desfazer TAG).");
            return true;
        }
        Player p = (Player) sender;
        if (sub.equals("criar") && args.length > 1) {
            create(p, args[1]);
        } else if (sub.equals("convidar") && args.length > 1) {
            invite(p, args[1]);
        } else if (sub.equals("aceitar")) {
            accept(p);
        } else if (sub.equals("sair")) {
            leave(p);
        } else if (sub.equals("expulsar") && args.length > 1) {
            kick(p, args[1]);
        } else if (sub.equals("info")) {
            info(p, args.length > 1 ? args[1].toUpperCase(Locale.ROOT) : clanOf.get(p.getUniqueId()));
        } else if (sub.equals("desfazer")) {
            Clan c = own(p);
            if (c == null) {
                return true;
            }
            if (!c.leader.equals(p.getUniqueId())) {
                p.sendMessage(PREFIX + "§cSó o líder desfaz o clã.");
                return true;
            }
            dissolve(c);
            p.sendMessage(PREFIX + "Clã [" + c.tag + "] desfeito.");
        } else {
            p.sendMessage(PREFIX + "§e/clan criar TAG§r (3 letras) · §e/clan convidar nick§r · §e/clan aceitar");
            p.sendMessage(PREFIX + "§e/clan sair§r · §e/clan expulsar nick§r · §e/clan info [TAG]§r · §e/clan lista§r · "
                    + "§e/clan desfazer");
        }
        return true;
    }

    private Clan own(Player p) {
        String tag = clanOf.get(p.getUniqueId());
        Clan c = tag == null ? null : clans.get(tag);
        if (c == null) {
            p.sendMessage(PREFIX + "§cVocê não está em nenhum clã.");
        }
        return c;
    }

    private void create(Player p, String raw) {
        String tag = raw.toUpperCase(Locale.ROOT);
        if (!tag.matches("[A-Z0-9]{3}")) {
            p.sendMessage(PREFIX + "§cA tag tem 3 letras ou números (ex.: ABC).");
        } else if (clanOf.containsKey(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§cVocê já está no clã [" + clanOf.get(p.getUniqueId()) + "]. Saia antes (/clan sair).");
        } else if (clans.containsKey(tag)) {
            p.sendMessage(PREFIX + "§cJá existe o clã [" + tag + "].");
        } else {
            Clan c = new Clan(tag, p.getUniqueId());
            c.members.put(p.getUniqueId(), p.getName());
            clans.put(tag, c);
            clanOf.put(p.getUniqueId(), tag);
            save();
            syncTeam(c);
            p.sendMessage(PREFIX + "Clã §b[" + tag + "]§r criado. Convide com /clan convidar nick.");
        }
    }

    private void invite(Player p, String nick) {
        Clan c = own(p);
        if (c == null) {
            return;
        }
        if (!c.leader.equals(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§cSó o líder convida.");
            return;
        }
        Player t = Bukkit.getPlayerExact(nick);
        if (t == null) {
            p.sendMessage(PREFIX + "§c" + nick + " não está online.");
        } else if (clanOf.containsKey(t.getUniqueId())) {
            p.sendMessage(PREFIX + "§c" + t.getName() + " já está num clã.");
        } else {
            invites.put(t.getUniqueId(), new Invite(c.tag, System.currentTimeMillis() + INVITE_MS));
            p.sendMessage(PREFIX + "Convite enviado para " + t.getName() + ".");
            t.sendMessage(PREFIX + p.getName() + " convidou você para o clã §b[" + c.tag + "]§r. Use §e/clan aceitar§r"
                    + " (vale 2 minutos).");
        }
    }

    private void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        Clan c = inv == null || inv.until < System.currentTimeMillis() ? null : clans.get(inv.tag);
        if (c == null) {
            p.sendMessage(PREFIX + "§cNenhum convite valendo.");
        } else if (clanOf.containsKey(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§cVocê já está num clã.");
        } else {
            c.members.put(p.getUniqueId(), p.getName());
            clanOf.put(p.getUniqueId(), c.tag);
            save();
            syncTeam(c);
            tell(c, p.getName() + " entrou no clã.");
        }
    }

    private void leave(Player p) {
        Clan c = own(p);
        if (c == null) {
            return;
        }
        remove(c, p.getUniqueId());
        p.sendMessage(PREFIX + "Você saiu do clã [" + c.tag + "].");
    }

    private void kick(Player p, String nick) {
        Clan c = own(p);
        if (c == null) {
            return;
        }
        if (!c.leader.equals(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§cSó o líder expulsa.");
            return;
        }
        for (Map.Entry<UUID, String> m : c.members.entrySet()) {
            if (m.getValue().equalsIgnoreCase(nick) && !m.getKey().equals(p.getUniqueId())) {
                String name = m.getValue();
                remove(c, m.getKey());
                Player t = Bukkit.getPlayer(m.getKey());
                if (t != null) {
                    t.sendMessage(PREFIX + "Você foi expulso do clã [" + c.tag + "].");
                }
                tell(c, name + " foi expulso do clã.");
                return;
            }
        }
        p.sendMessage(PREFIX + "§c" + nick + " não está no clã.");
    }

    /** Tira o membro; o lider que sai passa a lideranca ao mais antigo; sem ninguem, o cla acaba. */
    private void remove(Clan c, UUID who) {
        c.members.remove(who);
        clanOf.remove(who);
        if (c.members.isEmpty()) {
            dissolve(c);
            return;
        }
        if (c.leader.equals(who)) {
            Iterator<UUID> it = c.members.keySet().iterator();
            c.leader = it.next();
            tell(c, c.members.get(c.leader) + " agora é o líder.");
        }
        save();
        syncTeam(c);
    }

    private void dissolve(Clan c) {
        for (UUID u : c.members.keySet()) {
            clanOf.remove(u);
        }
        c.members.clear();
        clans.remove(c.tag);
        Team t = board().getTeam(TEAM + c.tag);
        if (t != null) {
            t.unregister();
        }
        save();
    }

    private void info(Player p, String tag) {
        Clan c = tag == null ? null : clans.get(tag);
        if (c == null) {
            p.sendMessage(PREFIX + (tag == null ? "§cVocê não está em nenhum clã." : "§cNão existe o clã " + tag + "."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, String> m : c.members.entrySet()) {
            if (sb.length() > 0) {
                sb.append("§7, ");
            }
            sb.append(Bukkit.getPlayer(m.getKey()) != null ? "§a" : "§7").append(m.getValue());
            if (m.getKey().equals(c.leader)) {
                sb.append(" §6(líder)");
            }
        }
        p.sendMessage(PREFIX + "Clã §b[" + c.tag + "]§r · " + c.members.size() + " membro(s): " + sb);
    }

    private void list(CommandSender s) {
        if (clans.isEmpty()) {
            s.sendMessage(PREFIX + "Nenhum clã ainda. Crie com /clan criar TAG.");
            return;
        }
        List<String> parts = new ArrayList<String>();
        for (Clan c : clans.values()) {
            parts.add("§b[" + c.tag + "]§7 " + c.members.size());
        }
        s.sendMessage(PREFIX + "Clãs: " + String.join("§r, ", parts));
    }

    private static void tell(Clan c, String text) {
        for (UUID u : c.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(PREFIX + text);
            }
        }
    }
}
