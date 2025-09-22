package stocks;

import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import service.Service;

import java.math.BigDecimal;
import java.util.List;

public class StockTickerDisplay {
    private final Hologram hologram;

    public StockTickerDisplay(Location location) {
        hologram = HologramsAPI.createHologram(Service.getInstance(), location);
        updateDisplay();
    }

    public void updateDisplay() {
        hologram.clearLines();
        List<StockDTO> stocks = Service.getInstance().getStockService().getAllStocks();

        hologram.appendTextLine(ChatColor.BOLD + "" + ChatColor.GOLD + "📈 실시간 주식 시세 📉");

        for (StockDTO stock : stocks) {
            String symbol = stock.getSymbol();
            String name = stock.getName();

            // ✅ 상장폐지된 종목이면 따로 표시
            if (stock.isDelisted()) {
                String formattedDelisted = ChatColor.DARK_GRAY + symbol + ChatColor.GRAY + " (" + name + ")" + " | " +
                        ChatColor.RED + "❌상장폐지❌ ";
                hologram.appendTextLine(formattedDelisted);
                continue;
            }

            BigDecimal price = stock.getPrice();
            BigDecimal changeRate = stock.getChangeRate();

            String changeColor = changeRate.compareTo(BigDecimal.ZERO) > 0 ? ChatColor.RED.toString()
                    : changeRate.compareTo(BigDecimal.ZERO) < 0 ? ChatColor.BLUE.toString()
                    : ChatColor.GRAY.toString();

            String formatted = ChatColor.YELLOW + symbol + ChatColor.GRAY + " (" + name + ")" + " | " +
                    ChatColor.GREEN + price + "원 " +
                    "(" + changeColor + changeRate + "%" + ChatColor.GRAY + ")";

            hologram.appendTextLine(formatted);
        }
    }


    public void delete() {
        hologram.delete();
    }
}
