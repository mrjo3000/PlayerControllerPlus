package com.playercontrollerplus.identity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.v1_12_R1.Packet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;

final class ControlManager {
    private final IdentityPlugin plugin;
    private final Map<UUID, UUID> controllerByTarget = new HashMap<UUID, UUID>();
    private final Map<UUID, UUID> targetByController = new HashMap<UUID, UUID>();
    private final PacketAdapter listener;

    ControlManager(IdentityPlugin plugin) {
        this.plugin = plugin;
        this.listener = new PacketAdapter(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.FLYING,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.LOOK,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.USE_ENTITY,
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE,
                PacketType.Play.Client.ARM_ANIMATION,
                PacketType.Play.Client.ENTITY_ACTION,
                PacketType.Play.Client.HELD_ITEM_SLOT,
                PacketType.Play.Client.STEER_VEHICLE,
                PacketType.Play.Client.CHAT) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handle(event);
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(listener);
    }

    void control(Player controller, Player target) {
        releasePlayer(controller.getUniqueId());
        releaseTarget(target.getUniqueId());
        targetByController.put(controller.getUniqueId(), target.getUniqueId());
        controllerByTarget.put(target.getUniqueId(), controller.getUniqueId());
        plugin.log(controller.getName() + " now controls " + target.getName());
    }

    boolean isTargetControlled(UUID targetId) {
        return controllerByTarget.containsKey(targetId);
    }

    UUID getTargetForController(UUID controllerId) {
        return targetByController.get(controllerId);
    }

    UUID getControllerForTarget(UUID targetId) {
        return controllerByTarget.get(targetId);
    }

    private void handle(PacketEvent event) {
        UUID controllerId = event.getPlayer().getUniqueId();
        UUID targetId = targetByController.get(controllerId);
        if (targetId == null) {
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            releasePlayer(controllerId);
            return;
        }

        PacketType packetType = event.getPacketType();
        if (packetType == PacketType.Play.Client.CHAT) {
            String text = event.getPacket().getStrings().readSafely(0);
            if (text == null) {
                return;
            }
            event.setCancelled(true);
            if (!text.startsWith("/")) {
                Bukkit.broadcastMessage("<" + target.getName() + "> " + text);
                plugin.getLogger().info("[Identity] " + event.getPlayer().getName() + " -> Control(" + target.getName() + "): " + text);
            }
            return;
        }

        if (isMovement(packetType) || isInteraction(packetType)) {
            event.setCancelled(true);
            try {
                forwardPacket(target, event.getPacket().getHandle());
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("Failed to route packet for " + target.getName() + ": " + exception.getMessage());
                releaseTarget(targetId);
            }
        }
    }

    private boolean isMovement(PacketType type) {
        return type == PacketType.Play.Client.FLYING
                || type == PacketType.Play.Client.POSITION
                || type == PacketType.Play.Client.LOOK
                || type == PacketType.Play.Client.POSITION_LOOK;
    }

    private boolean isInteraction(PacketType type) {
        return type == PacketType.Play.Client.USE_ENTITY
                || type == PacketType.Play.Client.BLOCK_DIG
                || type == PacketType.Play.Client.BLOCK_PLACE
                || type == PacketType.Play.Client.ARM_ANIMATION
                || type == PacketType.Play.Client.ENTITY_ACTION
                || type == PacketType.Play.Client.HELD_ITEM_SLOT
                || type == PacketType.Play.Client.STEER_VEHICLE;
    }

    private void forwardPacket(Player target, Object packet) throws ReflectiveOperationException {
        Object entityPlayer = ((CraftPlayer) target).getHandle();
        Object connection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
        connection.getClass().getMethod("a", Packet.class).invoke(connection, packet);
    }

    boolean releaseTarget(UUID targetId) {
        UUID controllerId = controllerByTarget.remove(targetId);
        if (controllerId == null) {
            return false;
        }
        targetByController.remove(controllerId);
        return true;
    }

    void releasePlayer(UUID playerId) {
        UUID targetId = targetByController.remove(playerId);
        if (targetId != null) {
            controllerByTarget.remove(targetId);
            return;
        }
        UUID controllerId = controllerByTarget.remove(playerId);
        if (controllerId != null) {
            targetByController.remove(controllerId);
        }
    }

    int releaseAll() {
        int count = controllerByTarget.size();
        controllerByTarget.clear();
        targetByController.clear();
        return count;
    }

    String getControllerName(UUID targetId) {
        UUID controllerId = controllerByTarget.get(targetId);
        if (controllerId == null) {
            return null;
        }
        Player controller = Bukkit.getPlayer(controllerId);
        return controller == null ? null : controller.getName();
    }

    void close() {
        releaseAll();
        ProtocolLibrary.getProtocolManager().removePacketListener(listener);
    }
}
