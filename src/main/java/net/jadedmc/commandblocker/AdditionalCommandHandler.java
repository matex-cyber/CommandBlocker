/*
 * This file is part of CommandBlocker, licensed under the MIT License.
 *
 *  Copyright (c) JadedMC
 *  Copyright (c) contributors
 */
package net.jadedmc.commandblocker;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers AdditionalCommands on the server's CommandMap so the client
 * recognizes them in tab-complete and doesn't show "Unknown command" error.
 */
public class AdditionalCommandHandler {
    private static final String FALLBACK_PREFIX = "commandblocker";

    private final CommandBlockerPlugin plugin;
    private final List<AdditionalCommand> registered = new ArrayList<>();
    private final Map<String, Command> displacedCommands = new HashMap<>();
    private final CommandMap commandMap;

    public AdditionalCommandHandler(@NotNull final CommandBlockerPlugin plugin) {
        this.plugin = plugin;
        this.commandMap = resolveCommandMap();
        registerAll();
        refreshOnlinePlayers();
    }

    private CommandMap resolveCommandMap() {
        try {
            final Field field = findField(Bukkit.getServer().getClass(), "commandMap");
            if(field == null) {
                plugin.getLogger().warning("Could not locate commandMap field. AdditionalCommands will not work.");
                return null;
            }
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (final IllegalAccessException exception) {
            plugin.getLogger().warning("Could not access CommandMap: " + exception.getMessage());
            return null;
        }
    }

    private void registerAll() {
        if(commandMap == null) return;
        for(final String name : plugin.getConfigManager().getAdditionalCommands()) {
            registerCommand(name);
        }
    }

    private void registerCommand(@NotNull final String rawName) {
        final String name = rawName.toLowerCase().trim();
        if(name.isEmpty()) return;

        final AdditionalCommand cmd = new AdditionalCommand(name);
        commandMap.register(FALLBACK_PREFIX, cmd);

        // If another plugin (or Bukkit itself) already owns this name, the register()
        // call above only bound the "commandblocker:<name>" fallback. Force the primary
        // name to point at our no-op so the client actually sees it in tab-complete
        // (otherwise HideColonCommands: true would also hide the colon variant).
        try {
            final Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            if(knownCommandsField != null) {
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                final Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

                final Command existing = knownCommands.get(name);
                if(existing != cmd) {
                    if(existing != null && !displacedCommands.containsKey(name)) {
                        displacedCommands.put(name, existing);
                    }
                    knownCommands.put(name, cmd);
                    cmd.setLabel(name);
                }
            }
        } catch (final IllegalAccessException exception) {
            plugin.getLogger().warning("Could not force-register AdditionalCommand '" + name + "': " + exception.getMessage());
        }

        registered.add(cmd);
    }

    private void unregisterAll() {
        if(commandMap == null || registered.isEmpty()) return;

        try {
            final Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            if(knownCommandsField == null) {
                plugin.getLogger().warning("Could not find knownCommands field. AdditionalCommands may linger until restart.");
                return;
            }
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            for(final AdditionalCommand cmd : registered) {
                final String name = cmd.getName();
                if(knownCommands.get(name) == cmd) {
                    knownCommands.remove(name);
                }
                if(knownCommands.get(FALLBACK_PREFIX + ":" + name) == cmd) {
                    knownCommands.remove(FALLBACK_PREFIX + ":" + name);
                }
                cmd.unregister(commandMap);
            }
            registered.clear();

            // Restore any commands we displaced so they work again after reload/shutdown.
            for(final Map.Entry<String, Command> entry : displacedCommands.entrySet()) {
                if(!knownCommands.containsKey(entry.getKey())) {
                    knownCommands.put(entry.getKey(), entry.getValue());
                }
            }
            displacedCommands.clear();
        } catch (final IllegalAccessException exception) {
            plugin.getLogger().warning("Could not unregister AdditionalCommands: " + exception.getMessage());
        }
    }

    private void refreshOnlinePlayers() {
        for(final Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    public void reload() {
        unregisterAll();
        registerAll();
        refreshOnlinePlayers();
    }

    public void shutdown() {
        unregisterAll();
    }

    private Field findField(@NotNull Class<?> type, @NotNull final String name) {
        while(type != null && type != Object.class) {
            try {
                return type.getDeclaredField(name);
            } catch (final NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Marker subclass so we can reliably identify commands registered by this plugin.
     */
    private static final class AdditionalCommand extends Command {
        private AdditionalCommand(@NotNull final String name) {
            super(name);
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            return true;
        }
    }
}
