package com.regayagamtor.regayasecurity.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Fungsi bantu untuk ItemStack dan indeks slot PlayerInventory Bukkit. */
public final class ItemUtil {

    /** Indeks PlayerInventory Bukkit: 0-8 hotbar, 9-35 utama, 36-39 armor (sepatu..helm), 40 offhand. */
    public static final int SLOT_BOOTS = 36;
    public static final int SLOT_LEGGINGS = 37;
    public static final int SLOT_CHESTPLATE = 38;
    public static final int SLOT_HELMET = 39;
    public static final int SLOT_OFFHAND = 40;

    private ItemUtil() {
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    /** Salinan item, atau null jika kosong. */
    public static ItemStack copy(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    /** Dua tumpukan dianggap sama jika keduanya kosong, atau jenis+metadata+jumlahnya identik. */
    public static boolean same(ItemStack a, ItemStack b) {
        boolean emptyA = isEmpty(a);
        boolean emptyB = isEmpty(b);
        if (emptyA || emptyB) {
            return emptyA && emptyB;
        }
        return a.getAmount() == b.getAmount() && a.isSimilar(b);
    }

    public static boolean isArmorSlot(int invSlot) {
        return invSlot >= SLOT_BOOTS && invSlot <= SLOT_HELMET;
    }

    /** Batas jumlah item pada satu slot (armor selalu 1, seperti vanilla). */
    public static int slotLimit(int invSlot, ItemStack item) {
        if (isArmorSlot(invSlot)) {
            return 1;
        }
        return Math.max(1, item.getMaxStackSize());
    }

    /** Apakah item boleh diletakkan di slot tersebut. Slot non-armor menerima apa saja. */
    public static boolean canPlace(ItemStack item, int invSlot, boolean strict) {
        if (isEmpty(item)) {
            return true;
        }
        if (!strict || !isArmorSlot(invSlot)) {
            return true;
        }
        String name = item.getType().name();
        return switch (invSlot) {
            case SLOT_BOOTS -> name.endsWith("_BOOTS");
            case SLOT_LEGGINGS -> name.endsWith("_LEGGINGS");
            case SLOT_CHESTPLATE -> name.endsWith("_CHESTPLATE") || name.equals("ELYTRA");
            case SLOT_HELMET -> name.endsWith("_HELMET")
                    || name.equals("CARVED_PUMPKIN")
                    || name.endsWith("_HEAD")
                    || name.endsWith("_SKULL");
            default -> true;
        };
    }

    /** Ikon GUI polos (nama + lore) dengan Component. */
    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(name);
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
