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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.xavier.stein.loader.api.Lang;

/**
 * As quatro listas de um JSON na internet: aliados, clas aliados, inimigos e clas inimigos.
 *
 * <p>E o "modo URL" do Combat: em vez de cada um escrever a lista a mao, o cla publica um arquivo e todo mundo aponta
 * para ele. Quem manda no arquivo manda em quem e aliado de quem, e uma troca de lado vale para todos no proximo
 * fetch — que acontece ao entrar num servidor e no botao Atualizar.
 *
 * <p>O formato e simples de escrever a mao, e tudo nele e opcional:
 *
 * <pre>
 * { "allies":     ["Nick1", "Nick2"],
 *   "allyClans":  ["ABC", "[DEF]"],
 *   "enemies":    ["Nick3"],
 *   "enemyClans": ["XYZ"],
 *   "servers": {
 *     "play.exemplo.com": { "allies": ["Nick4"], "enemyClans": ["QWE"] }
 *   } }
 * </pre>
 *
 * <p><b>Quem diz de que servidor e cada lista e o proprio arquivo</b>, em {@code servers}: as listas de fora valem em
 * todo servidor, e as de dentro de um endereco <b>somam</b> a elas quando o jogo esta naquele servidor. Uma URL so,
 * portanto, serve a todos os servidores em que o cla joga. O endereco pode ser o do servidor ("mc.exemplo.com") ou o
 * dominio dele ("exemplo.com"), que vale para os subdominios; havendo dois que sirvam, ganha o mais especifico.
 *
 * <p>Os nomes em portugues tambem servem ({@code aliados}, {@code clasAliados}, {@code inimigos},
 * {@code clasInimigos}, {@code servidores}), e uma tag de cla pode vir com ou sem colchetes. O que nao for nick
 * valido ou tag de tres letras e ignorado sem derrubar o resto: uma linha errada no arquivo do cla nao pode apagar a
 * lista de quem joga.
 *
 * <p>O que chegou fica guardado em {@code config/steincombat.json}, por URL: sem internet, ou com o arquivo fora do
 * ar, vale a ultima lista que baixou — e ela ja esta valendo antes de a busca responder.
 */
final class Feed {

    /** Nenhuma lista: e o que vale enquanto nada foi baixado. */
    static final Feed EMPTY = new Feed();

    /** Teto do que se aceita baixar: e uma lista de nomes, e nao ha por que ler mais do que isso. */
    private static final int LIMIT = 256 * 1024;

    /** Quando esta lista chegou (millis), ou 0 se nunca. */
    long at;
    final List<String> allies = new ArrayList<>();
    final List<String> allyClans = new ArrayList<>();
    final List<String> enemies = new ArrayList<>();
    final List<String> enemyClans = new ArrayList<>();
    /** O que o arquivo diz de cada servidor, pelo endereco dele. Dentro de uma secao dessas, fica vazio. */
    final java.util.Map<String, Feed> servers = new java.util.LinkedHashMap<>();

    /** Quantos nomes e tags ha nela, que e o que se mostra no Painel. */
    int size() {
        return allies.size() + allyClans.size() + enemies.size() + enemyClans.size();
    }

    /**
     * As listas que valem neste servidor: as de fora mais as da secao dele, se houver uma. Sem secao que sirva — e
     * fora de servidor, com {@code host} vazio — devolve estas mesmas.
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
        // Marcado dos dois lados: o inimigo do servidor ganha do aliado geral, que e o mais especifico ganhando.
        out.allies.removeIf(nick -> contains(part.enemies, nick));
        out.allyClans.removeIf(tag -> part.enemyClans.contains(tag));
        out.enemies.removeIf(nick -> contains(part.allies, nick));
        out.enemyClans.removeIf(tag -> part.allyClans.contains(tag));
        return out;
    }

    /**
     * A secao deste servidor: a do endereco igual ou, na falta dela, a do dominio que o termina ("exemplo.com" serve
     * "mc.exemplo.com"); entre varias, a mais longa, que e a mais especifica. Null quando nenhuma serve.
     */
    private Feed section(String host) {
        if (host == null || host.isEmpty() || servers.isEmpty()) {
            return null;
        }
        // A porta sai dos dois lados: quem escreve a lista pensa no servidor, e nao na porta em que ele atende.
        String h = noPort(host);
        Feed best = null;
        int bestLen = -1;
        for (java.util.Map.Entry<String, Feed> e : servers.entrySet()) {
            String key = noPort(e.getKey());
            if ((h.equals(key) || h.endsWith("." + key)) && key.length() > bestLen) {
                best = e.getValue();
                bestLen = key.length();
            }
        }
        return best;
    }

    /** O endereco sem a porta ("mc.exemplo.com:25566" -> "mc.exemplo.com"). */
    private static String noPort(String host) {
        int i = host.lastIndexOf(':');
        if (i < 0) {
            return host;
        }
        for (int j = i + 1; j < host.length(); j++) {
            if (host.charAt(j) < '0' || host.charAt(j) > '9') {
                return host;   // nao e porta (um IPv6, por exemplo): fica como esta
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

    boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Baixa e le a lista daquele endereco. So http e https: o campo e digitado por quem joga, e nao ha por que um
     * endereco de lista abrir arquivo local.
     */
    static Feed fetch(String url) throws IOException {
        String u = url.trim();
        String low = u.toLowerCase(Locale.ROOT);
        if (!low.startsWith("http://") && !low.startsWith("https://")) {
            throw new IOException(tr("steincombat.errScheme"));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "SteinCombat");
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IOException(tr("steincombat.errStatus", String.valueOf(code)));
            }
            StringBuilder body = new StringBuilder();
            try (InputStream in = c.getInputStream();
                    Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) {
                    body.append(buf, 0, n);
                    if (body.length() > LIMIT) {
                        throw new IOException(tr("steincombat.errBig"));
                    }
                }
            }
            return parse(body.toString());
        } finally {
            c.disconnect();
        }
    }

    /** As listas de um texto JSON. */
    static Feed parse(String json) throws IOException {
        JsonElement root;
        try {
            root = new JsonParser().parse(json);
        } catch (RuntimeException e) {
            throw new IOException(tr("steincombat.errJson"));
        }
        if (root == null || !root.isJsonObject()) {
            throw new IOException(tr("steincombat.errJson"));
        }
        Feed f = lists(root.getAsJsonObject(), true);
        f.at = System.currentTimeMillis();
        return f;
    }

    /**
     * As quatro listas de um objeto e, so no de fora ({@code withServers}), as secoes por servidor — uma secao nao
     * tem secoes dentro.
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
                for (java.util.Map.Entry<String, JsonElement> e : raw.getAsJsonObject().entrySet()) {
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
     * Um nick, ou uma tag, nunca esta nas duas listas: marcado inimigo vale acima da alianca, e ficar nas duas so
     * confundiria quem le a lista depois.
     */
    private static void clean(Feed f) {
        f.allies.removeIf(nick -> contains(f.enemies, nick));
        f.allyClans.removeIf(tag -> f.enemyClans.contains(tag));
    }

    /** Os nicks validos de um dos campos (o primeiro que existir), sem repetir. */
    private static void nicks(JsonObject o, List<String> out, String... keys) {
        for (JsonElement e : array(o, keys)) {
            String nick = string(e).trim();
            if (SteinCombat.validNick(nick) && !contains(out, nick)) {
                out.add(nick);
            }
        }
    }

    /** As tags de cla validas de um dos campos, em maiusculas e sem repetir. */
    private static void tags(JsonObject o, List<String> out, String... keys) {
        for (JsonElement e : array(o, keys)) {
            String tag = SteinCombat.tag(string(e));
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

    /** O texto de um item da lista; um item que nao seja texto (um objeto, um numero) vira vazio e cai fora. */
    private static String string(JsonElement e) {
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /**
     * O motivo de a lista nao ter vindo sai no chat de quem joga, entao ele vai na lingua do jogo. O que o proprio
     * Java diz (sem internet, DNS fora do ar) chega em ingles de qualquer jeito — nao ha o que traduzir ali.
     */
    private static String tr(String key) {
        return Lang.tr(key);
    }

    private static String tr(String key, String arg) {
        return Lang.tr(key, arg);
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
     * A copia guardada, como vai para o config/steincombat.json: o proprio arquivo que foi baixado, secoes e tudo,
     * mais a hora em que chegou.
     */
    JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (at != 0L) {
            o.addProperty("at", at);   // so a de fora tem hora: uma secao chegou junto com ela
        }
        o.add("allies", strings(allies));
        o.add("allyClans", strings(allyClans));
        o.add("enemies", strings(enemies));
        o.add("enemyClans", strings(enemyClans));
        if (!servers.isEmpty()) {
            JsonObject byServer = new JsonObject();
            for (java.util.Map.Entry<String, Feed> e : servers.entrySet()) {
                byServer.add(e.getKey(), e.getValue().toJson());
            }
            o.add("servers", byServer);
        }
        return o;
    }

    private static JsonArray strings(List<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) {
            a.add(new com.google.gson.JsonPrimitive(s));
        }
        return a;
    }

    /** A copia guardada de volta, ou null se aquele pedaco do arquivo nao der para ler. */
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
