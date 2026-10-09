/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Chat;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Stacks;

/**
 * Auto Refill: quando as reservas de armadura do inventario acabam (ou ficam abaixo do alvo, no modo constante),
 * manda o comando do bau virtual do servidor ("/bau vip"), espera o bau abrir e traz as armaduras para a bolsa — com
 * a tela do bau escondida (o jogador continua jogando) ou a vista, como o jogador escolher.
 *
 * <p>Um passo a cada 100 ms, e cada passo termina com o cursor vazio (o Smart Effect pode mexer no inventario no
 * meio). Trazer e: com um espaco livre na bolsa, pega do bau e larga nele; com uma armadura gasta na bolsa (e "guardar
 * as gastas" ligado, ou a bolsa cheia), <b>troca</b> — a do bau vem para o lugar da gasta e a gasta vai para o lugar
 * dela no bau, tres cliques. A troca e o que funciona com o bau cheio e com a bolsa cheia. Sem nada para trazer, as
 * gastas que sobraram vao para o bau, se couberem.
 *
 * <p>As regras sao as do Auto Armor de cada peca: so as pecas ligadas, o filtro de encantamento, e a prioridade de
 * protecao (as de maior ou de menor primeiro); gasta e a com a durabilidade no limite da regra.
 *
 * <p>Funciona com o Auto Armor ligado ou nao: ele so le as regras, que valem mesmo com o Auto Armor desligado. Juntos,
 * o Auto Armor veste e o refil repoe a bolsa; sozinho, o refil so mantem a bolsa com as reservas.
 *
 * <p>Tres modos: quando a reserva de uma peca zera, constante (mantem o alvo de reservas por peca) e na tecla — ai o
 * Auto Refill nao fica ligado: a tecla (ou o botao no Painel) faz uma ida ao bau na hora, ate o alvo, e ele se
 * desliga sozinho no fim.
 *
 * <p><b>Conferencia com o servidor</b>, como no Auto Armor: o cliente aplica cada clique na hora, e um clique que o
 * servidor recusa sem avisar deixaria o cliente achando que trouxe o que nao trouxe. Entao:
 * <ul>
 *   <li>cada passo confere o proprio resultado; tres passos seguidos sem efeito pedem o conteudo de verdade da janela
 *       (Inventory.resync) e esperam ele chegar antes de seguir;
 *   <li>antes de fechar, se houve clique desde a ultima conferencia, confere mais uma vez — se o servidor desfez algo,
 *       o trabalho continua;
 *   <li>um item que ficou no cursor (clique recusado no meio da troca) volta para um lugar livre no passo seguinte;
 *   <li>depois de fechar, a bolsa e conferida com o servidor (a janela do jogador);
 *   <li>nada trava: as conferencias por ida tem limite, a ida tem tempo maximo, e as idas que falham seguidas esperam
 *       cada vez mais antes da proxima.
 * </ul>
 */
final class AutoRefill {

    /** {@code -Dsteincombat.debugNotices=true}: os avisos (sem item, troca, bau) no log. */
    private static final boolean NOTICES = Boolean.getBoolean("steincombat.debugNotices");

    static final int WHEN_EMPTY = 0;
    static final int CONSTANT = 1;
    static final int ON_KEY = 2;

    /** 100 ms entre um passo e outro (2 ticks). */
    private static final int STEP_TICKS = 2;
    /** Quanto esperar o bau abrir depois do comando. */
    private static final int OPEN_TIMEOUT = 60;
    /** Tempo maximo com o bau aberto numa ida. */
    private static final int SESSION_TIMEOUT = 300;
    /** Passos seguidos sem efeito antes de pedir o conteudo de verdade. */
    private static final int STUCK_STEPS = 3;
    /** Conferencias com o servidor por ida, no maximo. */
    private static final int MAX_RESYNCS = 4;
    /** Quanto esperar o conteudo pedido chegar; sem resposta, segue com o que tem. */
    private static final int RESYNC_TIMEOUT = 40;
    /** Depois de fechar o bau, a bolsa e conferida com o servidor neste tanto de ticks. */
    private static final int VERIFY_TICKS = 10;

    /** O resultado de um passo. */
    private static final int NOTHING = 0;
    private static final int ACTED = 1;
    private static final int STUCK = 2;

    private enum State { IDLE, WAITING, WORKING }

    /** {@code -Dsteincombat.debugRefill=true}: cada passo no log. */
    private static final boolean DEBUG = Boolean.getBoolean("steincombat.debugRefill");

    private final SteinCombat mod;
    private State state = State.IDLE;
    private int timer;
    private int cooldown;
    private int stepWait;
    /** Armaduras trazidas nesta ida. */
    private int moved;
    /** As reservas que serviam ao abrir o bau: o aviso conta o que a ida ganhou, e nao os cliques refeitos. */
    private int startSpares;
    /** A janela do bau desta ida (para avisar o servidor se o jogador abrir outra tela por cima). */
    private int windowId;
    /** Passos seguidos sem efeito. */
    private int stuck;
    /** Conferencias pedidas nesta ida. */
    private int resyncs;
    /** Esperando o conteudo pedido ate este tanto de ticks (0 nao esperando). */
    private int awaiting;
    /** Houve clique desde a ultima conferencia. */
    private boolean dirty;
    /** O slot do bau do ultimo item pego (para onde volta um item que ficou no cursor). */
    private int lastFrom = -1;
    /** A bolsa ficou cheia sem gasta para trocar nesta ida (o aviso do fim). */
    private boolean bagFull;
    /** O bau nao aceitou guardar uma gasta nesta ida: nao tenta de novo ate a proxima. */
    private boolean depositBlocked;
    /** Ticks ate conferir a bolsa depois de fechar o bau (0 nada marcado). */
    private int verifyIn;
    /** Idas seguidas que nao trouxeram nada por recusa ou tempo: a espera ate a proxima dobra. */
    private int failStreak;

    AutoRefill(SteinCombat mod) {
        this.mod = mod;
    }

    /** Esta com o bau do refil aberto (ou esperando abrir)? */
    boolean busy() {
        return state != State.IDLE;
    }

    /** O servidor mandou a janela inteira (SteinMod.onWindowItems): a do bau e o que a conferencia esperava. */
    void windowItems(int id) {
        if (awaiting > 0 && state == State.WORKING && id == windowId) {
            awaiting = 0;
            log("conteudo de verdade chegou");
        }
    }

    void tick() {
        CombatConfig c = mod.config;
        if (Entities.self() == null) {
            state = State.IDLE;
            verifyIn = 0;
            if (c.autoRefill && c.refillMode == ON_KEY) {
                done(c);   // o pedido nao vale para o proximo mundo
            }
            return;
        }
        boolean onKey = c.refillMode == ON_KEY;
        switch (state) {
            case IDLE -> {
                if (verifyIn > 0 && --verifyIn == 0) {
                    if (Inventory.isContainerOpen()) {
                        verifyIn = 1;   // o jogador abriu outra janela: confere quando ela fechar
                    } else {
                        Inventory.resync();
                        log("bolsa conferida com o servidor");
                    }
                }
                if (cooldown > 0 && !onKey) {   // na tecla, quem pediu quer agora
                    cooldown--;
                    return;
                }
                if (!c.autoRefill || verifyIn > 0) {
                    return;   // a conferencia da ida anterior vem antes de decidir outra
                }
                if (onKey && !needs(c)) {
                    notice(Lang.tr("steincombat.refill.full"));
                    done(c);
                    return;
                }
                // Nunca por cima de uma tela de inventario do jogador: o bau tomaria o lugar dela.
                if (!Inventory.isContainerOpen() && !Inventory.isScreenOpen() && needs(c)) {
                    if (!c.refillShowWindow) {
                        // O bau vem virtual: aberto no servidor, sem tela no cliente — nem uma piscada.
                        Inventory.openVirtualNext(3000);
                    }
                    Chat.send(c.refillCommand.trim());
                    log("comando enviado: " + c.refillCommand.trim());
                    state = State.WAITING;
                    timer = OPEN_TIMEOUT;
                }
            }
            case WAITING -> {
                if (Inventory.isContainerOpen()) {
                    if (!c.refillShowWindow && Inventory.isScreenOpen()) {
                        Inventory.hideContainerScreen();   // o virtual nao pegou (outro caminho de abertura): esconde
                    }
                    log("bau aberto: " + Inventory.containerSize() + " slots, janela " + (c.refillShowWindow ? "a vista" : "escondida"));
                    state = State.WORKING;
                    windowId = Inventory.windowId();
                    timer = SESSION_TIMEOUT;
                    stepWait = STEP_TICKS;
                    moved = 0;
                    startSpares = totalSpares(c);
                    stuck = 0;
                    resyncs = 0;
                    awaiting = 0;
                    dirty = false;
                    lastFrom = -1;
                    bagFull = false;
                    depositBlocked = false;
                } else if (--timer <= 0) {
                    Inventory.openVirtualNext(0);
                    log("o bau nao abriu (janela " + Inventory.windowId() + ", tela " + Inventory.isScreenOpen() + ")");
                    notice(Lang.tr("steincombat.refill.noChest"));
                    state = State.IDLE;
                    cooldown = 200;
                    done(c);
                }
            }
            case WORKING -> work(c);
        }
    }

    private void work(CombatConfig c) {
        if (!Inventory.isContainerOpen() || Inventory.windowId() != windowId) {
            if (Inventory.isScreenOpen() || Inventory.windowId() != windowId) {
                // O jogador abriu o inventario (ou outra janela) por cima do bau escondido: o cliente ja trocou de
                // janela, o servidor ainda acha que o bau esta aberto e recusaria os cliques do jogador. Fecha o bau
                // la, sem mexer na tela dele; a busca volta quando ele fechar.
                Inventory.closeWindowOnServer(windowId);
                log("o jogador abriu outra janela: bau fechado no servidor (janela " + Inventory.windowId() + " em vez de "
                        + windowId + ", tela " + Inventory.isScreenOpen() + ")");
                state = State.IDLE;
                cooldown = 20;
                verifyIn = VERIFY_TICKS;
                done(c);
                return;
            }
            log("o servidor fechou o bau");
            state = State.IDLE;   // o servidor fechou
            cooldown = 40;
            verifyIn = VERIFY_TICKS;
            done(c);
            return;
        }
        if (--timer <= 0) {
            finish(false);
            return;
        }
        if (awaiting > 0) {
            if (--awaiting == 0) {
                log("o conteudo pedido nao chegou");   // segue com o que o cliente tem
            }
            return;
        }
        if (--stepWait > 0) {
            return;
        }
        stepWait = STEP_TICKS;
        int r = step(c);
        if (r == ACTED) {
            dirty = true;
            stuck = 0;
            return;
        }
        if (r == STUCK) {
            dirty = true;
            if (++stuck >= STUCK_STEPS) {
                stuck = 0;
                if (!resync("passos sem efeito")) {
                    finish(false);   // conferiu o que podia e o servidor continua recusando
                }
            }
            return;
        }
        // O bau abriu, mas os itens chegam num pacote depois: vazio nos primeiros ticks e esperar, nao "acabou".
        if (moved == 0 && SESSION_TIMEOUT - timer < 20 && chestEmpty()) {
            return;
        }
        // Nada mais a fazer pelo que o cliente ve. Se houve clique desde a ultima conferencia, confere antes de
        // fechar: o servidor pode ter desfeito algum sem avisar.
        if (dirty && resync("conferir antes de fechar")) {
            dirty = false;
            return;
        }
        finish(true);
    }

    /** Pede o conteudo de verdade do bau e da bolsa; false se as conferencias da ida acabaram. */
    private boolean resync(String why) {
        if (resyncs >= MAX_RESYNCS || !Inventory.resync()) {
            return false;
        }
        resyncs++;
        awaiting = RESYNC_TIMEOUT;
        log("conteudo pedido ao servidor: " + why);
        return true;
    }

    /** Um passo: devolve o item do cursor, traz (ou troca) uma armadura, ou guarda uma gasta. */
    private int step(CombatConfig c) {
        if (Inventory.cursor() != null) {
            int to = placeForCursor();
            log("item no cursor volta para o slot " + to);
            if (to < 0) {
                return STUCK;
            }
            Inventory.pickup(to);
            return Inventory.cursor() == null ? ACTED : STUCK;
        }
        int free = freeSlot();
        for (int piece : neediestFirst(c)) {
            AutoArmor.Rule r = c.armorRule(piece);
            if (r.mode == AutoArmor.OFF || AutoArmor.spares(piece, r) >= Math.max(1, c.refillTarget)) {
                continue;
            }
            int from = bestInChest(piece, r);
            if (from < 0) {
                continue;
            }
            // Com uma gasta na bolsa, troca: a do bau no lugar dela e ela no lugar da do bau. So com "guardar as
            // gastas" ligado ou sem espaco na bolsa — sem isso, a gasta e do jogador e fica com ele.
            int give = c.refillDeposit || free < 0 ? worn(c, piece) : -1;
            lastFrom = from;
            if (give >= 0) {
                int to = Inventory.slotOf(give);
                log("troca a peca " + piece + ": bau " + from + " <-> bolsa " + to);
                if (to < 0 || !Inventory.pickup(from)) {
                    return STUCK;
                }
                Inventory.pickup(to);
                Inventory.pickup(from);
                boolean ok = Inventory.cursor() == null && AutoArmor.usable(Inventory.stack(give), piece, r);
                if (ok) {
                    moved++;
                }
                return ok ? ACTED : STUCK;
            }
            if (free < 0) {
                bagFull = true;   // bolsa cheia e nenhuma gasta para trocar: esta peca fica sem
                continue;
            }
            int to = Inventory.slotOf(free);
            log("puxa a peca " + piece + " do slot " + from + " para " + to);
            if (to < 0 || !Inventory.pickup(from)) {
                return STUCK;
            }
            Inventory.pickup(to);
            boolean ok = Inventory.cursor() == null && Inventory.stack(free) != null;
            if (ok) {
                moved++;
            }
            return ok ? ACTED : STUCK;
        }
        // Nada a trazer: as gastas que sobraram vao para o bau, se ele tiver lugar.
        if (c.refillDeposit && !depositBlocked) {
            int g = worn(c, -1);
            if (g >= 0) {
                if (chestFree() < 0) {
                    depositBlocked = true;
                    log("bau cheio: as gastas que sobraram ficam na bolsa");
                    return NOTHING;
                }
                int slot = Inventory.slotOf(g);
                log("guarda a gasta do slot " + slot);
                if (slot < 0 || !Inventory.quickMove(slot)) {
                    return STUCK;
                }
                return Inventory.stack(g) == null ? ACTED : STUCK;
            }
        }
        return NOTHING;
    }

    private void finish(boolean done) {
        if (DEBUG) {
            int n = 0;
            for (int i = 0; i < Inventory.containerSize(); i++) {
                n += Inventory.slotStack(i) != null ? 1 : 0;
            }
            log("bau com " + n + " itens, janela " + Inventory.windowId() + ", livre " + freeSlot());
        }
        log("fecha: " + moved + " trazidas, " + (done ? "terminou" : "tempo esgotado ou recusado"));
        Inventory.closeContainer();
        state = State.IDLE;
        verifyIn = VERIFY_TICKS;
        if (moved > 0) {
            notice(Lang.tr("steincombat.refill.done", Math.max(0, totalSpares(mod.config) - startSpares)));
            failStreak = 0;
            cooldown = 40;
        } else if (done) {
            // Nada para trazer (o bau sem armadura que sirva, ou a bolsa cheia): espera mais antes de tentar de novo.
            notice(Lang.tr(bagFull ? "steincombat.refill.bagFull" : "steincombat.refill.empty"));
            failStreak = 0;
            cooldown = 1200;
        } else {
            // Tempo esgotado ou o servidor recusando: a espera dobra a cada ida assim, de 10 s ate 80 s, sem parar.
            notice(Lang.tr("steincombat.refill.timeout"));
            cooldown = 200 << Math.min(failStreak, 3);
            failStreak++;
        }
        done(mod.config);
    }

    /** No modo na tecla, a ida acabou (trouxe, nao achou ou nao abriu): o Auto Refill se desliga sozinho. */
    private void done(CombatConfig c) {
        if (c.refillMode == ON_KEY && c.autoRefill) {
            c.autoRefill = false;
            mod.save();
        }
    }

    /**
     * As pecas da que tem menos reservas para a que tem mais (no empate, do capacete as botas): cada peca ganha uma
     * reserva antes de alguma ganhar a segunda — com poucas gastas para trocar, nenhuma peca fica sem.
     */
    private static Integer[] neediestFirst(CombatConfig c) {
        Integer[] order = {Inventory.HELMET, Inventory.CHESTPLATE, Inventory.LEGGINGS, Inventory.BOOTS};
        int[] spares = new int[4];
        for (int piece = 0; piece < 4; piece++) {
            spares[piece] = AutoArmor.spares(piece, c.armorRule(piece));
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(spares[a], spares[b]));   // estavel: o empate fica
        return order;
    }

    /** As reservas que servem, somadas as quatro pecas. */
    private static int totalSpares(CombatConfig c) {
        int n = 0;
        for (int piece = 0; piece < 4; piece++) {
            n += AutoArmor.spares(piece, c.armorRule(piece));
        }
        return n;
    }

    /** Falta reserva de alguma peca ligada? (zero, no modo de quando acabar; abaixo do alvo, no constante e na tecla) */
    private static boolean needs(CombatConfig c) {
        for (int piece = Inventory.HELMET; piece >= Inventory.BOOTS; piece--) {
            AutoArmor.Rule r = c.armorRule(piece);
            if (r.mode == AutoArmor.OFF) {
                continue;
            }
            int spares = AutoArmor.spares(piece, r);
            if (c.refillMode == WHEN_EMPTY ? spares == 0 : spares < c.refillTarget) {
                return true;
            }
        }
        return false;
    }

    /**
     * Uma armadura gasta da bolsa ou da hotbar (no limite da regra dela): a da peca pedida primeiro, depois qualquer
     * uma; -1 sem nenhuma. {@code piece} -1 aceita qualquer peca.
     */
    private static int worn(CombatConfig c, int piece) {
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

    /** Para onde vai o item que ficou no cursor: o slot do bau de onde veio, outro vazio do bau, ou um da bolsa. */
    private int placeForCursor() {
        if (lastFrom >= 0 && lastFrom < Inventory.containerSize() && Inventory.slotStack(lastFrom) == null) {
            return lastFrom;
        }
        int chest = chestFree();
        if (chest >= 0) {
            return chest;
        }
        int free = freeSlot();
        return free < 0 ? -1 : Inventory.slotOf(free);
    }

    /** O slot do bau com a armadura da peca que a regra escolhe (maior ou menor protecao). */
    private static int bestInChest(int piece, AutoArmor.Rule r) {
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

    /** Um slot vazio do bau; -1 se ele esta cheio. */
    private static int chestFree() {
        for (int i = 0; i < Inventory.containerSize(); i++) {
            if (Inventory.slotStack(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private static boolean chestEmpty() {
        for (int i = 0; i < Inventory.containerSize(); i++) {
            if (Inventory.slotStack(i) != null) {
                return false;
            }
        }
        return true;
    }

    /** Um espaco livre da bolsa (9 a 35); sem nenhum, um da hotbar. */
    private static int freeSlot() {
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

    private static void log(String text) {
        if (DEBUG) {
            System.out.println("[refill] " + text);
        }
    }

    /**
     * O que aconteceu (sem item, troca feita, bau vazio...). Fora da action bar: la so aparece ligar e desligar
     * (pedido do usuario); com {@code -Dsteincombat.debugNotices=true}, no log.
     */
    private static void notice(String text) {
        if (NOTICES) {
            System.out.println("[Auto Refill] " + text);
        }
    }
}
