/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Screens;

/**
 * Auto Protect: a espada defendendo sozinha. Duas partes, cada uma com a sua chave, debaixo da chave do Auto Protect:
 *
 * <ul>
 *   <li><b>Defender</b>: com uma tela aberta (inventario, chat, Painel), a espada na mao e defendendo — quem mexe no
 *       inventario no meio da briga nao fica parado de braco abaixado;
 *   <li><b>Segurar a vida</b>: com a vida no limite ({@link CombatConfig#protectLowHealth}) no meio da luta, a espada
 *       defendendo ate a vida voltar ({@link CombatConfig#protectResumeHealth}). Defendendo, o golpe nao sai — o jogo
 *       nao bate com o item em uso, nem com o autoclick —, entao no grude o jogador para de tomar o contra-ataque do
 *       mcMMO (que devolve o dano de quem bate) e leva metade do dano de quem bate nele.
 * </ul>
 *
 * <p><b>A dinamica do "Segurar a vida"</b>, com duas marcas de vida (histerese):
 * <ul>
 *   <li>entra com a vida na marca de baixo ou abaixo, em luta (dano nos ultimos 3 segundos ou um inimigo — quem nao e
 *       aliado nem do meu cla — a ate 6 blocos) e com uma espada no inventario;
 *   <li>entrou, fica ate a vida chegar a marca de cima (ou ao maximo de vida, se ele for menor). Enquanto isso, a
 *       espada defende quando ha luta, e Velocidade e Forca do Smart Effect esperam — primeiro a vida, depois as
 *       pocoes. Sem luta (o inimigo foi embora), a espada baixa — da para fugir correndo —, mas as pocoes continuam
 *       esperando a vida;
 *   <li>o jogador trocou de posicao na hotbar no meio da defesa (a roda ou a tecla numerica): e ele que manda — a
 *       espada baixa por 3 segundos, e volta depois disso se a vida ainda estiver baixa.
 * </ul>
 *
 * <p><b>A ordem com o resto</b> (a mao e a tecla de usar tem um dono so, o {@link SmartEffect}, que pergunta aqui o
 * que fazer): armadura (Auto Armor), Regeneracao (a maca), as defesas daqui, e so depois Velocidade e Forca. A
 * Regeneracao vem antes de defender: comer a maca e o que traz a vida de volta, e defender e o que segura ate ela
 * vir. Faltando a Regeneracao, a espada baixa para comer e volta a defender no fim. O Auto Refill nao mexe na mao (so
 * na bolsa, pelo bau virtual) e segue junto; com o bau dele aberto, a espada so e procurada na hotbar.
 */
final class AutoProtect {

    /** Depois de levar dano, por quantos ticks ainda e luta (3 segundos). */
    private static final int COMBAT_WINDOW = 60;
    /** Um inimigo ate esta distancia e luta. */
    private static final int ENEMY_RADIUS = 6;
    /** Quanto a espada fica baixa quando o jogador troca de posicao no meio da defesa (3 segundos). */
    private static final int OVERRIDE_TICKS = 60;

    private final SteinCombat mod;
    /** "Segurar a vida" agindo: entrou abaixo da marca de baixo e ainda nao chegou a de cima. */
    private boolean holding;
    /** Ticks que ainda contam como luta depois do ultimo dano. */
    private int combatTicks;
    private float lastHealth = -1;
    /** Ticks de espada baixa porque o jogador trocou de posicao. */
    private int override;
    /** Luta agora (calculado uma vez por tick). */
    private boolean fighting;

    AutoProtect(SteinCombat mod) {
        this.mod = mod;
    }

    /** No comeco do tick do Smart Effect, antes de decidir a mao: a vida, a luta e a marca de entrar e sair. */
    void tick(Object me) {
        CombatConfig c = mod.config;
        if (me == null || !Entities.isAlive(me)) {
            reset();
            return;
        }
        float h = Entities.health(me);
        if (Entities.hurtTime(me) > 0 || lastHealth >= 0 && h < lastHealth) {
            combatTicks = COMBAT_WINDOW;
        } else if (combatTicks > 0) {
            combatTicks--;
        }
        lastHealth = h;
        if (override > 0) {
            override--;
        }
        if (!c.autoProtect || !c.protectSurvive) {
            holding = false;
            fighting = false;
            return;
        }
        fighting = combatTicks > 0 || SmartEffect.enemyNear(me, ENEMY_RADIUS);
        if (!holding) {
            if (h <= c.protectLowHealth && fighting && SmartEffect.hasSword()) {
                holding = true;
            }
        } else if (h >= Math.min(c.protectResumeHealth, Entities.maxHealth(me))) {
            holding = false;
            override = 0;
        }
    }

    /** "Defender": a chave ligada e uma tela aberta. */
    boolean defend() {
        CombatConfig c = mod.config;
        return c.autoProtect && c.protectDefend && Screens.isOpen();
    }

    /** "Segurar a vida" quer a espada defendendo agora: agindo, em luta e sem o jogador ter tomado a mao. */
    boolean survive() {
        return holding && fighting && override == 0;
    }

    /** Alguma das duas quer a espada defendendo agora. */
    boolean wantsBlock() {
        return defend() || survive();
    }

    /** "Segurar a vida" agindo (mesmo sem luta agora): Velocidade e Forca esperam a vida voltar. */
    boolean holdsBack() {
        return holding;
    }

    /** O jogador trocou de posicao no meio da defesa: a espada baixa por um tempo. */
    void playerTookHand() {
        override = OVERRIDE_TICKS;
    }

    void reset() {
        holding = false;
        fighting = false;
        combatTicks = 0;
        override = 0;
        lastHealth = -1;
    }
}
