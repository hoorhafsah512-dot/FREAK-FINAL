package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Intercepts chat to:
 *  1. Drive the first-login setup wizard.
 *  2. Format chat with nation prefix.
 *  3. Detect the oath command (/I BEAR OATH TO ...) typed in chat by mistake.
 */
public class PlayerChatListener implements Listener {

    private final NationsSMP plugin;

    public PlayerChatListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        Nation nation = plugin.getNationManager().getNation(player.getUniqueId());

        // ── Setup flow intercept ──────────────────────────────────────────────
        if (nation != null && !nation.isSetupComplete()) {
            boolean consumed = plugin.getNationManager().handleSetupInput(player, message);
            if (consumed) {
                event.setCancelled(true);
                return;
            }
        }

        // ── Oath command typed in chat without slash ───────────────────────────
        if (message.toUpperCase().startsWith("I BEAR OATH TO ")) {
            event.setCancelled(true);
            String args = message.substring("I BEAR OATH TO ".length()).trim();
            // Run sync because OathManager interacts with Bukkit API
            Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getOathManager().processOathCommand(player, args));
            return;
        }

        // ── Format chat with nation prefix ────────────────────────────────────
        if (nation != null && nation.isSetupComplete()) {
            String prefix = nation.getChatPrefix();
            String title  = nation.getTitle() != null ? nation.getTitle() : "";
            boolean oathbreaker = nation.isOathbreaker();

            String nameColor = oathbreaker
                ? ChatColor.DARK_RED.toString()
                : ChatColor.YELLOW.toString();

            String formatted = ChatColor.GRAY + prefix + " "
                + nameColor + player.getName()
                + ChatColor.DARK_GRAY + " [" + title + "]"
                + ChatColor.WHITE + ": " + message;

            event.setFormat(formatted);
        }
    }
}
