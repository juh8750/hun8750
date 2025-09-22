package stocks;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockDTO {
    private String symbol;
    private String name;
    private BigDecimal price;
    private BigDecimal changeRate;
    private boolean delisted;
    private LocalDateTime lastUpdated;

    public StockDTO(String symbol, String name, BigDecimal price, BigDecimal changeRate, boolean delisted, LocalDateTime lastUpdated) {
        this.symbol = symbol;
        this.name = name;
        this.price = price;
        this.changeRate = changeRate;
        this.delisted = delisted;
        this.lastUpdated = lastUpdated;
    }

    // Getters
    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getChangeRate() {
        return changeRate;
    }

    public boolean isDelisted() {
        return delisted;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    // Setters
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setChangeRate(BigDecimal changeRate) {
        this.changeRate = changeRate;
    }

    public void setDelisted(boolean delisted) {
        this.delisted = delisted;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
