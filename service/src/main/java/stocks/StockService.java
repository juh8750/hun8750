package stocks;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import redis.RedisManager;
import redis.clients.jedis.Jedis;
import service.BalanceService;
import service.Contoller;


public class StockService {
    private final StockDAO dao;
    private final Contoller controller; // 주입된 Controller
    private final BalanceService balanceService;
    private StockTickerDisplay tickerDisplay;
    private Map<String, StockDTO> stockCache = new HashMap<>();
    private long lastStockCacheUpdate = 0;
    private Map<String, StockDTO> symbolMap = new HashMap<>();
    private Map<String, StockDTO> nameMap = new HashMap<>();

    public List<StockDTO> getAllStocks() {
        return dao.getAllStocks();
    }

    public StockDTO getStock(String symbol) {
        long now = System.currentTimeMillis();
        if (stockCache.isEmpty() || now - lastStockCacheUpdate > CACHE_EXPIRE_MS) {
             rebuildStockCache(); // 전체 캐시 갱신
        }
        return stockCache.get(symbol.toLowerCase());
    }

    private void rebuildStockCache() {
        List<StockDTO> list = dao.getAllStocks();

        Map<String, StockDTO> symbolTemp = new HashMap<>();
        Map<String, StockDTO> nameTemp = new HashMap<>();

        for (StockDTO stock : list) {
            symbolTemp.put(stock.getSymbol().toLowerCase(), stock); // ✅ 여기도
            nameTemp.put(stock.getName().toLowerCase(), stock);
        }

        stockCache = symbolTemp; // ✅ 이 줄 반드시 추가!
        cachedStocks = list;
        symbolMap = symbolTemp;
        nameMap = nameTemp;
        lastCacheUpdate = System.currentTimeMillis();
    }

    private void ensureCache() {
        long now = System.currentTimeMillis();
        if (cachedStocks.isEmpty() || now - lastCacheUpdate > CACHE_EXPIRE_MS) {
            rebuildStockCache();
        }
    }



    private List<StockDTO> cachedStocks = new ArrayList<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_EXPIRE_MS = 10_000; // 10초


    public void refreshTickerDisplay() {
        if (tickerDisplay != null) {
            tickerDisplay.updateDisplay();
        }
    }

    public StockService(StockDAO dao, Contoller controller, BalanceService balanceService) {
        this.dao = dao;
        this.controller = controller;
        this.balanceService = balanceService;
    }

    public StockDAO getDao() {
        return dao;
    }

    public List<ProfitRankingDTO> getProfitRankingTop10() {
        return dao.getProfitRankingTop10();
    }


    public boolean buyStock(Player player, String symbol, int quantity) {
        UUID uuid = player.getUniqueId();
        StockDTO stock = dao.getStock(symbol);
        if (stock == null || stock.isDelisted()) return false;

        BigDecimal totalCost = stock.getPrice().multiply(BigDecimal.valueOf(quantity));

        if (totalCost.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            player.sendMessage(ChatColor.RED + "총 금액이 너무 큽니다. 수량을 줄여주세요.");
            return false;
        }

        int cost = totalCost.intValue();

        if (!balanceService.hasEnough(uuid, cost)) {
            player.sendMessage(ChatColor.RED + "잔액이 부족합니다.");
            return false;
        }

        balanceService.subtract(uuid, cost);
        controller.updateScoreboard(player);

        boolean success = dao.insertOrUpdatePortfolio(uuid, symbol, quantity, stock.getPrice());
        if (success) {
            player.sendMessage(ChatColor.GREEN + "주식 " + symbol + " " + quantity + "주를 매수했습니다.");
            clearCache();
        }
        return success;
    }

    public boolean sellStock(Player player, String symbol, int quantity) {
        UUID uuid = player.getUniqueId();
        StockPortfolioDTO portfolio = dao.getPortfolioItem(uuid, symbol);
        if (portfolio == null || portfolio.getQuantity() < quantity) return false;

        StockDTO stock = dao.getStock(symbol);
        if (stock == null) return false;

        BigDecimal sellPrice = stock.getPrice();
        BigDecimal avgBuyPrice = portfolio.getAvgPrice();
        BigDecimal revenue = sellPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        if (revenue.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            player.sendMessage(ChatColor.RED + "매도 금액이 너무 커서 처리할 수 없습니다. 수량을 줄여주세요.");
            return false;
        }
        int gain = revenue.intValue();

        BigDecimal profit = sellPrice.subtract(avgBuyPrice).multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRate = avgBuyPrice.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                profit.divide(avgBuyPrice.multiply(BigDecimal.valueOf(quantity)), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

        boolean success = dao.updatePortfolioAfterSell(uuid, symbol, quantity);
        if (!success) return false;

        balanceService.add(uuid, gain);
        controller.updateScoreboard(player);

        // ✅ 매도 로그 저장
        dao.logSell(uuid, player.getName(), symbol, quantity, sellPrice, avgBuyPrice, revenue, profit, profitRate);

        player.sendMessage(ChatColor.YELLOW + "주식 " + symbol + " " + quantity + "주를 매도하여 " + revenue + "원을 받았습니다.");
        clearCache();
        return true;
    }

    public void sendDelistingEmbed(String stockName, String symbol, LocalDateTime now) {
        try {
            URL url = new URL("https://discord.com/api/webhooks/1399713110973087784/kkuJGIi00HBkzktcE9shMGEzmiJ7OWq_uqGlhOvnZD4JTcmN1SJq4-Ga9lBKB8JY68nX");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String jsonPayload = """
        {
          "embeds": [
            {
              "title": "📉 %s %s 상장폐지",
              "description": "해당 종목은 하락으로 인해 상장폐지되었습니다.",
              "color": 15158332
            }
          ]
        }
        """.formatted(timestamp, stockName);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204 && responseCode != 200) {
                System.err.println("[ERROR] Webhook 전송 실패: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    public void simulatePriceChanges() {
        List<StockDTO> stocks = dao.getAllStocks();
        for (StockDTO stock : stocks) {
            BigDecimal currentPrice = stock.getPrice();
            if (currentPrice == null) continue;

            double changePercent = (Math.random() * 0.60) - 0.30;
            BigDecimal changeRate = BigDecimal.valueOf(changePercent * 100).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal newPrice = currentPrice.multiply(BigDecimal.valueOf(1 + changePercent)).setScale(2, BigDecimal.ROUND_HALF_UP);

            // 💥 자동 상장폐지 조건
            if (newPrice.compareTo(BigDecimal.valueOf(100)) <= 0) {
                dao.setDelisted(stock.getSymbol(), true);
                dao.logPriceChange(stock.getSymbol(), currentPrice, newPrice, changeRate);

                String name = stock.getName();
                String symbol = stock.getSymbol();
                String broadcast = "§c[" + name + " (" + symbol + ")] 주식 하락하여 상장폐지되었습니다.";
                sendDelistingEmbed(name, symbol, LocalDateTime.now());

                try (Jedis jedis = RedisManager.getJedis()) {
                    jedis.publish("stockBroadcast", broadcast);

                    List<UUID> holders = dao.getAllPlayersHolding(symbol);
                    for (UUID uuid : holders) {
                        int quantity = dao.getQuantity(uuid, symbol);
                        BigDecimal avgPrice = dao.getAverageBuyPrice(uuid, symbol);
                        BigDecimal revenue = newPrice.multiply(BigDecimal.valueOf(quantity));
                        BigDecimal cost = avgPrice.multiply(BigDecimal.valueOf(quantity));
                        BigDecimal profit = revenue.subtract(cost);
                        BigDecimal profitRate = cost.compareTo(BigDecimal.ZERO) == 0 ?
                                BigDecimal.ZERO :
                                profit.divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

                        String nickname = dao.getNickname(uuid); // 이건 dao에 구현 필요

                        // ✅ 수익 로그 기록
                        dao.logSell(uuid, nickname, symbol, quantity, newPrice, avgPrice, revenue, profit, profitRate);

                        // ✅ 포트폴리오에서 제거
                        dao.removeStockFromPortfolio(uuid, symbol);

                        // ✅ 알림 처리
                        String notifyMessage = "§7[" + name + " (" + symbol + ")] 주식이 상장폐지되어 청산 처리 되었습니다.";

                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            jedis.publish("playerNotify", "offlineNotification|" + uuid + "|" + notifyMessage);
                        } else {
                            dao.logOfflineNotification(uuid, notifyMessage);
                        }
                    }
                }
                continue;
            }

            // 가격 갱신
            dao.updateStockPrice(stock.getSymbol(), newPrice, changeRate);
            dao.logPriceChange(stock.getSymbol(), currentPrice, newPrice, changeRate);
        }

        refreshTickerDisplay(); // 전광판 갱신
        clearCache();
    }

    public void clearCache() {
        cachedStocks.clear();
        lastCacheUpdate = 0;
    }

    public List<StockDTO> getAllStocksCached() {
        long now = System.currentTimeMillis();
        if (cachedStocks.isEmpty() || now - lastCacheUpdate > CACHE_EXPIRE_MS) {
            cachedStocks = dao.getAllStocks();
            lastCacheUpdate = now;
        }
        return cachedStocks;
    }

    public List<StockPortfolioDTO> getPortfolio(Player player) {
        return dao.getPortfolio(player.getUniqueId());
    }

    public StockDTO findStockByNameOrSymbol(String input) {
        long now = System.currentTimeMillis();
        if (cachedStocks.isEmpty() || now - lastCacheUpdate > CACHE_EXPIRE_MS) {
            rebuildStockCache();
        }
        String key = input.toLowerCase();
        StockDTO result = symbolMap.get(key);
        if (result == null) result = nameMap.get(key);
        return result;
    }


    public void updateTickerDisplay(Location location) {
        if (tickerDisplay != null) {
            tickerDisplay.delete();
        }
        tickerDisplay = new StockTickerDisplay(location);
    }

}

