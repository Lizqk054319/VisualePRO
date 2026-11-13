package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.DiscordRpc;
import pl.holovis.GhostBot;
import pl.holovis.HoloSounds;
import pl.holovis.KillEffects;
import pl.holovis.Spotify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleToIntFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Menu w stylu kolumn z modułami: panele, przełączniki, rozwijane ustawienia, gradient, własne kolory. */
public class HoloScreen extends Screen {
    private static final int PW = 144, GAP = 8, HEAD = 24, ROW = 20;
    private static final int GRAY = 0x8791A6;
    private static final int[] PRESETS = {0x00F0FF, 0x3D7BFF, 0xB04CFF, 0xFF4FD8, 0xFF3B3B, 0xFF9A1F, 0xFFE81F, 0x3BFF7A, 0xFFFFFF};
    private static final int[] NEUTRALS = {0x000000, 0x0A1020, 0x151515, 0x1E2A3A, 0x3A3A48, 0x808080, 0xC8C8C8, 0xE8EEF8, 0xFFFFFF};

    private final List<Panel> panels = new ArrayList<>();
    private final long openedAt = Util.getMeasuringTimeMs();
    private float fade = 1f;
    private float sc = 1f;
    private int vw, vh;
    private int accent = 0x00F0FF, accent2 = 0xB04CFF, bgc = 0x0A1020, txtC = 0xE8EEF8;
    private float bgA = 0.88f;
    private boolean grad;
    private String filter = "";
    private boolean searchFocus, placed;
    private Panel dragPanel;
    private int dragDx, dragDy;
    private Setting dragging;
    private long closeStart = -1, lastFrame;
    private static String pendingStatus;
    private String status = "";
    private long statusUntil;

    public HoloScreen() {
        super(Text.literal("HoloVis"));
        HoloSounds.refresh();
        build();
        if (pendingStatus != null) { setStatus(pendingStatus); pendingStatus = null; }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { HoloConfig.save(); }

    // =====================================================================
    //  Budowa modułów
    // =====================================================================
    private void build() {
        HoloConfig c = HoloConfig.get();

        Panel vis = new Panel("PVP");
        Module hb = new Module("Hitbox", () -> c.hitboxEnabled, v -> c.hitboxEnabled = v);
        List<String> styles = Arrays.stream(HoloConfig.HitboxStyle.values()).map(e -> e.label).toList();
        List<String> targets = Arrays.stream(HoloConfig.Target.values()).map(e -> e.label).toList();
        hb.s.add(new CycleSet("Styl", () -> styles, () -> c.hitboxStyle.ordinal(),
                i -> c.hitboxStyle = HoloConfig.HitboxStyle.values()[i]));
        hb.s.add(new CycleSet("Cele", () -> targets, () -> c.target.ordinal(),
                i -> c.target = HoloConfig.Target.values()[i]));
        hb.s.add(new SliderSet("Wypełnienie", () -> c.fillAlpha, v -> c.fillAlpha = v, null, v -> (int) (v * 100) + "%"));
        colorRows(hb, c.hitboxColor, PRESETS);
        Module th = new Module("TargetHUD", () -> c.targetHud, v -> c.targetHud = v);
        th.s.add(new ToggleSet("Serca", () -> c.targetHearts, v -> c.targetHearts = v));
        th.s.add(new ToggleSet("Zbroja", () -> c.targetArmor, v -> c.targetArmor = v));
        th.s.add(new InfoSet("Pokazuje się po", "najechaniu na gracza.", "Pozycja: edytor HUD (L)"));

        List<String> killModes = List.of("Duch", "Piorun", "Duch + piorun");
        Module ke = new Module("Kill Effect", () -> c.killFx, v -> c.killFx = v);
        ke.s.add(new CycleSet("Tryb", () -> killModes, () -> c.killMode, i -> c.killMode = i));
        ke.s.add(new SliderSet("Czas ducha", () -> (c.killGhostSecs - 1f) / 7f, v -> c.killGhostSecs = 1f + v * 7f, null,
                v -> String.format("%.1f s", 1f + v * 7f)));
        ke.s.add(new SliderSet("Wysokość pioruna", () -> (c.killBoltHeight - 3f) / 37f, v -> c.killBoltHeight = 3f + v * 37f, null,
                v -> (int) (3f + v * 37f) + " bl."));
        ke.s.add(new ToggleSet("Też moby", () -> c.killMobs, v -> c.killMobs = v));
        ke.s.add(new ButtonRow(new String[]{"Test efektu"}, new Runnable[]{KillEffects::spawnTest}));
        Module kg = new Module("Kolor ducha", null, null);
        colorRows(kg, c.killGhostColor, PRESETS);
        Module kb = new Module("Kolor pioruna", null, null);
        colorRows(kb, c.killBoltColor, PRESETS);

        Module bot = new Module("TestBot", () -> GhostBot.exists(), v -> {
            if (v) GhostBot.spawn(client); else GhostBot.remove(client);
        });
        bot.s.add(new ButtonRow(new String[]{"Stwórz", "Usuń"},
                new Runnable[]{() -> GhostBot.spawn(client), () -> GhostBot.remove(client)}));
        bot.s.add(new InfoSet("Widzisz go tylko Ty.", "Komenda: /holovis ghost", "/holovis ghost remove"));

        vis.mods.add(hb);
        vis.mods.add(th);
        vis.mods.add(ke);
        vis.mods.add(kg);
        vis.mods.add(kb);
        vis.mods.add(bot);
        panels.add(vis);

        Panel snd = new Panel("Dźwięki");
        Module hit = new Module("Hit Sound", () -> c.hitSound.enabled, v -> c.hitSound.enabled = v);
        hit.ss = c.hitSound;
        soundRows(hit, c.hitSound);
        Module kill = new Module("Kill Sound", () -> c.killSound.enabled, v -> c.killSound.enabled = v);
        kill.ss = c.killSound;
        soundRows(kill, c.killSound);
        Module click = new Module("Click Sound", () -> c.clickSound.enabled, v -> c.clickSound.enabled = v);
        click.ss = c.clickSound;
        soundRows(click, c.clickSound);
        snd.mods.add(hit);
        snd.mods.add(kill);
        snd.mods.add(click);
        panels.add(snd);

        Panel world = new Panel("Świat");
        Module sky = new Module("Custom Sky", () -> c.skyEnabled, v -> c.skyEnabled = v);
        colorRows(sky, c.skyColor, PRESETS, () -> c.skyEnabled = true);
        sky.s.add(new ToggleSet("Pora dnia", () -> c.skyDaylight, v -> c.skyDaylight = v));
        sky.s.add(new ToggleSet("Chmury", () -> c.skyClouds, v -> c.skyClouds = v));
        world.mods.add(sky);
        panels.add(world);

        Panel hud = new Panel("HUD");
        Module wm = new Module("Watermark", () -> c.watermark, v -> c.watermark = v);
        wm.s.add(new ToggleSet("Pokaż FPS", () -> c.showFps, v -> c.showFps = v));
        colorRows(wm, c.hudColor, PRESETS);
        Module co = new Module("Koordynaty", () -> c.coords, v -> c.coords = v);
        Module sp = new Module("Spotify", () -> c.spotify, v -> { c.spotify = v; Spotify.sync(); });
        sp.s.add(new LiveInfoSet(() -> !Spotify.running ? "Nie wykryto Spotify" : (Spotify.playing ? Spotify.track : "Pauza")));
        sp.s.add(new ButtonRow(new String[]{"Poprz.", "Pauza", "Dalej"},
                new Runnable[]{Spotify::previous, Spotify::toggle, Spotify::next}));
        sp.s.add(new InfoSet("Czat: najedź na Spotify", "= tekst i przewijanie", "[ ] \\ = skok / pauza"));
        Module ed = new Module("Edytor HUD", null, null);
        ed.s.add(new ButtonRow(new String[]{"Otwórz (L)"}, new Runnable[]{() -> client.setScreen(new HudEditScreen(this))}));
        ed.s.add(new InfoSet("Przeciągnij FPS, XYZ,", "Spotify i TargetHUD", "w dowolne miejsce"));
        hud.mods.add(wm);
        hud.mods.add(co);
        hud.mods.add(sp);
        hud.mods.add(ed);
        panels.add(hud);

        Panel cl = new Panel("Klient");
        Module look = new Module("Akcent GUI", null, null);
        colorRows(look, c.guiColor, PRESETS);
        look.s.add(new ToggleSet("Efekt hologramu", () -> c.hologramFx, v -> c.hologramFx = v));
        Module gr = new Module("Gradient", () -> c.guiGradient, v -> c.guiGradient = v);
        colorRows(gr, c.guiColor2, PRESETS);
        gr.s.add(new ToggleSet("Gradient napisu", () -> c.guiGradientText, v -> c.guiGradientText = v));
        Module bg = new Module("Tło GUI", null, null);
        colorRows(bg, c.guiBg, NEUTRALS);
        bg.s.add(new SliderSet("Przezroczystość", () -> (c.guiBgAlpha - 0.2f) / 0.8f, v -> c.guiBgAlpha = 0.2f + v * 0.8f,
                null, v -> (int) ((0.2f + v * 0.8f) * 100) + "%"));
        Module tx = new Module("Kolor napisu", null, null);
        colorRows(tx, c.textColor, NEUTRALS);
        Module dc = new Module("Discord", () -> c.discordRpc, v -> { c.discordRpc = v; DiscordRpc.sync(); });
        dc.s.add(new LiveInfoSet(() -> DiscordRpc.status, "Status"));
        dc.s.add(new ButtonRow(new String[]{"Wklej ID ze schowka"}, new Runnable[]{this::pasteDiscordId}));
        dc.s.add(new InfoSet("ID aplikacji \"HoloVisuals\"", "z discord.com/developers", "(Application ID)"));

        Module cfgm = new Module("Config", null, null);
        cfgm.s.add(new ButtonRow(new String[]{"Kopiuj kod"}, new Runnable[]{this::copyConfig}));
        cfgm.s.add(new ButtonRow(new String[]{"Load config"}, new Runnable[]{this::loadConfig}));
        cfgm.s.add(new InfoSet("Kopiuj kod = Twoje", "ustawienia w schowku.", "Load config = wczytaj", "kod ze schowka."));

        cl.mods.add(look);
        cl.mods.add(gr);
        cl.mods.add(bg);
        cl.mods.add(tx);
        cl.mods.add(dc);
        cl.mods.add(cfgm);
        panels.add(cl);
    }

    private void colorRows(Module m, HoloConfig.ColorSetting cs, int[] pal) {
        colorRows(m, cs, pal, null);
    }

    private void colorRows(Module m, HoloConfig.ColorSetting cs, int[] pal, Runnable onChange) {
        Runnable ch = onChange == null ? () -> {} : onChange;
        m.s.add(new SwatchSet(cs, pal, ch));
        m.s.add(new SliderSet("Odcień", () -> cs.h, v -> { cs.h = v; cs.rainbow = false; ch.run(); },
                f -> HoloConfig.hsv((float) f, 1f, 1f), v -> (int) (v * 360) + "°"));
        m.s.add(new SliderSet("Nasycenie", () -> cs.s, v -> { cs.s = v; ch.run(); },
                f -> HoloConfig.hsv(cs.h, (float) f, cs.b), v -> (int) (v * 100) + "%"));
        m.s.add(new SliderSet("Jasność", () -> cs.b, v -> { cs.b = v; ch.run(); },
                f -> HoloConfig.hsv(cs.h, cs.s, (float) f), v -> (int) (v * 100) + "%"));
        m.s.add(new ToggleSet("Tęcza", () -> cs.rainbow, v -> { cs.rainbow = v; ch.run(); }));
    }

    private void soundRows(Module m, HoloConfig.SoundSetting ss) {
        m.s.add(new DropdownSet("Dźwięk",
                () -> HoloSounds.options().stream().map(HoloSounds::label).toList(),
                () -> HoloSounds.options().indexOf(ss.sound),
                i -> { ss.sound = HoloSounds.options().get(i); HoloSounds.play(ss); }));
        m.s.add(new SliderSet("Głośność", () -> ss.volume, v -> ss.volume = v, null, v -> (int) (v * 100) + "%"));
        m.s.add(new SliderSet("Pitch", () -> (ss.pitch - 0.5f) / 1.5f, v -> ss.pitch = 0.5f + v * 1.5f, null,
                v -> String.format("%.2f", 0.5f + v * 1.5f)));
        m.s.add(new ButtonRow(new String[]{"Test", "Folder", "Odśw."},
                new Runnable[]{() -> HoloSounds.play(ss), HoloSounds::openFolder, HoloSounds::refresh}));
        m.s.add(new InfoSet("Przeciągnij plik", ".wav na okno gry,", "aby dodać dźwięk"));
    }

    // =====================================================================
    //  Rysowanie
    // =====================================================================
    private int argb(int rgb, float a) {
        return (((int) (MathHelper.clamp(a * fade, 0f, 1f) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) { return Gfx.mix(a, b, t); }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** Zaokrąglony prostokąt z gradientem (pionowym albo poziomym) c1 -> c2 (ARGB). */
    private void box(DrawContext c, int x, int y, int w, int h, int r, int c1, int c2,
                     boolean horiz, boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (horiz) {
            for (int i = 0; i < w; i++) {
                int col = Gfx.lerpArgb(c1, c2, w <= 1 ? 0f : i / (float) (w - 1));
                int yt = 0, yb = 0;
                if (r > 0) {
                    int e = i < r ? i : (i >= w - r ? w - 1 - i : -1);
                    if (e >= 0) { int in = inset(r, e); if (top) yt = in; if (bottom) yb = in; }
                }
                c.fill(x + i, y + yt, x + i + 1, y + h - yb, col);
            }
        } else {
            for (int j = 0; j < h; j++) {
                int col = Gfx.lerpArgb(c1, c2, h <= 1 ? 0f : j / (float) (h - 1));
                int in = 0;
                if (r > 0) {
                    if (top && j < r) in = inset(r, j);
                    else if (bottom && j >= h - r) in = inset(r, h - 1 - j);
                }
                c.fill(x + in, y + j, x + w - in, y + j + 1, col);
            }
        }
    }

    private void rrect(DrawContext c, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
        box(c, x, y, w, h, r, color, color, false, top, bottom);
    }

    private void txt(DrawContext c, String s, int x, int y, int rgb, float a) {
        Gfx.text(c, textRenderer, s, x, y, argb(rgb, a), false);
    }

    private void toggle(DrawContext c, int x, int y, int w, int h, boolean on) {
        if (on) box(c, x, y, w, h, h / 2, argb(accent, 1f), argb(grad ? accent2 : accent, 1f), true, true, true);
        else rrect(c, x, y, w, h, h / 2, argb(0x3A4256, 1f), true, true);
        int k = h - 4;
        int kx = on ? x + w - k - 2 : x + 2;
        rrect(c, kx, y + 2, k, k, k / 2, argb(0xFFFFFF, 1f), true, true);
    }

    private boolean matches(Module m) {
        return filter.isEmpty() || m.name.toLowerCase().contains(filter.toLowerCase());
    }

    private void layout(Panel p, float dt) {
        int cy = p.y + HEAD + 3;
        for (Module m : p.mods) {
            m.visible = matches(m);
            if (!m.visible) { for (Setting st : m.s) st.visible = false; continue; }
            m.rowY = cy;
            cy += ROW;
            float target = m.open ? 1f : 0f;
            m.anim += (target - m.anim) * Math.min(1f, dt * 12f);
            if (Math.abs(target - m.anim) < 0.01f) m.anim = target;
            int full = 4;
            for (Setting st : m.s) full += st.h() + 2;
            int ah = m.anim <= 0f ? 0 : (int) (full * m.anim);
            int sy = cy + 2;
            for (Setting st : m.s) {
                st.x = p.x + 8; st.y = sy; st.w = PW - 16;
                st.visible = ah > 0 && st.y + st.h() <= cy + ah - 2;
                sy += st.h() + 2;
            }
            cy += ah;
            m.endY = cy;
        }
        p.h = cy + 3 - p.y;
    }

    @Override
    protected void init() {
        int total = panels.size() * PW + (panels.size() - 1) * GAP;
        sc = MathHelper.clamp((width - 16f) / total, 0.5f, 1f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
        if (placed) return;
        placed = true;
        int x = Math.max(8, (vw - total) / 2);
        int ord = 0;
        for (Panel p : panels) { p.x = x; p.y = 52; p.order = ord++; x += PW + GAP; }
    }

    @Override
    public void close() {
        if (closeStart < 0) closeStart = Util.getMeasuringTimeMs();
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        long now = Util.getMeasuringTimeMs();
        HoloConfig cfg = HoloConfig.get();
        accent = cfg.guiColor.rgb();
        accent2 = cfg.guiColor2.rgb();
        bgc = cfg.guiBg.rgb();
        txtC = cfg.textColor.rgb();
        bgA = cfg.guiBgAlpha;
        grad = cfg.guiGradient;

        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float closeF = 1f;
        if (closeStart >= 0) {
            float ct = (now - closeStart) / 180f;
            if (ct >= 1f) { if (client != null) client.setScreen(null); return; }
            closeF = 1f - ct;
        }
        float flick = 1f;
        if (cfg.hologramFx) {
            flick = 0.96f + 0.04f * (float) Math.sin(now / 130.0);
            if ((now / 90) % 53 == 0) flick *= 0.85f;
        }
        float intro = Math.min(1f, (now - openedAt) / 260f);
        float introE = 1f - (1f - intro) * (1f - intro) * (1f - intro);

        c.fill(0, 0, width, height, ((int) (0x66 * intro * closeF)) << 24);

        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int mx = (int) (rawMx / sc), my = (int) (rawMy / sc);

        fade = flick * introE * closeF;

        // tytuł
        c.getMatrices().push();
        c.getMatrices().scale(2f, 2f, 1f);
        int ta = (int) (MathHelper.clamp(fade, 0f, 1f) * 255);
        if (cfg.guiGradientText && grad) Gfx.gradientText(c, textRenderer, "holovis", 12, 8, accent, accent2, ta, false);
        else Gfx.text(c, textRenderer, "holovis", 12, 8, (ta << 24) | txtC, false);
        c.getMatrices().pop();
        txt(c, "VISUALS", 116, 14, accent, 0.9f);

        // wyszukiwarka
        int sx = vw - 170;
        rrect(c, sx, 18, 150, 18, 5, argb(bgc, 0.85f), true, true);
        c.drawBorder(sx, 18, 150, 18, argb(accent, searchFocus ? 0.8f : 0.25f));
        String shown = filter.isEmpty() && !searchFocus ? "Szukaj..." : filter + (searchFocus && (now / 500) % 2 == 0 ? "_" : "");
        txt(c, shown, sx + 8, 24, filter.isEmpty() ? GRAY : txtC, 1f);

        if (cfg.hologramFx) {
            int sy = (int) ((now / 10) % (vh + 40)) - 20;
            c.fillGradient(0, sy - 14, vw, sy, argb(accent, 0f), argb(accent, 0.07f));
            c.fill(0, sy, vw, sy + 1, argb(accent, 0.12f));
        }

        // panele: wjeżdżają kolejno (stagger), znikają przy zamykaniu
        for (Panel p : panels) {
            float pt = MathHelper.clamp((now - openedAt - p.order * 70L) / 320f, 0f, 1f);
            float e = 1f - (1f - pt) * (1f - pt) * (1f - pt);
            fade = flick * e * closeF;
            c.getMatrices().push();
            c.getMatrices().translate(0f, (1f - e) * -20f + (1f - closeF) * -12f, 0f);
            renderPanel(c, p, mx, my, cfg.hologramFx, dt);
            c.getMatrices().pop();
        }

        fade = flick * closeF;
        if (now < statusUntil) {
            int w = textRenderer.getWidth(status) + 20;
            int x = (vw - w) / 2, y = vh - 34;
            rrect(c, x, y, w, 20, 6, argb(bgc, 0.92f), true, true);
            txt(c, status, x + 10, y + 6, txtC, 1f);
        }
        txt(c, "Prawy Shift / ESC - zamknij  |  L - edytor HUD  |  PPM - ustawienia", 10, vh - 14, GRAY, 0.8f);

        c.getMatrices().pop();
    }

    private void renderPanel(DrawContext c, Panel p, int mx, int my, boolean fx, float dt) {
        layout(p, dt);
        int bodyTop = argb(grad ? mix(bgc, accent, 0.30f) : bgc, bgA);
        int bodyBot = argb(grad ? mix(bgc, accent2, 0.30f) : bgc, bgA);
        box(c, p.x, p.y + HEAD, PW, p.h - HEAD, 6, bodyTop, bodyBot, false, false, true);
        if (grad) {
            box(c, p.x, p.y, PW, HEAD, 6, argb(mix(bgc, accent, 0.8f), 0.95f), argb(mix(bgc, accent2, 0.8f), 0.95f), true, true, false);
        } else {
            rrect(c, p.x, p.y, PW, HEAD, 6, argb(mix(bgc, accent, 0.24f), 0.94f), true, false);
        }
        if (fx) {
            for (int yy = p.y + HEAD; yy < p.y + p.h - 3; yy += 3)
                c.fill(p.x + 3, yy, p.x + PW - 3, yy + 1, argb(accent, 0.04f));
        }

        txt(c, p.title, p.x + 10, p.y + 8, txtC, 1f);
        int vis = 0;
        for (Module m : p.mods) if (m.visible) vis++;
        String cnt = String.valueOf(vis);
        txt(c, cnt, p.x + PW - 10 - textRenderer.getWidth(cnt), p.y + 8, grad ? txtC : GRAY, 0.8f);

        for (Module m : p.mods) {
            if (!m.visible) continue;
            boolean hov = mx >= p.x && mx < p.x + PW && my >= m.rowY && my < m.rowY + ROW;
            boolean hasToggle = m.on != null;
            boolean on = !hasToggle || m.on.getAsBoolean();
            if (hov) c.fill(p.x + 4, m.rowY, p.x + PW - 4, m.rowY + ROW, argb(accent, 0.12f));
            if (hasToggle && on) {
                if (grad) c.fillGradient(p.x + 4, m.rowY + 4, p.x + 6, m.rowY + ROW - 4, argb(accent, 1f), argb(accent2, 1f));
                else c.fill(p.x + 4, m.rowY + 4, p.x + 6, m.rowY + ROW - 4, argb(accent, 1f));
            }
            txt(c, m.name, p.x + 12, m.rowY + 6, on ? txtC : GRAY, 1f);
            if (!m.s.isEmpty()) txt(c, m.open ? "v" : ">", p.x + PW - 46, m.rowY + 6, GRAY, 1f);
            if (hasToggle) toggle(c, p.x + PW - 32, m.rowY + 5, 22, 10, on);

            if (m.anim > 0.01f) {
                c.fill(p.x + 4, m.rowY + ROW, p.x + PW - 4, m.endY, argb(0x000000, 0.28f));
                for (Setting st : m.s) if (st.visible) st.draw(c, mx, my);
            }
        }
    }

    // =====================================================================
    //  Input
    // =====================================================================
    private void toggleOpen(Panel p, Module m) {
        boolean to = !m.open;
        for (Module o : p.mods) o.open = false;
        m.open = to;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx /= sc;
        my /= sc;
        int sx = vw - 170;
        searchFocus = mx >= sx && mx < sx + 150 && my >= 18 && my < 36;
        if (searchFocus) return true;

        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (mx < p.x || mx >= p.x + PW || my < p.y || my >= p.y + p.h) continue;
            if (my < p.y + HEAD) {
                if (button == 0) {
                    dragPanel = p;
                    dragDx = (int) mx - p.x;
                    dragDy = (int) my - p.y;
                    panels.remove(i);
                    panels.add(p);
                }
                return true;
            }
            for (Module m : p.mods) {
                if (!m.visible) continue;
                if (my >= m.rowY && my < m.rowY + ROW) { rowClick(p, m, mx, button); return true; }
                if (m.open) {
                    for (Setting s : m.s) {
                        if (s.visible && s.hit(mx, my)) {
                            s.click(mx, my, button);
                            if (s.draggable()) dragging = s;
                            return true;
                        }
                    }
                }
            }
            return true;
        }
        return true;
    }

    private void rowClick(Panel p, Module m, double mx, int button) {
        boolean hasToggle = m.on != null, hasSet = !m.s.isEmpty();
        if (button == 1) { if (hasSet) toggleOpen(p, m); return; }
        if (button != 0) return;
        int rx = (int) mx - p.x;
        if (hasToggle && rx >= PW - 36) m.setOn.accept(!m.on.getAsBoolean());
        else if (hasSet && (rx >= PW - 54 || !hasToggle)) toggleOpen(p, m);
        else if (hasToggle) m.setOn.accept(!m.on.getAsBoolean());
        HoloConfig.save();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx /= sc;
        my /= sc;
        if (dragPanel != null) {
            dragPanel.x = MathHelper.clamp((int) mx - dragDx, 0, Math.max(0, vw - PW));
            dragPanel.y = MathHelper.clamp((int) my - dragDy, 0, Math.max(0, vh - HEAD));
            return true;
        }
        if (dragging != null) { dragging.drag(mx); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        mx /= sc;
        my /= sc;
        for (int i = panels.size() - 1; i >= 0; i--) {
            for (Module m : panels.get(i).mods) {
                if (!m.visible || !m.open) continue;
                for (Setting st : m.s) if (st.visible && st.onScroll(mx, my, vAmt)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null || dragPanel != null) HoloConfig.save();
        dragging = null;
        dragPanel = null;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus && chr >= 32 && filter.length() < 20) { filter += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !filter.isEmpty()) { filter = filter.substring(0, filter.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { searchFocus = false; return true; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        if (key == GLFW.GLFW_KEY_L) { client.setScreen(new HudEditScreen(this)); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Przeciągnięcie plików .wav na okno gry = dodanie własnego dźwięku. */
    public void onFilesDropped(List<Path> paths) {
        String last = null;
        int ok = 0;
        for (Path p : paths) {
            String k = HoloSounds.importFile(p);
            if (k != null) { last = k; ok++; }
        }
        if (ok == 0) { setStatus("Obsługiwane są tylko pliki .wav"); return; }
        Module target = null, fallback = null;
        for (Panel p : panels) for (Module m : p.mods) {
            if (m.ss == null) continue;
            if (fallback == null) fallback = m;
            if (m.open) target = m;
        }
        if (target == null) target = fallback;
        if (target != null) { target.ss.sound = last; HoloConfig.save(); }
        setStatus("Dodano dźwięk: " + HoloSounds.label(last));
    }

    private void copyConfig() {
        String code = HoloConfig.exportCode();
        if (code.isEmpty()) { setStatus("Nie udało się utworzyć kodu"); return; }
        client.keyboard.setClipboard(code);
        setStatus("Skopiowano kod configu do schowka");
    }

    private void loadConfig() {
        String err = HoloConfig.importCode(client.keyboard.getClipboard());
        if (err != null) { setStatus(err); return; }
        Spotify.sync();
        DiscordRpc.sync();
        HoloSounds.refresh();
        pendingStatus = "Wczytano config";
        client.setScreen(new HoloScreen());   // odbuduj menu na nowych ustawieniach
    }

    private void pasteDiscordId() {
        String id = client.keyboard.getClipboard();
        id = id == null ? "" : id.trim();
        if (!id.matches("\\d{15,25}")) { setStatus("Skopiuj najpierw Application ID (same cyfry)"); return; }
        HoloConfig.get().discordAppId = id;
        HoloConfig.save();
        DiscordRpc.sync();
        setStatus("Zapisano ID aplikacji Discord");
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = Util.getMeasuringTimeMs() + 3500;
    }

    // =====================================================================
    //  Struktury
    // =====================================================================
    private class Panel {
        final String title;
        final List<Module> mods = new ArrayList<>();
        int x, y, h, order;
        Panel(String t) { title = t; }
    }

    private class Module {
        final String name;
        final BooleanSupplier on;
        final Consumer<Boolean> setOn;
        final List<Setting> s = new ArrayList<>();
        HoloConfig.SoundSetting ss;
        boolean open, visible = true;
        float anim;
        int rowY, endY;
        Module(String n, BooleanSupplier on, Consumer<Boolean> setOn) { name = n; this.on = on; this.setOn = setOn; }
    }

    private abstract class Setting {
        int x, y, w;
        boolean visible = true;
        boolean onScroll(double mx, double my, double amount) { return false; }
        abstract int h();
        abstract void draw(DrawContext c, int mx, int my);
        boolean click(double mx, double my, int button) { return false; }
        void drag(double mx) {}
        boolean draggable() { return false; }
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h(); }
        boolean hover(int mx, int my) { return hit(mx, my); }
    }

    private class ToggleSet extends Setting {
        final String label; final BooleanSupplier get; final Consumer<Boolean> set;
        ToggleSet(String l, BooleanSupplier g, Consumer<Boolean> s) { label = l; get = g; set = s; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            if (hover(mx, my)) c.fill(x - 2, y, x + w + 2, y + 16, argb(accent, 0.10f));
            txt(c, label, x + 2, y + 4, txtC, 0.95f);
            toggle(c, x + w - 20, y + 4, 18, 8, get.getAsBoolean());
        }
        boolean click(double mx, double my, int b) { set.accept(!get.getAsBoolean()); HoloConfig.save(); return true; }
    }

    private int accentLight() { return mix(accent, 0xFFFFFF, 0.55f); }

    private class CycleSet extends Setting {
        final String label; final Supplier<List<String>> opts; final IntSupplier get; final IntConsumer set;
        CycleSet(String l, Supplier<List<String>> o, IntSupplier g, IntConsumer s) { label = l; opts = o; get = g; set = s; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            if (hover(mx, my)) c.fill(x - 2, y, x + w + 2, y + 16, argb(accent, 0.10f));
            txt(c, label, x + 2, y + 4, GRAY, 1f);
            List<String> o = opts.get();
            int i = get.getAsInt();
            String v = i >= 0 && i < o.size() ? o.get(i) : "?";
            int maxW = w - textRenderer.getWidth(label) - 24;
            v = textRenderer.trimToWidth(v, Math.max(20, maxW));
            String s = "< " + v + " >";
            txt(c, s, x + w - textRenderer.getWidth(s) - 2, y + 4, accentLight(), 1f);
        }
        boolean click(double mx, double my, int b) {
            int n = opts.get().size();
            if (n == 0) return true;
            int i = get.getAsInt();
            int next = b == 1 ? (i <= 0 ? n - 1 : i - 1) : (i + 1) % n;
            set.accept(next);
            HoloConfig.save();
            return true;
        }
    }

    private class SliderSet extends Setting {
        final String label; final Supplier<Float> get; final Consumer<Float> set;
        final DoubleToIntFunction track; final Function<Float, String> fmt;
        SliderSet(String l, Supplier<Float> g, Consumer<Float> s, DoubleToIntFunction t, Function<Float, String> f) {
            label = l; get = g; set = s; track = t; fmt = f;
        }
        int h() { return 22; }
        boolean draggable() { return true; }
        void draw(DrawContext c, int mx, int my) {
            float v = MathHelper.clamp(get.get(), 0f, 1f);
            txt(c, label, x + 2, y + 1, GRAY, 1f);
            String s = fmt.apply(v);
            txt(c, s, x + w - textRenderer.getWidth(s) - 2, y + 1, txtC, 1f);
            int ty = y + 13, tw = w - 4, tx = x + 2;
            if (track != null) {
                for (int i = 0; i < tw; i += 2)
                    c.fill(tx + i, ty, tx + Math.min(i + 2, tw), ty + 4, argb(track.applyAsInt(i / (float) tw), 1f));
            } else {
                rrect(c, tx, ty, tw, 4, 2, argb(0x2A3246, 1f), true, true);
                int fw = (int) (tw * v);
                if (fw > 0) rrect(c, tx, ty, Math.max(4, fw), 4, 2, argb(accent, 1f), true, true);
            }
            int kx = tx + (int) (v * (tw - 4));
            rrect(c, kx, ty - 2, 4, 8, 2, argb(0xFFFFFF, 1f), true, true);
        }
        boolean click(double mx, double my, int b) { drag(mx); return true; }
        void drag(double mx) { set.accept(MathHelper.clamp((float) ((mx - (x + 2)) / (w - 4)), 0f, 1f)); }
    }

    private class SwatchSet extends Setting {
        final HoloConfig.ColorSetting cs; final int[] pal; final Runnable ch;
        SwatchSet(HoloConfig.ColorSetting cs, int[] pal, Runnable ch) { this.cs = cs; this.pal = pal; this.ch = ch; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            for (int i = 0; i < pal.length; i++) {
                int bx = x + 2 + i * 14;
                boolean hov = mx >= bx && mx < bx + 12 && my >= y + 1 && my < y + 13;
                rrect(c, bx, y + 1, 12, 12, 3, argb(pal[i], 1f), true, true);
                c.drawBorder(bx, y + 1, 12, 12, argb(hov ? 0xFFFFFF : 0x808080, hov ? 0.95f : 0.35f));
            }
        }
        boolean click(double mx, double my, int b) {
            int i = (int) ((mx - (x + 2)) / 14);
            if (i >= 0 && i < pal.length && (mx - (x + 2)) % 14 < 12) {
                cs.set(pal[i]);
                cs.rainbow = false;
                ch.run();
                HoloConfig.save();
            }
            return true;
        }
    }

    private class ButtonRow extends Setting {
        final String[] labels; final Runnable[] actions;
        ButtonRow(String[] l, Runnable[] a) { labels = l; actions = a; }
        int h() { return 18; }
        int bw() { return (w - 4 * (labels.length - 1)) / labels.length; }
        void draw(DrawContext c, int mx, int my) {
            int bw = bw();
            for (int i = 0; i < labels.length; i++) {
                int bx = x + i * (bw + 4);
                boolean hov = mx >= bx && mx < bx + bw && my >= y && my < y + 16;
                rrect(c, bx, y, bw, 16, 4, argb(accent, hov ? 0.40f : 0.22f), true, true);
                int tw = textRenderer.getWidth(labels[i]);
                txt(c, labels[i], bx + (bw - tw) / 2, y + 4, txtC, 1f);
            }
        }
        boolean click(double mx, double my, int b) {
            int bw = bw();
            int i = (int) ((mx - x) / (bw + 4));
            if (i >= 0 && i < actions.length && (mx - x) - i * (bw + 4) < bw) actions[i].run();
            return true;
        }
    }

    private class DropdownSet extends Setting {
        static final int IH = 13, MAXV = 6;
        final String label; final Supplier<List<String>> opts; final IntSupplier get; final IntConsumer set;
        boolean open;
        int scroll;
        DropdownSet(String l, Supplier<List<String>> o, IntSupplier g, IntConsumer s) { label = l; opts = o; get = g; set = s; }
        int rows() { return Math.min(MAXV, opts.get().size()); }
        int h() { return 16 + (open ? rows() * IH + 4 : 0); }
        void draw(DrawContext c, int mx, int my) {
            boolean hovHead = mx >= x && mx < x + w && my >= y && my < y + 16;
            if (hovHead) c.fill(x - 2, y, x + w + 2, y + 16, argb(accent, 0.10f));
            txt(c, label, x + 2, y + 4, GRAY, 1f);
            List<String> o = opts.get();
            int sel = get.getAsInt();
            String cur = sel >= 0 && sel < o.size() ? o.get(sel) : "?";
            String arrow = open ? "^" : "v";
            int maxW = w - textRenderer.getWidth(label) - 22;
            cur = textRenderer.trimToWidth(cur, Math.max(20, maxW));
            String right = cur + " " + arrow;
            txt(c, right, x + w - textRenderer.getWidth(right) - 2, y + 4, accentLight(), 1f);
            if (!open) return;
            int n = o.size(), r = rows();
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, n - r));
            int top = y + 17;
            rrect(c, x - 2, top - 1, w + 4, r * IH + 4, 3, argb(0x000000, 0.45f), true, true);
            for (int i = 0; i < r; i++) {
                int idx = scroll + i;
                int iy = top + i * IH;
                boolean hov = mx >= x && mx < x + w - 6 && my >= iy && my < iy + IH;
                if (hov) rrect(c, x, iy, w - 6, IH, 3, argb(accent, 0.28f), true, true);
                String t = textRenderer.trimToWidth(o.get(idx), w - 18);
                txt(c, t, x + 5, iy + 3, idx == sel ? accentLight() : txtC, 1f);
            }
            if (n > r) {
                int trackH = r * IH;
                int knobH = Math.max(8, trackH * r / n);
                int knobY = top + (trackH - knobH) * scroll / Math.max(1, n - r);
                c.fill(x + w - 3, top, x + w - 1, top + trackH, argb(0xFFFFFF, 0.08f));
                c.fill(x + w - 3, knobY, x + w - 1, knobY + knobH, argb(accent, 0.8f));
            }
        }
        boolean click(double mx, double my, int b) {
            if (my < y + 16) { open = !open; return true; }
            if (!open) return true;
            List<String> o = opts.get();
            int idx = scroll + (int) ((my - (y + 17)) / IH);
            if (my >= y + 17 && idx >= 0 && idx < o.size() && idx < scroll + rows()) {
                set.accept(idx);
                open = false;
                HoloConfig.save();
            }
            return true;
        }
        boolean onScroll(double mx, double my, double amount) {
            if (!open || !hit(mx, my)) return false;
            scroll -= (int) Math.signum(amount);
            return true;
        }
    }

    private class LiveInfoSet extends Setting {
        final Supplier<String> text; final String prefix;
        LiveInfoSet(Supplier<String> t) { this(t, "Teraz"); }
        LiveInfoSet(Supplier<String> t, String prefix) { text = t; this.prefix = prefix; }
        int h() { return 12; }
        void draw(DrawContext c, int mx, int my) {
            String t = textRenderer.trimToWidth(prefix + ": " + text.get(), w - 4);
            txt(c, t, x + 2, y + 2, accentLight(), 1f);
        }
    }

    private class InfoSet extends Setting {
        final String[] lines;
        InfoSet(String... l) { lines = l; }
        int h() { return lines.length * 10 + 2; }
        void draw(DrawContext c, int mx, int my) {
            for (int i = 0; i < lines.length; i++) txt(c, lines[i], x + 2, y + 2 + i * 10, GRAY, 0.85f);
        }
    }
}
