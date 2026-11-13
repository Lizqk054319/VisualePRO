package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/** TargetHud: po najechaniu na gracza pokazuje pasek serc, liczbę serc i jego zbroję. */
public final class TargetHud {
    private TargetHud() {}

    public static final int W = 150;

    private static LivingEntity target, lastDrawn;
    private static long lastSeen;
    private static float appear = 0f, shown = 0f;
    private static long lastNs = System.nanoTime();

    public static void tick(MinecraftClient mc) {
        if (!HoloConfig.get().targetHud || mc.player == null) { target = null; return; }
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity p && p != mc.player) {
            target = p;
            lastSeen = System.currentTimeMillis();
        }
    }

    /** Po uderzeniu gracza (też gdy już na niego nie patrzymy) HUD zostaje jeszcze chwilę. */
    public static void notifyAttack(LivingEntity e) {
        if (e instanceof PlayerEntity) { target = e; lastSeen = System.currentTimeMillis(); }
    }

    private static LivingEntity current() {
        if (!HoloConfig.get().targetHud || target == null || target.isRemoved()) return null;
        if (System.currentTimeMillis() - lastSeen > 2500) return null;
        return target;
    }

    public static boolean visible() { return current() != null || appear > 0.02f; }

    public static int[] size() { return new int[]{W, HoloConfig.get().targetArmor ? 52 : 34}; }

    private static String fmt(float v) { return String.format(Locale.ROOT, "%.1f", v).replace('.', ','); }

    /** preview = true w edytorze HUD (atrapa gracza). */
    public static void draw(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HoloConfig cfg = HoloConfig.get();
        TextRenderer tr = mc.textRenderer;

        long ns = System.nanoTime();
        float dt = Math.min(0.1f, (ns - lastNs) / 1_000_000_000f);
        lastNs = ns;

        LivingEntity t = preview ? null : current();
        float goal = (preview || t != null) ? 1f : 0f;
        appear += (goal - appear) * Math.min(1f, dt * 10f);
        if (preview) appear = 1f;
        if (appear < 0.03f) return;

        float hp, max;
        String name;
        ItemStack[] armor;
        if (t != null) {
            hp = t.getHealth();
            max = Math.max(1f, t.getMaxHealth());
            name = t.getName().getString();
            armor = new ItemStack[]{t.getEquippedStack(EquipmentSlot.HEAD), t.getEquippedStack(EquipmentSlot.CHEST),
                    t.getEquippedStack(EquipmentSlot.LEGS), t.getEquippedStack(EquipmentSlot.FEET)};
        } else if (preview) {
            hp = 15f; max = 20f; name = "Gracz";
            armor = new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.IRON_LEGGINGS), ItemStack.EMPTY};
        } else {
            // cel zniknął w trakcie animacji znikania - rysujemy ostatnie dane
            if (lastDrawn == null) return;
            hp = Math.max(0f, lastDrawn.getHealth()); max = Math.max(1f, lastDrawn.getMaxHealth());
            name = lastDrawn.getName().getString();
            armor = new ItemStack[]{lastDrawn.getEquippedStack(EquipmentSlot.HEAD), lastDrawn.getEquippedStack(EquipmentSlot.CHEST),
                    lastDrawn.getEquippedStack(EquipmentSlot.LEGS), lastDrawn.getEquippedStack(EquipmentSlot.FEET)};
        }
        LivingEntity drawn = t != null ? t : (preview ? null : lastDrawn);
        if (preview || (t != null && t != lastDrawn)) shown = hp;
        if (t != null) lastDrawn = t;
        shown += (hp - shown) * Math.min(1f, dt * 8f);

        float a = appear;
        int H = size()[1];
        y += (int) ((1f - a) * 6);
        int accent = cfg.guiColor.rgb();
        int accent2 = cfg.guiGradient ? cfg.guiColor2.rgb() : accent;

        Gfx.rrect(ctx, x, y, W, H, 6, Gfx.argb(0x0E141E, 0.84f * a));

        // głowa
        if (drawn instanceof AbstractClientPlayerEntity pl) {
            PlayerSkinDrawer.draw(ctx, pl.getSkinTextures(), x + 6, y + 6, 24);
        } else {
            Gfx.rrect(ctx, x + 6, y + 6, 24, 24, 4, Gfx.argb(0x2A3246, a));
        }

        // nick po lewej, liczba serc po prawej (na górze)
        Gfx.text(ctx, tr, tr.trimToWidth(name, 72), x + 36, y + 8, Gfx.argb(0xFFFFFF, a), false);
        int right = x + W - 8;
        if (cfg.targetHearts) {
            String val = fmt(shown / 2f);
            Gfx.heart(ctx, right - 7, y + 8, Gfx.argb(0xFF4D5E, a));
            Gfx.text(ctx, tr, val, right - 11 - tr.getWidth(val), y + 8, Gfx.argb(0xE8EEF8, a), false);
        } else {
            String val = fmt(shown) + " HP";
            Gfx.text(ctx, tr, val, right - tr.getWidth(val), y + 8, Gfx.argb(0xE8EEF8, a), false);
        }

        // pasek życia
        int bx = x + 36, by = y + 21, bw = W - 44;
        Gfx.rrect(ctx, bx, by, bw, 6, 3, Gfx.argb(0xFFFFFF, 0.14f * a));
        int fw = (int) (bw * MathHelper.clamp(shown / max, 0f, 1f));
        if (fw > 0) {
            int w = Math.max(6, fw);
            for (int i = 0; i < w; i++) {
                int cut = i < 3 ? 3 - i : (i >= w - 3 ? i - (w - 3) + 1 : 0);
                cut = Math.min(cut, 2);
                int col = Gfx.lerpArgb(Gfx.argb(accent, a), Gfx.argb(accent2, a), w <= 1 ? 0f : i / (float) (w - 1));
                ctx.fill(bx + i, by + cut, bx + i + 1, by + 6 - cut, col);
            }
        }

        // zbroja
        if (cfg.targetArmor) {
            for (int i = 0; i < 4; i++) {
                int ix = bx + i * 20, iy = y + 32;
                if (armor[i].isEmpty()) {
                    Gfx.rrect(ctx, ix + 1, iy + 1, 14, 14, 3, Gfx.argb(0xFFFFFF, 0.07f * a));
                } else {
                    ctx.drawItem(armor[i], ix, iy);
                    ctx.drawStackOverlay(tr, armor[i], ix, iy);
                }
            }
        }
    }
}
