/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;

/**
 * A lista das funcionalidades do Combat ligadas agora, na grade do HUD: uma linha por funcionalidade, com o nome dela
 * (o mesmo do Painel), uma barra de destaque do lado da borda e um fundo escuro. As cores sao as da categoria
 * ({@link Features#COLORS}: combate, movimento, equipe, automacao), um arco-iris que corre pela lista, ou uma cor so.
 *
 * <p>Ligar e desligar anima: a linha entra deslizando da borda e sai do mesmo jeito, e as outras se ajeitam. Cada
 * funcionalidade pode ficar fora da lista ("Mostrar na lista", no Painel). Sem nada ligado, o elemento some.
 */
final class ActiveFeatures implements HudElement {

    private static final int LINE = 11;
    private static final int PAD = 3;
    private static final int BAR = 2;
    /** Segundos de uma entrada ou saida. */
    private static final float ANIM_SECONDS = 0.18F;

    private final SteinCombat mod;
    /** O que esta na tela, entrando, saindo ou parado: id -> progresso (0 fora, 1 inteiro). */
    private final Map<String, Float> shown = new LinkedHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private long last = System.nanoTime();
    private int w;
    private int h;

    private static final class Row {
        String text;
        int category;
        int width;
        float t;
    }

    ActiveFeatures(SteinCombat mod) {
        this.mod = mod;
    }

    @Override
    public String id() {
        return "steincombat.features";
    }

    @Override
    public String name() {
        return Lang.tr("steincombat.hud.features");
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        long now = System.nanoTime();
        float step = Math.min(1F, (now - last) / 1e9F / ANIM_SECONDS);
        last = now;
        CombatConfig c = mod.config;
        // Quem deveria estar: ligada e nao escondida da lista (na previa do editor, duas de exemplo se nada liga).
        List<Features.Feature> want = new ArrayList<>();
        for (Features.Feature f : Features.ALL) {
            if (f.on.test(mod) && !c.featuresHidden.contains(f.id)) {
                want.add(f);
            }
        }
        if (want.isEmpty() && preview) {
            want.add(Features.ALL.get(0));
            want.add(Features.byId("keepSprint"));
        }
        for (Features.Feature f : Features.ALL) {
            boolean in = want.contains(f);
            Float t = shown.get(f.id);
            if (t == null && !in) {
                continue;
            }
            float v = t == null ? 0F : t;
            v = in ? Math.min(1F, v + step) : Math.max(0F, v - step);
            if (v <= 0F && !in) {
                shown.remove(f.id);
            } else {
                shown.put(f.id, v);
            }
        }
        rows.clear();
        for (Features.Feature f : Features.ALL) {
            Float t = shown.get(f.id);
            if (t == null) {
                continue;
            }
            Row r = new Row();
            r.text = Lang.tr(f.nameKey);
            r.category = f.category;
            r.width = Render.textWidth(r.text);
            r.t = t;
            rows.add(r);
        }
        if (rows.isEmpty()) {
            return false;
        }
        if (c.featuresByWidth) {
            rows.sort((a, b) -> Integer.compare(b.width, a.width));
        }
        w = 0;
        float total = 0;
        for (Row r : rows) {
            w = Math.max(w, r.width + PAD * 2 + BAR);
            total += LINE * ease(r.t);
        }
        h = Math.max(1, Math.round(total));
        return true;
    }

    @Override
    public int width() {
        return w;
    }

    @Override
    public int height() {
        return h;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        CombatConfig c = mod.config;
        int fixed = 0xFF000000 | Option.rgb(String.valueOf(c.featuresColor));
        float y = 0;
        long time = System.currentTimeMillis();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float e = ease(r.t);
            int color = c.featuresStyle == 0 ? Features.COLORS[r.category]
                    : c.featuresStyle == 1 ? rainbow(time, i) : fixed;
            int rowW = r.width + PAD * 2 + BAR;
            // Entra deslizando da borda em que a lista esta presa, e fica transparente ate chegar.
            float slide = (1 - e) * (rowW + 4);
            float x = alignRight ? w - rowW + slide : -slide;
            if (c.featuresBackground) {
                Render.rect(x, y, rowW, LINE * e, Render.alpha(0xB0101014, e));
            }
            float barX = alignRight ? x + rowW - BAR : x;
            Render.rect(barX, y, BAR, LINE * e, Render.alpha(color, e));
            int a = (int) (255 * e);
            if (a > 8) {
                float tx = alignRight ? x + PAD : x + BAR + PAD;
                Render.text(r.text, tx, y + 2, (a << 24) | (color & 0xFFFFFF), true);
            }
            y += LINE * e;
        }
    }

    /** Arco-iris que corre pela lista: cada linha um pouco a frente da de cima. */
    private static int rainbow(long time, int index) {
        float hue = ((time % 4000) / 4000F + index * 0.07F) % 1F;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.55F, 1F) & 0xFFFFFF);
    }

    private static float ease(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    @Override
    public HudPlacement defaultPlacement() {
        // Canto de cima, a direita, embaixo do minimapa.
        return new HudPlacement(2, 0, -2, 72, 1.0F, true, true);
    }

    @Override
    public void options(List<Option> out) {
        CombatConfig c = mod.config;
        out.add(Option.cycle(Lang.tr("steincombat.hud.featuresStyle"),
                new String[] {Lang.tr("steincombat.hud.featuresStyle.category"),
                    Lang.tr("steincombat.hud.featuresStyle.rainbow"), Lang.tr("steincombat.hud.featuresStyle.fixed")},
                () -> c.featuresStyle, i -> c.featuresStyle = i));
        out.add(Option.color(Lang.tr("steincombat.hud.featuresColor"), () -> String.valueOf(c.featuresColor),
                v -> c.featuresColor = v.charAt(0)).visibleWhen(() -> c.featuresStyle == 2));
        out.add(Option.toggle(Lang.tr("steincombat.hud.featuresBackground"), () -> c.featuresBackground,
                v -> c.featuresBackground = v));
        out.add(Option.cycle(Lang.tr("steincombat.hud.featuresOrder"),
                new String[] {Lang.tr("steincombat.hud.featuresOrder.width"), Lang.tr("steincombat.hud.featuresOrder.fixed")},
                () -> c.featuresByWidth ? 0 : 1, i -> c.featuresByWidth = i == 0));
    }

    @Override
    public void restoreOptions() {
        CombatConfig d = new CombatConfig();
        mod.config.featuresColor = d.featuresColor;
        mod.config.featuresByWidth = d.featuresByWidth;
        mod.config.featuresStyle = d.featuresStyle;
        mod.config.featuresBackground = d.featuresBackground;
    }

    @Override
    public void optionsChanged() {
        mod.save();
    }
}
