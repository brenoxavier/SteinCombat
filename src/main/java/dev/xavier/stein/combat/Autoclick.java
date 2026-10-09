/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Screens;

/**
 * Autoclick: cliques no ritmo escolhido ({@link CombatConfig#autoclickCps}), segurando o botao esquerdo (o padrao) ou
 * constante, sem segurar nada, enquanto estiver ligado ({@link CombatConfig#autoclickMode}).
 *
 * <p><b>Como um autoclick de fora:</b> cada clique e um aperto e uma soltura na fila de eventos do mouse
 * ({@link Keys#click}), a mesma em que o mouse fisico poe os dele. O jogo os le no tick seguinte, na ordem com os
 * outros: o golpe ou o bloco, o braco, os pacotes na ordem de sempre, o atraso de golpe do proprio jogo e o contador
 * de CPS do HUD (que conta os eventos). Nada de golpe chamado por fora. Segurando, o botao fisico continua apertado
 * para o jogo saber que o dedo esta la; o aperto de verdade ja e um clique, e o primeiro daqui vem um intervalo depois.
 *
 * <p><b>Ritmo:</b> a hora de cada clique sai do relogio (nanoTime), conferida a cada quadro: a CPS fica a escolhida
 * com qualquer FPS, sem variacao nenhuma; com mais de um clique por tick (CPS acima de 20), o jogo processa os do tick
 * um depois do outro, como faria com os do mouse. Uma travada (quadro preso, recarga) nao vira rajada: o ritmo
 * recomeca de onde esta.
 *
 * <p><b>Quando:</b> so sem tela aberta, e por ultimo na ordem do Combat — espera a armadura (Auto Armor), o refil
 * (Auto Refill), a comida e as pocoes (Smart Effect) e a espada defendendo (Auto Protect): enquanto eles mexem na mao
 * ou no inventario, nao sai clique (o golpe iria com o item errado, no meio de uma troca).
 */
final class Autoclick {

    /** Um atraso maior que isto (travada) recomeca o ritmo, em vez de mandar os cliques atrasados de uma vez. */
    private static final long STALL_NS = 100_000_000L;
    /** No maximo tantos cliques num quadro (FPS muito baixo com CPS alta): o resto do atraso e perdoado. */
    private static final int MAX_PER_FRAME = 8;

    private final SteinCombat mod;
    /** A hora (nanoTime) do proximo clique; 0: parado (o dedo nao esta no botao, ou algo esta na frente). */
    private long next;

    Autoclick(SteinCombat mod) {
        this.mod = mod;
    }

    /** A cada quadro, no fio do jogo. */
    void frame() {
        CombatConfig c = mod.config;
        boolean always = c.autoclickMode == 1;
        boolean trigger = always || Keys.isMouseDown(0);
        if (!c.autoclick || Screens.isOpen() || Entities.self() == null || !trigger || mod.handBusy()) {
            next = 0;
            return;
        }
        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, c.autoclickCps);
        if (next == 0) {
            if (!always) {
                next = now + interval;   // segurando: o aperto de verdade e o primeiro clique
                return;
            }
            next = now;
        }
        if (now - next > STALL_NS) {
            next = now;
        }
        for (int n = 0; now >= next && n < MAX_PER_FRAME; n++) {
            Keys.click(0);
            next += interval;
        }
        if (now >= next) {
            next = now + interval;
        }
    }
}
