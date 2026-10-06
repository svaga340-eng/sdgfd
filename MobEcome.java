package ru.mobecome;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class MobEcome extends JavaPlugin implements Listener, TabExecutor {

    static final Set<String> FLYERS = Set.of("BAT", "PARROT", "ALLAY", "BEE", "GHAST", "HAPPY_GHAST",
            "VEX", "PHANTOM", "BLAZE", "ENDER_DRAGON", "WITHER");
    static final Set<String> AQUATIC = Set.of("SQUID", "GLOW_SQUID", "COD", "SALMON", "TROPICAL_FISH",
            "PUFFERFISH", "DOLPHIN", "GUARDIAN", "ELDER_GUARDIAN", "TURTLE", "AXOLOTL", "DROWNED",
            "NAUTILUS", "ZOMBIE_NAUTILUS", "TADPOLE");
    static final Set<String> FIRE = Set.of("BLAZE", "GHAST", "MAGMA_CUBE", "STRIDER", "ZOMBIFIED_PIGLIN",
            "WITHER_SKELETON", "WITHER");
    static final Set<String> EXCLUDED = Set.of("PLAYER", "ARMOR_STAND", "MANNEQUIN", "UNKNOWN");

    static final Map<String, Attribute> ATTRS = new LinkedHashMap<>();

    static {
        ATTRS.put("max_health", Attribute.MAX_HEALTH);
        ATTRS.put("speed", Attribute.MOVEMENT_SPEED);
        ATTRS.put("scale", Attribute.SCALE);
        ATTRS.put("armor", Attribute.ARMOR);
        ATTRS.put("kb", Attribute.KNOCKBACK_RESISTANCE);
    }

    final Map<UUID, Session> sessions = new HashMap<>();
    final Set<UUID> dead = new HashSet<>();
    final List<EntityType> mobs = new ArrayList<>();
    NamespacedKey keyMob;
    NamespacedKey keyDisguise;
    NamespacedKey keyAbility;
    private Scoreboard sb;
    private Team hideTeam;
    private Abilities abilities;
    private File dataDir;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyMob = new NamespacedKey(this, "mob");
        keyDisguise = new NamespacedKey(this, "disguise");
        keyAbility = new NamespacedKey(this, "ability");
        dataDir = new File(getDataFolder(), "data");
        dataDir.mkdirs();

        sb = Bukkit.getScoreboardManager().getMainScoreboard();
        hideTeam = sb.getTeam("mobecome_hide");
        if (hideTeam == null) hideTeam = sb.registerNewTeam("mobecome_hide");
        hideTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        hideTeam.setCanSeeFriendlyInvisibles(false);

        buildMobList();
        abilities = new Abilities(this);

        getCommand("mobecome").setExecutor(this);
        getCommand("mobecome").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick(++tick);
            }
        }.runTaskTimer(this, 1L, 1L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (file(p).exists()) restore(p, false);
        }
        getLogger().info("Загружено мобов: " + mobs.size());
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restore(p, false);
            } else {
                Session s = sessions.remove(id);
                if (s != null && s.disguise != null) s.disguise.remove();
            }
        }
    }

    private void buildMobList() {
        mobs.clear();
        for (EntityType t : EntityType.values()) {
            if (EXCLUDED.contains(t.name())) continue;
            Class<?> c = t.getEntityClass();
            if (c == null || !t.isSpawnable() || !t.isAlive()) continue;
            if (!LivingEntity.class.isAssignableFrom(c)) continue;
            mobs.add(t);
        }
        mobs.sort(Comparator.comparing(EntityType::name));
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Команда только для игроков.");
            return true;
        }
        if (!p.hasPermission("mobecome.use")) {
            p.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("back")) {
            if (!sessions.containsKey(p.getUniqueId()) && !file(p).exists()) {
                p.sendMessage(Component.text("Вы и так в человеческом облике.", NamedTextColor.YELLOW));
                return true;
            }
            restore(p, false);
            p.sendMessage(Component.text("Вы снова человек, ресурсы возвращены.", NamedTextColor.GREEN));
            return true;
        }
        MobMenu.open(this, p, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "back".startsWith(args[0].toLowerCase())) return List.of("back");
        return List.of();
    }

    // ------------------------------------------------------------------ transform

    ItemStack abilityItem() {
        ItemStack it = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Способности моба", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("ЛКМ — атака / выстрел", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("ПКМ — особая способность", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(keyAbility, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    void disguise(Player p, EntityType type) {
        UUID id = p.getUniqueId();
        Session s = sessions.get(id);
        if (s == null) {
            saveSnapshot(p);
            s = new Session(type);
            s.origAllowFlight = p.getAllowFlight();
            sessions.put(id, s);

            int size = p.getInventory().getContents().length;
            p.getInventory().setContents(new ItemStack[size]);
            p.getInventory().setItem(0, abilityItem());
            p.getInventory().setHeldItemSlot(0);

            Team prev = sb.getEntryTeam(p.getName());
            if (prev != null) prev.removeEntry(p.getName());
            hideTeam.addEntry(p.getName());
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
        } else {
            if (s.disguise != null) s.disguise.remove();
            s.type = type;
        }

        try {
            spawnDisguise(p, s);
        } catch (RuntimeException ex) {
            getLogger().warning("Не удалось создать моба " + type + ": " + ex.getMessage());
            restore(p, false);
            p.sendMessage(Component.text("Не удалось превратиться в этого моба.", NamedTextColor.RED));
            return;
        }
        applyStats(p, s);
        p.playerListName(Component.translatable(type.translationKey()));
        p.sendMessage(Component.text("Вы превратились: ", NamedTextColor.GREEN)
                .append(Component.translatable(type.translationKey(), NamedTextColor.GOLD))
                .append(Component.text(". ЛКМ — атака, ПКМ — особая способность. Вернуться: /mobecome back",
                        NamedTextColor.GREEN)));
    }

    private void spawnDisguise(Player p, Session s) {
        Entity e = p.getWorld().spawnEntity(p.getLocation(), s.type);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            throw new IllegalStateException("не живая сущность");
        }
        le.setAI(false);
        le.setGravity(false);
        le.setInvulnerable(true);
        le.setSilent(true);
        le.setCollidable(false);
        le.setPersistent(false);
        le.setRemoveWhenFarAway(false);
        le.setCanPickupItems(false);
        le.customName(null);
        le.setCustomNameVisible(false);
        le.getPersistentDataContainer().set(keyDisguise, PersistentDataType.BYTE, (byte) 1);
        if (le instanceof Ageable a) a.setAdult();
        if (le instanceof Zombie z) z.setBaby(false);
        if (le instanceof Slime sl) sl.setSize(2);
        if (le instanceof Piglin pg) pg.setImmuneToZombification(true);
        if (le instanceof Hoglin h) h.setImmuneToZombification(true);
        if (le instanceof Boss b && b.getBossBar() != null) b.getBossBar().setVisible(false);
        s.disguise = le;
    }

    private void applyStats(Player p, Session s) {
        LivingEntity d = s.disguise;
        String n = s.type.name();

        double hp = clamp(attr(d, Attribute.MAX_HEALTH, 20), 1, 1024);
        setBase(p, Attribute.MAX_HEALTH, hp);
        p.setHealth(hp);
        p.setFoodLevel(20);
        p.setSaturation(20f);

        setBase(p, Attribute.MOVEMENT_SPEED, clamp(attr(d, Attribute.MOVEMENT_SPEED, 0.2) * 0.5, 0.05, 0.2));
        setBase(p, Attribute.ARMOR, clamp(attr(d, Attribute.ARMOR, 0), 0, 30));
        setBase(p, Attribute.KNOCKBACK_RESISTANCE, clamp(attr(d, Attribute.KNOCKBACK_RESISTANCE, 0), 0, 1));
        double scale = getConfig().getBoolean("scale-hitbox", true) ? clamp(d.getHeight() / 1.8, 0.25, 6) : 1.0;
        setBase(p, Attribute.SCALE, scale);

        removeInfinite(p, PotionEffectType.WATER_BREATHING);
        removeInfinite(p, PotionEffectType.DOLPHINS_GRACE);
        removeInfinite(p, PotionEffectType.FIRE_RESISTANCE);
        removeInfinite(p, PotionEffectType.SLOW_FALLING);
        if (AQUATIC.contains(n)) {
            infinite(p, PotionEffectType.WATER_BREATHING);
            infinite(p, PotionEffectType.DOLPHINS_GRACE);
        }
        if (FIRE.contains(n)) infinite(p, PotionEffectType.FIRE_RESISTANCE);
        if (n.equals("CHICKEN")) infinite(p, PotionEffectType.SLOW_FALLING);

        boolean fly = FLYERS.contains(n);
        p.setAllowFlight(fly || s.origAllowFlight);
        if (fly) p.setFlying(true);
        else if (!s.origAllowFlight) p.setFlying(false);
    }

    // ------------------------------------------------------------------ snapshot / restore

    private File file(Player p) {
        return new File(dataDir, p.getUniqueId() + ".yml");
    }

    private void saveSnapshot(Player p) {
        File f = file(p);
        if (f.exists()) return; // уже есть сохранённые оригинальные данные — не затираем
        YamlConfiguration y = new YamlConfiguration();
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] != null && !c[i].getType().isAir()) y.set("items." + i, c[i]);
        }
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", (double) p.getSaturation());
        y.set("allowFlight", p.getAllowFlight());
        y.set("flying", p.isFlying());
        for (Map.Entry<String, Attribute> en : ATTRS.entrySet()) {
            AttributeInstance ai = p.getAttribute(en.getValue());
            if (ai != null) y.set("attr." + en.getKey(), ai.getBaseValue());
        }
        Team prev = sb.getEntryTeam(p.getName());
        if (prev != null && !prev.equals(hideTeam)) y.set("team", prev.getName());
        try {
            y.save(f);
        } catch (IOException ex) {
            getLogger().severe("Не удалось сохранить инвентарь " + p.getName() + ": " + ex.getMessage());
        }
    }

    void restore(Player p, boolean died) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null && s.disguise != null) s.disguise.remove();

        hideTeam.removeEntry(p.getName());
        removeInfinite(p, PotionEffectType.INVISIBILITY);
        removeInfinite(p, PotionEffectType.WATER_BREATHING);
        removeInfinite(p, PotionEffectType.DOLPHINS_GRACE);
        removeInfinite(p, PotionEffectType.FIRE_RESISTANCE);
        removeInfinite(p, PotionEffectType.SLOW_FALLING);
        p.playerListName(null);

        File f = file(p);
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

        for (Map.Entry<String, Attribute> en : ATTRS.entrySet()) {
            String key = "attr." + en.getKey();
            AttributeInstance ai = p.getAttribute(en.getValue());
            if (ai != null && y.contains(key)) ai.setBaseValue(y.getDouble(key));
        }

        String teamName = y.getString("team");
        if (teamName != null) {
            Team t = sb.getTeam(teamName);
            if (t != null) t.addEntry(p.getName());
        }

        ItemStack[] arr = new ItemStack[p.getInventory().getContents().length];
        ConfigurationSection sec = y.getConfigurationSection("items");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    int i = Integer.parseInt(k);
                    if (i >= 0 && i < arr.length) arr[i] = sec.getItemStack(k);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        p.getInventory().setContents(arr);

        boolean allow = y.getBoolean("allowFlight");
        p.setAllowFlight(allow);
        p.setFlying(allow && y.getBoolean("flying"));

        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        double max = mh != null ? mh.getValue() : 20;
        p.setHealth(died ? max : clamp(y.getDouble("health", max), 1, max));
        if (!died) {
            p.setFoodLevel(y.getInt("food", 20));
            p.setSaturation((float) y.getDouble("saturation", 5));
        }
        f.delete();
    }

    // ------------------------------------------------------------------ tick

    private void tick(int t) {
        for (Map.Entry<UUID, Session> en : new ArrayList<>(sessions.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            Session s = en.getValue();
            if (p == null || !p.isOnline() || p.isDead()) continue;

            if (s.disguise == null || !s.disguise.isValid()) {
                try {
                    spawnDisguise(p, s);
                } catch (RuntimeException ex) {
                    continue;
                }
            }
            LivingEntity d = s.disguise;
            Location l = p.getLocation();
            d.teleport(l);
            d.setRotation(l.getYaw(), l.getPitch());
            d.setBodyYaw(l.getYaw());

            if (t % 10 == 0) {
                // скрываем у остальных предмет-«палочку» в руке игрока
                ItemStack air = new ItemStack(Material.AIR);
                for (Player v : p.getWorld().getPlayers()) {
                    if (!v.equals(p) && v.getLocation().distanceSquared(l) < 4096) {
                        v.sendEquipmentChange(p, EquipmentSlot.HAND, air);
                    }
                }
            }
            if (t % 20 == 0) {
                ItemStack cur = p.getInventory().getItem(0);
                if (cur == null || cur.getType() != Material.BLAZE_ROD) p.getInventory().setItem(0, abilityItem());
                if (p.getInventory().getHeldItemSlot() != 0) p.getInventory().setHeldItemSlot(0);

                p.sendActionBar(Component.text("Вы — ", NamedTextColor.GRAY)
                        .append(Component.translatable(s.type.translationKey(), NamedTextColor.GOLD))
                        .append(Component.text("   ПКМ: ", NamedTextColor.GRAY))
                        .append(Component.text(abilities.secondaryName(s.type), NamedTextColor.YELLOW))
                        .append(Component.text("   /mobecome back", NamedTextColor.DARK_GRAY)));
            }
        }
    }

    // ------------------------------------------------------------------ listeners

    boolean isDisguise(Entity e) {
        return e.getPersistentDataContainer().has(keyDisguise, PersistentDataType.BYTE);
    }

    private Session sess(Player p) {
        return sessions.get(p.getUniqueId());
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Session s = sess(e.getPlayer());
        if (s == null) return;
        if (System.currentTimeMillis() - s.lastRight < 250) return;
        abilities.primary(e.getPlayer(), s);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Session s = sess(e.getPlayer());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            s.lastRight = System.currentTimeMillis();
            abilities.secondary(e.getPlayer(), s);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Session s = sess(e.getPlayer());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        s.lastRight = System.currentTimeMillis();
        abilities.secondary(e.getPlayer(), s);
    }

    @EventHandler
    public void onDamageBy(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Session s = sess(p);
        if (s == null || s.own) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.ENTITY_ATTACK || c == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            e.setCancelled(true); // урон наносит Abilities, а не ванильный удар кулаком
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Session s = sess(p);
        if (s != null && s.disguise != null && s.disguise.isValid()) s.disguise.playHurtAnimation(0f);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        Entity t = e.getTarget();
        if (t != null && isDisguise(t)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (sess(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && sess(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (sess(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        if (sess(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (sess(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (sess(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MobMenu menu) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p
                    && e.getClickedInventory() != null
                    && e.getClickedInventory().equals(e.getView().getTopInventory())) {
                MobMenu.click(this, p, menu, e.getSlot(), e.getCurrentItem());
            }
            return;
        }
        if (e.getWhoClicked() instanceof Player p && sess(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MobMenu
                || (e.getWhoClicked() instanceof Player p && sess(p) != null)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        if (s.disguise != null) s.disguise.remove();
        e.getDrops().clear();
        e.setKeepInventory(false);
        e.setDroppedExp(0);
        dead.add(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (dead.remove(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (p.isOnline()) restore(p, true);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (sessions.containsKey(p.getUniqueId())) restore(p, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!sessions.containsKey(p.getUniqueId()) && file(p).exists()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && !sessions.containsKey(p.getUniqueId())) restore(p, false);
            }, 5L);
        }
    }

    // ------------------------------------------------------------------ helpers

    static double attr(LivingEntity e, Attribute a, double def) {
        AttributeInstance ai = e.getAttribute(a);
        return ai == null ? def : ai.getValue();
    }

    private static void setBase(Player p, Attribute a, double v) {
        AttributeInstance ai = p.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void infinite(Player p, PotionEffectType t) {
        p.addPotionEffect(new PotionEffect(t, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private static void removeInfinite(Player p, PotionEffectType t) {
        PotionEffect e = p.getPotionEffect(t);
        if (e != null && e.isInfinite()) p.removePotionEffect(t);
    }
}
