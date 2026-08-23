package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Wither;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_METADATA;

public final class HealthFilter extends Filter {
  private final IntavePlugin plugin;

  public HealthFilter(IntavePlugin plugin) {
    super("health");
    this.plugin = plugin;
  }

  @PacketSubscription(packetsOut = {ENTITY_METADATA}, priority = ListenerPriority.NORMAL)
  public void depriveHealth(ProtocolPacketEvent event) {
    EntityMetadataReader reader = PacketReaders.readerOf(event);
    try {
      Entity entity = reader.entityBy(event);
      if (!(entity instanceof LivingEntity) || entity instanceof EnderDragon || entity instanceof Wither
        || entity.getEntityId() == ((org.bukkit.entity.Player) event.getPlayer()).getEntityId()) {
        return;
      }
      Object health = reader.fetchRaw(6);
      if (health instanceof Float && ((Float) health) != 0.0F) {
        reader.setRaw(6, createFakeHealth());
      }
    } finally {
      reader.release();
    }
  }

  private float createFakeHealth() {
    return Math.max(1, (float) (Math.random() * 20.0F));
  }

  @Override
  protected boolean enabled() {
    return !MinecraftVersions.VER1_19.atOrAbove() && super.enabled();
  }
}
