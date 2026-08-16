package de.jpx3.intave.module.filter;

import de.jpx3.intave.IntavePlugin;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;

import java.util.Collections;

public final class EquipmentFilter extends Filter {
  private final IntavePlugin plugin;

  public EquipmentFilter(IntavePlugin plugin) {
    super("equipmentdata");
    this.plugin = plugin;
  }

  private ItemStack stripFromData(ItemStack itemStack) {
    itemStack.setAmount(1);
    if (itemStack.hasItemMeta()) {
      ItemMeta meta = itemStack.getItemMeta();
      if (meta.hasEnchants()) {
        for (Enchantment enchantment : itemStack.getEnchantments().keySet()) itemStack.removeEnchantment(enchantment);
        itemStack.addUnsafeEnchantment(Enchantment.THORNS, 1);
      }
      if (meta instanceof BookMeta) {
        BookMeta bookMeta = (BookMeta) meta;
        bookMeta.setTitle(null);
        bookMeta.setPages(Collections.emptyList());
        bookMeta.setAuthor(null);
      } else if (meta instanceof EnchantmentStorageMeta) {
        EnchantmentStorageMeta storage = (EnchantmentStorageMeta) meta;
        if (storage.hasStoredEnchants()) {
          for (Enchantment ench : storage.getStoredEnchants().keySet()) storage.removeStoredEnchant(ench);
          storage.addStoredEnchant(Enchantment.THORNS, 1, true);
        }
      } else if (meta instanceof FireworkEffectMeta) {
        ((FireworkEffectMeta) meta).setEffect(null);
      } else if (meta instanceof FireworkMeta) {
        FireworkMeta fireworkMeta = (FireworkMeta) meta;
        fireworkMeta.clearEffects();
        fireworkMeta.setPower(0);
      }
      meta.setDisplayName("");
      if (meta.getLore() != null) meta.setLore(Collections.emptyList());
      meta.removeItemFlags(meta.getItemFlags().toArray(new ItemFlag[0]));
    }
    return itemStack;
  }

  @Override
  protected boolean enabled() {
    return false;
  }
}
