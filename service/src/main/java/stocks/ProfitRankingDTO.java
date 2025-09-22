package stocks;

import java.math.BigDecimal;

public class ProfitRankingDTO {
    private final String playerName;
    private final BigDecimal totalProfit;

    public ProfitRankingDTO(String playerName, BigDecimal totalProfit) {
        this.playerName = playerName;
        this.totalProfit = totalProfit;
    }

    public String getPlayerName() {
        return playerName;
    }

    public BigDecimal getTotalProfit() {
        return totalProfit;
    }
}
