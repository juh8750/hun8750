package stocks;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import stocks.StockDTO;

public class StockDAO {

    private final Connection conn;

    public StockDAO(Connection conn) {
        this.conn = conn;
    }

    // ✅ getAllStocks
    public List<StockDTO> getAllStocks() {
        List<StockDTO> stocks = new ArrayList<>();
        String sql = "SELECT * FROM stocks WHERE delisted = FALSE";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                stocks.add(new StockDTO(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("change_rate"),
                        rs.getBoolean("delisted"),
                        rs.getTimestamp("last_updated").toLocalDateTime()
                ));
            }

        } catch (SQLException e) {
            System.err.println("[ERROR] getAllStocks 실패: " + e.getMessage());
        }

        return stocks;
    }

    // ✅ getStock
    public StockDTO getStock(String symbol) {
        String sql = "SELECT * FROM stocks WHERE symbol = ? AND delisted = FALSE";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new StockDTO(
                            rs.getString("symbol"),
                            rs.getString("name"),
                            rs.getBigDecimal("price"),
                            rs.getBigDecimal("change_rate"),
                            rs.getBoolean("delisted"),
                            rs.getTimestamp("last_updated").toLocalDateTime()
                    );
                }
            }

        } catch (SQLException e) {
            System.err.println("[ERROR] getStock 실패: " + e.getMessage());
        }

        return null;
    }

    public void logSell(UUID uuid, String playerName, String symbol, int quantity,
                        BigDecimal sellPrice, BigDecimal avgBuyPrice,
                        BigDecimal totalRevenue, BigDecimal profit, BigDecimal profitRate) {
        String sql = "INSERT INTO stock_sell_log (uuid, player_name, stock_symbol, quantity, sell_price, avg_buy_price, total_revenue, profit, profit_rate) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, symbol);
            ps.setInt(4, quantity);
            ps.setBigDecimal(5, sellPrice);
            ps.setBigDecimal(6, avgBuyPrice);
            ps.setBigDecimal(7, totalRevenue);
            ps.setBigDecimal(8, profit);
            ps.setBigDecimal(9, profitRate);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    // ✅ updateStockPrice
    public void updateStockPrice(String symbol, BigDecimal newPrice, BigDecimal changeRate) {
        String sql = "UPDATE stocks SET price = ?, change_rate = ?, last_updated = NOW() WHERE symbol = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, newPrice);
            stmt.setBigDecimal(2, changeRate);
            stmt.setString(3, symbol);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ERROR] updateStockPrice 실패: " + e.getMessage());
        }

    }

    // ✅ getPortfolio
    public List<StockPortfolioDTO> getPortfolio(UUID uuid) {
        List<StockPortfolioDTO> portfolio = new ArrayList<>();
        String sql = "SELECT * FROM stock_portfolio WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    portfolio.add(new StockPortfolioDTO(
                            uuid,
                            rs.getString("stock_symbol"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("avg_price")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getPortfolio 실패: " + e.getMessage());
        }

        return portfolio;
    }

    // ✅ getPortfolioItem
    public StockPortfolioDTO getPortfolioItem(UUID uuid, String symbol) {
        String sql = "SELECT * FROM stock_portfolio WHERE uuid = ? AND stock_symbol = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, symbol);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new StockPortfolioDTO(
                            uuid,
                            rs.getString("stock_symbol"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("avg_price")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getPortfolioItem 실패: " + e.getMessage());
        }

        return null;
    }

    // ✅ insertPortfolio
    public boolean insertPortfolio(UUID uuid, String symbol, int qty, BigDecimal avgPrice) {
        String sql = "INSERT INTO stock_portfolio (uuid, stock_symbol, quantity, avg_price) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, symbol);
            stmt.setInt(3, qty);
            stmt.setBigDecimal(4, avgPrice);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[ERROR] insertPortfolio 실패: " + e.getMessage());
        }

        return false;
    }

    // ✅ updatePortfolio
    public boolean updatePortfolio(UUID uuid, String symbol, int qty, BigDecimal avgPrice) {
        String sql = "UPDATE stock_portfolio SET quantity = ?, avg_price = ? WHERE uuid = ? AND stock_symbol = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, qty);
            stmt.setBigDecimal(2, avgPrice);
            stmt.setString(3, uuid.toString());
            stmt.setString(4, symbol);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[ERROR] updatePortfolio 실패: " + e.getMessage());
        }

        return false;
    }

    // ✅ deletePortfolio
    public boolean deletePortfolio(UUID uuid, String symbol) {
        String sql = "DELETE FROM stock_portfolio WHERE uuid = ? AND stock_symbol = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, symbol);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ERROR] deletePortfolio 실패: " + e.getMessage());
        }

        return false;
    }

    // ✅ markAsDelisted
    public boolean markAsDelisted(String symbol) {
        String sql = "UPDATE stocks SET delisted = TRUE, delisted_at = NOW() WHERE symbol = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ERROR] markAsDelisted 실패: " + e.getMessage());
        }

        return false;
    }


    public void setDelisted(String symbol, boolean delisted) {
        String sql = delisted ?
                "UPDATE stocks SET delisted = TRUE, delisted_at = NOW() WHERE symbol = ?" :
                "UPDATE stocks SET delisted = FALSE, delisted_at = NULL WHERE symbol = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getDelistedStocksOlderThan(Duration duration) {
        List<String> symbols = new ArrayList<>();
        String sql = "SELECT symbol FROM stocks WHERE delisted = TRUE AND delisted_at <= ?";

        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(duration));

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, cutoff);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    symbols.add(rs.getString("symbol"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getDelistedStocksOlderThan 실패: " + e.getMessage());
        }

        return symbols;
    }

    // ✅ 가격 변동 로그 저장
    public void logPriceChange(String symbol, BigDecimal oldPrice, BigDecimal newPrice, BigDecimal changeRate) {
        String sql = "INSERT INTO stock_price_log (symbol, old_price, new_price, change_rate) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, symbol);
            stmt.setBigDecimal(2, oldPrice);
            stmt.setBigDecimal(3, newPrice);
            stmt.setBigDecimal(4, changeRate);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ERROR] 가격 로그 저장 실패: " + e.getMessage());
        }
    }
    public boolean insertOrUpdatePortfolio(UUID uuid, String symbol, int quantity, BigDecimal pricePerStock) {
        StockPortfolioDTO existing = getPortfolioItem(uuid, symbol);
        if (existing == null) {
            return insertPortfolio(uuid, symbol, quantity, pricePerStock);
        } else {
            int newQty = existing.getQuantity() + quantity;
            BigDecimal newAvg = (
                    existing.getAvgPrice().multiply(BigDecimal.valueOf(existing.getQuantity()))
                            .add(pricePerStock.multiply(BigDecimal.valueOf(quantity)))
            ).divide(BigDecimal.valueOf(newQty), 2, BigDecimal.ROUND_HALF_UP);

            return updatePortfolio(uuid, symbol, newQty, newAvg);
        }
    }
    public boolean updatePortfolioAfterSell(UUID uuid, String symbol, int quantity) {
        StockPortfolioDTO existing = getPortfolioItem(uuid, symbol);
        if (existing == null || existing.getQuantity() < quantity) return false;

        int newQty = existing.getQuantity() - quantity;
        if (newQty == 0) {
            return deletePortfolio(uuid, symbol);
        } else {
            return updatePortfolio(uuid, symbol, newQty, existing.getAvgPrice());
        }
    }

    public BigDecimal getAverageBuyPrice(UUID uuid, String symbol) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT avg_price FROM stock_portfolio WHERE uuid = ? AND stock_symbol = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, symbol);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBigDecimal("avg_price");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }


    public int getQuantity(UUID uuid, String symbol) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT quantity FROM stock_portfolio WHERE uuid = ? AND stock_symbol = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, symbol);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("quantity");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }


    public String getNickname(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT nickname FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("nickname");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }


    public List<ProfitRankingDTO> getProfitRankingTop10() {
        List<ProfitRankingDTO> ranking = new ArrayList<>();
        String sql = "SELECT player_name, SUM(profit) AS total_profit " +
                "FROM stock_sell_log GROUP BY player_name " +
                "ORDER BY total_profit DESC LIMIT 10";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String playerName = rs.getString("player_name");
                BigDecimal totalProfit = rs.getBigDecimal("total_profit");
                ranking.add(new ProfitRankingDTO(playerName, totalProfit));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ranking;
    }


    public String getNameBySymbol(String symbol) {
        String sql = "SELECT name FROM stocks WHERE symbol = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return symbol; // 실패 시 symbol 반환
    }

    public List<UUID> getAllPlayersHolding(String symbol) {
        List<UUID> holders = new ArrayList<>();
        String sql = "SELECT uuid FROM stock_portfolio WHERE stock_symbol = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                holders.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return holders;
    }


    public void removeStockFromPortfolio(UUID uuid, String symbol) {
        String sql = "DELETE FROM stock_portfolio WHERE uuid = ? AND stock_symbol = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, symbol);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void logOfflineNotification(UUID uuid, String message) {
        String sql = "INSERT INTO stock_notifications (uuid, message) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getPendingNotifications(UUID uuid) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT message FROM stock_notifications WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(rs.getString("message"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public void clearPendingNotifications(UUID uuid) {
        String sql = "DELETE FROM stock_notifications WHERE uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}

