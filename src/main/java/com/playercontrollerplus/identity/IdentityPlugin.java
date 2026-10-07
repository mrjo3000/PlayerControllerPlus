package com.playercontrollerplus.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class IdentityPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private ControlManager controlManager;
    private CloneManager cloneManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().severe("ProtocolLib is required for packet-based control routing. Disabling Identity.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        controlManager = new ControlManager(this);
        cloneManager = new CloneManager(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("identity") != null) {
            getCommand("identity").setExecutor(this);
            getCommand("identity").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (controlManager != null) {
            controlManager.close();
        }
        if (cloneManager != null) {
            cloneManager.close();
        }
    }

    public ControlManager getControlManager() {
        return controlManager;
    }

    public CloneManager getCloneManager() {
        return cloneManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (controlManager != null) {
            controlManager.releasePlayer(event.getPlayer().getUniqueId());
        }
        if (cloneManager != null) {
            cloneManager.removeOwnedBy(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (cloneManager != null) {
            cloneManager.showTo(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTargetMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (controlManager != null && controlManager.isTargetControlled(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onControllerMove(PlayerMoveEvent event) {
        if (controlManager == null) {
            return;
        }
        UUID targetId = controlManager.getTargetForController(event.getPlayer().getUniqueId());
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target != null && event.getTo() != null) {
            Location destination = event.getTo().clone();
            destination.setWorld(target.getWorld());
            target.teleport(destination);
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTargetCommand(PlayerCommandPreprocessEvent event) {
        if (controlManager != null && controlManager.isTargetControlled(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTargetChat(PlayerChatEvent event) {
        if (controlManager != null && controlManager.isTargetControlled(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTargetInteract(PlayerInteractEvent event) {
        if (controlManager != null && controlManager.isTargetControlled(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTargetAttack(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player target = (Player) event.getEntity();
            if (controlManager != null && controlManager.isTargetControlled(target.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onControllerSneak(PlayerToggleSneakEvent event) {
        if (controlManager == null) {
            return;
        }
        UUID targetId = controlManager.getTargetForController(event.getPlayer().getUniqueId());
        if (targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.setSneaking(event.isSneaking());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onControllerSprint(PlayerToggleSprintEvent event) {
        if (controlManager == null) {
            return;
        }
        UUID targetId = controlManager.getTargetForController(event.getPlayer().getUniqueId());
        if (targetId != null) {
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.setSprinting(event.isSprinting());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCloneOwnerMove(PlayerMoveEvent event) {
        if (cloneManager == null) {
            return;
        }
        CloneManager.Clone clone = cloneManager.getByOwner(event.getPlayer().getUniqueId());
        if (clone != null && event.getTo() != null) {
            Location destination = event.getTo().clone();
            destination.setWorld(clone.getEntity().getBukkitEntity().getWorld());
            clone.getEntity().setLocation(destination.getX(), destination.getY(), destination.getZ(), destination.getYaw(), destination.getPitch());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "&e/identity control <player> | release <player> | releaseall | clone <player> [clone-id] | clones | removeclone <clone-id> | removeclones | clonechat <clone-id> <message> | info <player> | reload");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        if (action.equals("control") && args.length == 2) {
            if (!permitted(sender, "identity.control")) {
                return true;
            }
            if (!(sender instanceof Player)) {
                send(sender, "&cOnly an online player can initiate control.");
                return true;
            }
            Player controller = (Player) sender;
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                return message(sender, "player-not-found");
            }
            if (target.getUniqueId().equals(controller.getUniqueId())) {
                send(sender, "&cYou cannot control yourself.");
                return true;
            }
            controlManager.control(controller, target);
            send(sender, configured("control-started").replace("{player}", target.getName()));
            return true;
        }

        if (action.equals("release") && args.length == 2) {
            if (!permitted(sender, "identity.release")) {
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                return message(sender, "player-not-found");
            }
            if (!controlManager.releaseTarget(target.getUniqueId())) {
                send(sender, "&cThat player is not under active control.");
                return true;
            }
            send(sender, configured("control-released").replace("{player}", target.getName()));
            return true;
        }

        if (action.equals("releaseall") && args.length == 1) {
            if (!permitted(sender, "identity.release")) {
                return true;
            }
            send(sender, "&aReleased " + controlManager.releaseAll() + " control session(s). ");
            return true;
        }

        if (action.equals("clone") && (args.length == 2 || args.length == 3)) {
            if (!permitted(sender, "identity.clone")) {
                return true;
            }
            Player source = Bukkit.getPlayerExact(args[1]);
            if (source == null) {
                return message(sender, "player-not-found");
            }
            String id = args.length == 3 ? args[2] : source.getName() + "Clone";
            try {
                UUID ownerId = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
                CloneManager.Clone clone = cloneManager.create(source, id, ownerId);
                send(sender, configured("clone-created").replace("{id}", clone.getId()).replace("{player}", source.getName()));
            } catch (IllegalArgumentException exception) {
                send(sender, "&c" + exception.getMessage());
            }
            return true;
        }

        if (action.equals("clones") && args.length == 1) {
            if (!permitted(sender, "identity.clone")) {
                return true;
            }
            String list = String.join(", ", cloneManager.getIds());
            send(sender, "&eActive clones: &f" + (list.isEmpty() ? "none" : list));
            return true;
        }

        if (action.equals("removeclone") && args.length == 2) {
            if (!permitted(sender, "identity.clone.remove")) {
                return true;
            }
            if (!cloneManager.remove(args[1])) {
                return message(sender, "clone-not-found");
            }
            send(sender, configured("clone-removed").replace("{id}", args[1]));
            return true;
        }

        if (action.equals("removeclones") && args.length == 1) {
            if (!permitted(sender, "identity.clone.remove")) {
                return true;
            }
            send(sender, "&aRemoved " + cloneManager.removeAll() + " clone(s). ");
            return true;
        }

        if (action.equals("clonechat") && args.length >= 3) {
            if (!permitted(sender, "identity.chat")) {
                return true;
            }
            CloneManager.Clone clone = cloneManager.get(args[1]);
            if (clone == null) {
                return message(sender, "clone-not-found");
            }
            if (!getConfig().getBoolean("clone.allow-chat", true)) {
                send(sender, "&cClone chat is disabled.");
                return true;
            }
            String message = join(args, 2);
            Bukkit.broadcastMessage("<" + clone.getSourceName() + "> " + message);
            getLogger().info("[Identity] " + sender.getName() + " -> Clone(" + clone.getId() + "): " + message);
            return true;
        }

        if (action.equals("info") && args.length == 2) {
            if (!permitted(sender, "identity.admin")) {
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                return message(sender, "player-not-found");
            }
            String controller = controlManager.getControllerName(target.getUniqueId());
            send(sender, "&e" + target.getName() + " &7UUID: &f" + target.getUniqueId() + " &7World: &f" + target.getWorld().getName() + " &7Controller: &f" + (controller == null ? "none" : controller));
            return true;
        }

        if (action.equals("reload") && args.length == 1) {
            if (!permitted(sender, "identity.admin")) {
                return true;
            }
            reloadConfig();
            send(sender, configured("reloaded"));
            return true;
        }

        send(sender, "&cUsage: /identity <control|release|releaseall|clone|clones|removeclone|removeclones|clonechat|info|reload>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(args[0], "control", "release", "releaseall", "clone", "clones", "removeclone", "removeclones", "clonechat", "info", "reload");
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("control") || args[0].equalsIgnoreCase("release") || args[0].equalsIgnoreCase("clone") || args[0].equalsIgnoreCase("info")) {
                List<String> names = new ArrayList<String>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return matching(args[1], names.toArray(new String[names.size()]));
            }
            if (args[0].equalsIgnoreCase("removeclone") || args[0].equalsIgnoreCase("clonechat")) {
                List<String> ids = cloneManager.getIds();
                return matching(args[1], ids.toArray(new String[ids.size()]));
            }
        }

        return Collections.emptyList();
    }

    private boolean permitted(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("identity.admin")) {
            return true;
        }
        return message(sender, "no-permission");
    }

    private boolean message(CommandSender sender, String key) {
        send(sender, configured(key));
        return true;
    }

    private String configured(String key) {
        return getConfig().getString("messages." + key, "&cMissing message: " + key);
    }

    private void send(CommandSender sender, String text) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "") + text));
    }

    private List<String> matching(String prefix, String... choices) {
        List<String> matches = new ArrayList<String>();
        for (String choice : choices) {
            if (choice.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                matches.add(choice);
            }
        }
        return matches;
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    void log(String message) {
        if (getConfig().getBoolean("logging.enabled", true)) {
            getLogger().info(message);
        }
    }
}
