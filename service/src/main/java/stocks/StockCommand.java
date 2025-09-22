package stocks;

import stocks.StockService;
import stocks.StockDTO;
import stocks.StockPortfolioDTO;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public class StockCommand implements CommandExecutor {
    private final StockService service;

    public StockCommand(StockService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            player.sendMessage("사용법: /주식 [매수|매도|목록|보유] [종목] [수량]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "목록" -> {
                List<StockDTO> stocks = service.getAllStocksCached();
                player.sendMessage("§e[상장된 주식 목록]");
                for (StockDTO stock : stocks) {
                    player.sendMessage(String.format("§a%s §7(%.2f원, 변화율 %.2f%%)%s",
                            stock.getName(), stock.getPrice(), stock.getChangeRate(),
                            stock.isDelisted() ? " §c[상장폐지]" : ""));
                }
            }

            case "매수" -> {
                if (args.length < 3) {
                    player.sendMessage("사용법: /주식 매수 <종목명> <수량>");
                    return true;
                }

                String input = args[1];
                int qty;
                try {
                    qty = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("수량은 숫자여야 합니다.");
                    return true;
                }

                StockDTO stock = service.findStockByNameOrSymbol(input);
                if (stock == null || stock.isDelisted()) {
                    player.sendMessage("해당 종목은 존재하지 않거나 상장폐지되었습니다.");
                    return true;
                }

                boolean success = service.buyStock(player, stock.getSymbol(), qty);
                if (!success) {
                    player.sendMessage("매수에 실패했습니다.");
                }
                return true; //
            }


            case "매도" -> {
                if (args.length < 3) {
                    player.sendMessage("사용법: /주식 매도 <종목명> <수량>");
                    return true;
                }

                String input = args[1];
                int qty;
                try {
                    qty = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("수량은 숫자여야 합니다.");
                    return true;
                }

                StockDTO stock = service.findStockByNameOrSymbol(input);
                if (stock == null) {
                    player.sendMessage("해당 종목을 찾을 수 없습니다.");
                    return true;
                }

                boolean success = service.sellStock(player, stock.getSymbol(), qty);
                if (!success) {
                    player.sendMessage("매도에 실패했습니다. 수량 확인 필요.");
                }
                return true; //
            }

            case "보유" -> {
                List<StockPortfolioDTO> portfolio = service.getPortfolio(player);

                if (portfolio.isEmpty()) {
                    player.sendMessage("보유 중인 주식이 없습니다.");
                    return true;
                }

                player.sendMessage("§e[보유 주식 목록]");
                for (StockPortfolioDTO p : portfolio) {
                    StockDTO stock = service.getStock(p.getStockSymbol());
                    if (stock == null) continue;

                    BigDecimal avg = p.getAvgPrice();
                    BigDecimal current = stock.getPrice();
                    BigDecimal diff = current.subtract(avg).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal percent = diff.multiply(BigDecimal.valueOf(100)).divide(avg, 2, RoundingMode.HALF_UP);
                    BigDecimal totalInvested = avg.multiply(BigDecimal.valueOf(p.getQuantity())).setScale(2, RoundingMode.HALF_UP);

                    String statusColor = diff.compareTo(BigDecimal.ZERO) > 0 ? "§a"
                            : diff.compareTo(BigDecimal.ZERO) < 0 ? "§c" : "§7";
                    String sign = diff.compareTo(BigDecimal.ZERO) > 0 ? "+" : diff.compareTo(BigDecimal.ZERO) < 0 ? "-" : " ";
                    String word = diff.compareTo(BigDecimal.ZERO) > 0 ? "수익"
                            : diff.compareTo(BigDecimal.ZERO) < 0 ? "손실" : "변동 없음";

                    // 첫 줄: 기본 정보
                    player.sendMessage(String.format(
                            "§a%s §7| 수량: %d | 평균단가: %.2f | 현재가: %.2f",
                            stock.getName(), p.getQuantity(), avg, current
                    ));

                    // 둘째 줄: 수익/손실 정보
                    player.sendMessage(String.format(
                            "   %s%s%.2f원 (%.2f%%) %s §7| 총 투자: %.2f원",
                            statusColor, sign, diff.abs(), percent.abs(), word, totalInvested
                    ));
                }

                return true;
            }

            case "수익랭킹" -> {
                List<ProfitRankingDTO> ranking = service.getProfitRankingTop10();

                if (ranking.isEmpty()) {
                    player.sendMessage("§7아직 수익을 낸 플레이어가 없습니다.");
                    return true;
                }

                player.sendMessage("§e[주식 수익 랭킹 TOP 10]");
                int rank = 1;
                for (ProfitRankingDTO dto : ranking) {
                    player.sendMessage(String.format("§f%d위: §a%s §7- §b%.2f원 수익",
                            rank++, dto.getPlayerName(), dto.getTotalProfit()));
                }
                return true;
            }

            default -> {
                player.sendMessage("사용법: /주식 [매수|매도|목록|보유] [종목] [수량]");
                return true;
            }
        }
        return true;
    }
}
