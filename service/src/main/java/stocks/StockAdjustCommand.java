package stocks;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.RedisManager;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;

public class StockAdjustCommand implements CommandExecutor {

    private final StockService stockService;
    private final StockDAO dao;
    private final StockTickerDisplay Stocktickerdisplay;

    public StockAdjustCommand(StockService stockService, StockDAO dao, StockTickerDisplay Stocktickerdisplay) {
        this.stockService = stockService;
        this.dao = dao;
        this.Stocktickerdisplay = Stocktickerdisplay;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (!player.isOp()) {
            player.sendMessage("§cOP만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage("§e사용법: /주식조정 <종목> <등락률>");
            return true;
        }

        String symbol = args[0].toUpperCase();
        BigDecimal changeRate;

        try {
            changeRate = new BigDecimal(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c등락률은 숫자로 입력해야 합니다.");
            return true;
        }

        StockDTO stock = stockService.getStock(symbol);
        if (stock == null) {
            player.sendMessage("§c해당 종목을 찾을 수 없습니다.");
            return true;
        }

        BigDecimal currentPrice = stock.getPrice();
        if (currentPrice == null) {
            player.sendMessage("§c현재 가격 정보를 불러올 수 없습니다.");
            return true;
        }

        // 가격 계산: 현재 가격 * (1 + 등락률 / 100)
        BigDecimal newPrice = currentPrice.multiply(BigDecimal.valueOf(1 + changeRate.doubleValue() / 100))
                .setScale(2, BigDecimal.ROUND_HALF_UP);

        // ✅ 100원 이하일 경우 강제 상장폐지 처리
        if (newPrice.compareTo(BigDecimal.valueOf(100)) <= 0) {
            dao.setDelisted(symbol, true);
            dao.logPriceChange(symbol, currentPrice, newPrice, changeRate);

            String name = stock.getName();
            String message = "§c[" + name + " (" + symbol + ")] 주식이 100원 이하로 하락하여 상장폐지되었습니다.";
            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.publish("stockBroadcast", message);
            }

            // 캐시에도 반영
            stock.setPrice(newPrice);
            stock.setChangeRate(changeRate);
            stock.setDelisted(true);

            stockService.refreshTickerDisplay();
            player.sendMessage("§c[" + symbol + "] 종목이 가격 조정으로 인해 상장폐지 처리되었습니다.");
            return true;
        }

        dao.updateStockPrice(symbol, newPrice, changeRate);
        dao.logPriceChange(symbol, currentPrice, newPrice, changeRate);

        // ✅ 캐시된 객체도 직접 갱신해줘야 전광판에 바로 반영됨
        stock.setPrice(newPrice);
        stock.setChangeRate(changeRate);

        stockService.refreshTickerDisplay();

        player.sendMessage("§a" + symbol + " 주식이 등락률 " + changeRate + "%로 조정되어, 가격이 " + newPrice + "원이 되었습니다.");
        return true;
    }
}