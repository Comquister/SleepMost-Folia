package me.mrgeneralq.sleepmost.eventlisteners;

import me.mrgeneralq.sleepmost.interfaces.IBossBarService;
import me.mrgeneralq.sleepmost.interfaces.IMessageService;
import me.mrgeneralq.sleepmost.interfaces.ISleepMostPlayerService;
import me.mrgeneralq.sleepmost.interfaces.IUpdateService;
import me.mrgeneralq.sleepmost.statics.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import com.tcoded.folialib.FoliaLib;

import static me.mrgeneralq.sleepmost.statics.ChatColorUtils.colorize;

public class PlayerJoinEventListener implements Listener {

    private final IMessageService messageService;
    private final IBossBarService bossBarService;
    private final ISleepMostPlayerService sleepMostPlayerService;
    private final IUpdateService updateService;
    private final FoliaLib foliaLib;

    private static final String UPDATE_PERMISSION = "sleepmost.alerts.update";

    public PlayerJoinEventListener(
            IUpdateService updateService,
            IMessageService messageService,
            IBossBarService bossBarService,
            ISleepMostPlayerService sleepMostPlayerService,
            FoliaLib foliaLib // NOVO!
    ) {
        this.updateService = updateService;
        this.messageService = messageService;
        this.bossBarService = bossBarService;
        this.sleepMostPlayerService = sleepMostPlayerService;
        this.foliaLib = foliaLib;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {

        Player player = e.getPlayer();

        if(!this.sleepMostPlayerService.playerExists(player))
            this.sleepMostPlayerService.registerNewPlayer(player);

        if(ServerVersion.CURRENT_VERSION.supportsBossBars())
            this.bossBarService.registerPlayer(player.getWorld(), player);

        if (!player.hasPermission(UPDATE_PERMISSION))
            return;

        // Compatível com Spigot, Paper e Folia
        foliaLib.getScheduler().runAsync(task -> {
            if(updateService.hasUpdate())
                notifyNewUpdate(player);
        });
    }

    private void notifyNewUpdate(CommandSender sender) {
        sender.sendMessage(colorize("&b==============================================="));
        sender.sendMessage(messageService.getMessagePrefixed("&bA newer version of &esleep-most &bis available: &e%updateLink%")
                .setPlaceHolder("%updateLink%", updateService.getCachedUpdateVersion())
                .build());
        sender.sendMessage(ChatColor.GREEN + ServerVersion.UPDATE_URL);
        sender.sendMessage(colorize("&eYou may ignore this message if you just updated (spigot takes some time)"));
        sender.sendMessage(colorize("&b==============================================="));
    }
}
