package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class SkyHelper {
    private SkyHelper() {}

    /** Custom Sky działa tylko w Overworld i tylko gdy jest włączony. */
    public static boolean active(World w) {
        return HoloConfig.get().skyEnabled && w != null && w.getRegistryKey() == World.OVERWORLD;
    }

    /** Kolor nieba (0xRRGGBB), opcjonalnie przyciemniany nocą. */
    public static int color(World w, float tickDelta) {
        HoloConfig cfg = HoloConfig.get();
        int rgb = cfg.skyColor.rgb();
        if (!cfg.skyDaylight) return rgb;
        float f = MathHelper.clamp(MathHelper.cos(w.getSkyAngle(tickDelta) * ((float) Math.PI * 2f)) * 2f + 0.5f, 0f, 1f);
        float k = 0.12f + 0.88f * f;
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** Kanał 0=R, 1=G, 2=B jako float 0..1 albo wartość oryginalna, gdy sky wyłączone. */
    public static float channel(int idx, float orig) {
        World w = MinecraftClient.getInstance().world;
        if (!active(w)) return orig;
        int rgb = color(w, 1f);
        return ((rgb >> (16 - 8 * idx)) & 255) / 255f;
    }
}
