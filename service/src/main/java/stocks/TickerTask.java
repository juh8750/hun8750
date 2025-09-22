package stocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TickerTask extends BukkitRunnable {
    private final JavaPlugin plugin;
    private Location tickerLocation;

    public TickerTask(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadLocation();
    }

    public void reloadLocation() {
        FileConfiguration config = plugin.getConfig();
        World world = Bukkit.getWorld(config.getString("ticker.world"));
        if (world == null) return;
        int x = config.getInt("ticker.x");
        int y = config.getInt("ticker.y");
        int z = config.getInt("ticker.z");
        tickerLocation = new Location(world, x, y, z);
    }

    @Override
    public void run() {
        if (tickerLocation == null) return;
        // tickerLocation을 이용해 전광판 수정 또는 연결 내용 작성
    }
}