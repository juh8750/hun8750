package stocks;

import org.bukkit.Bukkit;
import redis.RedisManager;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

public class DelistRecoveryTask implements Runnable {
    private final StockDAO dao;
    private final StockService stockService;

    public DelistRecoveryTask(StockDAO dao, StockService stockService) {
        this.dao = dao;
        this.stockService = stockService;
    }

    @Override
    public void run() {
        try {
            List<String> symbolsToRecover = dao.getDelistedStocksOlderThan(Duration.ofMinutes(30));
            for (String symbol : symbolsToRecover) {

                double randomPrice = 10000 + Math.random() * 40000; // 10000 ~ 49999.999...
                BigDecimal newPrice = BigDecimal.valueOf(randomPrice).setScale(2, RoundingMode.HALF_UP);

                // ✅ 2. 가격 업데이트 및 상장 상태 복구
                dao.updateStockPrice(symbol, newPrice, BigDecimal.ZERO);
                dao.setDelisted(symbol, false);

                // ✅ 3. 이름 가져와서 알림
                String name = dao.getNameBySymbol(symbol);
                String message = "§a[" + name + " (" + symbol + ")] 주식이 상장폐지 해제되어 " + newPrice + "원으로 다시 상장되었습니다!";
                try (Jedis jedis = RedisManager.getJedis()) {
                    jedis.publish("stockBroadcast", message);
                }
            }

            // ✅ 4. 전광판 갱신
            stockService.refreshTickerDisplay();

        } catch (Exception e) {
            System.err.println("[ERROR] 상장 복구 스케줄러 오류: " + e.getMessage());
        }
    }
}
