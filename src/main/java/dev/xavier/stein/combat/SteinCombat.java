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
import dev.xavier.stein.loader.api.Hud;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.ModContext;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Page;
import dev.xavier.stein.loader.api.Players;
import dev.xavier.stein.loader.api.Relation;
import dev.xavier.stein.loader.api.Relations;
import dev.xavier.stein.loader.api.SteinMod;
import dev.xavier.stein.loader.internal.Combat;

import java.io.File;

/**
 * Combat: Better Sprinting's sprint and BattleChatter's allies, in code of its own.
 *
 * <ul>
 *   <li>Toggle sprint key (G): sprinting without holding the game's sprint key ({@link
 *       ToggleSprint}). With "Sprint in all directions" (an option), also backwards and sideways,
 *       through the Loader ({@code Combat.setSprintAllDirections}).
 *   <li>Allies: nicks and clans on a list (the Panel, or the Y key aimed at a player; with Shift,
 *       their clan), and the members of my clan (an option, on). What being an ally gives is <b>the
 *       name's color</b>, in the Tab list ({@code onPlayerListName}) and above the head ({@code
 *       onNametag}).
 *   <li>My clan, and only it, has hits cancelled before they reach the server and disappears from
 *       the screen with the hide key ({@link ClanGuard}) — both only with "My clan is allied" on.
 *   <li>Enemies: nicks and clans on a list (the Panel, or the U key aimed at a player; with Shift,
 *       their clan). Marked as enemy beats clan and alliance ({@link Teams}).
 *   <li>The list from the internet ({@link RemoteList}): instead of everyone writing their own, the
 *       four lists come from a JSON file at a URL — the clan's, for instance.
 *   <li>Fight automations: Auto Armor, Auto Refill, Smart Effect, Auto Protect and Autoclick, each
 *       in its own class, in that order every tick.
 * </ul>
 *
 * The clan tag comes from the scoreboard ({@link Clans}).
 */
public final class SteinCombat implements SteinMod {

    /** The mod's name, which is the name of its page in the Panel. */
    static final String NAME = "Combat";

    /** The mod id: the owner of what it registers in the Loader. */
    static final String MOD_ID = "steincombat";

    /** No Slowdown is disabled: out of the Panel and always off. true brings it back. */
    static final boolean NO_SLOWDOWN_ENABLED = false;

    private final ModContext ctx = ModContext.of(this);

    CombatConfig config = new CombatConfig();
    final Teams teams = new Teams(this);
    final ToggleSprint sprint = new ToggleSprint();
    final ClanGuard guard = new ClanGuard(this);
    final RemoteList remote = new RemoteList(this);
    final Marks marks = new Marks(this);
    CombatKeys keys;

    private final AutoArmor autoArmor = new AutoArmor(this);
    private final AutoRefill autoRefill = new AutoRefill(this);
    private final SmartEffect smartEffect = new SmartEffect(this);
    private final Autoclick autoclick = new Autoclick(this);
    private final Diagnostics diagnostics = new Diagnostics(this);

    /** Ticks since Auto Armor last touched the inventory. */
    private int armorQuiet = 100;

    private File configFile;

    @Override
    public void afterStartGame() {
        configFile = ctx.configFile();
        config = ConfigFile.load(configFile);
        keys = new CombatKeys(this);
        apply();
        CombatApi.mod = this;
        // What this server calls a group, and who is what to the player: the Loader takes it to
        // every
        // mod (Relations/Players). The group is decided entirely here — this project's clan
        // plugin's
        // sc_ team, or the bracketed three-letter tag; "" = no group, and the Loader's generic way
        // is
        // not even tried.
        Players.groupResolver(
                MOD_ID,
                nick -> {
                    String tag = Clans.of(nick);
                    return tag == null ? "" : tag;
                });
        Relations.register(MOD_ID, teams::relationOf);
        publishColors();
        Hud.register(new NearestPlayer(this, false));
        Hud.register(new NearestPlayer(this, true));
        Hud.register(new ActiveFeatures(this));
        log().info(
                        "started ("
                                + configFile
                                + ", "
                                + config.allies.size()
                                + " allies, "
                                + config.allyClans.size()
                                + " allied clans, "
                                + config.enemies.size()
                                + " enemies)");
    }

    Log log() {
        return ctx.log();
    }

    File configFile() {
        return configFile;
    }

    /**
     * Joined a server: this is where the clan's list is fetched, once per join — so it arrives
     * fresh every session without anyone pressing anything, and a change of side in the file
     * applies to everyone on the next join.
     */
    @Override
    public void onJoinGame() {
        remote.joined();
        apply();
        if (config.remote) {
            remote.fetch(false);
        }
    }

    /**
     * The whole inventory arrived from the server: Auto Armor and Auto Refill may be waiting for it
     * in a check.
     */
    @Override
    public void onWindowItems(int windowId) {
        autoArmor.windowItems(windowId);
        autoRefill.windowItems(windowId);
    }

    /** Auto Refill has the chest open (or is waiting for it): the bag is its. */
    boolean refillBusy() {
        return autoRefill.busy();
    }

    /** The player's health went down: Auto Armor checks whether the worn armor wore. */
    @Override
    public void onHealthChanged(
            Object entity,
            float oldHealth,
            float newHealth,
            float oldAbsorption,
            float newAbsorption) {
        if (entity == Entities.self() && newHealth + newAbsorption < oldHealth + oldAbsorption) {
            autoArmor.hurt();
        }
    }

    /** After changing the options: saves, rebuilds the lists and tells the Loader. */
    void save() {
        if (configFile != null) {
            ConfigFile.save(config, configFile);
        }
        apply();
    }

    /**
     * Rebuilds the lists in effect ({@link Teams#apply}: one source or the other), sets the engine
     * switches and tells the Loader.
     */
    void apply() {
        teams.apply(config, config.remote ? remote.feed() : null);
        Combat.setNoHitDelay(config.noHitDelay);
        Combat.setSprintOnHit(config.sprintOnHit);
        // No Slowdown: disabled (hidden from the Panel, always off). The code stays to bring it
        // back.
        Combat.setNoHitSlowdown(NO_SLOWDOWN_ENABLED && config.noHitSlowdown);
        Combat.setSprintAllDirections(config.allDirections);
        publishColors();
    }

    /**
     * The colors chosen here apply to every mod; names without color, white in all. Says something
     * changed.
     */
    private void publishColors() {
        boolean on = config.colorNames;
        Relations.setColor(
                Relation.GROUP, on ? Option.rgb(String.valueOf(config.clanColor)) : 0xFFFFFF);
        Relations.setColor(
                Relation.ALLY, on ? Option.rgb(String.valueOf(config.allyColor)) : 0xFFFFFF);
        Relations.setColor(
                Relation.ENEMY, on ? Option.rgb(String.valueOf(config.enemyColor)) : 0xFFFFFF);
        Relations.setColor(
                Relation.NEUTRAL, on ? Option.rgb(String.valueOf(config.neutralColor)) : 0xFFFFFF);
        Relations.changed();
    }

    /** The Autoclick: each click's time is checked every frame (see {@link Autoclick}). */
    @Override
    public void onRenderWorld(float partialTicks) {
        autoclick.frame();
    }

    /**
     * Another Combat feature has the hand or the inventory: eating, drinking, switching slots,
     * blocking (Smart Effect and Auto Protect), with the refill chest (Auto Refill) or equipping
     * armor (Auto Armor, until one tick later). The Autoclick, last in line, waits.
     */
    boolean handBusy() {
        return smartEffect.busy() || autoRefill.busy() || armorQuiet < 2;
    }

    @Override
    public void onTickEnd() {
        if (keys == null) {
            return;
        }
        // Changed server: the per-server sections may be others, and with them the lists.
        if (remote.serverChanged()) {
            apply();
        }
        diagnostics.beforeKeys();
        keys.handle();
        // Armor first: in the tick it touches the inventory, Smart Effect starts nothing.
        boolean armorActed = autoArmor.tick();
        armorQuiet = armorActed ? 0 : Math.min(100, armorQuiet + 1);
        autoRefill.tick();
        smartEffect.tick(armorActed);
        sprint.tick();
        diagnostics.afterTick();
    }

    @Override
    public boolean onAttackEntity(Object target) {
        return guard.onAttackEntity(target);
    }

    @Override
    public boolean onHideEntity(Object entity) {
        return guard.onHideEntity(entity);
    }

    @Override
    public boolean onGhostEntity(Object entity) {
        return guard.onGhostEntity(entity);
    }

    @Override
    public String onPlayerListName(Object playerInfo, String name) {
        String nick = config.colorNames ? Players.nickOf(playerInfo) : null;
        return nick == null ? name : teams.paintListName(nick, name);
    }

    @Override
    public Object onNametag(Object entity, Object name) {
        if (!config.colorNames || !Entities.isPlayer(entity)) {
            return name;
        }
        String text = Chat.formatted(name); // "" if it is not a game text
        // A new component every time it changes, as before.
        String painted = teams.paintNametag(Entities.name(entity), text);
        return painted.equals(text) ? name : Chat.text(painted);
    }

    @Override
    public void onPage(Page page) {
        CombatPage.build(this, page);
    }
}
