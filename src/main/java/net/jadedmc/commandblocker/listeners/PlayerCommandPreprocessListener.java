/*
 * This file is part of CommandBlocker, licensed under the MIT License.
 *
 *  Copyright (c) JadedMC
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
package net.jadedmc.commandblocker.listeners;

import net.jadedmc.commandblocker.CommandBlockerPlugin;
import net.jadedmc.commandblocker.utils.ChatUtils;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Listens to the PlaceCommandPreprocessEvent, which runs when a player goes to send a command.
 * We use this to block commands set in the config.yml.
 */
public class PlayerCommandPreprocessListener implements Listener {
    private final CommandBlockerPlugin plugin;
    private final ConcurrentMap<UUID, String> originalMessages = new ConcurrentHashMap<>();

    /**
     * Creates the listener.
     * @param plugin Instance of the plugin.
     */
    public PlayerCommandPreprocessListener(@NotNull final CommandBlockerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Captures the original command message before other plugins modify it.
     * Runs at LOWEST priority so replacement plugins (like ChatControl) haven't altered the message yet.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEarlyCommand(@NotNull final PlayerCommandPreprocessEvent event) {
        originalMessages.put(event.getPlayer().getUniqueId(), event.getMessage());
    }

    /**
     * Runs when the event is called.
     * @param event PlaceCommandPreprocessEvent.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(@NotNull final PlayerCommandPreprocessEvent event) {
        final String originalMessage = originalMessages.remove(event.getPlayer().getUniqueId());

        // If the original command (before replacement by other plugins) was in AdditionalCommands,
        // allow it through the blacklist.
        if(originalMessage != null) {
            final String originalFirstWord = originalMessage.split(" ")[0].replaceFirst("/", "").toLowerCase();
            if(plugin.getConfigManager().getAdditionalCommands().contains(originalFirstWord)) {
                return;
            }
        }

        final String mode = plugin.getConfigManager().getConfig().getString("Mode");

        // Don't block commands if
        if(mode == null || mode.equalsIgnoreCase("HIDE")) {
            return;
        }

        // Allow players with bypass permission to use all commands.
        if(event.getPlayer().hasPermission("commandblocker.bypass")) {
            return;
        }

        //  Identify the command being blocked.
        final String command = event.getMessage().split(" ")[0];

        if(mode.equalsIgnoreCase("blacklist")) {
            for(final String blacklist : plugin.getConfigManager().getCommands()) {
                if(command.equalsIgnoreCase(blacklist)) {
                    blockCommand(event);
                    break;
                }

                // Checks for sub commands.
                if(blacklist.contains(" ")) {
                    final String fullCommand = event.getMessage().toLowerCase();

                    if(fullCommand.startsWith(blacklist.toLowerCase())) {
                        if(fullCommand.equalsIgnoreCase(blacklist) || fullCommand.startsWith(blacklist.toLowerCase() + " ")) {
                            blockCommand(event);
                            break;
                        }
                    }
                }
            }
        }
        else if (mode.equalsIgnoreCase("whitelist")) {
            for(String whitelist : plugin.getConfigManager().getCommands()) {
                if(command.equalsIgnoreCase(whitelist)) {
                    return;
                }
            }

            blockCommand(event);
        }
    }

    /**
     * Blocks the command, and displays block notifications.
     * @param event PlayerCommandPreprocessEvent
     */
    private void blockCommand(@NotNull final PlayerCommandPreprocessEvent event) {
        // Prevent the command from being sent.
        event.setCancelled(true);

        // Send the block message if enabled.
        if(plugin.getConfigManager().getConfig().isSet("Message") && !plugin.getConfigManager().getConfig().getString("Message").isEmpty()) {
            ChatUtils.chat(event.getPlayer(), plugin.getConfigManager().getConfig().getString("Message"));
        }

        // Send the block action bar if enabled.
        if(plugin.getConfigManager().getConfig().isSet("ActionBar") && !plugin.getConfigManager().getConfig().getString("ActionBar").isEmpty()) {
            ChatUtils.getAdventure().player(event.getPlayer()).sendActionBar(ChatUtils.translate(plugin.getConfigManager().getConfig().getString("ActionBar")));
        }

        // Play the block sound if enabled.
        final String soundName = plugin.getConfigManager().getConfig().getString("Sound.Sound");
        if(soundName != null && !soundName.isEmpty()) {
            try {
                final Sound sound = Sound.valueOf(soundName);
                final float volume = (float) plugin.getConfigManager().getConfig().getDouble("Sound.Volume");
                final float pitch = (float) plugin.getConfigManager().getConfig().getDouble("Sound.Pitch");

                // Plays the sound.
                event.getPlayer().playSound(event.getPlayer(), sound, volume, pitch);
            } catch (final IllegalArgumentException | NullPointerException exception) {
                plugin.getLogger().warning("Invalid Sound.Sound value in config: '" + soundName + "'");
            }
        }
    }
}
