package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.UPDATE_ATTRIBUTES;

public final class AttributeTracker extends Module {
  @PacketSubscription(priority = ListenerPriority.HIGH, packetsOut = {UPDATE_ATTRIBUTES})
  public void sentAttributes(ProtocolPacketEvent event) {
    if (!(event instanceof PacketSendEvent)) return;
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes((PacketSendEvent) event);
    if (wrapper.getEntityId() != player.getEntityId()) return;
    List<WrapperPlayServerUpdateAttributes.Property> properties = wrapper.getProperties();
    user.tickFeedback(() -> properties.forEach(property -> receivedAttribute(user, property)));
  }

  private void receivedAttribute(User user, WrapperPlayServerUpdateAttributes.Property property) {
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    String attributeKey = property.getKey();
    if (abilities.findAttribute(attributeKey) != null) {
      Attribute intaveAttribute = Attribute.fromPacketEvents(property);
      List<AttributeModifier> intaveAttributes = abilities.modifiersOf(intaveAttribute);
      intaveAttributes.clear();
      Set<AttributeModifier> serverAttributes = intaveAttribute.modifiers();
      movement.hasSprintSpeed = serverAttributes.contains(MovementMetadata.SPRINTING_MODIFIER);
      intaveAttributes.addAll(new HashSet<>(serverAttributes));
      abilities.modifyBaseValue(attributeKey, property.getValue());
    }
  }
}
