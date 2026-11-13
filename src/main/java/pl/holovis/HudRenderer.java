package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/** Elementy HUD: Watermark (FPS), Koordynaty, Spotify. Pozycje (ułamki ekranu) są w configu. */
public final class HudRenderer {
    private HudRenderer() {}

    public enum Element {
        WATERMARK("Watermark / FPS"), COORDS("Koordynaty"), SPOTIFY("Spotify"), TARGET("TargetHUD");
        public final String label;
        Element(String l) { label = l; }
    }

    public static boolean enabled(Element e) {
        HoloConfig c = HoloConfig.get();
        return switch (e) {
            case WATERMARK -> c.watermark;
            case COORDS -> c.coords;
            case SPOTIFY -> c.spotify;
            case TARGET -> c.targetHud;
        };
    }

    public static float[] pos(Element e) {
        HoloConfig c = HoloConfig.get();
        return switch (e) {
            case WATERMARK -> new float[]{c.wmX, c.wmY};
            case COORDS -> new float[]{c.coX, c.coY};
            case SPOTIFY -> new float[]{c.spX, c.spY};
            case TARGET -> new float[]{c.tgX, c.tgY};
        };
    }

    public static void setPos(Element e, float x, float y) {
        HoloConfig c = HoloConfig.get();
        switch (e) {
            case WATERMARK -> { c.wmX = x; c.wmY = y; }
            case COORDS -> { c.coX = x; c.coY = y; }
            case SPOTIFY -> { c.spX = x; c.spY = y; }
            case TARGET -> { c.tgX = x; c.tgY = y; }
        }
    }

    public static void resetPositions() {
        HoloConfig c = HoloConfig.get();
        c.wmX = 0.01f; c.wmY = 0.02f;
        c.coX = 0.01f; c.coY = 0.93f;
        c.spX = 0.01f; c.spY = 0.09f;
        c.tgX = 0.55f; c.tgY = 0.55f;
    }

    private static final int GAP = 7;

    /** Segmenty tekstu: pierwszy kolorowy, kolejne rozdzielone stałym odstępem. */
    private static String[] parts(Element e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        switch (e) {
            case WATERMARK:
                return cfg.showFps ? new String[]{"HoloVis", "|", mc.getCurrentFps() + " FPS"} : new String[]{"HoloVis"};
            case COORDS:
                String xyz = mc.player == null ? "0 0 0"
                        : mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
                return new String[]{"XYZ", xyz};
            case TARGET:
                return new String[]{"TargetHUD"};
            default:
                String t;
                if (!Spotify.running) t = "nie wykryto";
                else if (Spotify.track.isEmpty()) t = "pauza";
                else t = Spotify.playing ? Spotify.track : Spotify.track + " (pauza)";
                return new String[]{"Spotify", mc.textRenderer.trimToWidth(t, 200)};
        }
    }

    private static int totalWidth(TextRenderer tr, String[] p) {
        int w = 8;
        for (int i = 0; i < p.length; i++) w += tr.getWidth(p[i]) + (i > 0 ? GAP : 0);
        return w;
    }

    public static int[] size(Element e) {
        if (e == Element.TARGET) return TargetHud.size();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        return new int[]{totalWidth(tr, parts(e)), 14};
    }

    public static int[] pixelPos(Element e, int sw, int sh) {
        int[] s = size(e);
        float[] f = pos(e);
        return new int[]{
                MathHelper.clamp((int) (f[0] * sw), 0, Math.max(0, sw - s[0])),
                MathHelper.clamp((int) (f[1] * sh), 0, Math.max(0, sh - s[1]))};
    }

    /** preview = true w edytorze HUD. */
    public static void draw(DrawContext ctx, Element e, int x, int y, boolean preview) {
        if (e == Element.TARGET) { TargetHud.draw(ctx, x, y, preview); return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;
        int c1, c2;
        if (cfg.guiGradient) { c1 = cfg.guiColor.rgb(); c2 = cfg.guiColor2.rgb(); }
        else { c1 = cfg.hudColor.rgb(); c2 = c1; }

        String[] p = parts(e);
        int w = totalWidth(tr, p);
        ctx.fill(x, y, x + w, y + 14, 0x88000000);
        ctx.fill(x, y, x + 2, y + 14, 0xFF000000 | c1);
        int cx = x + 5;
        Gfx.gradientText(ctx, tr, p[0], cx, y + 3, c1, c2, 255, false);
        cx += tr.getWidth(p[0]);
        for (int i = 1; i < p.length; i++) {
            cx += GAP;
            int col = p[i].equals("|") ? 0xFF6B7488 : 0xFFE8EEF8;
            ctx.drawText(tr, p[i], cx, y + 3, col, false);
            cx += tr.getWidth(p[i]);
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        for (Element e : Element.values()) {
            if (!enabled(e)) continue;
            if (e == Element.TARGET && !TargetHud.visible()) continue;
            int[] p = pixelPos(e, sw, sh);
            draw(ctx, e, p[0], p[1], false);
        }
    }
}
