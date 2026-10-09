/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opcoes do Stein Combat, em config/steincombat.json. */
final class CombatConfig {


    /** Cores possiveis (codigo de formatacao do jogo), na ordem do botao. */
    static final String COLORS = "abed96fc7";

    /** Pintar os nicks pela relacao, no Tab, acima da cabeca e no minimapa. */
    boolean colorNames = true;
    /** Do meu cla: verde. */
    char clanColor = 'a';
    /** Aliado que nao e do meu cla: azul. */
    char allyColor = '9';
    /** Sem cla e sem alianca: branco. */
    char neutralColor = 'f';
    /** De outro cla, que nao e aliado: vermelho. */
    char enemyColor = 'c';
    /**
     * Correr tambem para tras e para os lados. <b>Em servidor, anticheat costuma punir</b>: avisa ao ser ligada, como o
     * No Hit Delay e a corrida no golpe. Nasce ligada: e o padrao escolhido para o Stein.
     */
    boolean allDirections = true;
    /** Golpe em aliado cancelado. */
    boolean protectAllies = true;
    /**
     * Golpe que atravessa o meu cla: o golpe cancelado num do meu cla vai para o primeiro da linha da mira, atras
     * dele e dentro do alcance, que nao e do meu cla. Sem ninguem la, so cancela.
     */
    boolean clanHitThrough = true;
    /**
     * Com a tecla de esconder ligada, o aliado que nao e do meu cla vira fantasma em vez de continuar solido: a
     * silhueta a 15% do proprio jogo, sem armadura. Ele continua ali para todo o resto — inclusive para o golpe.
     */
    boolean ghostAllies = true;
    /** Os membros do meu cla (plugin SteinCombat do servidor) contam como aliados. */
    boolean clanAllies = true;
    /** Ler o cla pelo prefixo do time ("[ABC] Nick"), para os servidores com outro plugin de cla. */
    boolean clanFromPrefix = true;
    /**
     * Tratar como inimigo quem tem um colchete diferente do meu no nome. <b>Desligada</b>: num servidor comum
     * "[VIP]", "[ADM]" e "[PRO]" tem as mesmas tres letras de uma tag de cla, e meia lista virava inimiga sem ser.
     * Ligue so onde o colchete e mesmo o cla de todo mundo.
     */
    boolean enemyFromPrefix = false;
    /** O nick junto da distancia, no elemento do inimigo mais proximo. */
    boolean nearestName = true;
    /** O nick junto da distancia, no elemento do aliado mais proximo. */
    boolean nearestAllyName = true;
    /** Smart Effect ligado, e o que ele mantem. */
    boolean smartEffect = false;
    boolean autoGapple = true;
    boolean autoSpeed = false;
    boolean autoStrength = false;
    /** So com inimigo (quem nao e aliado nem do meu cla) a ate effectEnemyRadius blocos. */
    boolean effectWaitEnemy = false;
    int effectEnemyRadius = 8;
    /** Tambem nos segundos depois de levar dano. Sem nenhuma das duas: sempre. */
    boolean effectOnDamage = false;
    /** Auto Refill: buscar armadura no bau virtual do servidor. */
    boolean autoRefill = false;
    String refillCommand = "/bau vip";
    boolean refillShowWindow = false;
    /**
     * AutoRefill.WHEN_EMPTY (busca quando a reserva de uma peca zera), CONSTANT (mantem refillTarget por peca) ou ON_KEY
     * (uma ida ate refillTarget quando a tecla e apertada, e o Auto Refill se desliga sozinho).
     */
    int refillMode = AutoRefill.WHEN_EMPTY;
    int refillTarget = 2;
    /** Guardar no bau as armaduras gastas da bolsa. */
    boolean refillDeposit = false;

    /**
     * Auto Protect ligado: a espada defendendo sozinha, nos dois casos que ele cuida (cada um com a sua chave). O
     * arquivo de antes das duas chaves so tinha esta, que era a defesa com a tela aberta: ela continua aqui, e a
     * defesa ({@link #protectDefend}) nasce ligada.
     */
    boolean autoProtect = false;
    /** "Defender": com uma tela aberta (inventario, chat, Painel), a espada defendendo. */
    boolean protectDefend = true;
    /**
     * "Segurar a vida": com a vida no limite no meio da luta, a espada defendendo ate a vida voltar. Defendendo, o
     * golpe nao sai (o jogo nao bate com o item em uso) — e o que salva do contra-ataque do mcMMO no grude.
     */
    boolean protectSurvive = true;
    /** Entra com a vida nisto ou abaixo (pontos de vida: 2 = um coracao). */
    int protectLowHealth = 6;
    /** Sai, e as pocoes voltam, com a vida nisto ou acima. Sempre ao menos um coracao acima de protectLowHealth. */
    int protectResumeHealth = 14;

    /**
     * Autoclick: com o botao esquerdo segurado e nenhuma tela aberta, cliques no ritmo de {@link #autoclickCps}, na fila
     * do mouse como os de verdade. O ultimo na ordem: espera a armadura, o refil, a comida e as pocoes e a defesa.
     */
    boolean autoclick = false;
    /** 0: enquanto o botao esquerdo esta segurado; 1: sempre (constante), sem segurar nada. */
    int autoclickMode = 0;
    /** Cliques por segundo do Autoclick. */
    int autoclickCps = 20;
    static final int AUTOCLICK_MIN = 5;
    static final int AUTOCLICK_MAX = 100;

    /** Auto Armor ligado. */
    boolean autoArmor = false;
    /** Uma regra por peca (o modo "Por peca"), na ordem do jogo: botas, calca, peitoral, capacete. */
    final AutoArmor.Rule[] armorRules = {new AutoArmor.Rule(), new AutoArmor.Rule(), new AutoArmor.Rule(),
        new AutoArmor.Rule()};
    /** A regra unica do modo simples, para as quatro pecas. */
    final AutoArmor.Rule armorAll = new AutoArmor.Rule();
    /** Modo "Por peca" (false: simples, uma regra para tudo). */
    boolean armorPerPiece = false;

    /** A volta do "Segurar a vida" sempre ao menos um coracao acima da entrada (senao entraria e sairia junto). */
    void fixProtectHealth() {
        if (protectResumeHealth < protectLowHealth + 2) {
            protectResumeHealth = Math.min(20, protectLowHealth + 2);
        }
    }

    /** A regra que vale para a peca agora. */
    AutoArmor.Rule armorRule(int piece) {
        return armorPerPiece ? armorRules[piece] : armorAll;
    }

    /** Cor da lista de funcionalidades ligadas (codigo de cor do jogo). */
    char featuresColor = 'f';
    /** A lista em ordem de largura (a mais comprida em cima); desligada, na ordem do Painel. */
    boolean featuresByWidth = true;
    /** Cores da lista: 0 por categoria, 1 arco-iris, 2 a cor fixa ({@link #featuresColor}). */
    int featuresStyle = 0;
    /** Fundo escuro atras de cada linha da lista. */
    boolean featuresBackground = true;
    /** Funcionalidades ligadas que nao aparecem na lista (os ids de {@link Features}). */
    final List<String> featuresHidden = new ArrayList<>();
    /**
     * "No Hit Delay": sem a punicao de errar o golpe do 1.8 — errar um clique nao trava mais os golpes por meio
     * segundo.
     */
    boolean noHitDelay = true;
    /**
     * A corrida atravessa o golpe: sem o "sprint reset" do 1.8, em que todo golpe que empurra derruba a corrida.
     */
    boolean sprintOnHit = true;
    /** Sub-opcao do Keep Sprint: o golpe tambem nao freia. */
    boolean noHitSlowdown = false;
    /** Nicks dos aliados, como foram escritos. */
    final List<String> allies = new ArrayList<>();
    /** Clas aliados, pela tag em maiusculas: todo mundo com a tag conta como aliado. */
    final List<String> allyClans = new ArrayList<>();
    /**
     * Nicks marcados como inimigos, como foram escritos. Valem acima de tudo: um inimigo marcado e inimigo mesmo sem
     * cla, num cla aliado ou no meu cla. Um nick nunca esta nas duas listas.
     */
    final List<String> enemies = new ArrayList<>();
    /**
     * Clas inimigos, pela tag em maiusculas: todo mundo com a tag conta como inimigo, mesmo num servidor em que eu nao
     * tenha cla. Um nick marcado a mao vale acima da tag dele — a marca no nick e sobre aquela pessoa.
     */
    final List<String> enemyClans = new ArrayList<>();

    /**
     * Modo URL: as quatro listas vem de um JSON na internet ({@link Feed}) e <b>as escritas a mao ficam paradas</b> —
     * e sempre uma ou a outra. Desligando, as listas a mao voltam como estavam: elas continuam no arquivo.
     */
    boolean remote = false;
    /**
     * O endereco do JSON — <b>um so</b>, para todos os servidores: quem diz de que servidor e cada lista e o
     * proprio arquivo, na secao "servers" dele (ver {@link Feed}).
     */
    String remoteUrl = "";
    /**
     * A ultima lista que cada URL entregou, pela propria URL. E o que vale antes de a busca responder, e o que
     * continua valendo sem internet ou com o arquivo fora do ar.
     */
    final Map<String, Feed> remoteFeeds = new LinkedHashMap<>();

    /** Os padroes do Combat (o que vale sem arquivo e o que "Restaurar padrao" traz de volta), no pacote do mod. */
    static final String DEFAULTS = "/assets/steincombat/defaults.json";

    /** Os valores dos campos, com os padroes do pacote ({@link #DEFAULTS}) por cima. */
    CombatConfig() {
        try (java.io.InputStream in = CombatConfig.class.getResourceAsStream(DEFAULTS)) {
            if (in != null) {
                read(new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        } catch (Exception e) {
            System.out.println("[mods] Combat: padroes do pacote ilegiveis: " + e);
        }
    }

    static CombatConfig load(File file) {
        CombatConfig c = new CombatConfig();
        if (!file.isFile()) {
            c.save(file);
            return c;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            c.read(new JsonParser().parse(r).getAsJsonObject());
        } catch (Exception e) {
            System.out.println("[mods] Combat: " + file + " ilegivel, padroes: " + e);
        }
        return c;
    }

    /**
     * Le as opcoes de um JSON por cima das de agora: o arquivo do jogador, ou os padroes do pacote do mod
     * ({@link #DEFAULTS}). O que falta no JSON fica como esta.
     */
    private void read(JsonObject o) {
        CombatConfig c = this;
        c.allDirections = bool(o, "allDirections", c.allDirections);
        c.protectAllies = bool(o, "protectAllies", c.protectAllies);
        c.ghostAllies = bool(o, "ghostAllies", c.ghostAllies);
        c.colorNames = bool(o, "colorNames", bool(o, "colorAllies", c.colorNames));
        // Arquivo de antes das quatro cores (so tinha "allyColor", que valia para aliado e para o meu cla):
        // as cores novas entram todas no padrao, senao o verde de la viraria a cor de aliado e ninguem entenderia
        // por que aliado e meu cla ficaram iguais.
        if (o.has("clanColor")) {
            c.clanColor = color(o, "clanColor", c.clanColor);
            c.allyColor = color(o, "allyColor", c.allyColor);
            c.neutralColor = color(o, "neutralColor", c.neutralColor);
            c.enemyColor = color(o, "enemyColor", c.enemyColor);
        }
        c.clanAllies = bool(o, "clanAllies", c.clanAllies);
        c.clanFromPrefix = bool(o, "clanFromPrefix", c.clanFromPrefix);
        c.enemyFromPrefix = bool(o, "enemyFromPrefix", c.enemyFromPrefix);
        c.nearestName = bool(o, "nearestName", c.nearestName);
        c.nearestAllyName = bool(o, "nearestAllyName", c.nearestAllyName);
        c.featuresColor = color(o, "featuresColor", c.featuresColor);
        c.featuresByWidth = bool(o, "featuresByWidth", c.featuresByWidth);
        if (o.has("featuresStyle")) {
            c.featuresStyle = Math.max(0, Math.min(2, o.get("featuresStyle").getAsInt()));
        }
        c.featuresBackground = bool(o, "featuresBackground", c.featuresBackground);
        if (o.has("featuresHidden") && o.get("featuresHidden").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("featuresHidden")) {
                c.featuresHidden.add(e.getAsString());
            }
        }
        c.autoArmor = bool(o, "autoArmor", c.autoArmor);
        c.smartEffect = bool(o, "smartEffect", c.smartEffect);
        c.autoGapple = bool(o, "autoGapple", c.autoGapple);
        c.autoSpeed = bool(o, "autoSpeed", c.autoSpeed);
        c.autoStrength = bool(o, "autoStrength", c.autoStrength);
        c.effectWaitEnemy = bool(o, "effectWaitEnemy", c.effectWaitEnemy);
        c.effectOnDamage = bool(o, "effectOnDamage", c.effectOnDamage);
        c.autoProtect = bool(o, "autoProtect", c.autoProtect);
        c.protectDefend = bool(o, "protectDefend", c.protectDefend);
        // Arquivo de antes do "Segurar a vida": quem ja usava o Auto Protect continua com ele como estava (so a
        // defesa com a tela aberta); a parte nova fica para ligar no Painel.
        c.protectSurvive = bool(o, "protectSurvive", c.protectSurvive && !c.autoProtect);
        if (o.has("protectLowHealth")) {
            c.protectLowHealth = Math.max(2, Math.min(16, o.get("protectLowHealth").getAsInt()));
        }
        if (o.has("protectResumeHealth")) {
            c.protectResumeHealth = Math.max(4, Math.min(20, o.get("protectResumeHealth").getAsInt()));
        }
        c.fixProtectHealth();
        c.clanHitThrough = bool(o, "clanHitThrough", c.clanHitThrough);
        c.autoclick = bool(o, "autoclick", c.autoclick);
        if (o.has("autoclickMode")) {
            c.autoclickMode = Math.max(0, Math.min(1, o.get("autoclickMode").getAsInt()));
        }
        if (o.has("autoclickCps")) {
            c.autoclickCps = Math.max(AUTOCLICK_MIN, Math.min(AUTOCLICK_MAX, o.get("autoclickCps").getAsInt()));
        }
        c.autoRefill = bool(o, "autoRefill", c.autoRefill);
        if (o.has("refillCommand") && !o.get("refillCommand").getAsString().trim().isEmpty()) {
            c.refillCommand = o.get("refillCommand").getAsString();
        }
        c.refillShowWindow = bool(o, "refillShowWindow", c.refillShowWindow);
        if (o.has("refillMode")) {
            int m = o.get("refillMode").getAsInt();
            c.refillMode = m == AutoRefill.CONSTANT || m == AutoRefill.ON_KEY ? m : AutoRefill.WHEN_EMPTY;
        }
        if (o.has("refillTarget")) {
            c.refillTarget = Math.max(1, Math.min(27, o.get("refillTarget").getAsInt()));
        }
        c.refillDeposit = bool(o, "refillDeposit", c.refillDeposit);
        if (o.has("effectEnemyRadius")) {
            c.effectEnemyRadius = Math.max(2, Math.min(64, o.get("effectEnemyRadius").getAsInt()));
        }
        c.armorPerPiece = bool(o, "armorPerPiece", c.armorPerPiece);
        rule(o, "armorAll", c.armorAll);
        String[] names = {"armorBoots", "armorLeggings", "armorChestplate", "armorHelmet"};
        for (int i = 0; i < 4; i++) {
            rule(o, names[i], c.armorRules[i]);
        }
        c.noHitDelay = bool(o, "noHitDelay", c.noHitDelay);
        c.sprintOnHit = bool(o, "sprintOnHit", c.sprintOnHit);
        c.noHitSlowdown = bool(o, "noHitSlowdown", c.noHitSlowdown);
        if (o.has("allies") && o.get("allies").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("allies")) {
                String nick = e.getAsString().trim();
                if (SteinCombat.validNick(nick) && !c.hasAlly(nick)) {
                    c.allies.add(nick);
                }
            }
        }
        if (o.has("allyClans") && o.get("allyClans").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("allyClans")) {
                String tag = SteinCombat.tag(e.getAsString());
                if (tag != null && !c.allyClans.contains(tag)) {
                    c.allyClans.add(tag);
                }
            }
        }
        if (o.has("enemyClans") && o.get("enemyClans").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("enemyClans")) {
                String tag = SteinCombat.tag(e.getAsString());
                if (tag != null && !c.enemyClans.contains(tag)) {
                    c.enemyClans.add(tag);
                }
            }
        }
        c.loadRemote(o.get("remote"));
        if (o.has("enemies") && o.get("enemies").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("enemies")) {
                String nick = e.getAsString().trim();
                if (SteinCombat.validNick(nick) && !c.hasEnemy(nick) && !c.hasAlly(nick)) {
                    c.enemies.add(nick);
                }
            }
        }
    }

    void save(File file) {
        JsonObject o = new JsonObject();
        o.addProperty("allDirections", allDirections);
        o.addProperty("protectAllies", protectAllies);
        o.addProperty("ghostAllies", ghostAllies);
        o.addProperty("colorNames", colorNames);
        o.addProperty("clanColor", String.valueOf(clanColor));
        o.addProperty("allyColor", String.valueOf(allyColor));
        o.addProperty("neutralColor", String.valueOf(neutralColor));
        o.addProperty("enemyColor", String.valueOf(enemyColor));
        o.addProperty("clanAllies", clanAllies);
        o.addProperty("clanFromPrefix", clanFromPrefix);
        o.addProperty("enemyFromPrefix", enemyFromPrefix);
        o.addProperty("nearestName", nearestName);
        o.addProperty("nearestAllyName", nearestAllyName);
        o.addProperty("featuresColor", String.valueOf(featuresColor));
        o.addProperty("featuresByWidth", featuresByWidth);
        o.addProperty("featuresStyle", featuresStyle);
        o.addProperty("featuresBackground", featuresBackground);
        JsonArray hidden = new JsonArray();
        for (String s : featuresHidden) {
            hidden.add(new com.google.gson.JsonPrimitive(s));
        }
        o.add("featuresHidden", hidden);
        o.addProperty("autoArmor", autoArmor);
        o.addProperty("smartEffect", smartEffect);
        o.addProperty("autoGapple", autoGapple);
        o.addProperty("autoSpeed", autoSpeed);
        o.addProperty("autoStrength", autoStrength);
        o.addProperty("effectWaitEnemy", effectWaitEnemy);
        o.addProperty("effectEnemyRadius", effectEnemyRadius);
        o.addProperty("effectOnDamage", effectOnDamage);
        o.addProperty("autoProtect", autoProtect);
        o.addProperty("protectDefend", protectDefend);
        o.addProperty("protectSurvive", protectSurvive);
        o.addProperty("protectLowHealth", protectLowHealth);
        o.addProperty("protectResumeHealth", protectResumeHealth);
        o.addProperty("clanHitThrough", clanHitThrough);
        o.addProperty("autoclick", autoclick);
        o.addProperty("autoclickMode", autoclickMode);
        o.addProperty("autoclickCps", autoclickCps);
        o.addProperty("autoRefill", autoRefill);
        o.addProperty("refillCommand", refillCommand);
        o.addProperty("refillShowWindow", refillShowWindow);
        o.addProperty("refillMode", refillMode);
        o.addProperty("refillTarget", refillTarget);
        o.addProperty("refillDeposit", refillDeposit);
        o.addProperty("armorPerPiece", armorPerPiece);
        o.add("armorAll", rule(armorAll));
        String[] names = {"armorBoots", "armorLeggings", "armorChestplate", "armorHelmet"};
        for (int i = 0; i < 4; i++) {
            o.add(names[i], rule(armorRules[i]));
        }
        o.addProperty("noHitDelay", noHitDelay);
        o.addProperty("sprintOnHit", sprintOnHit);
        o.addProperty("noHitSlowdown", noHitSlowdown);
        JsonArray a = new JsonArray();
        for (String nick : allies) {
            a.add(new com.google.gson.JsonPrimitive(nick));
        }
        o.add("allies", a);
        JsonArray cl = new JsonArray();
        for (String tag : allyClans) {
            cl.add(new com.google.gson.JsonPrimitive(tag));
        }
        o.add("allyClans", cl);
        JsonArray en = new JsonArray();
        for (String nick : enemies) {
            en.add(new com.google.gson.JsonPrimitive(nick));
        }
        o.add("enemies", en);
        JsonArray ec = new JsonArray();
        for (String tag : enemyClans) {
            ec.add(new com.google.gson.JsonPrimitive(tag));
        }
        o.add("enemyClans", ec);
        o.add("remote", saveRemote());
        File dir = file.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(o, w);
        } catch (Exception e) {
            System.out.println("[mods] Combat: nao gravou " + file + ": " + e);
        }
    }

    /** O bloco "remote" do arquivo: as opcoes do modo URL, as URLs e a ultima lista de cada uma. */
    private void loadRemote(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return;
        }
        JsonObject o = raw.getAsJsonObject();
        remote = bool(o, "enabled", remote);
        remoteUrl = urlOf(o, "url");
        if (o.has("feeds") && o.get("feeds").isJsonObject()) {
            for (java.util.Map.Entry<String, JsonElement> e : o.getAsJsonObject("feeds").entrySet()) {
                Feed f = Feed.fromJson(e.getValue());
                if (f != null && !e.getKey().trim().isEmpty()) {
                    remoteFeeds.put(e.getKey().trim(), f);
                }
            }
        }
    }

    /** Quantas listas guardadas se mantem alem das que estao em uso (ver {@link #saveRemote}). */
    private static final int SPARE_FEEDS = 8;

    /**
     * O bloco "remote" de volta ao arquivo. Ficam guardadas as listas das URLs em uso e mais algumas das ultimas
     * baixadas: o campo do Painel grava a cada tecla, e uma URL sendo digitada deixa de ser "a em uso" por um
     * instante — guardando so as em uso, apagar uma letra jogaria fora a copia que faz a lista valer sem internet.
     */
    private JsonObject saveRemote() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", remote);
        o.addProperty("url", remoteUrl);
        List<Map.Entry<String, Feed>> rest = new ArrayList<>();
        JsonObject feeds = new JsonObject();
        for (Map.Entry<String, Feed> e : remoteFeeds.entrySet()) {
            if (used(e.getKey())) {
                feeds.add(e.getKey(), e.getValue().toJson());
            } else {
                rest.add(e);
            }
        }
        rest.sort((a, b) -> Long.compare(b.getValue().at, a.getValue().at));   // a mais recente primeiro
        for (int i = 0; i < rest.size() && i < SPARE_FEEDS; i++) {
            feeds.add(rest.get(i).getKey(), rest.get(i).getValue().toJson());
        }
        o.add("feeds", feeds);
        return o;
    }

    /** Esta URL ainda e a que esta valendo? */
    private boolean used(String url) {
        return url.equals(remoteUrl);
    }

    /** Grava o endereco como ele fica no arquivo. */
    void url(String url) {
        remoteUrl = clean(url);
    }

    /** A ultima lista daquela URL, ou uma vazia. */
    Feed feed(String url) {
        Feed f = url == null || url.isEmpty() ? null : remoteFeeds.get(url);
        return f == null ? Feed.EMPTY : f;
    }

    /** Guarda o que a URL acabou de entregar. */
    void feed(String url, Feed f) {
        if (url != null && !url.isEmpty() && f != null) {
            remoteFeeds.put(url, f);
        }
    }

    /** Marca o cla como aliado, tirando dos inimigos: as duas listas de clas tambem nao se cruzam. */
    void addAllyClan(String tag) {
        enemyClans.remove(tag);
        if (!allyClans.contains(tag)) {
            allyClans.add(tag);
        }
    }

    /** Marca o cla como inimigo, tirando dos aliados. */
    void addEnemyClan(String tag) {
        allyClans.remove(tag);
        if (!enemyClans.contains(tag)) {
            enemyClans.add(tag);
        }
    }

    boolean hasAlly(String nick) {
        for (String a : allies) {
            if (a.equalsIgnoreCase(nick)) {
                return true;
            }
        }
        return false;
    }

    void removeAlly(String nick) {
        allies.removeIf(a -> a.equalsIgnoreCase(nick));
    }

    boolean hasEnemy(String nick) {
        for (String e : enemies) {
            if (e.equalsIgnoreCase(nick)) {
                return true;
            }
        }
        return false;
    }

    void removeEnemy(String nick) {
        enemies.removeIf(e -> e.equalsIgnoreCase(nick));
    }

    /** Marca como aliado, tirando dos inimigos: as duas listas nao se cruzam. */
    void addAlly(String nick) {
        removeEnemy(nick);
        if (!hasAlly(nick)) {
            allies.add(nick);
        }
    }

    /** Marca como inimigo, tirando dos aliados. */
    void addEnemy(String nick) {
        removeAlly(nick);
        if (!hasEnemy(nick)) {
            enemies.add(nick);
        }
    }

    /** Uma URL do arquivo, sem espacos em volta. */
    private static String urlOf(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? clean(o.get(key).getAsString()) : "";
    }

    /**
     * O endereco como ele fica gravado: so sem os espacos em volta. Se e http, https ou coisa nenhuma, quem diz e a
     * hora de baixar — o campo nao conserta o que o jogador digitou por conta propria.
     */
    private static String clean(String url) {
        return url == null ? "" : url.trim();
    }

    /** Le uma regra do Auto Armor (um objeto com modo, limite, prioridade e sem encantamento). */
    private static void rule(JsonObject o, String key, AutoArmor.Rule r) {
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            return;
        }
        JsonObject j = o.getAsJsonObject(key);
        if (j.has("mode")) {
            r.mode = Math.max(AutoArmor.OFF, Math.min(AutoArmor.SWAP, j.get("mode").getAsInt()));
        }
        if (j.has("threshold")) {
            r.threshold = Math.max(1, Math.min(300, j.get("threshold").getAsInt()));
        }
        r.highest = bool(j, "highest", r.highest);
        r.unenchanted = bool(j, "unenchanted", r.unenchanted);
        if (j.has("keepLevel")) {
            int k = j.get("keepLevel").getAsInt();
            r.keepLevel = k <= 0 ? 0 : Math.max(4, Math.min(10, k));
        }
        if (j.has("durability")) {
            r.durability = Math.max(AutoArmor.DUR_OFF, Math.min(AutoArmor.DUR_LOWEST, j.get("durability").getAsInt()));
        }
    }

    private static JsonObject rule(AutoArmor.Rule r) {
        JsonObject j = new JsonObject();
        j.addProperty("mode", r.mode);
        j.addProperty("threshold", r.threshold);
        j.addProperty("highest", r.highest);
        j.addProperty("unenchanted", r.unenchanted);
        j.addProperty("durability", r.durability);
        j.addProperty("keepLevel", r.keepLevel);
        return j;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    /** Um codigo de cor do arquivo, se for um dos de COLORS. */
    private static char color(JsonObject o, String key, char def) {
        if (!o.has(key)) {
            return def;
        }
        String s = o.get(key).getAsString();
        return s.length() == 1 && COLORS.indexOf(s.charAt(0)) >= 0 ? s.charAt(0) : def;
    }
}
