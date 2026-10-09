/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import java.util.List;
import dev.xavier.stein.loader.api.Effects;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Keys;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Screens;
import dev.xavier.stein.loader.api.Stacks;

/**
 * Smart Effect: mantem os efeitos de combate, comendo e bebendo sozinho, com qualquer tela aberta ou sem nenhuma.
 *
 * <ul>
 *   <li><b>Auto Gapple</b>: Regeneracao IV ou mais, com mais de 3 segundos — sem ela, come a maca dourada encantada
 *       (como o script de referencia: ate o efeito voltar);
 *   <li><b>Auto Speed</b> e <b>Auto Strength</b>: Velocidade e Forca ativas, com mais de 3 segundos — bebe a pocao (so
 *       as de beber, nunca as de arremesso).
 * </ul>
 *
 * <p><b>Quando:</b> sem nenhuma condicao ligada, sempre (fica comendo sempre que faltar). Com "esperar inimigo
 * perto", so com um jogador que nao e aliado nem do meu cla a ate tantos blocos; com "ao tomar dano", nos segundos
 * depois de levar um golpe. As duas juntas: qualquer uma das duas.
 *
 * <p><b>Prioridades:</b> a Regeneracao antes de tudo; depois a espada defendendo do Auto Protect ({@link AutoProtect},
 * que so decide: a mao e a tecla de usar sao daqui, um dono so); Velocidade e Forca esperam — e, com o "Segurar a
 * vida" agindo, esperam a vida voltar. A armadura (Auto Armor) vem antes: no tick em que ele age, este nao comeca nada
 * (os dois mexem no inventario) — mas um consumo ja comecado continua, porque os cliques dele nao tiram o item da mao.
 *
 * <p><b>Como:</b> traz o item para a hotbar (se estiver na bolsa), escolhe a posicao, usa o item e segura a tecla de
 * usar ate acabar; depois volta para a ultima arma usada (espada, machado ou arco).
 */
final class SmartEffect {

    /** {@code -Dsteincombat.debugNotices=true}: os avisos (sem item, troca, bau) no log. */
    private static final boolean NOTICES = Boolean.getBoolean("steincombat.debugNotices");

    private static final int SPEED = 1;
    private static final int STRENGTH = 5;
    private static final int REGENERATION = 10;
    /** Mais que isto de tempo restante, em ticks (3 segundos), conta como ativo. */
    private static final int MIN_TICKS = 60;
    /** Depois de levar dano, por quantos ticks a condicao "ao tomar dano" vale. */
    private static final int DAMAGE_WINDOW = 60;
    /** Ticks de espera pelo efeito depois de terminar de comer, antes de tentar de novo. */
    private static final int WAIT_EFFECT = 6;
    /** Comer leva 32 ticks; usando por mais que isto, o servidor se perdeu. */
    private static final int MAX_USE = 40;

    /** {@code -Dsteincombat.debugEffect=true}: cada passo no log (comecar, terminar, defender). */
    private static final boolean DEBUG = Boolean.getBoolean("steincombat.debugEffect");

    private final SteinCombat mod;
    /** O efeito sendo buscado agora (0 = parado). */
    private int effect;
    /** A posicao da hotbar com o item, enquanto consome. */
    private int slot = -1;
    private int ticks;
    /** Ticks parado (sem usar) desde o ultimo consumo. */
    private int idle;
    private int usingTicks;
    private int retries;
    /** Saiu da posicao por um tick para zerar o uso no servidor. */
    private boolean resyncing;
    /** A ultima posicao da hotbar com uma arma na mao. */
    private int weaponSlot = -1;
    private int damageTicks;
    private float lastHealth = -1;
    private boolean holding;
    /** Defendendo com a espada (Auto Protect). */
    private boolean blocking;
    /** A posicao da hotbar em que a espada esta defendendo (para ver o jogador trocar no meio). */
    private int blockSlot = -1;
    /** Ticks seguidos em que a armadura adiou o resto. */
    private int deferred;
    /** Ja avisou que falta o item (avisa de novo quando voltar a ter). */
    private final boolean[] warned = new boolean[3];
    /** Quando defender com a espada: este e o dono da mao e da tecla de usar, o Auto Protect so decide. */
    final AutoProtect protect;

    SmartEffect(SteinCombat mod) {
        this.mod = mod;
        this.protect = new AutoProtect(mod);
    }

    /**
     * No fim de cada tick, depois do Auto Armor ({@code armorActed}: ele mexeu no inventario neste tick).
     *
     * <p>A ordem: armadura, Regeneracao, a defesa com a espada (Auto Protect: "Segurar a vida" ou uma tela aberta),
     * Velocidade e Forca. A armadura so adia o resto por um tick: um Auto Armor que tenta e nao consegue equipar (e
     * tentaria de novo todo tick) nao pode segurar a maca.
     */
    /** Usando a mao agora: comendo, bebendo, trocando de posicao ou defendendo com a espada. */
    boolean busy() {
        return effect != 0 || blocking || resyncing;
    }

    void tick(boolean armorActed) {
        CombatConfig c = mod.config;
        Object me = Entities.self();
        protect.tick(me);
        if (me == null) {
            reset();
            return;
        }
        trackDamage(me);
        if (blocking && !Screens.isOpen() && Inventory.selectedHotbar() != blockSlot) {
            // O jogador trocou de posicao no meio da defesa (roda ou tecla numerica): ele manda. A tecla solta e a mao
            // fica onde ele pos; o "Segurar a vida" espera um pouco antes de pegar a espada de novo.
            release();
            blocking = false;
            protect.playerTookHand();
        }
        if (effect == 0 && !blocking) {
            trackWeapon();
        }
        boolean guard = protect.wantsBlock();
        // "Segurar a vida" agindo: primeiro a vida, depois as pocoes (Velocidade e Forca esperam a vida voltar).
        boolean wait = guard || protect.holdsBack();
        boolean defer = armorActed && deferred < 1;
        deferred = armorActed ? deferred + 1 : 0;
        boolean effects = c.smartEffect && active(c, me);
        if (effect != 0) {
            if (!c.smartEffect) {
                finish();
            } else if (effect != REGENERATION && wait) {
                abort();   // Velocidade e Forca cedem para a defesa e para a vida
            } else {
                continueUsing(me);
                return;
            }
        }
        // A Regeneracao antes da defesa: e a maca que traz a vida de volta. Com o "Segurar a vida" agindo ela vale
        // mesmo fora das condicoes de "quando" — ali ja e luta, e a vida esta no limite.
        boolean regen = c.smartEffect && c.autoGapple && (effects || protect.holdsBack());
        if (!defer && regen && missing(me, REGENERATION, 3)) {
            if (blocking) {
                release();   // a espada baixa para comer; a defesa volta quando a maca acabar
                blocking = false;
            }
            if (start(REGENERATION, 0)) {
                return;
            }
        }
        if (guard) {
            if (!defer) {
                block();
            }
            return;
        }
        if (blocking) {
            endBlock();
        }
        if (defer || !effects || wait) {
            return;
        }
        if (c.autoSpeed && missing(me, SPEED, 0) && start(SPEED, 1)) {
            return;
        }
        if (c.autoStrength && missing(me, STRENGTH, 0)) {
            start(STRENGTH, 2);
        }
    }

    /**
     * Auto Protect ({@link AutoProtect}): a espada na mao e defendendo, com a tecla de usar segurada (sem ela o jogo
     * larga a defesa no tick seguinte). Quando ele nao quer mais (a tela fechou, a vida voltou), solta a tecla e a mao
     * volta para a ultima arma.
     */
    private void block() {
        int sword = swordSlot();
        if (sword < 0) {
            if (blocking) {
                endBlock();   // a espada sumiu da mao (quebrou, caiu): nada de tecla segurada com outro item
            }
            return;
        }
        if (Inventory.selectedHotbar() != sword) {
            Inventory.selectHotbar(sword);
        }
        blockSlot = sword;
        if (!Inventory.isUsingItem()) {
            Inventory.useHeldItem();
            if (DEBUG) {
                System.out.println("[smart] defende na posicao " + sword);
            }
        }
        // O jogo solta o uso do item se a tecla de usar nao estiver apertada: segura so enquanto defende de fato.
        if (Inventory.isUsingItem()) {
            hold();
        } else {
            release();
        }
        blocking = true;
    }

    private void endBlock() {
        blocking = false;
        release();
        if (weaponSlot >= 0 && isWeapon(Inventory.stack(weaponSlot)) && Inventory.selectedHotbar() != weaponSlot) {
            Inventory.selectHotbar(weaponSlot);
        }
    }

    /** Uma espada na hotbar (a da ultima arma, se for espada); so na bolsa, ela vem para a hotbar. -1 sem espada. */
    private int swordSlot() {
        if (weaponSlot >= 0 && isSword(Inventory.stack(weaponSlot))) {
            return weaponSlot;
        }
        for (int i = 0; i < 9; i++) {
            if (isSword(Inventory.stack(i))) {
                return i;
            }
        }
        if (mod.refillBusy()) {
            return -1;   // com o bau do Auto Refill aberto, a bolsa e dele: so a espada que ja esta na hotbar
        }
        for (int i = Inventory.MAIN_START; i < Inventory.ARMOR_START; i++) {
            if (isSword(Inventory.stack(i))) {
                int hotbar = targetHotbar();
                int from = Inventory.slotOf(i);
                return from >= 0 && Inventory.swapHotbar(from, hotbar) ? hotbar : -1;
            }
        }
        return -1;
    }

    /** Alguma espada na hotbar ou na bolsa (o "Segurar a vida" so entra com uma). */
    static boolean hasSword() {
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            if (isSword(Inventory.stack(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSword(Object s) {
        String id = s == null ? null : Stacks.id(s);
        return id != null && id.endsWith("_sword");
    }

    /** Larga um consumo no meio (Velocidade ou Forca cedendo para a defesa). */
    private void abort() {
        release();
        effect = 0;
        slot = -1;
    }

    /** As condicoes: nenhuma ligada = sempre; senao, inimigo perto ou dano recente. */
    private boolean active(CombatConfig c, Object me) {
        if (!c.effectWaitEnemy && !c.effectOnDamage) {
            return true;
        }
        if (c.effectOnDamage && damageTicks > 0) {
            return true;
        }
        return c.effectWaitEnemy && enemyNear(me, c.effectEnemyRadius);
    }

    static boolean enemyNear(Object me, int radius) {
        for (Object p : Entities.players()) {
            if (p == me || !Entities.isAlive(p)) {
                continue;
            }
            int r = CombatApi.relation(Entities.name(p));
            if (r == CombatApi.ALLY || r == CombatApi.CLAN) {
                continue;
            }
            if (Entities.distance(me, p) <= radius) {
                return true;
            }
        }
        return false;
    }

    private void trackDamage(Object me) {
        float h = Entities.health(me);
        if (Entities.hurtTime(me) > 0 || lastHealth >= 0 && h < lastHealth) {
            damageTicks = DAMAGE_WINDOW;
        } else if (damageTicks > 0) {
            damageTicks--;
        }
        lastHealth = h;
    }

    /** Lembra a posicao da arma na mao (espada, machado ou arco), para voltar a ela depois. */
    private void trackWeapon() {
        int sel = Inventory.selectedHotbar();
        if (isWeapon(Inventory.stack(sel))) {
            weaponSlot = sel;
        }
    }

    private static boolean isWeapon(Object s) {
        String id = s == null ? null : Stacks.id(s);
        return id != null && (id.endsWith("_sword") || id.endsWith("_axe") || id.endsWith("bow"));
    }

    /** O efeito falta, ou acaba em menos de 3 segundos, ou esta abaixo do nivel (amplificador) minimo? */
    private static boolean missing(Object me, int id, int minAmplifier) {
        List<Effects.Effect> list = Effects.of(me);
        for (Effects.Effect e : list) {
            if (e.id == id && e.amplifier >= minAmplifier && (e.permanent || e.duration > MIN_TICKS)) {
                return false;
            }
        }
        return true;
    }

    /** Acha o item, traz para a hotbar, escolhe e comeca a usar. False se nao ha item. */
    private boolean start(int id, int warnIndex) {
        int index = find(id);
        if (index < 0) {
            if (!warned[warnIndex]) {
                warned[warnIndex] = true;
                notice(Lang.tr("steincombat.effect.none", Lang.tr(nameKey(id))));
            }
            return false;
        }
        warned[warnIndex] = false;
        int hotbar = index;
        if (index >= Inventory.MAIN_START) {
            // Da bolsa para a hotbar: uma posicao vazia, ou a que nao e a da arma.
            hotbar = targetHotbar();
            int from = Inventory.slotOf(index);
            if (from < 0 || !Inventory.swapHotbar(from, hotbar)) {
                return false;
            }
        }
        if (DEBUG) {
            System.out.println("[smart] comeca " + id + " na posicao " + hotbar);
        }
        effect = id;
        slot = hotbar;
        ticks = 0;
        idle = 0;
        usingTicks = 0;
        retries = 0;
        resyncing = false;
        Inventory.selectHotbar(slot);
        use();
        return true;
    }

    private void continueUsing(Object me) {
        ticks++;
        if (resyncing) {
            resyncing = false;
            Inventory.selectHotbar(slot);
            use();
            return;
        }
        if (!missing(me, effect, effect == REGENERATION ? 3 : 0)) {
            finish();
            return;
        }
        if (Inventory.selectedHotbar() != slot) {
            Inventory.selectHotbar(slot);   // o jogador trocou no meio: volta
        }
        Object held = Inventory.stack(slot);
        if (held == null || !matches(held, effect)) {
            // Acabou o item dessa posicao (ou ela mudou): procura outro do mesmo efeito.
            int id = effect;
            release();
            effect = 0;
            if (!start(id, id == REGENERATION ? 0 : id == SPEED ? 1 : 2)) {
                finish();
            }
            return;
        }
        if (Inventory.isUsingItem()) {
            idle = 0;
            hold();
            if (++usingTicks > MAX_USE) {
                // Comer leva 32 ticks: passou disso, o servidor nao esta acompanhando. Recomeca do zero.
                resync();
            }
            return;
        }
        usingTicks = 0;
        // Sem item em uso, a tecla de usar nao fica apertada (o jogo trataria como clique direito na mira).
        release();
        // Parou de usar. Acabou de comer: o efeito vem do servidor um instante depois — espera um pouco antes de
        // comer outra (senao seriam duas macas por uma). O efeito nao veio: o servidor nao aceitou o consumo (o item
        // voltou, ou ficou). Recomeca do zero — troca de posicao e volta, que zera o uso no servidor — e come de novo.
        if (++idle < WAIT_EFFECT && ticks > 1) {
            return;
        }
        idle = 0;
        if (ticks > 1) {
            resync();
            return;
        }
        use();
    }

    /**
     * O servidor perdeu o consumo (comeu no cliente e nao no servidor, ou o contrario). Solta a tecla e sai da posicao
     * por um tick: a troca de posicao chega ao servidor e zera o uso la; no tick seguinte volta e come de novo.
     */
    private void resync() {
        retries++;
        usingTicks = 0;
        release();
        if (DEBUG) {
            System.out.println("[smart] consumo nao confirmado, tentativa " + retries);
        }
        int other = slot == 8 ? 7 : slot + 1;
        Inventory.selectHotbar(other);
        resyncing = true;
    }

    private void use() {
        Inventory.useHeldItem();
        if (Inventory.isUsingItem()) {
            hold();   // so segura com o item em uso
        }
    }

    /** Segura a tecla de usar: sem tela aberta, o jogo para de consumir quando ela solta. */
    private void hold() {
        Keys.game(Keys.GameKey.USE).hold(true);
        holding = true;
    }

    private void release() {
        if (holding) {
            Keys.game(Keys.GameKey.USE).hold(false);
            holding = false;
        }
    }

    /** Pronto: solta a tecla e volta para a ultima arma (ou a primeira arma da hotbar). */
    private void finish() {
        if (DEBUG) {
            System.out.println("[smart] termina " + effect + ", volta para " + weaponSlot);
        }
        release();
        effect = 0;
        slot = -1;
        int back = weaponSlot >= 0 && isWeapon(Inventory.stack(weaponSlot)) ? weaponSlot : firstWeapon();
        if (back >= 0) {
            Inventory.selectHotbar(back);
        }
    }

    private void reset() {
        release();
        blocking = false;
        blockSlot = -1;
        effect = 0;
        slot = -1;
        lastHealth = -1;
    }

    private static int firstWeapon() {
        for (int i = 0; i < 9; i++) {
            if (isWeapon(Inventory.stack(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Uma posicao da hotbar para trazer o item: vazia; senao, a primeira que nao e arma. */
    private int targetHotbar() {
        for (int i = 0; i < 9; i++) {
            if (Inventory.stack(i) == null) {
                return i;
            }
        }
        for (int i = 8; i >= 0; i--) {
            if (i != weaponSlot && !isWeapon(Inventory.stack(i))) {
                return i;
            }
        }
        return weaponSlot == 8 ? 7 : 8;
    }

    /** O item (hotbar primeiro, depois a bolsa) que da o efeito. */
    private static int find(int id) {
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            Object s = Inventory.stack(i);
            if (s != null && matches(s, id)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matches(Object s, int id) {
        String item = Stacks.id(s);
        if (item == null) {
            return false;
        }
        if (id == REGENERATION) {
            return item.endsWith("golden_apple") && Stacks.damage(s) == 1;
        }
        if (!item.endsWith("potion") || Stacks.isSplashPotion(s)) {
            return false;
        }
        for (int e : Stacks.potionEffects(s)) {
            if (e == id) {
                return true;
            }
        }
        return false;
    }

    private static String nameKey(int id) {
        return id == REGENERATION ? "steincombat.effect.gapple" : id == SPEED ? "steincombat.effect.speed"
                : "steincombat.effect.strength";
    }

    /**
     * O que aconteceu (sem item, troca feita, bau vazio...). Fora da action bar: la so aparece ligar e desligar
     * (pedido do usuario); com {@code -Dsteincombat.debugNotices=true}, no log.
     */
    private static void notice(String text) {
        if (NOTICES) {
            System.out.println("[Smart Effect] " + text);
        }
    }
}
