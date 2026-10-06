package ru.mobecome;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.BreezeWindCharge;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** ЛКМ (primary) и ПКМ (secondary) способности мобов. */
final class Abilities {
    private final MobEcome pl;
    private final Random rnd = new Random();

    Abilities(MobEcome pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------------ names

    String secondaryName(EntityType t) {
        return switch (t.name()) {
            case "WARDEN" -> "звуковой луч";
            case "SKELETON", "STRAY", "BOGGED", "PARCHED", "PILLAGER" -> "залп стрел";
            case "BLAZE" -> "огненная очередь";
            case "GHAST" -> "большой файрбол";
            case "WITHER" -> "синий череп";
            case "ENDER_DRAGON" -> "огненный шар дракона";
            case "ENDERMAN" -> "телепорт";
            case "CREEPER" -> "взрыв";
            case "EVOKER" -> "круг клыков";
            case "ELDER_GUARDIAN" -> "проклятие";
            case "PUFFERFISH" -> "ядовитый всплеск";
            case "SQUID", "GLOW_SQUID" -> "чернильный рывок";
            case "IRON_GOLEM" -> "подбросить цель";
            case "RAVAGER" -> "рёв";
            case "GOAT" -> "таран";
            case "CAMEL", "CAMEL_HUSK" -> "рывок";
            case "PHANTOM", "VEX" -> "пике";
            case "SPIDER", "CAVE_SPIDER", "SLIME", "MAGMA_CUBE", "RABBIT", "FROG", "FOX" -> "прыжок";
            case "BREEZE" -> "взлёт вихря";
            case "WITCH" -> "зелье скорости";
            default -> "нет";
        };
    }

    // ------------------------------------------------------------------ LMB

    void primary(Player p, Session s) {
        if (p.getGameMode() == GameMode.SPECTATOR || s.disguise == null) return;
        String n = s.type.name();
        Vector d = look(p);
        switch (n) {
            case "SKELETON", "STRAY", "BOGGED", "PARCHED", "PILLAGER" -> {
                if (!ready(s, true, 900)) return;
                s.disguise.swingMainHand();
                arrow(p, n, d, 0);
            }
            case "BLAZE" -> {
                if (!ready(s, true, 500)) return;
                fire(p, d, SmallFireball.class);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
            }
            case "GHAST" -> {
                if (!ready(s, true, 1500)) return;
                LargeFireball f = fire(p, d, LargeFireball.class);
                f.setYield(1.5f);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 2f, 1f);
            }
            case "BREEZE" -> {
                if (!ready(s, true, 1200)) return;
                p.launchProjectile(BreezeWindCharge.class, d.clone().multiply(1.5));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
            }
            case "WITHER" -> {
                if (!ready(s, true, 700)) return;
                fire(p, d, WitherSkull.class);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);
            }
            case "SHULKER" -> {
                if (!ready(s, true, 1500)) return;
                ShulkerBullet b = p.launchProjectile(ShulkerBullet.class, d.clone());
                Location eye = p.getEyeLocation();
                RayTraceResult r = p.getWorld().rayTraceEntities(eye, d, 30, 0.8, filter(p));
                if (r != null && r.getHitEntity() instanceof LivingEntity t) b.setTarget(t);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1f);
            }
            case "SNOW_GOLEM" -> {
                if (!ready(s, true, 400)) return;
                p.launchProjectile(Snowball.class, d.clone().multiply(1.5));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 1f);
            }
            case "LLAMA", "TRADER_LLAMA" -> {
                if (!ready(s, true, 800)) return;
                p.launchProjectile(LlamaSpit.class, d.clone().multiply(1.5));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1f, 1f);
            }
            case "WITCH" -> {
                if (!ready(s, true, 1400)) return;
                PotionType[] types = {PotionType.POISON, PotionType.HARMING, PotionType.SLOWNESS, PotionType.WEAKNESS};
                ItemStack it = new ItemStack(Material.SPLASH_POTION);
                PotionMeta m = (PotionMeta) it.getItemMeta();
                m.setBasePotionType(types[rnd.nextInt(types.length)]);
                it.setItemMeta(m);
                ThrownPotion tp = p.launchProjectile(ThrownPotion.class, d.clone().multiply(0.9));
                tp.setItem(it);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 1f);
            }
            case "EVOKER" -> {
                if (!ready(s, true, 2000)) return;
                s.disguise.swingMainHand();
                fangsLine(p);
            }
            case "GUARDIAN", "ELDER_GUARDIAN" -> {
                if (!ready(s, true, 1500)) return;
                beam(p, s);
            }
            default -> {
                if (!ready(s, true, 600)) return;
                melee(p, s, n);
            }
        }
    }

    // ------------------------------------------------------------------ RMB

    void secondary(Player p, Session s) {
        if (p.getGameMode() == GameMode.SPECTATOR || s.disguise == null) return;
        String n = s.type.name();
        Vector d = look(p);
        switch (n) {
            case "WARDEN" -> {
                if (!ready(s, false, 3000)) return;
                sonic(p, s);
            }
            case "SKELETON", "STRAY", "BOGGED", "PARCHED", "PILLAGER" -> {
                if (!ready(s, false, 2500)) return;
                s.disguise.swingMainHand();
                for (int i = 0; i < 5; i++) arrow(p, n, d, 0.12);
            }
            case "BLAZE" -> {
                if (!ready(s, false, 2500)) return;
                for (int i = 0; i < 3; i++) {
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        if (!p.isOnline()) return;
                        fire(p, look(p), SmallFireball.class);
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
                    }, i * 5L);
                }
            }
            case "GHAST" -> {
                if (!ready(s, false, 4000)) return;
                LargeFireball f = fire(p, d, LargeFireball.class);
                f.setYield(4f);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 3f, 0.7f);
            }
            case "WITHER" -> {
                if (!ready(s, false, 2500)) return;
                WitherSkull w = fire(p, d, WitherSkull.class);
                w.setCharged(true);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.8f);
            }
            case "ENDER_DRAGON" -> {
                if (!ready(s, false, 3000)) return;
                fire(p, d, DragonFireball.class);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 2f, 1f);
            }
            case "ENDERMAN" -> {
                if (!ready(s, false, 1000)) return;
                teleport(p);
            }
            case "CREEPER" -> {
                if (!ready(s, false, 5000)) return;
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1f, 1f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4, false, false, false));
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (p.isOnline()) p.getWorld().createExplosion(p.getLocation(), 3f, false, true, p);
                }, 15L);
            }
            case "EVOKER" -> {
                if (!ready(s, false, 4000)) return;
                fangsCircle(p);
            }
            case "ELDER_GUARDIAN" -> {
                if (!ready(s, false, 15000)) return;
                for (Player o : p.getWorld().getPlayers()) {
                    if (!o.equals(p) && o.getLocation().distanceSquared(p.getLocation()) < 48 * 48) {
                        o.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 1200, 2));
                        o.playSound(o.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                    }
                }
            }
            case "PUFFERFISH" -> {
                if (!ready(s, false, 4000)) return;
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 4, 3, 4, filter(p))) {
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1));
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 40, 1.5, 0.5, 1.5, 0.05);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PUFFER_FISH_BLOW_UP, 1f, 1f);
            }
            case "SQUID", "GLOW_SQUID" -> {
                if (!ready(s, false, 5000)) return;
                p.setVelocity(d.clone().multiply(1.2));
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 5, 3, 5, filter(p))) {
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                }
                p.getWorld().spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 40, 1, 0.5, 1, 0.05);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SQUID_SQUIRT, 1f, 1f);
            }
            case "IRON_GOLEM" -> {
                if (!ready(s, false, 3000)) return;
                s.disguise.swingMainHand();
                Location eye = p.getEyeLocation();
                RayTraceResult r = p.getWorld().rayTraceEntities(eye, d, 5, 0.6, filter(p));
                if (r != null && r.getHitEntity() instanceof LivingEntity t) {
                    hit(p, s, t, 7);
                    t.setVelocity(new Vector(0, 1.1, 0));
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1f);
                }
            }
            case "RAVAGER" -> {
                if (!ready(s, false, 5000)) return;
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 6, 3, 6, filter(p))) {
                    LivingEntity t = (LivingEntity) e;
                    Vector away = t.getLocation().toVector().subtract(p.getLocation().toVector());
                    t.setVelocity(flat(away).multiply(1.4).setY(0.4));
                    hit(p, s, t, 6);
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 60, 3, 0.5, 3, 0.05);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 1f);
            }
            case "GOAT" -> {
                if (!ready(s, false, 4000)) return;
                dash(p, s, 1.6, 6, true);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GOAT_RAM_IMPACT, 1f, 1f);
            }
            case "CAMEL", "CAMEL_HUSK" -> {
                if (!ready(s, false, 3000)) return;
                dash(p, s, 1.8, 0, true);
            }
            case "PHANTOM", "VEX" -> {
                if (!ready(s, false, 3000)) return;
                dash(p, s, 1.5, 5, false);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
            }
            case "SPIDER", "CAVE_SPIDER", "RABBIT", "FROG", "FOX" -> {
                if (!ready(s, false, 1500)) return;
                p.setVelocity(d.clone().multiply(0.9).setY(0.6));
            }
            case "SLIME", "MAGMA_CUBE" -> {
                if (!ready(s, false, 1500)) return;
                p.setVelocity(d.clone().multiply(0.7).setY(0.9));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 1f);
            }
            case "BREEZE" -> {
                if (!ready(s, false, 3000)) return;
                p.setVelocity(new Vector(d.getX() * 0.4, 1.4, d.getZ() * 0.4));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
            }
            case "WITCH" -> {
                if (!ready(s, false, 8000)) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITCH_DRINK, 1f, 1f);
            }
            default -> {
                if (ready(s, false, 1500)) {
                    p.sendActionBar(Component.text("У этого моба нет особой способности", NamedTextColor.GRAY));
                }
            }
        }
    }

    // ------------------------------------------------------------------ implementations

    private void melee(Player p, Session s, String n) {
        double dmg = MobEcome.attr(s.disguise, Attribute.ATTACK_DAMAGE, 1.0);
        if (dmg <= 0) dmg = 1.0;
        if (n.equals("ENDER_DRAGON")) dmg = Math.max(dmg, 10);

        AttributeInstance sc = p.getAttribute(Attribute.SCALE);
        double scale = sc != null ? sc.getValue() : 1.0;
        double reach = Math.max(3.0, Math.min(9.0, 3.0 * scale));

        s.disguise.swingMainHand();
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceEntities(eye, eye.getDirection(), reach, 0.3, filter(p));
        if (r == null || !(r.getHitEntity() instanceof LivingEntity t)) return;

        hit(p, s, t, dmg);
        switch (n) {
            case "WITHER_SKELETON" -> t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0));
            case "HUSK", "CAMEL_HUSK" -> t.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
            case "CAVE_SPIDER", "BEE" -> t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 150, 0));
            default -> {
            }
        }
    }

    private void arrow(Player p, String n, Vector dir, double spread) {
        Vector v = dir.clone();
        if (spread > 0) {
            v.add(new Vector((rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread,
                    (rnd.nextDouble() - 0.5) * spread)).normalize();
        }
        Arrow a = p.launchProjectile(Arrow.class, v.multiply(3.0));
        a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        switch (n) {
            case "BOGGED" -> a.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 0), true);
            case "STRAY" -> a.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 0), true);
            case "PARCHED" -> a.addCustomEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0), true);
            default -> {
            }
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1f, 1f);
    }

    private <T extends Fireball> T fire(Player p, Vector dir, Class<T> cls) {
        T f = p.launchProjectile(cls, dir.clone());
        f.setDirection(dir.clone());
        return f;
    }

    private void beam(Player p, Session s) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        RayTraceResult r = w.rayTrace(eye, d, 16, FluidCollisionMode.NEVER, true, 0.3, filter(p));
        double dist = 16;
        if (r != null) {
            dist = r.getHitPosition().distance(eye.toVector());
            if (r.getHitEntity() instanceof LivingEntity t) hit(p, s, t, 6);
        }
        for (double i = 1; i <= dist; i += 0.5) {
            w.spawnParticle(Particle.END_ROD, eye.clone().add(d.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
        w.playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1f);
    }

    private void sonic(Player p, Session s) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection().normalize();
        s.disguise.swingMainHand();
        w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 1f);
        for (int i = 1; i <= 15; i++) {
            w.spawnParticle(Particle.SONIC_BOOM, eye.clone().add(d.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
        for (Entity e : w.getNearbyEntities(eye, 16, 16, 16, filter(p))) {
            LivingEntity t = (LivingEntity) e;
            Vector to = t.getBoundingBox().getCenter().subtract(eye.toVector());
            double proj = to.dot(d);
            if (proj < 0 || proj > 16) continue;
            double off = to.clone().subtract(d.clone().multiply(proj)).length();
            if (off > 1.2 + t.getWidth() / 2) continue;
            hit(p, s, t, 10);
            t.setVelocity(t.getVelocity().add(d.clone().multiply(1.6).setY(0.5)));
        }
    }

    private void teleport(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        RayTraceResult r = w.rayTraceBlocks(eye, d, 32, FluidCollisionMode.NEVER, true);
        Location dest;
        if (r != null && r.getHitBlockFace() != null) {
            dest = r.getHitPosition().toLocation(w).add(r.getHitBlockFace().getDirection().multiply(0.6));
            if (r.getHitBlockFace() != BlockFace.UP) dest.subtract(0, p.getEyeHeight(), 0);
        } else {
            dest = eye.clone().add(d.clone().multiply(32)).subtract(0, p.getEyeHeight(), 0);
        }
        dest.setYaw(eye.getYaw());
        dest.setPitch(eye.getPitch());
        w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        p.teleport(dest);
        w.spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private void fangsLine(Player p) {
        Vector d = flat(look(p));
        Location base = p.getLocation();
        for (int i = 1; i <= 12; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline()) spawnFang(p, base.clone().add(d.clone().multiply(k * 1.3)));
            }, i * 2L);
        }
        p.getWorld().playSound(base, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
    }

    private void fangsCircle(Player p) {
        Location c = p.getLocation();
        for (int ring = 0; ring < 2; ring++) {
            final double radius = ring == 0 ? 3 : 5;
            final int points = ring == 0 ? 10 : 16;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                for (int i = 0; i < points; i++) {
                    double a = 2 * Math.PI * i / points;
                    spawnFang(p, c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
                }
            }, ring * 8L);
        }
        p.getWorld().playSound(c, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
    }

    private void spawnFang(Player p, Location l) {
        EvokerFangs f = (EvokerFangs) p.getWorld().spawnEntity(l, EntityType.EVOKER_FANGS);
        f.setOwner(p);
    }

    private void dash(Player p, Session s, double power, double dmg, boolean flatDash) {
        final Vector d = look(p);
        Vector v = flatDash ? flat(d).multiply(power).setY(0.25) : d.clone().multiply(power);
        p.setVelocity(v);
        final Set<UUID> hitSet = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || ++t > 12) {
                    cancel();
                    return;
                }
                if (dmg <= 0) return;
                for (Entity e : p.getNearbyEntities(1.8, 1.4, 1.8)) {
                    if (e instanceof LivingEntity le && !pl.isDisguise(e) && hitSet.add(e.getUniqueId())) {
                        hit(p, s, le, dmg);
                        le.setVelocity(d.clone().setY(0.35));
                    }
                }
            }
        }.runTaskTimer(pl, 1L, 1L);
    }

    // ------------------------------------------------------------------ utils

    private void hit(Player p, Session s, LivingEntity t, double dmg) {
        s.own = true;
        try {
            t.damage(dmg, p);
        } finally {
            s.own = false;
        }
    }

    private Predicate<Entity> filter(Player p) {
        return e -> e instanceof LivingEntity
                && !e.equals(p)
                && !pl.isDisguise(e)
                && !(e instanceof Player o && o.getGameMode() == GameMode.SPECTATOR);
    }

    private boolean ready(Session s, boolean primary, long ms) {
        long now = System.currentTimeMillis();
        if (primary) {
            if (now < s.lmbReady) return false;
            s.lmbReady = now + ms;
        } else {
            if (now < s.rmbReady) return false;
            s.rmbReady = now + ms;
        }
        return true;
    }

    private static Vector look(Player p) {
        return p.getEyeLocation().getDirection();
    }

    private static Vector flat(Vector v) {
        Vector f = v.clone().setY(0);
        if (f.lengthSquared() < 1e-6) return new Vector(0, 0, 1);
        return f.normalize();
    }
}
