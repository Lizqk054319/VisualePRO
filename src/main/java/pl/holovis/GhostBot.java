package pl.holovis;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * /holovis ghost - gracz "TestBot" istniejący tylko u Ciebie (po stronie klienta). Można go bić:
 * uderzenia nie są wysyłane na serwer, obrażenia i odrzut liczymy lokalnie.
 */
public final class GhostBot {
    private GhostBot() {}

    private static OtherClientPlayerEntity bot;
    private static Vec3d vel = Vec3d.ZERO;
    private static double groundY;
    private static int deadTicks;
    private static boolean dying;
    private static int idCounter;

    public static boolean isBot(Entity e) { return e != null && bot != null && e == bot; }

    public static boolean exists() { return bot != null; }

    public static void spawn(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) return;
        remove(mc);

        OtherClientPlayerEntity b = new OtherClientPlayerEntity(w, new GameProfile(UUID.randomUUID(), "TestBot"));
        Vec3d look = p.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        Vec3d pos = p.getPos().add(flat.multiply(3.0));
        float yaw = p.getYaw() + 180f;
        b.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        b.setHeadYaw(yaw);
        b.setBodyYaw(yaw);
        b.setId(2_000_000_000 + (idCounter++ % 1000));
        b.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        b.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        b.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        b.equipStack(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        b.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        b.setHealth(20f);
        w.addEntity(b);

        bot = b;
        vel = Vec3d.ZERO;
        groundY = pos.y;
        deadTicks = 0;
        dying = false;
        p.sendMessage(Text.literal("[HoloVis] TestBot pojawił się (widzisz go tylko Ty). Bij go; /holovis ghost remove usuwa."), false);
    }

    public static void remove(MinecraftClient mc) {
        if (bot != null && mc.world != null) {
            try { mc.world.removeEntity(bot.getId(), Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
        }
        bot = null;
        dying = false;
    }

    /** Uderzenie bota (atak jest anulowany przed wysłaniem na serwer). */
    public static void onHit(MinecraftClient mc, ClientPlayerEntity p) {
        if (bot == null || dying) return;
        p.swingHand(Hand.MAIN_HAND);

        HoloConfig cfg = HoloConfig.get();
        if (cfg.hitSound.enabled) HoloSounds.play(cfg.hitSound);
        TargetHudNotify.hit(bot);

        float hp = Math.max(0f, bot.getHealth() - 2f);
        bot.setHealth(hp);
        bot.hurtTime = 10;
        bot.maxHurtTime = 10;

        Vec3d d = bot.getPos().subtract(p.getPos());
        d = new Vec3d(d.x, 0, d.z);
        d = d.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : d.normalize();
        vel = new Vec3d(d.x * 0.30, 0.30, d.z * 0.30);

        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", "entity.player.hurt"));
        if (ev != null) mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1f, 1f));

        if (hp <= 0f) {
            dying = true;
            if (cfg.killSound.enabled) HoloSounds.play(cfg.killSound);
            KillEffects.spawn(bot.getPos(), bot.getYaw());
        }
    }

    public static void tick(MinecraftClient mc) {
        if (bot == null) return;
        if (mc.world == null || bot.getWorld() != mc.world || bot.isRemoved()) { bot = null; return; }

        if (vel.lengthSquared() > 1e-6 || bot.getY() > groundY + 1e-3) {
            double ny = bot.getY() + vel.y;
            double vy = vel.y - 0.08;
            if (ny <= groundY) { ny = groundY; vy = 0; }
            bot.setPosition(bot.getX() + vel.x, ny, bot.getZ() + vel.z);
            double f = ny <= groundY ? 0.5 : 0.91;
            vel = new Vec3d(vel.x * f, vy, vel.z * f);
            if (ny <= groundY && Math.abs(vel.x) < 0.004 && Math.abs(vel.z) < 0.004) vel = Vec3d.ZERO;
        }

        if (dying) {
            deadTicks++;
            if (deadTicks > 22) remove(mc);
        }
    }

    /** Pomost do TargetHud (żeby GhostBot nie zależał od jego wnętrza). */
    private static final class TargetHudNotify {
        static void hit(net.minecraft.entity.LivingEntity e) { TargetHud.notifyAttack(e); }
    }
}
