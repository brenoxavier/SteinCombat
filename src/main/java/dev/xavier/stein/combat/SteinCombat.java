/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import dev.xavier.stein.loader.internal.Combat;
import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Server;
import dev.xavier.stein.loader.api.SteinMod;
import dev.xavier.stein.loader.api.Targeting;

/**
 * Stein Combat: a corrida do Better Sprinting e os aliados do BattleChatter, com codigo proprio.
 *
 * <ul>
 *   <li>Tecla de alternar a corrida (G): corre sem segurar a tecla de correr do jogo — o mod a mantem apertada. Com
 *       "Correr em todas as direcoes" (opcao, desligada), tambem para tras e para os lados, pelo Loader
 *       ({@code Combat.setSprintAllDirections}). Sem pulo automatico e sem acelerar o voo.
 *   <li>Aliados: nicks e clas numa lista (o Painel, ou a tecla Y mirando num jogador; com Shift, o cla dele), e os
 *       membros do meu cla (opcao, ligada). O que ser aliado da e <b>a cor do nome</b>, no Tab
 *       ({@code onPlayerListName}) e acima da cabeca ({@code onNametag}).
 *   <li>O meu cla, e so ele, tem golpe cancelado antes de ir ao servidor ({@code onAttackEntity}) e some da tela com
 *       a tecla de esconder ({@code onHideEntity}) — os dois valem so com "Meu cla e aliado" ligada. Proteger e
 *       esconder aliado marcado e cla aliado saiu do mod; o caminho antigo esta escrito, em comentario, nos dois
 *       metodos.
 *   <li>Inimigos: nicks e clas numa lista (o Painel, ou a tecla U mirando num jogador; com Shift, o cla dele).
 *       Marcado como inimigo vale acima de cla e alianca — sai da protecao de golpe, fica na cor de inimigo, entra no
 *       "inimigo mais proximo" e no filtro de inimigos do Tab. Aliado e inimigo nunca ao mesmo tempo: marcar um tira
 *       da outra lista, e a marca num nick vale acima da tag do cla dele.
 *   <li>Lista pela internet ({@link Feed}): em vez de cada um escrever a sua, as quatro listas vem de um JSON numa
 *       URL — do cla, por exemplo. Ligado o modo URL, as listas escritas a mao ficam paradas (e sempre uma ou a
 *       outra) e a busca acontece ao entrar num servidor e no botao Atualizar. A URL pode ser uma so, ou uma por
 *       servidor (opcao), pelo endereco dele.
 * </ul>
 * A tag do cla vem do placar: o time "sc_ABC" do plugin SteinCombat do servidor ou, nos servidores com outro plugin de
 * cla, o prefixo do time ("[ABC] Nick" no Tab; opcao, ligada). Num servidor que use o prefixo para cargo ("[VIP]"),
 * desligue a opcao.
 *
 * <p>Escrito com os nomes legiveis do MCP (os do SDK); o tools/build-mods.sh traduz para os do jogo.
 */
public final class SteinCombat implements SteinMod {

    /** Nome do mod, que e o nome da pagina dele no Painel. */
    static final String NAME = "Combat";

    /** Aviso de golpe cancelado no maximo a cada 1,5 s (varios cliques seguidos num aliado). */
    /** Times do plugin SteinCombat no placar: "sc_" + tag. */
    private static final String CLAN_TEAM = "sc_";
    /** O id do mod: a origem do que ele cadastra no Loader. */
    static final String MOD_ID = "steincombat";

    /** O No Slowdown esta desativado: fora do Painel e sempre desligado. true o traz de volta. */
    private static final boolean NO_SLOWDOWN_ENABLED = false;

    CombatConfig config = new CombatConfig();
    private final ModContext ctx = ModContext.of(this);
    Keys.Binding toggleKey;
    Keys.Binding allyKey;
    Keys.Binding enemyKey;
    Keys.Binding hideKey;
    /** Liga e desliga cada funcionalidade de luta (sem tecla por padrao: o jogador escolhe no Painel). */
    Keys.Binding noHitDelayKey;
    Keys.Binding keepSprintKey;
    Keys.Binding omniSprintKey;
    Keys.Binding clanProtectKey;
    Keys.Binding autoArmorKey;
    private final AutoArmor autoArmor = new AutoArmor(this);
    Keys.Binding smartEffectKey;
    Keys.Binding autoProtectKey;
    Keys.Binding autoRefillKey;
    private final AutoRefill autoRefill = new AutoRefill(this);
    private final SmartEffect smartEffect = new SmartEffect(this);
    Keys.Binding autoclickKey;
    private final Autoclick autoclick = new Autoclick(this);
    /** Ticks desde a ultima vez que o Auto Armor mexeu no inventario. */
    private int armorQuiet = 100;
    private File configFile;
    /** Aliados em minusculas, para a consulta de cada quadro. */
    private Set<String> allySet = new HashSet<>();
    /** Tags dos clas aliados. */
    private Set<String> clanSet = new HashSet<>();
    /** Inimigos marcados em minusculas. */
    private Set<String> enemySet = new HashSet<>();
    /** Tags dos clas inimigos. */
    private Set<String> enemyClanSet = new HashSet<>();
    /**
     * O servidor para o qual as listas de agora foram montadas (o endereco dele, ou vazio fora de servidor). E a
     * chave da URL por servidor: mudando de servidor, muda a URL e muda a lista.
     */
    private String appliedKey = "";
    /** Uma busca esta correndo noutra thread. */
    private volatile boolean fetching;
    /** O que a busca trouxe, esperando a thread do jogo aplicar. */
    private volatile Feed fetched;
    /** O motivo de a ultima busca nao ter dado, esperando a thread do jogo. */
    private volatile String fetchError;
    /** A URL daquela busca. */
    private volatile String fetchedUrl = "";
    /** A busca foi pedida no botao (e nao ao entrar no servidor): o resultado sai no chat. */
    private volatile boolean fetchManual;
    /** O motivo da ultima falha, para o Painel mostrar; null quando a ultima busca deu certo. */
    private String failed;
    /** Corrida alternada ligada. */
    boolean toggled;
    /**
     * Os meus sumiram da tela (a tecla liga e desliga). Nao vai para o arquivo: comeca desligado a cada partida —
     * abrir o jogo sem os aliados na tela, sem lembrar por que, seria pior do que apertar a tecla de novo.
     */
    boolean hiding;
    /** O mod esta segurando a tecla de correr do jogo. */
    private boolean holding;

    @Override
    public void afterStartGame() {
        configFile = ctx.configFile();
        config = CombatConfig.load(configFile);
        toggleKey = Keys.bind("key.steincombat.toggleSprint", Keys.KEY_G, "key.categories.steincombat");
        allyKey = Keys.bind("key.steincombat.ally", Keys.KEY_Y, "key.categories.steincombat");
        enemyKey = Keys.bind("key.steincombat.enemy", Keys.KEY_U, "key.categories.steincombat");
        hideKey = Keys.bind("key.steincombat.hide", Keys.KEY_H, "key.categories.steincombat");
        noHitDelayKey = Keys.bind("key.steincombat.noHitDelay", Keys.KEY_NONE, "key.categories.steincombat");
        keepSprintKey = Keys.bind("key.steincombat.keepSprint", Keys.KEY_NONE, "key.categories.steincombat");
        omniSprintKey = Keys.bind("key.steincombat.omniSprint", Keys.KEY_NONE, "key.categories.steincombat");
        clanProtectKey = Keys.bind("key.steincombat.clanProtect", Keys.KEY_NONE, "key.categories.steincombat");
        autoArmorKey = Keys.bind("key.steincombat.autoArmor", Keys.KEY_NONE, "key.categories.steincombat");
        smartEffectKey = Keys.bind("key.steincombat.smartEffect", Keys.KEY_NONE, "key.categories.steincombat");
        autoProtectKey = Keys.bind("key.steincombat.autoProtect", Keys.KEY_NONE, "key.categories.steincombat");
        autoRefillKey = Keys.bind("key.steincombat.autoRefill", Keys.KEY_NONE, "key.categories.steincombat");
        autoclickKey = Keys.bind("key.steincombat.autoclick", Keys.KEY_NONE, "key.categories.steincombat");
        apply();
        CombatApi.mod = this;
        // O que este servidor chama de grupo e quem e o que para o jogador: o Loader leva para todos os mods
        // (Relations/Players). O grupo e decidido aqui inteiro — o time sc_ do plugin de clas daqui, ou a tag de 3
        // letras ou numeros entre colchetes; "" = sem grupo, e o jeito generico do Loader nem e tentado.
        Players.groupResolver(MOD_ID, nick -> {
            String tag = clanInfo(nick).tag;
            return tag == null ? "" : tag;
        });
        Relations.register(MOD_ID, this::relationOf);
        publishRelations();
        dev.xavier.stein.loader.api.Hud.register(new NearestPlayer(this, false));
        dev.xavier.stein.loader.api.Hud.register(new NearestPlayer(this, true));
        dev.xavier.stein.loader.api.Hud.register(new ActiveFeatures(this));
        System.out.println("[mods] Combat iniciado (" + configFile + ", " + config.allies.size() + " aliados, "
                + config.allyClans.size() + " clas aliados, " + config.enemies.size() + " inimigos)");
    }

    /**
     * Entrou num servidor: e aqui que a lista do cla e buscada, uma vez por entrada — assim ela chega fresca em cada
     * partida sem ninguem ter de apertar nada, e uma troca de lado no arquivo vale para todo mundo na entrada
     * seguinte.
     */
    @Override
    public void onJoinGame() {
        appliedKey = host();
        apply();
        if (config.remote) {
            fetch(false);
        }
    }

    /** O inventario inteiro chegou do servidor: o Auto Armor e o Auto Refill podem estar esperando por ele numa conferencia. */
    @Override
    public void onWindowItems(int windowId) {
        autoArmor.windowItems(windowId);
        autoRefill.windowItems(windowId);
    }

    /** O Auto Refill esta com o bau aberto (ou esperando abrir): a bolsa e dele. */
    boolean refillBusy() {
        return autoRefill.busy();
    }

    /** A vida do jogador baixou: o Auto Armor confere se a armadura vestida gastou. */
    @Override
    public void onHealthChanged(Object entity, float oldHealth, float newHealth, float oldAbsorption,
            float newAbsorption) {
        if (entity == Entities.self() && newHealth + newAbsorption < oldHealth + oldAbsorption) {
            autoArmor.hurt();
        }
    }

    /** Depois de mudar as opcoes: grava, refaz as listas e avisa o Loader. */
    void save() {
        if (configFile != null) {
            config.save(configFile);
        }
        apply();
    }

    /**
     * Refaz as quatro listas que valem agora. <b>E uma fonte ou a outra</b>: com o modo URL ligado valem as listas
     * que a URL deste servidor entregou, e as escritas a mao ficam paradas (continuam no arquivo, e voltam a valer
     * desligando o modo).
     */
    private void apply() {
        Feed f = config.remote ? feed() : null;
        allySet = lower(f == null ? config.allies : f.allies);
        clanSet = new HashSet<>(f == null ? config.allyClans : f.allyClans);
        enemySet = lower(f == null ? config.enemies : f.enemies);
        enemyClanSet = new HashSet<>(f == null ? config.enemyClans : f.enemyClans);
        Combat.setNoHitDelay(config.noHitDelay);
        Combat.setSprintOnHit(config.sprintOnHit);
        // No Slowdown: desativado (escondido do Painel, sempre desligado). O codigo fica para voltar quando quiser.
        Combat.setNoHitSlowdown(NO_SLOWDOWN_ENABLED && config.noHitSlowdown);
        Combat.setSprintAllDirections(config.allDirections);
        publishRelations();
    }

    /**
     * A relacao que o Combat da ao jogador, para o Loader (Relations). A mesma ordem de sempre: marcado como inimigo
     * vale acima de tudo, depois o meu cla, os aliados e, com prova, o cla de outro. null = sem opiniao: o Loader
     * segue para os cadastros de outros mods e, sem nada, neutro.
     */
    Relation relationOf(String nick, String group) {
        if (nick != null) {
            // A mesma conta da CombatApi.relation, guardada; neutro aqui e "sem opiniao".
            return switch (relation(nick)) {
                case CombatApi.ENEMY -> Relation.ENEMY;
                case CombatApi.CLAN -> Relation.GROUP;
                case CombatApi.ALLY -> Relation.ALLY;
                default -> null;
            };
        }
        if (isEnemy(nick)) {
            return Relation.ENEMY;
        }
        Clan clan = clanInfo(nick);
        String mine = myClan();
        if (mine != null && mine.equals(clan.tag)) {
            return Relation.GROUP;
        }
        if (isAlly(nick)) {
            return Relation.ALLY;
        }
        boolean proof = clan.proven || config.enemyFromPrefix;
        return clan.tag != null && mine != null && proof ? Relation.ENEMY : null;
    }

    /** As cores escolhidas aqui valem para todos os mods; nomes sem cor, branco em todas. Avisa que algo mudou. */
    private void publishRelations() {
        boolean on = config.colorNames;
        Relations.setColor(Relation.GROUP, on ? Option.rgb(String.valueOf(config.clanColor)) : 0xFFFFFF);
        Relations.setColor(Relation.ALLY, on ? Option.rgb(String.valueOf(config.allyColor)) : 0xFFFFFF);
        Relations.setColor(Relation.ENEMY, on ? Option.rgb(String.valueOf(config.enemyColor)) : 0xFFFFFF);
        Relations.setColor(Relation.NEUTRAL, on ? Option.rgb(String.valueOf(config.neutralColor)) : 0xFFFFFF);
        Relations.changed();
    }

    private static Set<String> lower(List<String> list) {
        Set<String> s = new HashSet<>();
        for (String a : list) {
            s.add(a.toLowerCase(Locale.ROOT));
        }
        return s;
    }

    /**
     * A lista que vale aqui: a que a URL entregou, com a secao do servidor de agora ja somada — e o proprio
     * arquivo que diz de que servidor e cada nome. Vazia antes da primeira busca, ou sem URL.
     */
    private Feed feed() {
        return config.feed(config.remoteUrl).forHost(appliedKey);
    }

    /**
     * O endereco do servidor de agora, como as URLs por servidor o guardam (minusculo e sem a porta padrao); vazio
     * fora de servidor.
     */
    static String host() {
        return Server.address();
    }

    /**
     * Vai buscar a lista da URL de agora, noutra thread — o jogo nao espera a internet. O resultado e aplicado na
     * thread do jogo, em {@link #applyFetched}.
     */
    void fetch(boolean manual) {
        if (fetching) {
            return;
        }
        String url = config.remoteUrl;
        if (url.isEmpty()) {
            failed = null;
            if (manual) {
                chat(tr("steincombat.remoteAskUrl"));
            }
            return;
        }
        fetching = true;
        fetchManual = manual;
        fetched = null;
        fetchError = null;
        fetchedUrl = url;
        Thread t = new Thread(() -> {
            try {
                fetched = Feed.fetch(url);
            } catch (Exception | LinkageError e) {
                String why = e.getMessage();
                fetchError = why == null || why.isEmpty() ? e.getClass().getSimpleName() : why;
            } finally {
                fetching = false;
            }
        }, "steincombat-lista");
        t.setDaemon(true);
        t.start();
    }

    /** Na thread do jogo: guarda o que a busca trouxe (ou anota a falha) e refaz as listas. */
    private void applyFetched() {
        Feed f = fetched;
        String err = fetchError;
        if (f == null && err == null) {
            return;
        }
        fetched = null;
        fetchError = null;
        if (f != null) {
            failed = null;
            config.feed(fetchedUrl, f);
            save();
            if (fetchManual) {
                chat(tr("steincombat.remoteOk", String.valueOf(f.size())));
            }
            System.out.println("[mods] Combat: lista de " + fetchedUrl + " com " + f.size() + " nomes");
            return;
        }
        failed = err;
        // Sempre no chat, inclusive na busca automatica: quem entrou achando que a lista do cla esta valendo tem de
        // saber que ela nao chegou — e disso depende em quem o mod deixa bater.
        chat(tr(config.feed(fetchedUrl).isEmpty() ? "steincombat.remoteFailNone" : "steincombat.remoteFailKept",
                err));
        System.out.println("[mods] Combat: lista de " + fetchedUrl + " nao veio: " + err);
    }

    /** Ha algum aliado possivel (evita o trabalho por nome no Tab e nas cabecas quando nao ha). */
    private boolean anyAllies() {
        return !allySet.isEmpty() || !clanSet.isEmpty() || config.clanAllies;
    }

    /**
     * Inimigo pela lista: o nick marcado, ou quem esta num cla marcado como inimigo. Vale acima do cla e das
     * aliancas — inclusive num servidor em que eu nao tenha cla.
     *
     * <p>O nick vale acima da tag: quem esta na lista de aliados continua aliado mesmo com o cla dele marcado como
     * inimigo, porque a marca no nick e sobre aquela pessoa e a tag e sobre um grupo.
     */
    boolean isEnemy(String nick) {
        if (nick == null) {
            return false;
        }
        String low = nick.toLowerCase(Locale.ROOT);
        if (enemySet.contains(low)) {
            return true;
        }
        if (enemyClanSet.isEmpty() || allySet.contains(low)) {
            return false;
        }
        String tag = clanOf(nick);
        return tag != null && enemyClanSet.contains(tag);
    }

    /** Aliado: na lista de nicks, num cla aliado ou, com a opcao, no meu cla — e nao marcado como inimigo. */
    boolean isAlly(String nick) {
        if (nick == null || isEnemy(nick)) {
            return false;
        }
        if (allySet.contains(nick.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (clanSet.isEmpty() && !config.clanAllies) {
            return false;
        }
        String tag = clanOf(nick);
        if (tag == null) {
            return false;
        }
        if (clanSet.contains(tag)) {
            return true;
        }
        return config.clanAllies && Players.self() != null && !nick.equalsIgnoreCase(Players.self())
                && tag.equals(myClan());
    }

    static boolean validNick(String nick) {
        return nick != null && nick.matches("[A-Za-z0-9_]{1,16}");
    }

    /**
     * Tag de cla digitada ("abc" ou "[ABC]") em maiusculas, ou null se nao for uma tag.
     *
     * <p><b>Exatamente tres</b> letras ou numeros: e o formato do servidor, e e o que separa um cla de qualquer outra
     * coisa entre colchetes no nome ("[VIP]" tem tres e conta, mas "[ADMIN]" ou "[PV]" nao sao cla). Com mais ou com
     * menos, nao e cla.
     */
    static String tag(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("[") && t.endsWith("]") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t.matches("[A-Za-z0-9]{3}") ? t.toUpperCase(Locale.ROOT) : null;
    }

    /**
     * Tag do cla do jogador pelo time do placar: o time "sc_ABC" do plugin SteinCombat, ou o prefixo que o servidor poe
     * no nome ("[ABC] Nick"), que e como os outros plugins de cla marcam quem e de quem.
     */
    /**
     * O cla de um jogador e <b>de onde ele veio</b>. A diferenca importa: o time {@code sc_TAG} do plugin daqui e
     * prova (foi um plugin de cla que escreveu), enquanto um colchete no nome e so indicio — num servidor comum
     * {@code [VIP]}, {@code [ADM]} e {@code [PRO]} tem as mesmas tres letras de uma tag de cla.
     */
    static final class Clan {
        static final Clan NONE = new Clan(null, false);

        final String tag;
        /** Veio do plugin de clas daqui (time sc_TAG), e nao de um colchete no nome. */
        final boolean proven;

        Clan(String tag, boolean proven) {
            this.tag = tag;
            this.proven = proven;
        }
    }

    /**
     * Tag do cla do jogador, guardada por um segundo. O clanOf de verdade consulta o placar e a lista do Tab, e a
     * busca por nick na lista do Tab e uma varredura por todos os jogadores: a lista do Tab do HUD pergunta a relacao
     * e o cla de cada jogador a cada quadro, o que virava uma varredura por jogador por quadro (com 200 na lista,
     * dezenas de milhares de voltas por quadro — era isso que travava o Tab segurado).
     */
    static String clanOf(String nick) {
        return clanInfo(nick).tag;
    }

    /** O mesmo, com a origem junto (ver {@link Clan}). */
    static Clan clanInfo(String nick) {
        clanEpoch();
        // containsKey e nao computeIfAbsent: "sem cla" e resposta valida e tem de ficar guardada tambem.
        if (CLAN_CACHE.containsKey(nick)) {
            return CLAN_CACHE.get(nick);
        }
        Clan clan = lookupClan(nick);
        CLAN_CACHE.put(nick, clan);
        return clan;
    }

    private static final java.util.Map<String, Clan> CLAN_CACHE = new java.util.HashMap<>();

    private static long clanCacheAt;

    /**
     * Comeca outra rodada do cache de clas se a de agora passou de um segundo, e devolve quando a de agora comecou: o
     * numero muda a cada rodada nova, e e por ele que o cache de relacoes ({@link #relation}) sabe que os clas podem ter
     * mudado.
     */
    static long clanEpoch() {
        long now = System.nanoTime();
        if (now - clanCacheAt > 1_000_000_000L) {
            CLAN_CACHE.clear();
            clanCacheAt = now;
        }
        return clanCacheAt;
    }

    private static Clan lookupClan(String nick) {
        if (nick == null || Players.self() == null) {
            return Clan.NONE;
        }
        String team = Players.team(nick);
        SteinCombat mod = CombatApi.mod;
        boolean fromName = mod == null || mod.config.clanFromPrefix;
        if (team != null) {
            if (team.startsWith(CLAN_TEAM)) {
                return new Clan(tag(team.substring(CLAN_TEAM.length())), true);   // o plugin de clas daqui
            }
            if (fromName) {
                String prefix = bracketTag(Players.prefix(nick));
                if (prefix != null) {
                    return new Clan(prefix, false);                // outro plugin, pelo prefixo do time
                }
            }
        }
        // E o caso mais comum: o servidor nao usa time nenhum e manda o cla dentro do proprio nome do Tab
        // ("[ABC] Nick"). Players.displayName e null quando o servidor nao manda nome proprio.
        String named = fromName ? bracketTag(Players.displayName(nick)) : null;
        return named == null ? Clan.NONE : new Clan(named, false);
    }

    /** A tag entre colchetes de um prefixo de time ("§b[ABC] " -> "ABC"), ou null. */
    static String bracketTag(String prefix) {
        if (prefix == null) {
            return null;
        }
        String clean = prefix.replaceAll("§.", "").trim();
        int open = clean.indexOf(0x5B);
        int close = clean.indexOf(0x5D, open + 1);
        return open >= 0 && close > open ? tag(clean.substring(open, close + 1)) : null;
    }

    /** Tag do meu cla neste servidor, ou null. */
    static String myClan() {
        return Players.self() == null ? null : clanOf(Players.self());
    }

    // ---- relacao guardada ----

    /**
     * A relacao de um nick e o texto pintado da ultima nametag e do ultimo nome do Tab dele. A relacao sai so das
     * quatro listas (trocadas inteiras no {@link #apply}), de algumas opcoes, do meu nick e dos clas — e os clas ja
     * ficam guardados por rodada de um segundo ({@link #clanEpoch}). Enquanto nada disso muda, a resposta e a mesma, e
     * a nametag de cada jogador perguntava a cada quadro: duas vezes as listas, tres minusculas novas e quatro buscas.
     */
    private static final class Rel {
        int relation;
        String tagText;
        String tagPainted;
        String listText;
        String listPainted;
    }

    private final java.util.HashMap<String, Rel> relCache = new java.util.HashMap<>();
    // O que valia quando o cache foi montado: mudou qualquer um, ele recomeca.
    private long relEpoch;
    private String relSelf;
    private CombatConfig relConfig;
    private Set<String> relAllies;
    private Set<String> relClans;
    private Set<String> relEnemies;
    private Set<String> relEnemyClans;
    private boolean relClanAllies;
    private boolean relClanFromPrefix;
    private boolean relEnemyFromPrefix;
    // As cores entram no texto pintado guardado.
    private boolean relColorNames;
    private long relColors;

    /** {@link CombatApi#relation}, guardada (nick nao nulo). */
    int relation(String nick) {
        return rel(nick).relation;
    }

    private Rel rel(String nick) {
        long epoch = clanEpoch();
        String self = Players.self();
        CombatConfig c = config;
        if (epoch != relEpoch || c != relConfig || allySet != relAllies || clanSet != relClans
                || enemySet != relEnemies || enemyClanSet != relEnemyClans || c.clanAllies != relClanAllies
                || c.clanFromPrefix != relClanFromPrefix || c.enemyFromPrefix != relEnemyFromPrefix
                || c.colorNames != relColorNames || colors(c) != relColors
                || !java.util.Objects.equals(self, relSelf)) {
            relCache.clear();
            relEpoch = epoch;
            relSelf = self;
            relConfig = c;
            relAllies = allySet;
            relClans = clanSet;
            relEnemies = enemySet;
            relEnemyClans = enemyClanSet;
            relClanAllies = c.clanAllies;
            relClanFromPrefix = c.clanFromPrefix;
            relEnemyFromPrefix = c.enemyFromPrefix;
            relColorNames = c.colorNames;
            relColors = colors(c);
        }
        Rel r = relCache.get(nick);
        if (r == null) {
            r = new Rel();
            r.relation = computeRelation(nick);
            relCache.put(nick, r);
        }
        return r;
    }

    /** As quatro cores dos nomes num numero so (cada uma e um char). */
    private static long colors(CombatConfig c) {
        return (long) c.clanColor << 48 | (long) c.allyColor << 32 | (long) c.neutralColor << 16 | c.enemyColor;
    }

    /** A relacao calculada de novo; ver {@link CombatApi#relation}. */
    private int computeRelation(String nick) {
        // Marcado como inimigo vale acima de tudo: e escolha do jogador sobre aquele nick, mais forte que cla e alianca.
        if (isEnemy(nick)) {
            return CombatApi.ENEMY;
        }
        Clan clan = clanInfo(nick);
        String mine = myClan();
        if (mine != null && mine.equals(clan.tag)) {
            return CombatApi.CLAN;
        }
        if (isAlly(nick)) {
            return CombatApi.ALLY;
        }
        boolean proof = clan.proven || config.enemyFromPrefix;
        return clan.tag != null && mine != null && proof ? CombatApi.ENEMY : CombatApi.NEUTRAL;
    }

    /**
     * {@code -Dsteincombat.selfTestAtSec=N}: N segundos depois de abrir o jogo, confere os inimigos marcados com gente
     * de verdade da lista do Tab — um sem cla, um de outro cla e um do meu cla —, pelos mesmos caminhos do Painel, e
     * devolve a configuracao como estava. O resultado sai no log, linhas "Autoteste do Combat".
     */
    private static final long SELF_TEST_AT = Long.getLong("steincombat.selfTestAtSec", -1L);
    private boolean selfTested;

    private void selfTest() {
        selfTested = true;
        String tag = "Autoteste do Combat: ";
        if (!Server.isMultiplayer() || Players.self() == null) {
            System.out.println(tag + "sem servidor");
            return;
        }
        String me = Players.self();
        String mine = myClan();
        String noClan = null;
        String other = null;
        String clanmate = null;
        for (String nick : Players.online()) {
            if (nick.equalsIgnoreCase(me) || config.hasAlly(nick) || config.hasEnemy(nick)) {
                continue;
            }
            String clan = clanOf(nick);
            if (clan == null && noClan == null) {
                noClan = nick;
            } else if (clan != null && !clan.equals(mine) && !clanSet.contains(clan) && other == null) {
                other = nick;
            } else if (clan != null && clan.equals(mine) && clanmate == null) {
                clanmate = nick;
            }
        }
        List<String> savedAllies = new ArrayList<>(config.allies);
        List<String> savedEnemies = new ArrayList<>(config.enemies);
        List<String> savedAllyClans = new ArrayList<>(config.allyClans);
        List<String> savedEnemyClans = new ArrayList<>(config.enemyClans);
        boolean savedRemote = config.remote;
        String savedUrl = config.remoteUrl;
        java.util.Map<String, Feed> savedFeeds = new java.util.LinkedHashMap<>(config.remoteFeeds);
        int[] result = {0, 0};
        java.util.function.BiConsumer<Boolean, String> check = (ok, what) -> {
            result[0]++;
            if (!ok) {
                result[1]++;
                System.out.println(tag + "FALHA " + what);
            }
        };
        try {
            // A primeira parte e sobre as listas escritas a mao: o modo URL sai, senao nada do que ela marca esta
            // valendo e todas as conferencias falhariam com razao. O finally devolve o modo como estava.
            config.remote = false;
            apply();
            System.out.println(tag + "meu cla " + mine + "; sem cla " + noClan + " (" + CombatApi.relation(noClan)
                    + "), outro cla " + other + " (" + CombatApi.relation(other) + "), meu cla " + clanmate + " ("
                    + CombatApi.relation(clanmate) + ")");
            for (String nick : new String[] {noClan, other, clanmate}) {
                if (nick == null) {
                    continue;
                }
                int before = CombatApi.relation(nick);
                pendingEnemy = nick;
                addEnemy(false);   // o botao do Painel
                check.accept(config.hasEnemy(nick) && pendingEnemy.isEmpty(), nick + ": nao entrou na lista");
                check.accept(CombatApi.relation(nick) == CombatApi.ENEMY, nick + ": relacao "
                        + CombatApi.relation(nick) + " depois de marcado");
                check.accept(!isAlly(nick), nick + ": marcado inimigo e ainda aliado (golpe seria cancelado)");
                check.accept(!config.protectAllies || !onAttackEntity(Entities.player(nick))
                        || Entities.player(nick) == null, nick + ": golpe cancelado num inimigo");
                check.accept(CombatApi.code(nick) == config.enemyColor, nick + ": cor " + CombatApi.code(nick)
                        + ", a de inimigo e " + config.enemyColor);
                // Aliado tira dos inimigos, e inimigo tira dos aliados.
                pending = nick;
                add(false);   // o botao "Jogador" dos aliados
                check.accept(config.hasAlly(nick) && !config.hasEnemy(nick), nick + ": aliado nao tirou dos inimigos");
                check.accept(CombatApi.relation(nick) != CombatApi.ENEMY, nick + ": aliado continua inimigo");
                pendingEnemy = nick;
                addEnemy(false);
                check.accept(config.hasEnemy(nick) && !config.hasAlly(nick), nick + ": inimigo nao tirou dos aliados");
                // Gravado e lido de novo, a lista continua.
                CombatConfig again = CombatConfig.load(configFile);
                check.accept(again.hasEnemy(nick), nick + ": nao voltou do arquivo");
                // Tirar pela lista do Painel devolve a relacao de antes.
                config.removeEnemy(nick);
                save();
                check.accept(CombatApi.relation(nick) == before, nick + ": depois de tirar, relacao "
                        + CombatApi.relation(nick) + ", era " + before);
            }
            pendingEnemy = me;
            addEnemy(false);
            check.accept(!config.hasEnemy(me), "consegui me marcar como inimigo");
            pendingEnemy = "nome com espaco";
            addEnemy(false);
            check.accept(!config.hasEnemy("nome com espaco"), "aceitou um nick invalido");
            pendingEnemy = "";
            listsTest(check, other, tag);
            guardTest(check, other, clanmate, tag);
        } catch (Throwable t) {
            check.accept(false, "parou com " + t);
            t.printStackTrace(System.out);
        } finally {
            config.allies.clear();
            config.allies.addAll(savedAllies);
            config.enemies.clear();
            config.enemies.addAll(savedEnemies);
            config.allyClans.clear();
            config.allyClans.addAll(savedAllyClans);
            config.enemyClans.clear();
            config.enemyClans.addAll(savedEnemyClans);
            config.remote = savedRemote;
            config.remoteUrl = savedUrl;
            config.remoteFeeds.clear();
            config.remoteFeeds.putAll(savedFeeds);
            save();
        }
        System.out.println(tag + result[0] + " conferencias, " + result[1] + " falha(s)");
    }

    /**
     * A segunda parte do autoteste: os clas inimigos, o modo URL (uma fonte <b>ou</b> a outra) e a URL por servidor.
     * Tudo pelos mesmos caminhos do Painel e sem tocar a internet — a lista de exemplo e lida do texto, como se
     * tivesse acabado de ser baixada.
     */
    private void listsTest(java.util.function.BiConsumer<Boolean, String> check, String other, String tag)
            throws Exception {
        String clan = other == null ? null : clanOf(other);
        System.out.println(tag + "servidor de agora: \"" + host() + "\", cla de outro: " + clan);
        if (clan != null) {
            pendingEnemy = clan;
            addEnemy(true);   // o botao "Adicionar cla" dos inimigos
            check.accept(config.enemyClans.contains(clan), "o cla " + clan + " nao entrou nos inimigos");
            check.accept(isEnemy(other) && CombatApi.relation(other) == CombatApi.ENEMY,
                    other + ": o cla marcado nao o deixou inimigo");
            check.accept(!isAlly(other), other + ": de cla inimigo e ainda aliado");
            // O nick marcado vale acima da tag do cla dele.
            config.addAlly(other);
            save();
            check.accept(isAlly(other) && !isEnemy(other), other + ": aliado a mao perdeu para o cla inimigo");
            config.removeAlly(other);
            config.addAllyClan(clan);
            check.accept(!config.enemyClans.contains(clan), "o cla ficou nas duas listas");
            config.enemyClans.clear();
            config.allyClans.clear();
            save();
        }
        Feed f = Feed.parse("{\"allies\":[\"Alfa\",\"nick invalido\"],\"allyClans\":[\"[abc]\"],"
                + "\"enemies\":[\"Beta\"],\"enemyClans\":[\"xyz\",\"XYZ\"]}");
        check.accept(f.allies.size() == 1 && f.allies.contains("Alfa"), "lista da URL: aliados " + f.allies);
        check.accept(f.allyClans.equals(java.util.Collections.singletonList("ABC")),
                "lista da URL: clas aliados " + f.allyClans);
        check.accept(f.enemies.equals(java.util.Collections.singletonList("Beta")),
                "lista da URL: inimigos " + f.enemies);
        check.accept(f.enemyClans.equals(java.util.Collections.singletonList("XYZ")),
                "lista da URL: clas inimigos " + f.enemyClans);
        config.addAlly("Gama");
        config.remote = true;
        config.remoteUrl = "https://exemplo.invalido/lista.json";
        config.feed(config.remoteUrl, f);
        save();
        check.accept(isAlly("Alfa"), "modo URL: o aliado da URL nao valeu");
        check.accept(isEnemy("Beta"), "modo URL: o inimigo da URL nao valeu");
        check.accept(!isAlly("Gama"), "modo URL: o aliado escrito a mao continuou valendo");
        check.accept(locked(), "modo URL: a tecla de marcar nao esta travada");
        CombatConfig again = CombatConfig.load(configFile);
        check.accept(again.remote && again.feed(config.remoteUrl).allies.contains("Alfa"),
                "modo URL: a lista guardada nao voltou do arquivo");
        config.remote = false;
        save();
        check.accept(isAlly("Gama") && !isAlly("Alfa"), "modo manual: a lista escrita a mao nao voltou");
        config.removeAlly("Gama");
        // As secoes por servidor: quem diz de que servidor e cada lista e o proprio arquivo.
        String key = host();
        if (!key.isEmpty()) {
            String dominio = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
            Feed porServidor = Feed.parse("{\"allies\":[\"Alfa\"],\"enemies\":[\"Beta\"],\"servers\":{"
                    + "\"" + dominio + "\":{\"allies\":[\"Delta\"],\"enemies\":[\"Alfa\"],"
                    + "\"enemyClans\":[\"QWE\"]},"
                    + "\"outro.exemplo.com\":{\"allies\":[\"Epsilon\"]}}}");
            check.accept(porServidor.servers.size() == 2, "secoes lidas: " + porServidor.servers.keySet());
            Feed aqui = porServidor.forHost(key);
            check.accept(aqui.allies.contains("Delta"), "secao do servidor: o aliado dela nao entrou");
            check.accept(!aqui.allies.contains("Alfa") && aqui.enemies.contains("Alfa"),
                    "secao do servidor: o inimigo de la nao ganhou do aliado geral");
            check.accept(!aqui.allies.contains("Epsilon"), "entrou o aliado da secao de outro servidor");
            check.accept(aqui.enemyClans.contains("QWE"), "secao do servidor: o cla inimigo dela nao entrou");
            check.accept(porServidor.forHost("nada.a.ver.com").allies.contains("Alfa"),
                    "servidor sem secao: a lista de fora nao valeu sozinha");
            // Guardada e lida de novo, as secoes continuam la.
            config.feed(config.remoteUrl, porServidor);
            save();
            CombatConfig volta = CombatConfig.load(configFile);
            check.accept(volta.feed(config.remoteUrl).forHost(key).allies.contains("Delta"),
                    "secao do servidor: nao voltou do arquivo");
        }
        save();
    }

    /**
     * A terceira parte do autoteste: esconder e proteger sao so do meu cla. Confere o que mudou — aliado marcado
     * apanha e aparece, mas continua com a cor de aliado — e a condicao dos dois, que e a opcao "Meu cla e aliado".
     */
    private void guardTest(java.util.function.BiConsumer<Boolean, String> check, String other, String clanmate,
            String tag) {
        boolean savedHiding = hiding;
        boolean savedProtect = config.protectAllies;
        boolean savedGhost = config.ghostAllies;
        boolean savedClanAllies = config.clanAllies;
        boolean savedHitThrough = config.clanHitThrough;
        try {
            config.remote = false;
            config.protectAllies = true;
            config.clanHitThrough = false;   // o golpe do autoteste e a toa: nada de bater em quem esta atras
            config.clanAllies = true;
            hiding = true;
            save();
            if (other != null) {
                Object e = Entities.player(other);
                config.addAlly(other);
                save();
                check.accept(isAlly(other), other + ": nao ficou aliado");
                check.accept(CombatApi.code(other) == config.allyColor, other + ": aliado marcado sem a cor de aliado");
                if (e == null) {
                    System.out.println(tag + other + " nao esta no mundo: golpe e tela nao conferidos");
                } else {
                    check.accept(!onAttackEntity(e), other + ": aliado marcado ainda protegido do golpe");
                    check.accept(!onHideEntity(e), other + ": aliado marcado ainda some da tela");
                    // O fantasma e a sub-opcao do nome.
                    check.accept(onGhostEntity(e), other + ": aliado marcado nao virou fantasma");
                    config.ghostAllies = false;
                    check.accept(!onGhostEntity(e), other + ": virou fantasma com a opcao desligada");
                    config.ghostAllies = true;
                    hiding = false;
                    check.accept(!onGhostEntity(e), other + ": fantasma com a tecla de esconder desligada");
                    hiding = true;
                }
                config.removeAlly(other);
                save();
            }
            Object mate = clanmate == null ? null : Entities.player(clanmate);
            if (mate == null) {
                System.out.println(tag + "sem ninguem do meu cla no mundo: esconder e proteger nao conferidos");
            } else {
                check.accept(onAttackEntity(mate), clanmate + ": golpe no meu cla nao foi cancelado");
                check.accept(onHideEntity(mate), clanmate + ": o meu cla nao sumiu da tela");
                // Quem some nao vira fantasma, e a sub-opcao do nome so vale para o fantasma.
                check.accept(!onGhostEntity(mate), clanmate + ": o meu cla virou fantasma em vez de sumir");
                // "Meu cla e aliado" desligada: o cla deixa de ser aliado, e os dois param junto.
                config.clanAllies = false;
                save();
                check.accept(!onAttackEntity(mate), clanmate + ": protegido com \"Meu cla e aliado\" desligada");
                check.accept(!onHideEntity(mate), clanmate + ": sumiu com \"Meu cla e aliado\" desligada");
                config.clanAllies = true;
                save();
                // A tecla desligada nao esconde ninguem.
                hiding = false;
                check.accept(!onHideEntity(mate), clanmate + ": sumiu com a tecla de esconder desligada");
            }
        } finally {
            hiding = savedHiding;
            config.protectAllies = savedProtect;
            config.ghostAllies = savedGhost;
            config.clanAllies = savedClanAllies;
            config.clanHitThrough = savedHitThrough;
            save();
        }
    }

    /** O Autoclick: a hora de cada clique e conferida a cada quadro (ver {@link Autoclick}). */
    @Override
    public void onRenderWorld(float partialTicks) {
        autoclick.frame();
    }

    /**
     * Outra funcionalidade do Combat esta com a mao ou o inventario: comendo, bebendo, trocando de posicao, defendendo
     * (Smart Effect e Auto Protect), com o bau do refil (Auto Refill) ou equipando armadura (Auto Armor, ate um tick
     * depois). O Autoclick, ultimo na ordem, espera.
     */
    boolean handBusy() {
        return smartEffect.busy() || autoRefill.busy() || armorQuiet < 2;
    }

    @Override
    public void onTickEnd() {
        if (toggleKey == null) {
            return;
        }
        // Trocou de servidor: a URL pode ser outra, e com ela as listas.
        String key = host();
        if (!key.equals(appliedKey)) {
            appliedKey = key;
            apply();
        }
        applyFetched();
        if (SELF_TEST_AT >= 0 && !selfTested && System.nanoTime() - started > SELF_TEST_AT * 1_000_000_000L) {
            selfTest();
        }
        while (toggleKey.consumeClick()) {
            toggled = !toggled;
            notice(Lang.tr(toggled ? "steincombat.sprintOn" : "steincombat.sprintOff"));
        }
        while (allyKey.consumeClick()) {
            allyAtCrosshair();
        }
        while (enemyKey.consumeClick()) {
            enemyAtCrosshair();
        }
        while (hideKey.consumeClick()) {
            hiding = !hiding;
            chat(Lang.tr(hiding ? "steincombat.hideOn" : "steincombat.hideOff"));
        }
        while (noHitDelayKey.consumeClick()) {
            config.noHitDelay = !config.noHitDelay;
            featureToggled("steincombat.noHitDelay", config.noHitDelay);
        }
        while (keepSprintKey.consumeClick()) {
            config.sprintOnHit = !config.sprintOnHit;
            featureToggled("steincombat.sprintOnHit", config.sprintOnHit);
        }
        while (omniSprintKey.consumeClick()) {
            config.allDirections = !config.allDirections;
            featureToggled("steincombat.allDirections", config.allDirections);
        }
        while (clanProtectKey.consumeClick()) {
            config.protectAllies = !config.protectAllies;
            featureToggled("steincombat.protect", config.protectAllies);
        }
        while (autoArmorKey.consumeClick()) {
            config.autoArmor = !config.autoArmor;
            featureToggled("steincombat.autoArmor", config.autoArmor);
        }
        while (autoRefillKey.consumeClick()) {
            // Na tecla, apertar e pedir uma ida ao bau (que desliga sozinha no fim), e nao alternar: apertar de novo no
            // meio da ida nao a cancela.
            config.autoRefill = config.refillMode == AutoRefill.ON_KEY || !config.autoRefill;
            featureToggled("steincombat.autoRefill", config.autoRefill);
        }
        while (autoProtectKey.consumeClick()) {
            config.autoProtect = !config.autoProtect;
            featureToggled("steincombat.autoProtect", config.autoProtect);
        }
        while (smartEffectKey.consumeClick()) {
            config.smartEffect = !config.smartEffect;
            featureToggled("steincombat.smartEffect", config.smartEffect);
        }
        while (autoclickKey.consumeClick()) {
            config.autoclick = !config.autoclick;
            featureToggled("steincombat.autoclick", config.autoclick);
        }
        // A armadura primeiro: no tick em que ela mexe no inventario, o Smart Effect nao comeca nada.
        boolean armorActed = autoArmor.tick();
        armorQuiet = armorActed ? 0 : Math.min(100, armorQuiet + 1);
        autoRefill.tick();
        smartEffect.tick(armorActed);
        holdSprint();
        if (DUMP_AT >= 0 && !dumped && System.nanoTime() - started > DUMP_AT * 1_000_000_000L) {
            dumpClans();
        }
    }

    /**
     * {@code -Dsteincombat.dumpAtSec=N}: N segundos depois de iniciar, escreve no log o que o servidor manda de cada
     * jogador da lista do Tab — nome do time, prefixo, sufixo, nome pronto do Tab — e o que a leitura de cla tira
     * disso. E a unica forma honesta de descobrir como um servidor marca cla: cada um marca de um jeito, e sem ver o
     * texto cru a gente so adivinha.
     */
    private static final long DUMP_AT = Long.getLong("steincombat.dumpAtSec", -1L);
    private final long started = System.nanoTime();
    private boolean dumped;

    private void dumpClans() {
        dumped = true;
        if (!Server.isMultiplayer()) {
            System.out.println("[combat] despejo: sem servidor");
            return;
        }
        System.out.println("[combat] despejo: meu cla = " + myClan() + ", nick = "
                + (Players.self() == null ? "?" : Players.self()));
        int i = 0;
        for (String nick : Players.online()) {
            if (i++ >= 20) {
                break;
            }
            String team = Players.team(nick);
            String shown = Players.displayName(nick);
            System.out.println("[combat] " + nick
                    + " | time=" + (team == null ? "-" : team)
                    + " | prefixo=" + (team == null ? "-" : show(Players.prefix(nick)))
                    + " | sufixo=" + (team == null ? "-" : show(Players.suffix(nick)))
                    + " | tab=" + (shown == null ? "-" : show(shown))
                    + " | cla=" + clanOf(nick)
                    + " | relacao=" + CombatApi.relation(nick));
        }
    }

    /** O texto com os codigos de formatacao visiveis: "§b[ABC] " vira "&b[ABC] ". */
    private static String show(String s) {
        return s == null ? "-" : "\"" + s.replace('§', '&') + "\"";
    }

    /** Corrida alternada: a tecla de correr do jogo fica apertada; ao desligar, volta ao estado do teclado. */
    private void holdSprint() {
        boolean want = toggled && Players.self() != null && !Screens.isOpen();
        if (want) {
            Keys.game(Keys.GameKey.SPRINT).hold(true);
            holding = true;
        } else if (holding) {
            holding = false;
            Keys.game(Keys.GameKey.SPRINT).hold(false);
        }
    }

    /**
     * A tecla de aliado: mirando num jogador, marca ou tira (com Shift, o cla dele). <b>Sem mirar em ninguem, nao faz
     * nada</b>: quem abre o Painel e o Insert, e so ele — uma tecla do mod que abre tela no meio de uma briga era o
     * jeito mais facil de ficar parado olhando um menu.
     */
    private void allyAtCrosshair() {
        if (Screens.isOpen()) {
            return;
        }
        Object target = Targeting.pointedEntity();
        if (!Entities.isPlayer(target) || locked()) {
            return;
        }
        String nick = Entities.name(target);
        if (Keys.isShiftDown()) {
            String tag = clanOf(nick);
            if (tag == null) {
                chat(Lang.tr("steincombat.targetNoClan", nick));
                return;
            }
            if (config.allyClans.remove(tag)) {
                chat(Lang.tr("steincombat.clanRemoved", tag));
            } else {
                config.addAllyClan(tag);
                chat(Lang.tr("steincombat.clanAdded", tag));
            }
        } else if (config.hasAlly(nick)) {
            config.removeAlly(nick);
            chat(Lang.tr("steincombat.allyRemoved", nick));
        } else {
            config.addAlly(nick);
            chat(Lang.tr("steincombat.allyAdded", nick));
        }
        save();
    }

    /** A tecla de inimigo: mirando num jogador, marca ou tira (com Shift, o cla dele). Sem mirar, nao faz nada. */
    private void enemyAtCrosshair() {
        if (Screens.isOpen()) {
            return;
        }
        Object target = Targeting.pointedEntity();
        if (!Entities.isPlayer(target) || locked()) {
            return;
        }
        String nick = Entities.name(target);
        if (Keys.isShiftDown()) {
            String tag = clanOf(nick);
            if (tag == null) {
                chat(Lang.tr("steincombat.targetNoClan", nick));
                return;
            }
            if (config.enemyClans.remove(tag)) {
                chat(Lang.tr("steincombat.enemyClanRemoved", tag));
            } else {
                config.addEnemyClan(tag);
                chat(Lang.tr("steincombat.enemyClanAdded", tag));
            }
        } else if (config.hasEnemy(nick)) {
            config.removeEnemy(nick);
            chat(Lang.tr("steincombat.enemyRemoved", nick));
        } else {
            config.addEnemy(nick);
            chat(Lang.tr("steincombat.enemyAdded", nick));
        }
        save();
    }

    /** O proprio jogador nao entra em lista nenhuma. */
    private static boolean isMe(String nick) {
        return Players.self() != null && nick.equalsIgnoreCase(Players.self());
    }

    /**
     * Do meu cla, e so com "Meu cla e aliado" ligada — que e o que faz o cla contar como aliado. E quem esconder e
     * proteger olham: aliado marcado e cla aliado ficaram de fora dos dois.
     *
     * <p>Marcado como inimigo vale acima de tudo, inclusive aqui: um inimigo do meu proprio cla nao e protegido nem
     * some da tela.
     */
    boolean isClanmate(String nick) {
        if (!config.clanAllies || nick == null || isMe(nick) || isEnemy(nick)) {
            return false;
        }
        String mine = myClan();
        return mine != null && mine.equals(clanOf(nick));
    }

    /**
     * Golpe cancelado antes de ir ao servidor. <b>So no meu cla</b>: os aliados marcados e os clas aliados nao sao
     * mais protegidos — da para bater neles como em qualquer um.
     */
    @Override
    public boolean onAttackEntity(Object target) {
        if (!config.protectAllies || !Entities.isPlayer(target)) {
            return false;
        }
        String nick = Entities.name(target);
        // A protecao de aliado saiu do mod: ficou so a do proprio cla. O que ela era esta aqui, e uma linha —
        // trocar o isClanmate por isAlly devolve os nicks marcados e os clas aliados a protecao:
        //     if (!isAlly(nick)) {
        if (!isClanmate(nick)) {
            return false;
        }
        // Sem aviso na barra de acao: quem ligou "nao bater no meu cla" sabe o que pediu, e o texto piscando no meio
        // de uma briga atrapalha mais do que informa (e ainda cobre o que o servidor escreve ali).
        Object through = config.clanHitThrough && !hittingThrough ? behindClan(target) : null;
        if (through != null) {
            // O golpe atravessa: vai para quem esta atras, pelo mesmo caminho do clique (o braco ja balancou). O
            // Targeting.attack nao passa por aqui de novo; a marca e so a garantia de nunca entrar em volta.
            hittingThrough = true;
            try {
                Targeting.attack(through);
            } finally {
                hittingThrough = false;
            }
        }
        if (Boolean.getBoolean("steincombat.log")) {
            System.out.println("[mods] Combat: golpe no meu cla cancelado (" + nick + ")"
                    + (through == null ? "" : ", atravessou para " + Entities.name(through)));
        }
        return true;
    }

    /** Mandando o golpe que atravessa o meu cla (ver {@link #behindClan}). */
    private boolean hittingThrough;

    /**
     * Golpe que atravessa o meu cla: na linha da mira, no alcance de golpe do jogo e antes do primeiro bloco, o mais
     * perto que nao sou eu, nao e do meu cla e esta vivo — ou null. A mesma conta do jogo para mirar (a caixa de
     * cada entidade com a borda de colisao), so que sem parar no primeiro: o do meu cla na frente fica de fora.
     */
    private Object behindClan(Object clanmate) {
        Targeting.Ray ray = Targeting.raycast(Targeting.query().living().cutAtBlock().exclude(clanmate)
                .exclude(Entities.self()));
        for (Targeting.Hit hit : ray.hits) {
            Object e = hit.entity;
            if (hit.distance >= ray.blockDistance) {
                break;
            }
            if (Entities.isPlayer(e) && isClanmate(Entities.name(e))) {
                continue;   // outro do meu cla na fila: o golpe atravessa ele tambem
            }
            return e;
        }
        return null;
    }

    /**
     * O meu cla some da tela enquanto a tecla esta ligada — corpo, armadura, nome e sombra. Nao e a invisibilidade do
     * jogo: nao fica nada, nem a armadura.
     *
     * <p><b>So o meu cla</b>, e so com "Meu cla e aliado" ligada: esconder aliado marcado e cla aliado saiu do mod.
     * Eles continuam ali para tudo o mais: aparecem no Tab, no minimapa e nas mensagens, e continuam com a cor de
     * aliado no nome. E so a tela que fica limpa; desligando a tecla, todo mundo volta.
     */
    @Override
    public boolean onHideEntity(Object entity) {
        if (!hiding || !Entities.isPlayer(entity)) {
            return false;
        }
        if (entity == Entities.self()) {
            return false;   // eu nunca sumo de mim (terceira pessoa)
        }
        // Esconder aliado saiu do mod junto com a protecao, e pelo mesmo caminho: some quem e do meu cla. O que era
        // antes esta aqui, e uma linha — sumiam tambem os nicks marcados e os clas aliados:
        //     return isAlly(Entities.name(entity));
        return isClanmate(Entities.name(entity));
    }

    /**
     * Com a tecla de esconder ligada, o aliado que <b>nao</b> e do meu cla vira fantasma: a silhueta a 15% que o
     * jogo ja desenha de quem esta invisivel e do seu time, sem armadura e sem item na mao.
     *
     * <p>E o meio-termo de esconder aliado: da para enxergar quem esta batendo atras dele, mas ele continua ali —
     * com o nome acima da cabeca, e <b>levando golpe como qualquer um</b>. Sumir ganha de virar fantasma, entao o
     * meu cla continua sumindo inteiro.
     */
    @Override
    public boolean onGhostEntity(Object entity) {
        if (!hiding || !config.ghostAllies || !Entities.isPlayer(entity)) {
            return false;
        }
        if (entity == Entities.self()) {
            return false;   // eu nunca virei fantasma para mim (terceira pessoa)
        }
        String nick = Entities.name(entity);
        return isAlly(nick) && !isClanmate(nick);
    }

    @Override
    public String onPlayerListName(Object playerInfo, String name) {
        String nick = config.colorNames ? Players.nickOf(playerInfo) : null;
        if (nick == null) {
            return name;
        }
        // O mesmo texto do quadro passado (mesmo nick, mesma relacao): o mesmo texto pintado, sem montar de novo.
        Rel r = rel(nick);
        if (r.listText == null || !(r.listText == name || r.listText.equals(name))) {
            r.listPainted = paint(name, nick);
            r.listText = name;
        }
        return r.listPainted;
    }

    @Override
    public Object onNametag(Object entity, Object name) {
        if (!config.colorNames || !Entities.isPlayer(entity)) {
            return name;
        }
        String text = Chat.formatted(name);   // "" se nao for um texto do jogo
        String nick = Entities.name(entity);
        String painted;
        if (nick == null) {
            painted = paint(text, nick);
        } else {
            // Por nick: o texto que o jogo monta a cada quadro e quase sempre o mesmo, e o pintado tambem (a relacao
            // esta guardada junto, e recomeca quando ela pode ter mudado). Um componente novo a cada vez, como antes.
            Rel r = rel(nick);
            if (r.tagText == null || !(r.tagText == text || r.tagText.equals(text))) {
                r.tagPainted = paint(text, nick);
                r.tagText = text;
            }
            painted = r.tagPainted;
        }
        return painted.equals(text) ? name : Chat.text(painted);
    }

    /**
     * O nick na cor da relacao dele comigo, dentro do texto do jogo (a tag do cla e o que mais o servidor tenha posto
     * em volta ficam como estao). Meu cla, aliado, neutro e inimigo tem cada um a sua cor, escolhidas no Painel.
     */
    private String paint(String text, String nick) {
        int i = text.indexOf(nick);
        if (i < 0) {
            return text;
        }
        return text.substring(0, i) + "§" + CombatApi.code(nick) + nick + "§r" + text.substring(i + nick.length());
    }

    /** Texto acima da barra de itens. */
    /** Uma funcionalidade ligada ou desligada pela tecla: grava e avisa na action bar. */
    private void featureToggled(String nameKey, boolean on) {
        save();
        notice(Lang.tr(on ? "steincombat.featureOn" : "steincombat.featureOff", Lang.tr(nameKey)));
    }

    /**
     * A funcionalidade aparece na lista de funcionalidades ativas do HUD ({@link ActiveFeatures})? E a caixinha de
     * marcar na linha dela no Painel (Option.withCheck).
     */
    private boolean listed(String id) {
        return !config.featuresHidden.contains(id);
    }

    private void setListed(String id, boolean shown) {
        config.featuresHidden.remove(id);
        if (!shown) {
            config.featuresHidden.add(id);
        }
        save();
    }

    /** As quatro opcoes de uma regra do Auto Armor (modo, durabilidade, prioridade, sem encantamento). */
    private void armorRuleOptions(Page page, AutoArmor.Rule r, String prefix, java.util.function.BooleanSupplier when) {
        String[] modes = {tr("steincombat.armor.off"), tr("steincombat.armor.replace"), tr("steincombat.armor.swap")};
        page.option(Option.cycle(prefix + tr("steincombat.armor.mode"), modes, () -> r.mode, m -> {
            r.mode = m;
            save();
        }).visibleWhen(when));
        // Como Option (e nao Option.Slider): o metodo e da classe base.
        Option threshold = Option.slider(prefix + tr("steincombat.armor.threshold"), 5, 150, 5, () -> r.threshold, v -> {
            r.threshold = (int) Math.round(v);
            save();
        }, v -> String.valueOf(Math.round(v)));
        page.option(threshold.visibleWhen(when));
        page.option(Option.cycle(prefix + tr("steincombat.armor.priority"),
                new String[] {tr("steincombat.armor.highest"), tr("steincombat.armor.lowest")},
                () -> r.highest ? 0 : 1, i -> {
                    r.highest = i == 0;
                    save();
                }).visibleWhen(when));
        page.option(Option.cycle(prefix + tr("steincombat.armor.durability"),
                new String[] {tr("steincombat.armor.durability.off"), tr("steincombat.armor.durability.highest"),
                    tr("steincombat.armor.durability.lowest")},
                () -> r.durability, i -> {
                    r.durability = i;
                    save();
                }).visibleWhen(when));
        page.option(Option.toggle(prefix + tr("steincombat.armor.unenchanted"), () -> r.unenchanted, v -> {
            r.unenchanted = v;
            save();
        }).visibleWhen(when));
        // Nao deixar quebrar: desligado, ou Protecao IV a X.
        String[] keep = {tr("steincombat.armor.off"), "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        page.option(Option.cycle(prefix + tr("steincombat.armor.keep"), keep,
                () -> r.keepLevel <= 0 ? 0 : r.keepLevel - 3, i -> {
                    r.keepLevel = i == 0 ? 0 : i + 3;
                    save();
                }).visibleWhen(when));
    }

    private static void notice(String text) {
        Chat.actionBar(text);
    }

    private static void chat(String text) {
        Chat.print("§7[Combat] §r" + text);
    }

    /**
     * A pagina do Combat no Painel, que e o unico lugar de mexer nele: opcoes e a lista de aliados, tudo ali. O mod
     * nao tem mais tela de configuracao propria (o "Configurar" do menu do Loader fica desligado para ele).
     */
    @Override
    public void onPage(Page page) {
        page.icon("combat");
        CombatConfig c = config;
        // ---- Combate: o golpe ----
        page.section(tr("steincombat.section.combat"));
        page.option(Option.toggle(tr("steincombat.noHitDelay"), () -> c.noHitDelay, v -> {
            c.noHitDelay = v;
            save();
        }).withKey(noHitDelayKey::key, noHitDelayKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("noHitDelay"),
                        v -> setListed("noHitDelay", v)));
        // Autoclick: o ultimo na ordem (espera a armadura, o refil, os efeitos e a defesa), so sem tela aberta.
        page.option(Option.toggle(tr("steincombat.autoclick"), () -> c.autoclick, v -> {
            c.autoclick = v;
            save();
        }).withKey(autoclickKey::key, autoclickKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("autoclick"),
                        v -> setListed("autoclick", v)));
        page.option(Option.cycle("  " + tr("steincombat.autoclick.mode"),
                new String[] {tr("steincombat.autoclick.mode.hold"), tr("steincombat.autoclick.mode.always")},
                () -> c.autoclickMode, i -> {
                    c.autoclickMode = i;
                    save();
                }).visibleWhen(() -> c.autoclick));
        Option cps = Option.slider("  " + tr("steincombat.autoclick.cps"), CombatConfig.AUTOCLICK_MIN,
                CombatConfig.AUTOCLICK_MAX, 1, () -> c.autoclickCps, v -> {
                    c.autoclickCps = (int) Math.round(v);
                    save();
                }, v -> (int) Math.round(v) + " CPS");
        page.option(cps.visibleWhen(() -> c.autoclick));
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.noHitDelay = d.noHitDelay;
            c.autoclick = d.autoclick;
            c.autoclickMode = d.autoclickMode;
            c.autoclickCps = d.autoclickCps;
            apply();
            save();
            noHitDelayKey.setKey(Keys.KEY_NONE);
            autoclickKey.setKey(Keys.KEY_NONE);
        });
        // ---- Movimento: a corrida ----
        page.section(tr("steincombat.section.movement"));
        // A corrida alternada: a tecla (G) liga e desliga, e a linha tambem.
        page.option(Option.toggle(tr("steincombat.toggleSprintName"), () -> toggled, v -> toggled = v)
                .withKey(toggleKey::key, toggleKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("toggleSprint"),
                        v -> setListed("toggleSprint", v)));
        page.option(Option.toggle(tr("steincombat.sprintOnHit"), () -> c.sprintOnHit, v -> {
            c.sprintOnHit = v;
            save();
        }).withKey(keepSprintKey::key, keepSprintKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("keepSprint"),
                        v -> setListed("keepSprint", v)));
        // Sub-opcao do Keep Sprint (No Slowdown): desativada, fora do Painel.
        if (NO_SLOWDOWN_ENABLED) {
            page.option(Option.toggle("  " + tr("steincombat.noHitSlowdown"), () -> c.noHitSlowdown, v -> {
                c.noHitSlowdown = v;
                save();
            }).visibleWhen(() -> c.sprintOnHit));
        }
        page.option(Option.toggle(tr("steincombat.allDirections"), () -> c.allDirections, v -> {
            c.allDirections = v;
            save();
        }).withKey(omniSprintKey::key, omniSprintKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("omniSprint"),
                        v -> setListed("omniSprint", v)));
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.sprintOnHit = d.sprintOnHit;
            c.noHitSlowdown = d.noHitSlowdown;
            c.allDirections = d.allDirections;
            save();
            keepSprintKey.setKey(Keys.KEY_NONE);
            omniSprintKey.setKey(Keys.KEY_NONE);
            toggleKey.setKey(Keys.KEY_G);
        });
        // ---- Equipe: o meu cla, os aliados e os inimigos pela tag ----
        page.section(tr("steincombat.section.team"));
        page.option(Option.toggle(tr("steincombat.protect"), () -> c.protectAllies, v -> {
            c.protectAllies = v;
            save();
        }).withKey(clanProtectKey::key, clanProtectKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("clanProtect"),
                        v -> setListed("clanProtect", v)));
        // Sub-opcao do Clan Protect: o golpe cancelado no meu cla atravessa para quem esta atras.
        page.option(Option.toggle("  " + tr("steincombat.clanHitThrough"), () -> c.clanHitThrough, v -> {
            c.clanHitThrough = v;
            save();
        }).visibleWhen(() -> c.protectAllies));
        page.option(Option.toggle(tr("steincombat.hide"), () -> hiding, v -> hiding = v)
                .withKey(hideKey::key, hideKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("hiddenAllies"),
                        v -> setListed("hiddenAllies", v)));
        page.option(Option.toggle("  " + tr("steincombat.ghostAllies"), () -> c.ghostAllies, v -> {
            c.ghostAllies = v;
            save();
        }));
        page.option(Option.toggle(tr("steincombat.clanAllies"), () -> c.clanAllies, v -> {
            c.clanAllies = v;
            save();
        }));
        // Desligada por padrao: e ela que fazia "[VIP]" e "[ADM]" virarem inimigo em servidor que nao e de cla.
        page.option(Option.toggle(tr("steincombat.enemyFromPrefix"), () -> c.enemyFromPrefix, v -> {
            c.enemyFromPrefix = v;
            save();
        }).advanced());
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.protectAllies = d.protectAllies;
            c.clanHitThrough = d.clanHitThrough;
            c.ghostAllies = d.ghostAllies;
            c.clanAllies = d.clanAllies;
            c.enemyFromPrefix = d.enemyFromPrefix;
            save();
            clanProtectKey.setKey(Keys.KEY_NONE);
            hideKey.setKey(Keys.KEY_H);
        });
        // ---- Auto Armor: liga, a tecla, o jeito de cada peca e o limite da troca ----
        page.section(tr("steincombat.autoArmor"));
        page.option(Option.toggle(tr("steincombat.autoArmor"), () -> c.autoArmor, v -> {
            c.autoArmor = v;
            save();
        }).withKey(autoArmorKey::key, autoArmorKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("autoArmor"),
                        v -> setListed("autoArmor", v)));
        page.option(Option.cycle(tr("steincombat.armor.config"),
                new String[] {tr("steincombat.armor.simple"), tr("steincombat.armor.perPiece")},
                () -> c.armorPerPiece ? 1 : 0, i -> {
                    c.armorPerPiece = i == 1;
                    save();
                }));
        // Modo simples: uma regra para as quatro pecas.
        armorRuleOptions(page, c.armorAll, "", () -> !c.armorPerPiece);
        // Por peca: a mesma regra, uma para cada peca, com o nome dela na frente.
        int[] pieces = {3, 2, 1, 0};
        for (int piece : pieces) {
            armorRuleOptions(page, c.armorRules[piece], tr(AutoArmor.PIECE_KEYS[piece]) + ": ", () -> c.armorPerPiece);
        }
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.autoArmor = d.autoArmor;
            c.armorPerPiece = d.armorPerPiece;
            c.armorAll.copyFrom(d.armorAll);
            for (int i = 0; i < 4; i++) {
                c.armorRules[i].copyFrom(d.armorRules[i]);
            }
            save();
            autoArmorKey.setKey(Keys.KEY_NONE);
        });
        // ---- Auto Refill: liga sem o Auto Armor; quais pecas e armaduras servem sao as regras do Auto Armor ----
        page.section(tr("steincombat.autoRefill"));
        page.option(Option.toggle(tr("steincombat.autoRefill"), () -> c.autoRefill, v -> {
            c.autoRefill = v;
            save();
        }).withKey(autoRefillKey::key, autoRefillKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("autoRefill"),
                        v -> setListed("autoRefill", v)));
        page.option(Option.cycle(tr("steincombat.refill.mode"),
                new String[] {tr("steincombat.refill.mode.empty"), tr("steincombat.refill.mode.constant"),
                    tr("steincombat.refill.mode.key")},
                () -> c.refillMode, i -> {
                    c.refillMode = i;
                    if (i == AutoRefill.ON_KEY) {
                        c.autoRefill = false;   // na tecla ele so liga quando pedido
                    }
                    save();
                }));
        // Como Option (e nao Option.Text): o metodo e da classe base.
        Option command = new Option.Text(tr("steincombat.refill.command"), "/bau vip", () -> c.refillCommand, v -> {
            if (!v.trim().isEmpty()) {
                c.refillCommand = v;
                save();
            }
        });
        page.option(command);
        Option target = Option.slider(tr("steincombat.refill.target"), 1, 9, 1, () -> c.refillTarget, v -> {
            c.refillTarget = (int) Math.round(v);
            save();
        }, v -> String.valueOf(Math.round(v)));
        page.option(target.visibleWhen(() -> c.refillMode != AutoRefill.WHEN_EMPTY));
        page.option(Option.toggle(tr("steincombat.refill.deposit"), () -> c.refillDeposit, v -> {
            c.refillDeposit = v;
            save();
        }));
        page.option(Option.toggle(tr("steincombat.refill.window"), () -> c.refillShowWindow, v -> {
            c.refillShowWindow = v;
            save();
        }));
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.autoRefill = d.autoRefill;
            c.refillCommand = d.refillCommand;
            c.refillShowWindow = d.refillShowWindow;
            c.refillMode = d.refillMode;
            c.refillTarget = d.refillTarget;
            c.refillDeposit = d.refillDeposit;
            autoRefillKey.setKey(Keys.KEY_NONE);
            save();
        });
        // ---- Smart Effect: o que manter e quando ----
        page.section(tr("steincombat.smartEffect"));
        page.option(Option.toggle(tr("steincombat.smartEffect"), () -> c.smartEffect, v -> {
            c.smartEffect = v;
            save();
        }).withKey(smartEffectKey::key, smartEffectKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("smartEffect"),
                        v -> setListed("smartEffect", v)));
        page.option(Option.toggle(tr("steincombat.effect.autoGapple"), () -> c.autoGapple, v -> {
            c.autoGapple = v;
            save();
        }));
        page.option(Option.toggle(tr("steincombat.effect.autoSpeed"), () -> c.autoSpeed, v -> {
            c.autoSpeed = v;
            save();
        }));
        page.option(Option.toggle(tr("steincombat.effect.autoStrength"), () -> c.autoStrength, v -> {
            c.autoStrength = v;
            save();
        }));
        page.option(Option.toggle(tr("steincombat.effect.waitEnemy"), () -> c.effectWaitEnemy, v -> {
            c.effectWaitEnemy = v;
            save();
        }));
        Option radius = Option.slider("  " + tr("steincombat.effect.radius"), 2, 32, 1, () -> c.effectEnemyRadius,
                v -> {
                    c.effectEnemyRadius = (int) Math.round(v);
                    save();
                }, v -> Math.round(v) + " " + tr("steincombat.effect.blocks"));
        page.option(radius.visibleWhen(() -> c.effectWaitEnemy));
        page.option(Option.toggle(tr("steincombat.effect.onDamage"), () -> c.effectOnDamage, v -> {
            c.effectOnDamage = v;
            save();
        }));
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.smartEffect = d.smartEffect;
            c.autoGapple = d.autoGapple;
            c.autoSpeed = d.autoSpeed;
            c.autoStrength = d.autoStrength;
            c.effectWaitEnemy = d.effectWaitEnemy;
            c.effectEnemyRadius = d.effectEnemyRadius;
            c.effectOnDamage = d.effectOnDamage;
            save();
            smartEffectKey.setKey(Keys.KEY_NONE);
        });
        // ---- Auto Protect: liga sem o Smart Effect; na ordem, depois da Regeneracao e antes de Velocidade e Forca ----
        page.section(tr("steincombat.autoProtect"));
        page.option(Option.toggle(tr("steincombat.autoProtect"), () -> c.autoProtect, v -> {
            c.autoProtect = v;
            save();
        }).withKey(autoProtectKey::key, autoProtectKey::setKey)
                .withCheck(tr("steincombat.showInList"), () -> listed("autoProtect"),
                        v -> setListed("autoProtect", v)));
        page.option(Option.toggle(tr("steincombat.protect.defend"), () -> c.protectDefend, v -> {
            c.protectDefend = v;
            save();
        }));
        page.option(Option.toggle(tr("steincombat.protect.survive"), () -> c.protectSurvive, v -> {
            c.protectSurvive = v;
            save();
        }));
        // Em coracoes inteiros (o arquivo guarda pontos de vida: 2 por coracao).
        Option low = Option.slider("  " + tr("steincombat.protect.low"), 1, 8, 1, () -> c.protectLowHealth / 2.0,
                v -> {
                    c.protectLowHealth = 2 * (int) Math.round(v);
                    c.fixProtectHealth();   // a volta sobe junto, se ficou abaixo
                    save();
                }, SteinCombat::hearts);
        page.option(low.visibleWhen(() -> c.protectSurvive));
        Option resume = Option.slider("  " + tr("steincombat.protect.resume"), 2, 10, 1,
                () -> c.protectResumeHealth / 2.0, v -> {
                    c.protectResumeHealth = 2 * (int) Math.round(v);
                    if (c.protectResumeHealth < c.protectLowHealth + 2) {
                        c.protectLowHealth = c.protectResumeHealth - 2;   // a entrada desce junto, se ficou acima
                    }
                    save();
                }, SteinCombat::hearts);
        page.option(resume.visibleWhen(() -> c.protectSurvive));
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.autoProtect = d.autoProtect;
            c.protectDefend = d.protectDefend;
            c.protectSurvive = d.protectSurvive;
            c.protectLowHealth = d.protectLowHealth;
            c.protectResumeHealth = d.protectResumeHealth;
            save();
            autoProtectKey.setKey(Keys.KEY_NONE);
        });
        page.section(tr("steincombat.colorsSection"));
        page.option(Option.toggle(tr("steincombat.colorNames"), () -> c.colorNames, v -> {
            c.colorNames = v;
            save();
        }));
        page.option(Option.color(tr("steincombat.clanColor"), () -> String.valueOf(c.clanColor), v -> {
            c.clanColor = v.charAt(0);
            save();
        }).advanced());
        page.option(Option.color(tr("steincombat.allyColor"), () -> String.valueOf(c.allyColor), v -> {
            c.allyColor = v.charAt(0);
            save();
        }).advanced());
        page.option(Option.color(tr("steincombat.enemyColor"), () -> String.valueOf(c.enemyColor), v -> {
            c.enemyColor = v.charAt(0);
            save();
        }).advanced());
        page.option(Option.color(tr("steincombat.neutralColor"), () -> String.valueOf(c.neutralColor), v -> {
            c.neutralColor = v.charAt(0);
            save();
        }).advanced());
        page.restore(() -> {
            CombatConfig d = new CombatConfig();
            c.colorNames = d.colorNames;
            c.clanColor = d.clanColor;
            c.allyColor = d.allyColor;
            c.enemyColor = d.enemyColor;
            c.neutralColor = d.neutralColor;
            save();
        });
        // A lista pela internet vem antes das listas: e ela que decide se as de baixo estao valendo.
        page.section(tr("steincombat.remoteSection"));
        page.option(Option.toggle(tr("steincombat.remoteOn"), () -> c.remote, v -> {
            c.remote = v;
            save();
            if (v) {
                fetch(false);
            }
            // A pagina muda de forma (as listas passam a ser so de ver, ou voltam a ter os campos de escrever), e ela
            // e montada na abertura do Painel: sem reabrir, o jogador desligaria o modo e continuaria vendo a lista
            // sem os campos, sem entender por que nao da para escrever nela.
            dev.xavier.stein.loader.api.Panel.open(null, NAME);
        }));
        page.option(new Option.Text(tr("steincombat.remoteUrl"), tr("steincombat.remoteUrlHint"),
                () -> c.remoteUrl, v -> {
                    c.url(v);
                    failed = null;
                    save();
                }));
        page.option(new Line(tr("steincombat.remoteRefresh"), this::remoteStatus, () -> fetch(true)));
        page.restore(() -> {
            c.remote = false;
            c.remoteUrl = "";
            c.remoteFeeds.clear();
            failed = null;
            save();
        });
        boolean url = c.remote;
        page.section(tr("steincombat.alliesShort"));
        if (url) {
            // So para ver: no modo URL quem manda na lista e o arquivo.
            page.items(() -> chips(feed().allyClans, feed().allies), s -> { }, "", false);
        } else {
            page.option(new Option.Text(tr("steincombat.addLabel"), "", () -> pending,
                    v -> pending = v));
            page.option(Option.action(tr("steincombat.addPlayer"), "", () -> add(false))
                    .withKey(allyKey::key, allyKey::setKey));
            page.option(Option.action(tr("steincombat.addClan"), "", () -> add(true)));
            page.items(() -> chips(c.allyClans, c.allies), s -> {
                String t = clanChip(s);
                if (t != null) {
                    c.allyClans.remove(t);
                } else {
                    c.removeAlly(s);
                }
                save();
            }, "", true);
            page.restore(() -> {   // o padrao e nenhum aliado marcado
                c.allies.clear();
                c.allyClans.clear();
                save();
            });
        }
        page.section(tr("steincombat.enemiesShort"));
        if (url) {
            page.items(() -> chips(feed().enemyClans, feed().enemies), s -> { }, "", false);
        } else {
            page.option(new Option.Text(tr("steincombat.addLabel"), "",
                    () -> pendingEnemy, v -> pendingEnemy = v));
            page.option(Option.action(tr("steincombat.addPlayer"), "", () -> addEnemy(false))
                    .withKey(enemyKey::key, enemyKey::setKey));
            page.option(Option.action(tr("steincombat.addClan"), "", () -> addEnemy(true)));
            page.items(() -> chips(c.enemyClans, c.enemies), s -> {
                String t = clanChip(s);
                if (t != null) {
                    c.enemyClans.remove(t);
                } else {
                    c.removeEnemy(s);
                }
                save();
            }, "", true);
            page.restore(() -> {
                c.enemies.clear();
                c.enemyClans.clear();
                save();
            });
        }
    }

    /**
     * Uma lista para o Painel: os clas primeiro, entre colchetes, e depois os nicks. Os colchetes nao sao enfeite —
     * e por eles que o clique sabe se tirou um cla ou um jogador, e um cla de tres letras tem a mesma cara de um nick
     * de tres letras.
     */
    private static List<String> chips(List<String> clans, List<String> nicks) {
        List<String> all = new ArrayList<>();
        for (String tag : clans) {
            all.add("[" + tag + "]");
        }
        all.addAll(nicks);
        return all;
    }

    /** A tag de um item da lista que seja cla ("[ABC]" -> "ABC"), ou null se e um nick. */
    private static String clanChip(String item) {
        return item.startsWith("[") ? tag(item) : null;
    }

    /** O estado da lista remota, a direita do "Atualizar agora". */
    private String remoteStatus() {
        if (fetching) {
            return "§e" + tr("steincombat.remoteFetching");
        }
        String url = config.remoteUrl;
        if (url.isEmpty()) {
            return "§7" + tr("steincombat.remoteNoUrl");
        }
        Feed f = feed();
        if (failed != null) {
            return "§c" + tr("steincombat.remoteFail");
        }
        if (f.at == 0) {
            return "§7" + tr("steincombat.remoteNever");
        }
        // Verde so quando a lista esta valendo mesmo: com o modo desligado ela existe, mas nao manda em nada.
        return (config.remote ? "§a" : "§7") + tr("steincombat.remoteCount", String.valueOf(f.size()));
    }

    /** Texto do campo de inimigos enquanto o Painel esta aberto. */
    private String pendingEnemy = "";

    /** Acrescenta o que esta no campo aos inimigos, como cla ou como jogador (e tira dos aliados, se estava la). */
    private void addEnemy(boolean clan) {
        String text = pendingEnemy.trim();
        if (text.isEmpty()) {
            return;
        }
        if (clan) {
            String t = tag(text);
            if (t == null) {
                return;   // nao e uma tag: o texto fica no campo, para o jogador ver o que digitou
            }
            config.addEnemyClan(t);
        } else if (validNick(text) && !isMe(text)) {
            config.addEnemy(text);
        } else {
            return;
        }
        pendingEnemy = "";
        save();
    }

    /** Texto do campo de aliados enquanto o Painel esta aberto (nao vai para o arquivo). */
    private String pending = "";

    /** Acrescenta o que esta no campo como cla (com colchetes) ou como jogador. */
    private void add(boolean clan) {
        String text = pending.trim();
        if (text.isEmpty()) {
            return;
        }
        if (clan) {
            String t = tag(text);
            if (t == null) {
                return;   // nao e uma tag: o texto fica no campo, para o jogador ver o que digitou
            }
            config.addAllyClan(t);
        } else if (validNick(text) && !isMe(text)) {
            config.addAlly(text);
        } else {
            return;
        }
        pending = "";
        save();
    }

    private static String tr(String key) {
        return Lang.tr(key);
    }

    /** Coracoes do slider: "1 coracao", "3 coracoes". */
    private static String hearts(double v) {
        long n = Math.round(v);
        return n == 1 ? tr("steincombat.protect.heart") : Lang.tr("steincombat.protect.hearts", n);
    }

    private static String tr(String key, String arg) {
        return Lang.tr(key, arg);
    }

    /**
     * No modo URL as listas escritas a mao ficam paradas, e as teclas de marcar avisam e nao mudam nada: marcar
     * alguem numa lista que nao esta valendo so faria o jogador achar que marcou.
     */
    private boolean locked() {
        if (!config.remote) {
            return false;
        }
        chat(tr("steincombat.remoteLocked"));
        return true;
    }

    /**
     * Uma linha do Painel com texto vivo: o rotulo a esquerda, o que {@code value} disser na hora a direita, e o
     * clique faz {@code run}. E o "Atualizar agora", que precisa mostrar o estado da busca enquanto ela corre — o
     * {@link Option#action} guarda um texto fixo, escolhido quando a pagina foi montada.
     */
    private static final class Line extends Option {
        private final java.util.function.Supplier<String> value;
        private final Runnable run;

        Line(String label, java.util.function.Supplier<String> value, Runnable run) {
            super(label);
            this.value = value;
            this.run = run;
        }

        @Override
        public String value() {
            return value.get();
        }

        @Override
        public void click(boolean forward) {
            run.run();
        }
    }
}
