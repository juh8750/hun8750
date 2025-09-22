package stocks;

import java.math.BigDecimal;
import java.util.UUID;

public class StockPortfolioDTO {
    private UUID uuid;
    private String stockSymbol;
    private int quantity;
    private BigDecimal avgPrice;

    public StockPortfolioDTO(UUID uuid, String stockSymbol, int quantity, BigDecimal avgPrice) {
        this.uuid = uuid;
        this.stockSymbol = stockSymbol;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }
}
