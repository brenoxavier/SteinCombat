/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import dev.xavier.stein.loader.api.Lang;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The four lists of a JSON file on the internet: allies, allied clans, enemies and enemy clans.
 *
 * <p>It is Combat's "URL mode": instead of everyone writing the list by hand, the clan publishes a
 * file and everyone points at it. Whoever controls the file controls who is allied with whom, and a
 * change of side applies to everyone on the next fetch — which happens when joining a server and on
 * the Refresh button.
 *
 * <p>The format is easy to write by hand, and everything in it is optional:
 *
 * <pre>
 * { "allies":     ["Nick1", "Nick2"],
 *   "allyClans":  ["ABC", "[DEF]"],
 *   "enemies":    ["Nick3"],
 *   "enemyClans": ["XYZ"],
 *   "servers": {
 *     "play.example.com": { "allies": ["Nick4"], "enemyClans": ["QWE"] }
 *   } }
 * </pre>
 *
 * <p><b>The file itself says which server each list is for</b>, in {@code servers}: the outer lists
 * apply on every server, and those under an address are <b>added</b> to them while the game is on
 * that server. A single URL therefore serves every server the clan plays on. The address can be the
 * server's ("mc.example.com") or its domain ("example.com"), which covers the subdomains; when two
 * match, the more specific wins.
 *
 * <p>The Portuguese names work too ({@code aliados}, {@code clasAliados}, {@code inimigos}, {@code
 * clasInimigos}, {@code servidores}), and a clan tag may come with or without brackets. Anything
 * that is not a valid nick or a three-character tag is ignored without dropping the rest: a wrong
 * line in the clan's file must not erase the list of whoever plays.
 *
 * <p>What arrived is kept in {@code config/steincombat.json}, per URL: with no internet, or with
 * the file offline, the last downloaded list applies — and it already applies before the fetch
 * answers.
 */
final class Feed {

    /** No list: what applies while nothing has been downloaded. */
    static final Feed EMPTY = new Feed();

    /** The most that is accepted: it is a list of names, there is no reason to read more. */
    private static final int LIMIT = 256 * 1024;

    private static final int TIMEOUT_MS = 8000;

    /** When this list arrived (millis), or 0 if never. */
    long at;

    final List<String> allies = new ArrayList<>();
    final List<String> allyClans = new ArrayList<>();
    final List<String> enemies = new ArrayList<>();
    final List<String> enemyClans = new ArrayList<>();

    /** What the file says about each server, by its address. Empty inside one of those sections. */
    final Map<String, Feed> servers = new LinkedHashMap<>();

    /** How many names and tags it has, which is what the Panel shows. */
    int size() {
        return allies.size() + allyClans.size() + enemies.size() + enemyClans.size();
    }

    boolean isEmpty() {
        return size() == 0;
    }

    /**
     * The lists that apply on this server: the outer ones plus its section, if there is one. With
     * no matching section — and outside a server, with {@code host} empty — returns this same feed.
     */
    Feed forHost(String host) {
        Feed part = section(host);
        if (part == null) {
            return this;
        }
        Feed out = new Feed();
        out.at = at;
        add(out.allies, allies, part.allies);
        add(out.allyClans, allyClans, part.allyClans);
        add(out.enemies, enemies, part.enemies);
        add(out.enemyClans, enemyClans, part.enemyClans);
        // Marked on both sides: the server's enemy beats the general ally, the more specific wins.
        out.allies.removeIf(nick -> contains(part.enemies, nick));
        out.allyClans.removeIf(tag -> part.enemyClans.contains(tag));
        out.enemies.removeIf(nick -> contains(part.allies, nick));
        out.enemyClans.removeIf(tag -> part.allyClans.contains(tag));
        return out;
    }

    /**
     * This server's section: the one with the same address or, failing that, the one of the domain
     * that ends it ("example.com" covers "mc.example.com"); among several, the longest, which is
     * the most specific. Null when none matches.
     */
    private Feed section(String host) {
        if (host == null || host.isEmpty() || servers.isEmpty()) {
            return null;
        }
        // The port goes on both sides: whoever writes the list thinks of the server, not of the
        // port it listens on.
        String h = noPort(host);
        Feed best = null;
        int bestLen = -1;
        for (Map.Entry<String, Feed> e : servers.entrySet()) {
            String key = noPort(e.getKey());
            if ((h.equals(key) || h.endsWith("." + key)) && key.length() > bestLen) {
                best = e.getValue();
                bestLen = key.length();
            }
        }
        return best;
    }

    /** The address without the port ("mc.example.com:25566" -> "mc.example.com"). */
    private static String noPort(String host) {
        int i = host.lastIndexOf(':');
        if (i < 0) {
            return host;
        }
        for (int j = i + 1; j < host.length(); j++) {
            if (host.charAt(j) < '0' || host.charAt(j) > '9') {
                return host; // not a port (an IPv6 address, for instance): kept as it is
            }
        }
        return host.substring(0, i);
    }

    private static void add(List<String> out, List<String> a, List<String> b) {
        out.addAll(a);
        for (String s : b) {
            if (!contains(out, s)) {
                out.add(s);
            }
        }
    }

    /**
     * Downloads and reads the list at that address. Only http and https: the field is typed by the
     * player, and a list address has no reason to open a local file. Runs off the game thread.
     */
    @SuppressWarnings("deprecation") // new URL(String): kept for the same parsing as always
    static Feed fetch(String url) throws IOException {
        String u = url.trim();
        String low = u.toLowerCase(Locale.ROOT);
        if (!low.startsWith("http://") && !low.startsWith("https://")) {
            throw new IOException(Lang.tr("steincombat.errScheme"));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "SteinCombat");
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IOException(Lang.tr("steincombat.errStatus", String.valueOf(code)));
            }
            StringBuilder body = new StringBuilder();
            try (InputStream in = c.getInputStream();
                    Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) {
                    body.append(buf, 0, n);
                    if (body.length() > LIMIT) {
                        throw new IOException(Lang.tr("steincombat.errBig"));
                    }
                }
            }
            return parse(body.toString());
        } finally {
            c.disconnect();
        }
    }

    /**
     * The lists of a JSON text. The reasons it fails go to the player's chat, so they are in the
     * game's language; what Java itself says (no internet, DNS down) arrives in English anyway.
     */
    static Feed parse(String json) throws IOException {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException e) {
            throw new IOException(Lang.tr("steincombat.errJson"));
        }
        if (root == null || !root.isJsonObject()) {
            throw new IOException(Lang.tr("steincombat.errJson"));
        }
        Feed f = lists(root.getAsJsonObject(), true);
        f.at = System.currentTimeMillis();
        return f;
    }

    /**
     * The four lists of an object and, only in the outer one ({@code withServers}), the per-server
     * sections — a section has no sections inside.
     */
    private static Feed lists(JsonObject o, boolean withServers) {
        Feed f = new Feed();
        nicks(o, f.allies, "allies", "aliados");
        nicks(o, f.enemies, "enemies", "inimigos");
        tags(o, f.allyClans, "allyClans", "clasAliados", "clans");
        tags(o, f.enemyClans, "enemyClans", "clasInimigos");
        if (withServers) {
            JsonElement raw = o.has("servers") ? o.get("servers") : o.get("servidores");
            if (raw != null && raw.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject().entrySet()) {
                    String host = e.getKey().trim().toLowerCase(Locale.ROOT);
                    if (!host.isEmpty() && e.getValue().isJsonObject()) {
                        f.servers.put(host, lists(e.getValue().getAsJsonObject(), false));
                    }
                }
            }
        }
        clean(f);
        return f;
    }

    /**
     * A nick, or a tag, is never on both lists: marked as enemy beats the alliance, and being on
     * both would only confuse whoever reads the list later.
     */
    private static void clean(Feed f) {
        f.allies.removeIf(nick -> contains(f.enemies, nick));
        f.allyClans.removeIf(tag -> f.enemyClans.contains(tag));
    }

    /** The valid nicks of one of the fields (the first that exists), without repeats. */
    private static void nicks(JsonObject o, List<String> out, String... keys) {
        for (JsonElement e : array(o, keys)) {
            String nick = string(e).trim();
            if (Clans.validNick(nick) && !contains(out, nick)) {
                out.add(nick);
            }
        }
    }

    /** The valid clan tags of one of the fields, in upper case and without repeats. */
    private static void tags(JsonObject o, List<String> out, String... keys) {
        for (JsonElement e : array(o, keys)) {
            String tag = Clans.tag(string(e));
            if (tag != null && !out.contains(tag)) {
                out.add(tag);
            }
        }
    }

    private static JsonArray array(JsonObject o, String... keys) {
        for (String key : keys) {
            JsonElement e = o.get(key);
            if (e != null && e.isJsonArray()) {
                return e.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    /**
     * The text of a list item; an item that is not text (an object, a number) becomes empty and
     * drops out.
     */
    private static String string(JsonElement e) {
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static boolean contains(List<String> list, String nick) {
        for (String s : list) {
            if (s.equalsIgnoreCase(nick)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The stored copy, as it goes into config/steincombat.json: the downloaded file itself,
     * sections and all, plus the time it arrived.
     */
    JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (at != 0L) {
            o.addProperty("at", at); // only the outer one has a time: a section arrived with it
        }
        o.add("allies", strings(allies));
        o.add("allyClans", strings(allyClans));
        o.add("enemies", strings(enemies));
        o.add("enemyClans", strings(enemyClans));
        if (!servers.isEmpty()) {
            JsonObject byServer = new JsonObject();
            for (Map.Entry<String, Feed> e : servers.entrySet()) {
                byServer.add(e.getKey(), e.getValue().toJson());
            }
            o.add("servers", byServer);
        }
        return o;
    }

    private static JsonArray strings(List<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) {
            a.add(new JsonPrimitive(s));
        }
        return a;
    }

    /** The stored copy read back, or null if that part of the file cannot be read. */
    static Feed fromJson(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        JsonObject o = raw.getAsJsonObject();
        Feed f = lists(o, true);
        try {
            f.at = o.has("at") ? o.get("at").getAsLong() : 0L;
        } catch (RuntimeException e) {
            f.at = 0L;
        }
        return f;
    }
}
