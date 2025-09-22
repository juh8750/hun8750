package stocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import stocks.TickerTask;

public class TickerCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final TickerTask tickerTask;
    private final StockService stockService;

    public TickerCommand(JavaPlugin plugin, TickerTask tickerTask, StockService stockService) {
        this.plugin = plugin;
        this.tickerTask = tickerTask;
        this.stockService = stockService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (!player.isOp()) {
            player.sendMessage("§cOP만 사용 가능한 명령입니다.");
            return true;
        }

        // 1. 현재 위치를 config에 저장
        Location loc = player.getLocation();
        FileConfiguration config = plugin.getConfig();
        config.set("ticker.world", loc.getWorld().getName());
        config.set("ticker.x", loc.getBlockX());
        config.set("ticker.y", loc.getBlockY());
        config.set("ticker.z", loc.getBlockZ());
        plugin.saveConfig();

        // 2. 위치 갱신 (tickerTask용)
        tickerTask.reloadLocation();

        stockService.updateTickerDisplay(loc);

        player.sendMessage("§a전광판 위치가 설정되었고, 새로 생성되었습니다.");
        return true;
    }

}
