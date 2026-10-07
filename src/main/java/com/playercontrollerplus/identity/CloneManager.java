package com.playercontrollerplus.identity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.EnumItemSlot;
import net.minecraft.server.v1_12_R1.EnumProtocolDirection;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.NetworkManager;
import net.minecraft.server.v1_12_R1.Packet;
import net.minecraft.server.v1_12_R1.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_12_R1.PacketPlayOutEntityEquipment;
import net.minecraft.server.v1_12_R1.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_12_R1.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_12_R1.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_12_R1.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_12_R1.PlayerConnection;
import net.minecraft.server.v1_12_R1.PlayerInteractManager;
import net.minecraft.server.v1_12_R1.WorldServer;
import net.minecraft.server.v1_12_R1.PacketPlayOutPlayerInfo.EnumPlayerInfoAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class CloneManager {
    private static final Pattern CLONE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private final IdentityPlugin plugin;
    private final Map<String, Clone> clones = new HashMap<String, Clone>();
    private final Map<UUID, Set<String>> cloneIdsByOwner = new HashMap<UUID, Set<String>>();

    CloneManager(IdentityPlugin plugin) {
        this.plugin = plugin;
    }

    Clone create(Player source, String id, UUID ownerId) {
        String normalized = id.trim();
        if (!CLONE_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Clone ID must use 1-32 letters, numbers, or underscores.");
        }
        String key = normalized.toLowerCase(Locale.ROOT);
        if (clones.containsKey(key)) {
            throw new IllegalArgumentException("That clone ID is already in use.");
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer world = ((CraftWorld) source.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), normalized);
        if (plugin.getConfig().getBoolean("clone.copy-skin", true)) {
            GameProfile sourceProfile = ((CraftPlayer) source).getProfile();
            if (sourceProfile != null && sourceProfile.getProperties() != null) {
                profile.getProperties().putAll(sourceProfile.getProperties());
            }
        }

        EntityPlayer entity = new EntityPlayer(server, world, profile, new PlayerInteractManager(world));
        Location location = source.getLocation().clone();
        entity.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        entity.playerConnection = new PlayerConnection(server, new NetworkManager(EnumProtocolDirection.SERVERBOUND), entity);
        world.addEntity(entity);

        Clone clone = new Clone(normalized, source.getName(), ownerId, entity);
        clones.put(key, clone);
        if (ownerId != null) {
            Set<String> owned = cloneIdsByOwner.get(ownerId);
            if (owned == null) {
                owned = new HashSet<String>();
                cloneIdsByOwner.put(ownerId, owned);
            }
            owned.add(key);
        }

        syncEquipment(clone, source);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            showClone(viewer, clone);
        }
        return clone;
    }

    Clone getByOwner(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        Set<String> owned = cloneIdsByOwner.get(ownerId);
        if (owned == null || owned.isEmpty()) {
            return null;
        }
        String key = owned.iterator().next();
        return clones.get(key);
    }

    Clone get(String id) {
        if (id == null) {
            return null;
        }
        return clones.get(id.toLowerCase(Locale.ROOT));
    }

    List<String> getIds() {
        List<String> ids = new ArrayList<String>();
        for (Clone clone : clones.values()) {
            ids.add(clone.id);
        }
        Collections.sort(ids, String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    void showTo(Player viewer) {
        for (Clone clone : clones.values()) {
            showClone(viewer, clone);
        }
    }

    private void showClone(Player viewer, Clone clone) {
        send(viewer, new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.ADD_PLAYER, clone.entity));
        send(viewer, new PacketPlayOutNamedEntitySpawn(clone.entity));
        send(viewer, new PacketPlayOutEntityMetadata(clone.entity.getId(), clone.entity.getDataWatcher(), true));
        send(viewer, new PacketPlayOutEntityHeadRotation(clone.entity, (byte) (clone.entity.yaw * 256.0F / 360.0F)));
        sendEquipment(viewer, clone);
    }

    private void syncEquipment(Clone clone, Player source) {
        if (plugin.getConfig().getBoolean("clone.copy-held-item", true)) {
            clone.entity.inventory.setItem(0, CraftItemStack.asNMSCopy(source.getInventory().getItemInMainHand()));
        }
        if (plugin.getConfig().getBoolean("clone.copy-armor", true)) {
            ItemStack[] armor = source.getInventory().getArmorContents();
            if (armor.length >= 4) {
                clone.entity.inventory.armor.set(3, CraftItemStack.asNMSCopy(armor[0]));
                clone.entity.inventory.armor.set(2, CraftItemStack.asNMSCopy(armor[1]));
                clone.entity.inventory.armor.set(1, CraftItemStack.asNMSCopy(armor[2]));
                clone.entity.inventory.armor.set(0, CraftItemStack.asNMSCopy(armor[3]));
            }
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendEquipment(viewer, clone);
        }
    }

    private void sendEquipment(Player viewer, Clone clone) {
        EnumItemSlot[] slots = new EnumItemSlot[] { EnumItemSlot.MAINHAND, EnumItemSlot.HEAD, EnumItemSlot.CHEST, EnumItemSlot.LEGS, EnumItemSlot.FEET };
        for (EnumItemSlot slot : slots) {
            Packet<?> packet = new PacketPlayOutEntityEquipment(clone.entity.getId(), slot, clone.entity.getEquipment(slot));
            send(viewer, packet);
        }
    }

    private void send(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    boolean remove(String id) {
        if (id == null) {
            return false;
        }
        Clone clone = clones.remove(id.toLowerCase(Locale.ROOT));
        if (clone == null) {
            return false;
        }
        if (clone.ownerId != null) {
            Set<String> owned = cloneIdsByOwner.get(clone.ownerId);
            if (owned != null) {
                owned.remove(id.toLowerCase(Locale.ROOT));
                if (owned.isEmpty()) {
                    cloneIdsByOwner.remove(clone.ownerId);
                }
            }
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            send(viewer, new PacketPlayOutEntityDestroy(clone.entity.getId()));
            send(viewer, new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.REMOVE_PLAYER, clone.entity));
        }
        clone.entity.world.removeEntity(clone.entity);
        return true;
    }

    int removeAll() {
        List<String> ids = new ArrayList<String>(clones.keySet());
        for (String id : ids) {
            remove(id);
        }
        return ids.size();
    }

    void removeOwnedBy(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        Set<String> owned = cloneIdsByOwner.remove(ownerId);
        if (owned == null) {
            return;
        }
        for (String key : new ArrayList<String>(owned)) {
            remove(key);
        }
    }

    void close() {
        removeAll();
    }

    static final class Clone {
        private final String id;
        private final String sourceName;
        private final UUID ownerId;
        private final EntityPlayer entity;

        Clone(String id, String sourceName, UUID ownerId, EntityPlayer entity) {
            this.id = id;
            this.sourceName = sourceName;
            this.ownerId = ownerId;
            this.entity = entity;
        }

        String getId() {
            return id;
        }

        String getSourceName() {
            return sourceName;
        }

        EntityPlayer getEntity() {
            return entity;
        }
    }
}
