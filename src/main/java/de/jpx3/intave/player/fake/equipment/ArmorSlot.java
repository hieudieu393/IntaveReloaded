package de.jpx3.intave.player.fake.equipment;

import de.jpx3.intave.packet.nativeapi.wrappers.EnumWrappers;

public enum ArmorSlot {
  HELMET(EnumWrappers.ItemSlot.HEAD, 4),
  CHESTPLATE(EnumWrappers.ItemSlot.CHEST, 3),
  LEGGINGS(EnumWrappers.ItemSlot.LEGS, 2),
  BOOTS(EnumWrappers.ItemSlot.FEET, 1);

  private final EnumWrappers.ItemSlot itemSlot;
  private final int slotId;

  ArmorSlot(EnumWrappers.ItemSlot itemSlot, int slotId) {
    this.itemSlot = itemSlot;
    this.slotId = slotId;
  }

  public EnumWrappers.ItemSlot itemSlot() {
    return this.itemSlot;
  }

  public int slotId() {
    return slotId;
  }
}
