/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Stacks;

/**
 * Auto Armor: repoe e troca a armadura sozinho, a cada tick, com os mesmos cliques de quem joga — sem abrir o
 * inventario, sem pausar, sem segurar o jogador. Vale com o inventario aberto ou fechado, no Esc, no chat, no
 * Painel: a janela do proprio inventario existe o tempo todo. A unica excecao e um container aberto (bau, fornalha,
 * bancada): o servidor so aceita clique na janela dele, e nela nao ha os slots da armadura; ao fechar, ele age no
 * mesmo tick.
 *
 * <p>Cada peca segue uma regra ({@link Rule}) — a mesma para todas no modo simples, ou uma por peca:
 * <ul>
 *   <li><b>modo</b>: desligado; <b>repor ao quebrar</b> (deixa estourar e veste a reserva no tick em que o slot
 *       esvazia); <b>trocar antes</b> (com a durabilidade no limite ou abaixo troca por uma reserva que tem mais que
 *       isso, e repoe se quebrar mesmo assim);
 *   <li><b>prioridade</b>: a reserva de maior protecao primeiro, ou a de menor (gastar as fracas antes);
 *   <li><b>sem encantamento</b>: se aceita reserva sem encantamento (desligado por padrao).
 * </ul>
 *
 * <p>A protecao de uma peca ({@link #score}) conta tudo: os pontos de armadura do material, a Protecao, as protecoes
 * especificas e Inquebravel. A durabilidade que sobra so entra se a regra pedir ({@link Rule#durability}), e so como
 * desempate entre pecas que protegem o mesmo; depois dela, a bolsa vem antes da hotbar.
 *
 * <p>Os cliques sao shift+cliques: tira a velha e veste a nova, sem passar pelo cursor (o jogador pode estar
 * segurando um item no inventario). Sem espaco livre, os tres cliques da troca, so com o cursor vazio.
 */
final class AutoArmor {

    /** {@code -Dsteincombat.debugNotices=true}: os avisos (sem item, troca, bau) no log. */
    private static final boolean NOTICES = Boolean.getBoolean("steincombat.debugNotices");

    static final int OFF = 0;
    static final int REPLACE = 1;
    static final int SWAP = 2;

    static final int DUR_OFF = 0;
    static final int DUR_HIGHEST = 1;
    static final int DUR_LOWEST = 2;

    /** A regra de uma peca (ou de todas, no modo simples). */
    static final class Rule {
        int mode = SWAP;
        /** Trocar com esta durabilidade restante, ou menos. */
        int threshold = 35;
        /** A reserva de maior protecao primeiro (false: a de menor). */
        boolean highest = true;
        /** Aceitar reserva sem encantamento. */
        boolean unenchanted = false;
        /** Desempate pela durabilidade: {@link #DUR_OFF} (ignora, o padrao), a maior primeiro ou a menor primeiro. */
        int durability = DUR_OFF;
        /**
         * Nao deixar quebrar a peca com Protecao deste nivel ou mais (4 a 10; 0 desligado): no limite de durabilidade
         * ela sai — trocada pela reserva, ou guardada no inventario se nao houver reserva —, mesmo no modo de repor.
         */
        int keepLevel = 0;

        void copyFrom(Rule o) {
            keepLevel = o.keepLevel;
            mode = o.mode;
            threshold = o.threshold;
            highest = o.highest;
            unenchanted = o.unenchanted;
            durability = o.durability;
        }
    }

    /** Ids dos encantamentos no jogo. */
    private static final int PROTECTION = 0;
    private static final int FIRE_PROTECTION = 1;
    private static final int BLAST_PROTECTION = 3;
    private static final int PROJECTILE_PROTECTION = 4;
    private static final int UNBREAKING = 34;

    /** Ticks de espera de uma peca depois de agir (o servidor confirmar). */
    private static final int COOLDOWN = 4;
    /** Meio segundo depois de agir, o inventario de verdade e pedido ao servidor (Inventory.resync). */
    private static final int VERIFY_TICKS = 10;
    /** Quanto esperar o inventario chegar depois do pedido; sem resposta, segue com o que tem. */
    private static final int RESYNC_TIMEOUT = 40;
    /**
     * Agir de novo na mesma peca antes disto (10 s) conta como a troca nao ter pegado. Maior que a espera maxima com a
     * conferencia: senao, depois da espera mais longa, a conta zerava e as tentativas voltavam a ser rapidas.
     */
    private static final int RETRY_WINDOW = 200;
    /** A espera depois de agir dobra a cada troca que nao pegou, ate 4 << 5 = 128 ticks (6,4 s). */
    private static final int MAX_BACKOFF = 5;
    /** Golpes seguidos sem a durabilidade da armadura vestida descer, antes de pedir o inventario de verdade. */
    private static final int STILL_HITS = 3;
    /** Intervalo minimo entre dois pedidos pela durabilidade parada (10 s). */
    private static final int STILL_RESYNC_GAP = 200;

    static final String[] PIECE_KEYS = {"steincombat.armor.boots", "steincombat.armor.leggings",
        "steincombat.armor.chestplate", "steincombat.armor.helmet"};

    /** {@code -Dsteincombat.debugArmor=true}: cada decisao no log. */
    private static final boolean DEBUG = Boolean.getBoolean("steincombat.debugArmor");

    private final SteinCombat mod;
    private final int[] cooldown = new int[4];
    /** Ja avisou que nao ha reserva para a peca (avisa de novo quando voltar a ter). */
    private final boolean[] warned = new boolean[4];

    /*
     * Conferencia com o servidor. O cliente aplica o clique na hora; se o servidor recusar sem devolver o slot (plugin
     * que cancela o clique, anticheat, lag), o cliente fica mostrando a armadura vestida que o servidor nao tem — e o
     * Auto Armor, que so ve o cliente, acha que esta tudo certo. Entao, depois de agir, o inventario de verdade e
     * pedido ao servidor (Inventory.resync); se a troca nao pegou, o slot volta vazio e a peca e trocada de novo. A
     * peca que nao pega varias vezes seguidas espera cada vez mais entre uma tentativa e outra, sem nunca parar de vez.
     * E levar golpes com a durabilidade da armadura vestida parada tambem e sinal de cliente diferente do servidor.
     */
    private int now;
    /** O tick em que o inventario sera pedido (-1 nenhum marcado). */
    private int verifyAt = -1;
    /** Esperando o inventario pedido ate este tick (-1 nao esperando): nada se decide sobre o estado velho. */
    private int awaitUntil = -1;
    private int lastResync = -STILL_RESYNC_GAP;
    private final int[] lastAct = {-RETRY_WINDOW, -RETRY_WINDOW, -RETRY_WINDOW, -RETRY_WINDOW};
    /** Trocas seguidas da peca que nao pegaram. */
    private final int[] fails = new int[4];
    /** Ja avisou que o servidor esta recusando a peca. */
    private final boolean[] warnedRefused = new boolean[4];
    /** Golpe a conferir: neste tick a durabilidade vestida e comparada com a de quando ele chegou. */
    private int hitCheckAt = -1;
    private long hitWear;
    private int stillHits;

    AutoArmor(SteinCombat mod) {
        this.mod = mod;
    }

    /** Se agiu neste tick (o Smart Effect nao comeca nada no mesmo tick). */
    private boolean acted;

    /** O servidor mandou a janela inteira (SteinMod.onWindowItems): a do inventario e o que o pedido esperava. */
    void windowItems(int windowId) {
        if (windowId == 0 && awaitUntil >= 0) {
            awaitUntil = -1;
            log("inventario de verdade chegou");
        }
    }

    /** A vida do jogador baixou: a armadura vestida devia gastar. Conferida uns ticks depois, quando o slot chega. */
    void hurt() {
        if (mod.config.autoArmor && hitCheckAt < 0 && wornWear() >= 0) {
            hitWear = wornWear();
            hitCheckAt = now + 10;
        }
    }

    /** O desgaste somado das pecas vestidas que gastam; -1 sem nenhuma. */
    private static long wornWear() {
        long sum = -1;
        for (int piece = 0; piece < 4; piece++) {
            Object s = Inventory.armor(piece);
            if (s != null && Stacks.isDamageable(s)) {
                sum = Math.max(sum, 0) + Stacks.damage(s);
            }
        }
        return sum;
    }

    /** Pede o inventario de verdade; ate ele chegar (ou o tempo acabar), nada se decide. */
    private void resync(String why) {
        if (Inventory.resync()) {
            lastResync = now;
            awaitUntil = now + RESYNC_TIMEOUT;
            log("inventario pedido ao servidor: " + why);
        }
    }

    /** Um tick; devolve true se mexeu no inventario. */
    boolean tick() {
        acted = false;
        now++;
        CombatConfig c = mod.config;
        if (!c.autoArmor || Entities.self() == null) {
            verifyAt = -1;
            awaitUntil = -1;
            hitCheckAt = -1;
            return false;
        }
        if (Inventory.isContainerOpen()) {
            return false;   // a conferencia marcada fica para quando fechar: a janela do bau nao tem a armadura
        }
        if (awaitUntil >= 0) {
            if (now < awaitUntil) {
                return false;
            }
            awaitUntil = -1;   // nao respondeu: segue com o que o cliente tem, e a proxima troca confere de novo
            log("o inventario pedido nao chegou");
        }
        if (verifyAt >= 0 && now >= verifyAt) {
            verifyAt = -1;
            resync("conferir a troca");
            return false;
        }
        if (hitCheckAt >= 0 && now >= hitCheckAt) {
            hitCheckAt = -1;
            long wear = wornWear();
            stillHits = wear >= 0 && wear == hitWear ? stillHits + 1 : 0;
            // Queda, fogo e veneno nao gastam armadura: so alguns golpes seguidos parados, e com intervalo, pedem.
            if (stillHits >= STILL_HITS && now - lastResync >= STILL_RESYNC_GAP) {
                stillHits = 0;
                resync("golpes sem a durabilidade descer");
                return false;
            }
        }
        if (Inventory.cursor() != null && !Inventory.isScreenOpen()) {
            // Sem tela aberta ninguem segura item no cursor: sobrou de uma troca que o servidor desfez. Larga num
            // espaco livre (com o inventario aberto pode ser o jogador arrastando: ai nao mexe).
            int free = Inventory.firstEmpty();
            if (free >= 0) {
                Inventory.pickup(Inventory.slotOf(free));
            }
            return true;
        }
        // Do capacete para as botas, como a tela mostra.
        for (int piece = Inventory.HELMET; piece >= Inventory.BOOTS; piece--) {
            if (cooldown[piece] > 0) {
                cooldown[piece]--;
                continue;
            }
            Rule r = c.armorRule(piece);
            if (r.mode == OFF) {
                continue;
            }
            Object worn = Inventory.armor(piece);
            if (worn == null) {
                // Repondo no modo de trocar: primeiro uma reserva acima do limite (senao a quase quebrada seria vestida e
                // trocada de novo logo em seguida); so sem nenhuma, qualquer uma que ainda tenha durabilidade.
                int index = r.mode == SWAP ? best(piece, r.threshold + 1, r) : -1;
                if (index < 0) {
                    index = best(piece, 1, r, false);
                }
                if (index < 0) {
                    // Nada melhor: as protegidas guardadas no limite voltam a ser usadas ate quebrar (sem nenhuma
                    // outra peca, e melhor gastar o que tem do que ficar sem armadura).
                    index = best(piece, 1, r, true);
                }
                act(piece, index, "steincombat.armor.equipped");
            } else if (Stacks.isDamageable(worn) && Stacks.maxDamage(worn) - Stacks.damage(worn) <= r.threshold) {
                boolean keep = protected_(worn, r);
                if (DEBUG) {
                    System.out.println("[armor] peca " + piece + " no limite (" + (Stacks.maxDamage(worn) - Stacks.damage(worn))
                            + "), protegida " + keep + ", modo " + r.mode);
                }
                if (r.mode == SWAP || keep) {
                    int index = best(piece, r.threshold + 1, r);
                    if (index >= 0) {
                        act(piece, index, "steincombat.armor.swapped");
                    } else if (keep && !warned[piece]) {
                        // Sem reserva nenhuma (e o Auto Refill desligado, ou o bau sem armadura que sirva): a peca fica
                        // no corpo e e usada ate quebrar — ficar sem armadura e pior. Chegando uma reserva (o refil),
                        // ela e trocada na hora.
                        warned[piece] = true;
                        notice(Lang.tr("steincombat.armor.noSpareKeep", Lang.tr(PIECE_KEYS[piece])));
                    }
                }
            }
        }
        return acted;
    }

    private void act(int piece, int index, String doneKey) {
        if (index < 0) {
            if (!warned[piece]) {
                warned[piece] = true;
                notice(Lang.tr("steincombat.armor.none", Lang.tr(PIECE_KEYS[piece])));
            }
            return;
        }
        warned[piece] = false;
        if (swap(piece, index)) {
            acted = true;
            // Agir de novo na mesma peca logo depois: a troca de antes nao pegou no servidor (a conferencia trouxe o
            // slot de volta). A espera dobra a cada vez, e volta ao normal quando uma troca fica.
            fails[piece] = now - lastAct[piece] < RETRY_WINDOW ? fails[piece] + 1 : 0;
            lastAct[piece] = now;
            cooldown[piece] = COOLDOWN << Math.min(fails[piece], MAX_BACKOFF);
            boolean warnNow = false;
            if (fails[piece] == 0) {
                warnedRefused[piece] = false;
            } else if (fails[piece] >= 3 && !warnedRefused[piece]) {
                warnedRefused[piece] = true;
                warnNow = true;
            }
            log("peca " + piece + " trocada" + (fails[piece] > 0 ? ", tentativa " + (fails[piece] + 1) : ""));
            if (verifyAt < 0) {
                verifyAt = now + VERIFY_TICKS;
            }
            notice(Lang.tr(warnNow ? "steincombat.armor.refused" : doneKey, Lang.tr(PIECE_KEYS[piece])));
        }
    }

    private static void log(String text) {
        if (DEBUG) {
            System.out.println("[armor] " + text);
        }
    }

    /** A peca tem Protecao no nivel que a regra nao deixa quebrar? */
    static boolean protected_(Object s, Rule r) {
        return r.keepLevel > 0 && Stacks.enchantLevel(s, PROTECTION) >= r.keepLevel;
    }

    /** A peca serve de reserva pela regra (o filtro de encantamento e a durabilidade acima do limite)? */
    static boolean usable(Object s, int piece, Rule r) {
        if (s == null || Inventory.armorPiece(s) != piece || !r.unenchanted && !Stacks.isEnchanted(s)) {
            return false;
        }
        int max = Stacks.maxDamage(s);
        return max <= 0 || max - Stacks.damage(s) > r.threshold;
    }

    /** Quantas reservas que servem a peca tem no inventario (hotbar e bolsa). */
    static int spares(int piece, Rule r) {
        int n = 0;
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            if (usable(Inventory.stack(i), piece, r)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Veste a peca do indice. Slot vazio: um shift+clique. Com a armadura ainda no corpo e um espaco livre: dois
     * shift+cliques (tira a velha, veste a nova), sem nada passar pelo cursor. Sem espaco: os tres cliques da troca (a
     * velha vai para o lugar da nova), so com o cursor vazio.
     */
    private static boolean swap(int piece, int index) {
        int from = Inventory.slotOf(index);
        if (from < 0) {
            return false;
        }
        if (Inventory.armor(piece) == null) {
            return Inventory.quickMove(from);
        }
        if (Inventory.firstEmpty() >= 0) {
            return Inventory.unequip(piece) && Inventory.quickMove(from);
        }
        return Inventory.cursor() == null && Inventory.equip(index);
    }

    /**
     * O indice da reserva da peca com pelo menos {@code minLeft} de durabilidade que a regra escolhe: a de maior
     * protecao, ou a de menor. A pontuacao sempre decide; so no empate a bolsa vem antes da hotbar (o que esta na
     * hotbar e o que o jogador quer a mao) — por isso a bolsa e lida primeiro e so uma pontuacao melhor troca a escolha.
     */
    private static int best(int piece, int minLeft, Rule r) {
        return best(piece, minLeft, r, false);
    }

    /** {@code allowKept}: aceita tambem a protegida que saiu no limite (o ultimo recurso, sem nenhuma outra). */
    private static int best(int piece, int minLeft, Rule r, boolean allowKept) {
        int best = -1;
        long bestScore = 0;
        int bestLeft = 0;
        for (int n = 0; n < Inventory.ARMOR_START; n++) {
            // A bolsa (9 a 35) e depois a hotbar (0 a 8).
            int bag = Inventory.ARMOR_START - Inventory.MAIN_START;
            int i = n < bag ? Inventory.MAIN_START + n : n - bag;
            Object s = Inventory.stack(i);
            if (s == null || Inventory.armorPiece(s) != piece || !r.unenchanted && !Stacks.isEnchanted(s)) {
                continue;
            }
            int max = Stacks.maxDamage(s);
            int left = max - Stacks.damage(s);
            if (max > 0 && left < minLeft) {
                continue;
            }
            if (!allowKept && max > 0 && left <= r.threshold && protected_(s, r)) {
                continue;   // a protegida que saiu para nao quebrar nao volta para o corpo
            }
            long score = score(s);
            boolean better = best < 0 || (r.highest ? score > bestScore : score < bestScore);
            if (!better && score == bestScore && r.durability != DUR_OFF) {
                // Protegem o mesmo: decide a durabilidade, se a regra pedir.
                better = r.durability == DUR_HIGHEST ? left > bestLeft : left < bestLeft;
            }
            if (better) {
                bestScore = score;
                bestLeft = left;
                best = i;
            }
        }
        return best;
    }

    /**
     * Quanto a peca protege, contando tudo. Os pontos de armadura do material e os de Protecao (EPF, a conta do jogo:
     * (6 + nivel ao quadrado) x 0,75 / 3) valem o mesmo — cada um tira cerca de 4% do dano; as protecoes especificas,
     * um pouco; Inquebravel, um pouco menos. A durabilidade nao entra (e opcao da regra, como desempate).
     */
    static long score(Object s) {
        int p = Stacks.enchantLevel(s, PROTECTION);
        long epf = p == 0 ? 0 : (long) Math.floor((6 + p * p) * 0.75 / 3.0);
        long special = Stacks.enchantLevel(s, FIRE_PROTECTION) + Stacks.enchantLevel(s, BLAST_PROTECTION)
                + Stacks.enchantLevel(s, PROJECTILE_PROTECTION);
        long score = Inventory.armorValue(s) * 1000L + epf * 1000L + special * 250L
                + Stacks.enchantLevel(s, UNBREAKING) * 120L;
        return score;
    }

    /**
     * O que aconteceu (sem item, troca feita, bau vazio...). Fora da action bar: la so aparece ligar e desligar
     * (pedido do usuario); com {@code -Dsteincombat.debugNotices=true}, no log.
     */
    private static void notice(String text) {
        if (NOTICES) {
            System.out.println("[Auto Armor] " + text);
        }
    }
}
