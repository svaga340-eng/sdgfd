package ru.mobecome;

import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** GUI выбора моба (постраничное). */
final class MobMenu implements InventoryHolder {
    private static final int PER_PAGE = 45;
    private static final Map<String, Material> SPECIAL = Map.of(
            "ENDER_DRAGON", Material.DRAGON_HEAD,
            "WITHER", Material.WITHER_SKELETON_SKULL,
            "GIANT", Material.ZOMBIE_HEAD,
            "IRON_GOLEM", Material.IRON_BLOCK,
            "SNOW_GOLEM", Material.SNOW_BLOCK,
            "ILLUSIONER", Material.TOTEM_OF_UNDYING);

    private final int page;
    private Inventory inv;

    private MobMenu(int page) {
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    static void open(MobEcome pl, Player p, int page) {
        List<EntityType> mobs = pl.mobs;
        int pages = Math.max(1, (int) Math.ceil(mobs.size() / (double) PER_PAGE));
        int pg = Math.max(0, Math.min(page, pages - 1));
        MobMenu holder = new MobMenu(pg);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Выбор моба — стр. " + (pg + 1) + "/" + pages));
        holder.inv = inv;

        int from = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < mobs.size(); i++) {
            inv.setItem(i, icon(pl, mobs.get(from + i)));
        }
        if (pg > 0) inv.setItem(45, nav(Material.ARROW, "← Назад"));
        inv.setItem(49, nav(Material.BARRIER, "Закрыть"));
        if (pg < pages - 1) inv.setItem(53, nav(Material.ARROW, "Вперёд →"));
        p.openInventory(inv);
    }

    static void click(MobEcome pl, Player p, MobMenu holder, int slot, ItemStack item) {
        if (!p.hasPermission("mobecome.use")) {
            p.closeInventory();
            return;
        }
        if (slot == 45) {
            open(pl, p, holder.page - 1);
            return;
        }
        if (slot == 53) {
            open(pl, p, holder.page + 1);
            return;
        }
        if (slot == 49) {
            p.closeInventory();
            return;
        }
        if (item == null || !item.hasItemMeta()) return;
        String name = item.getItemMeta().getPersistentDataContainer()
                .get(pl.keyMob, PersistentDataType.STRING);
        if (name == null) return;
        try {
            EntityType type = EntityType.valueOf(name);
            p.closeInventory();
            pl.disguise(p, type);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static ItemStack icon(MobEcome pl, EntityType t) {
        Material m = Material.matchMaterial(t.name() + "_SPAWN_EGG");
        if (m == null) m = SPECIAL.getOrDefault(t.name(), Material.NAME_TAG);
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.translatable(t.translationKey(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Нажмите, чтобы превратиться", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(pl.keyMob, PersistentDataType.STRING, t.name());
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack nav(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }
}
