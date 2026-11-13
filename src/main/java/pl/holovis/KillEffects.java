package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Kill Effect: duch lecący w górę i/lub piorun, po którym sylwetka wystrzeliwuje w górę. */
public final class KillEffects {
    private KillEffects() {}

    private record Fx(Vec3d pos, float yaw, long start, int kind, float secs, int color, float height, long seed) {}

    private static final List<Fx> LIST = new ArrayList<>();

    /** Wywołanie po zabiciu (sprawdza, czy efekt jest włączony). */
    public static void spawn(Vec3d pos, float yaw) {
        HoloConfig c = HoloConfig.get();
        if (!c.killFx) return;
        add(pos, yaw);
    }

    private static void add(Vec3d pos, float yaw) {
        HoloConfig c = HoloConfig.get();
        long now = Util.getMeasuringTimeMs();
        if (c.killMode == 0 || c.killMode == 2)
            LIST.add(new Fx(pos, yaw, now, 0, Math.max(0.5f, c.killGhostSecs), c.killGhostColor.rgb(), 0f, now));
        if (c.killMode == 1 || c.killMode == 2) {
            LIST.add(new Fx(pos, yaw, now, 1, 1.8f, c.killBoltColor.rgb(), Math.max(1f, c.killBoltHeight), now));
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", "entity.lightning_bolt.thunder"));
            if (ev != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ev, 0.8f, 1f));
        }
    }

    /** Przycisk "Test" w menu: efekt 3 bloki przed graczem, nawet gdy moduł jest wyłączony. */
    public static void spawnTest() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        add(mc.player.getPos().add(flat.multiply(3.0)), mc.player.getYaw() + 180f);
    }

    // ------------------------------------------------------------------
    private static int argb(int rgb, float a) {
        return (((int) (MathHelper.clamp(a, 0f, 1f) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(WorldRenderContext ctx) {
        if (LIST.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        long now = Util.getMeasuringTimeMs();

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        Iterator<Fx> it = LIST.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            float t = (now - f.start()) / (f.secs() * 1000f);
            if (t >= 1f) { it.remove(); continue; }
            double rx = f.pos().x - cam.x, ry = f.pos().y - cam.y, rz = f.pos().z - cam.z;
            if (f.kind() == 0) ghost(ms, vc, f, t, rx, ry, rz);
            else bolt(ms, vc, f, t, rx, ry, rz);
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLightning());
    }

    private static void ghost(MatrixStack ms, VertexConsumer vc, Fx f, float t, double rx, double ry, double rz) {
        float rise = f.secs() * 1.6f;
        float sway = (float) Math.sin(t * 7f) * 0.12f;
        float a = (t < 0.1f ? t / 0.1f : 1f - (t - 0.1f) / 0.9f) * 0.55f;
        ms.push();
        ms.translate((float) rx + sway, (float) ry + t * rise, (float) rz);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f.yaw() + t * 220f));
        ms.scale(0.9f, 0.9f, 0.9f);
        silhouette(ms.peek().getPositionMatrix(), vc, f.color(), a, 0f);
        silhouette(ms.peek().getPositionMatrix(), vc, f.color(), a * 0.3f, 0.06f);
        ms.pop();
    }

    private static void bolt(MatrixStack ms, VertexConsumer vc, Fx f, float t, double rx, double ry, double rz) {
        float el = t * f.secs();

        // sam piorun (pierwsze pół sekundy)
        if (el < 0.5f) {
            Random r = new Random(f.seed() + (long) (el * 30));
            float ba = el < 0.3f ? 0.75f + 0.25f * r.nextFloat() : 1f - (el - 0.3f) / 0.2f;
            ms.push();
            ms.translate((float) rx, (float) ry, (float) rz);
            Matrix4f m = ms.peek().getPositionMatrix();
            int n = 16;
            float topY = 45f, ox = 0, oz = 0;
            float px = 0, py = topY, pz = 0;
            for (int i = 1; i <= n; i++) {
                float k = 1f - i / (float) n;
                ox += (r.nextFloat() - 0.5f) * 2.2f;
                oz += (r.nextFloat() - 0.5f) * 2.2f;
                float nx = i == n ? 0 : ox * k, nz = i == n ? 0 : oz * k;
                float ny = topY * (1f - i / (float) n);
                seg(m, vc, px, py, pz, nx, ny, nz, 0.20f, argb(f.color(), ba * 0.35f));
                seg(m, vc, px, py, pz, nx, ny, nz, 0.06f, argb(0xFFFFFF, ba));
                px = nx; py = ny; pz = nz;
            }
            ms.pop();
        }

        // sylwetka wystrzelona w górę na ustawioną wysokość
        float lt = MathHelper.clamp((el - 0.1f) / 1.5f, 0f, 1f);
        if (lt > 0f && lt < 1f) {
            float ease = 1f - (1f - lt) * (1f - lt);
            float la = (lt < 0.12f ? lt / 0.12f : 1f) * (1f - lt) * 0.75f;
            ms.push();
            ms.translate((float) rx, (float) ry + f.height() * ease, (float) rz);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f.yaw() + lt * 540f));
            ms.scale(0.9f, 0.9f, 0.9f);
            silhouette(ms.peek().getPositionMatrix(), vc, f.color(), la, 0f);
            silhouette(ms.peek().getPositionMatrix(), vc, 0xFFFFFF, la * 0.35f, 0.04f);
            ms.pop();
        }
    }

    private static void seg(Matrix4f m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2,
                            float th, int color) {
        cuboid(m, vc, Math.min(x1, x2) - th, Math.min(y1, y2), Math.min(z1, z2) - th,
                Math.max(x1, x2) + th, Math.max(y1, y2), Math.max(z1, z2) + th, color);
    }

    /** Sylwetka gracza z prostopadłościanów (jednostki: bloki, stopy w y=0). */
    private static void silhouette(Matrix4f m, VertexConsumer vc, int rgb, float alpha, float g) {
        int c = argb(rgb, alpha);
        cuboid(m, vc, -0.25f - g, 0f - g, -0.125f - g, 0.00f + g, 0.75f, 0.125f + g, c);
        cuboid(m, vc, 0.00f - g, 0f - g, -0.125f - g, 0.25f + g, 0.75f, 0.125f + g, c);
        cuboid(m, vc, -0.25f - g, 0.75f, -0.125f - g, 0.25f + g, 1.5f, 0.125f + g, c);
        cuboid(m, vc, -0.5f - g, 0.75f, -0.125f - g, -0.25f, 1.5f, 0.125f + g, c);
        cuboid(m, vc, 0.25f, 0.75f, -0.125f - g, 0.5f + g, 1.5f, 0.125f + g, c);
        cuboid(m, vc, -0.25f - g, 1.5f, -0.25f - g, 0.25f + g, 2.0f + g, 0.25f + g, c);
    }

    private static void v(Matrix4f m, VertexConsumer vc, float x, float y, float z, int c) {
        vc.vertex(m, x, y, z).color(c);
    }

    private static void cuboid(Matrix4f m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
        v(m, vc, x1, y1, z1, c); v(m, vc, x2, y1, z1, c); v(m, vc, x2, y1, z2, c); v(m, vc, x1, y1, z2, c);
        v(m, vc, x1, y2, z1, c); v(m, vc, x2, y2, z1, c); v(m, vc, x2, y2, z2, c); v(m, vc, x1, y2, z2, c);
        v(m, vc, x1, y1, z1, c); v(m, vc, x1, y2, z1, c); v(m, vc, x2, y2, z1, c); v(m, vc, x2, y1, z1, c);
        v(m, vc, x1, y1, z2, c); v(m, vc, x1, y2, z2, c); v(m, vc, x2, y2, z2, c); v(m, vc, x2, y1, z2, c);
        v(m, vc, x1, y1, z1, c); v(m, vc, x1, y2, z1, c); v(m, vc, x1, y2, z2, c); v(m, vc, x1, y1, z2, c);
        v(m, vc, x2, y1, z1, c); v(m, vc, x2, y2, z1, c); v(m, vc, x2, y2, z2, c); v(m, vc, x2, y1, z2, c);
    }
}
