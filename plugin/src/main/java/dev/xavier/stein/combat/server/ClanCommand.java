/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat.server;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /clan (or /cla): criar TAG, convidar nick, aceitar, sair, expulsar nick, info [TAG], lista,
 * desfazer [TAG]. The leader invites and kicks; when the leader leaves, the oldest member leads;
 * the last one to leave ends the clan. The subcommand names are what players type: they never
 * change.
 */
final class ClanCommand {

    /** An invitation lasts 2 minutes. */
    private static final long INVITE_MS = 120_000L;

    private static final String ADMIN = "steincombat.admin";

    private static final class Invite {
        final String tag;
        final long until;

        Invite(String tag, long until) {
            this.tag = tag;
            this.until = until;
        }
    }

    private final ClanStore store;
    private final Messages msg;
    private final Map<UUID, Invite> invites = new HashMap<>();

    ClanCommand(ClanStore store, Messages msg) {
        this.store = store;
        this.msg = msg;
    }

    void run(CommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("lista")) {
            list(sender);
            return;
        }
        if (sub.equals("desfazer") && args.length > 1 && sender.hasPermission(ADMIN)) {
            Clan c = store.get(args[1].toUpperCase(Locale.ROOT));
            if (c == null) {
                sender.sendMessage(msg.line("clan.notFound", args[1]));
            } else {
                dissolve(c);
                sender.sendMessage(msg.line("clan.dissolved", c.tag));
            }
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(msg.line("console.playersOnly"));
            return;
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
            info(
                    p,
                    args.length > 1
                            ? args[1].toUpperCase(Locale.ROOT)
                            : store.tagOf(p.getUniqueId()));
        } else if (sub.equals("desfazer")) {
            dissolveOwn(p);
        } else {
            p.sendMessage(msg.line("help.1"));
            p.sendMessage(msg.line("help.2"));
        }
    }

    /** The player's clan, or null after telling them they have none. */
    private Clan own(Player p) {
        Clan c = store.clanOf(p.getUniqueId());
        if (c == null) {
            p.sendMessage(msg.line("clan.notIn"));
        }
        return c;
    }

    private void create(Player p, String raw) {
        String tag = raw.toUpperCase(Locale.ROOT);
        if (!tag.matches("[A-Z0-9]{3}")) {
            p.sendMessage(msg.line("create.badTag"));
        } else if (store.tagOf(p.getUniqueId()) != null) {
            p.sendMessage(msg.line("create.alreadyIn", store.tagOf(p.getUniqueId())));
        } else if (store.exists(tag)) {
            p.sendMessage(msg.line("create.exists", tag));
        } else {
            Clan c = new Clan(tag, p.getUniqueId());
            store.add(c);
            store.join(c, p.getUniqueId(), p.getName());
            store.save();
            ClanTeams.sync(c);
            p.sendMessage(msg.line("create.done", tag));
        }
    }

    private void invite(Player p, String nick) {
        Clan c = own(p);
        if (c == null) {
            return;
        }
        if (!c.leader.equals(p.getUniqueId())) {
            p.sendMessage(msg.line("leader.invite"));
            return;
        }
        Player t = Bukkit.getPlayerExact(nick);
        if (t == null) {
            p.sendMessage(msg.line("invite.offline", nick));
        } else if (store.tagOf(t.getUniqueId()) != null) {
            p.sendMessage(msg.line("invite.inClan", t.getName()));
        } else {
            invites.put(t.getUniqueId(), new Invite(c.tag, System.currentTimeMillis() + INVITE_MS));
            p.sendMessage(msg.line("invite.sent", t.getName()));
            t.sendMessage(msg.line("invite.received", p.getName(), c.tag));
        }
    }

    private void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        Clan c = inv == null || inv.until < System.currentTimeMillis() ? null : store.get(inv.tag);
        if (c == null) {
            p.sendMessage(msg.line("accept.none"));
        } else if (store.tagOf(p.getUniqueId()) != null) {
            p.sendMessage(msg.line("accept.inClan"));
        } else {
            store.join(c, p.getUniqueId(), p.getName());
            store.save();
            ClanTeams.sync(c);
            tell(c, msg.line("accept.joined", p.getName()));
        }
    }

    private void leave(Player p) {
        Clan c = own(p);
        if (c == null) {
            return;
        }
        remove(c, p.getUniqueId());
        p.sendMessage(msg.line("leave.done", c.tag));
    }

    private void kick(Player p, String nick) {
        Clan c = own(p);
        if (c == null) {
            return;
        }
        if (!c.leader.equals(p.getUniqueId())) {
            p.sendMessage(msg.line("leader.kick"));
            return;
        }
        for (Map.Entry<UUID, String> m : c.members.entrySet()) {
            if (m.getValue().equalsIgnoreCase(nick) && !m.getKey().equals(p.getUniqueId())) {
                String name = m.getValue();
                UUID who = m.getKey();
                remove(c, who);
                Player t = Bukkit.getPlayer(who);
                if (t != null) {
                    t.sendMessage(msg.line("kick.you", c.tag));
                }
                tell(c, msg.line("kick.done", name));
                return;
            }
        }
        p.sendMessage(msg.line("kick.notMember", nick));
    }

    private void dissolveOwn(Player p) {
        Clan c = own(p);
        if (c == null) {
            return;
        }
        if (!c.leader.equals(p.getUniqueId())) {
            p.sendMessage(msg.line("leader.dissolve"));
            return;
        }
        dissolve(c);
        p.sendMessage(msg.line("clan.dissolved", c.tag));
    }

    /**
     * Removes the member; a leader who leaves passes the lead to the oldest member; with nobody
     * left, the clan ends.
     */
    private void remove(Clan c, UUID who) {
        store.leave(c, who);
        if (c.members.isEmpty()) {
            dissolve(c);
            return;
        }
        if (c.leader.equals(who)) {
            Iterator<UUID> it = c.members.keySet().iterator();
            c.leader = it.next();
            tell(c, msg.line("leader.new", c.members.get(c.leader)));
        }
        store.save();
        ClanTeams.sync(c);
    }

    private void dissolve(Clan c) {
        store.remove(c);
        ClanTeams.remove(c);
        store.save();
    }

    private void info(Player p, String tag) {
        Clan c = tag == null ? null : store.get(tag);
        if (c == null) {
            p.sendMessage(tag == null ? msg.line("clan.notIn") : msg.line("clan.notFound", tag));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, String> m : c.members.entrySet()) {
            if (sb.length() > 0) {
                sb.append("§7, ");
            }
            sb.append(Bukkit.getPlayer(m.getKey()) != null ? "§a" : "§7").append(m.getValue());
            if (m.getKey().equals(c.leader)) {
                sb.append(msg.get("info.leader"));
            }
        }
        p.sendMessage(msg.line("info.line", c.tag, c.members.size(), sb));
    }

    private void list(CommandSender s) {
        if (store.isEmpty()) {
            s.sendMessage(msg.line("list.empty"));
            return;
        }
        List<String> parts = new ArrayList<>();
        for (Clan c : store.all()) {
            parts.add("§b[" + c.tag + "]§7 " + c.members.size());
        }
        s.sendMessage(msg.line("list.line", String.join("§r, ", parts)));
    }

    /** A line to every online member of the clan. */
    private static void tell(Clan c, String line) {
        for (UUID u : c.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendMessage(line);
            }
        }
    }
}
