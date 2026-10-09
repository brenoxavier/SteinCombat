/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

/**
 * O que o Stein Combat conta para outros mods (o minimapa do HUD pinta os jogadores por ela). Os mods ficam no mesmo
 * carregador de classes do Loader: quem quiser acha esta classe pelo nome, e sem o Combat ativo ela nao existe. O nome
 * e os metodos ficam fora da ofuscacao (tools/dist.sh).
 */
/*
 * Mantida para mods compilados contra ela (o HUD ate a 0.1.6). O caminho de agora e o do Loader: api.Relations e
 * api.Players, que o Combat alimenta.
 */
public final class CombatApi {

    /** Sem o Combat iniciado, ou o proprio jogador. */
    public static final int NONE = 0;
    /** Na lista de aliados ou num cla aliado, mas nao no meu cla. */
    public static final int ALLY = 1;
    /** Do meu cla. */
    public static final int CLAN = 2;
    /** De outro cla, que nao e aliado. */
    public static final int ENEMY = 3;
    /** Sem cla e sem alianca: nem inimigo nem aliado. Desde a 0.1.1. */
    public static final int NEUTRAL = 4;

    /** Cor de cada codigo de formatacao do jogo (0-9, a-f). */
    static volatile SteinCombat mod;

    private CombatApi() {
    }

    /**
     * Relacao do jogador de nick com o jogador local: {@link #NONE}, {@link #CLAN}, {@link #ALLY}, {@link #ENEMY} ou
     * {@link #NEUTRAL}.
     *
     * <p><b>Inimigo so com prova.</b> Vermelho na tela e um aviso, e aviso errado e pior do que aviso nenhum: quem
     * joga confia nele para decidir em quem bater. Entao so e inimigo quem esta marcado a mao pelo jogador, ou quem
     * o <b>plugin de clas</b> poe num cla diferente do meu — e, mesmo assim, so se eu tiver cla, porque a briga e
     * entre clas e quem nao tem nenhum nao esta em briga nenhuma.
     *
     * <p>O colchete no nome ({@code "[ABC] Nick"}) continua servindo para <b>reconhecer os meus</b>, e nunca para
     * pintar alguem de inimigo: num servidor comum {@code [VIP]}, {@code [ADM]} e {@code [PRO]} tem as mesmas tres
     * letras de uma tag de cla, e era assim que meia lista virava inimiga sem ser.
     */
    public static int relation(String nick) {
        SteinCombat m = mod;
        if (m == null || nick == null) {
            return NONE;
        }
        // Guardada no Combat enquanto listas, opcoes, meu nick e clas nao mudam (a nametag pergunta a cada quadro).
        return m.relation(nick);
    }


    /**
     * Tag do cla do jogador de nick ("ABC"), ou null sem cla. Vem do time "sc_TAG" do plugin e, com a opcao ligada,
     * do prefixo entre colchetes. E o que permite a outro mod agrupar por cla (a lista do Tab do HUD).
     */
    public static String clan(String nick) {
        return mod == null ? null : SteinCombat.clanOf(nick);
    }

    /** Cor dos aliados escolhida no Combat (RGB). */
    public static int allyColor() {
        SteinCombat m = mod;
        return m == null ? dev.xavier.stein.loader.api.Option.RGB[10] : rgb(m.config.allyColor);
    }

    /**
     * A cor do nick deste jogador (RGB), pela relacao e pelas quatro cores do Combat: meu cla, aliado, neutro e
     * inimigo. E o mesmo numero que o Tab, a cabeca e o minimapa usam, para os tres concordarem. Com as cores
     * desligadas, ou sem o Combat, devolve branco. Desde a 0.1.1.
     */
    public static int colorOf(String nick) {
        SteinCombat m = mod;
        if (m == null || !m.config.colorNames) {
            return 0xFFFFFF;
        }
        return rgb(switch (relation(nick)) {
            case CLAN -> m.config.clanColor;
            case ALLY -> m.config.allyColor;
            case ENEMY -> m.config.enemyColor;
            default -> m.config.neutralColor;
        });
    }

    /** O codigo de formatacao do jogo ('a', '9'...) como RGB. */
    static int rgb(char code) {
        return dev.xavier.stein.loader.api.Option.rgb(String.valueOf(code));
    }

    /** O codigo de formatacao do nick deste jogador, para quem escreve texto do jogo (o Tab e a cabeca). */
    static char code(String nick) {
        SteinCombat m = mod;
        if (m == null || !m.config.colorNames) {
            return 'f';
        }
        return switch (relation(nick)) {
            case CLAN -> m.config.clanColor;
            case ALLY -> m.config.allyColor;
            case ENEMY -> m.config.enemyColor;
            default -> m.config.neutralColor;
        };
    }
}
