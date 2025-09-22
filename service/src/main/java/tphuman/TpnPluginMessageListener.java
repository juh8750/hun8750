package tphuman;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

public class TpnPluginMessageListener implements PluginMessageListener {

    public static final String CHANNEL = "guildchat:tpcoord";
    private final JavaPlugin plugin;

    public TpnPluginMessageListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);

        try {
            String mode = in.readUTF();

            switch (mode) {
                case "COORD" -> {
                    UUID targetUuid = new UUID(in.readLong(), in.readLong());
                    double x = in.readDouble();
                    double y = in.readDouble();
                    double z = in.readDouble();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player target = Bukkit.getPlayer(targetUuid);
                        if (target != null && target.isOnline()) {
                            target.teleport(new Location(target.getWorld(), x, y, z));
                        }
                    });
                }

                case "TP_UUID" -> {
                    UUID targetUuid = new UUID(in.readLong(), in.readLong());
                    UUID senderUuid = new UUID(in.readLong(), in.readLong());
                    tryTeleportLater(senderUuid, targetUuid, 8);
                }

                default -> {
                    plugin.getLogger().warning("Unknown TPN mode: " + mode);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("TPN 메시지 처리 중 오류 발생: " + e.getMessage());
        }
    }


    public void tryTeleportLater(UUID senderUuid, UUID targetUuid, int attemptsLeft) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player sender = Bukkit.getPlayer(senderUuid);
            Player target = Bukkit.getPlayer(targetUuid);

            if (sender != null && target != null && sender.isOnline() && target.isOnline()) {
                if (target.getWorld().equals(sender.getWorld())) {
                    sender.teleport(target.getLocation());
                   // sender.sendMessage(ChatColor.GREEN + "서버 전환 후 텔레포트 완료!");
                } else if (attemptsLeft > 0) {
                    tryTeleportLater(senderUuid, targetUuid, attemptsLeft - 1);
                }
            } else if (attemptsLeft > 0) {
                tryTeleportLater(senderUuid, targetUuid, attemptsLeft - 1);
            }
        }, 20L); // 1초 후 재시도
    }

}
