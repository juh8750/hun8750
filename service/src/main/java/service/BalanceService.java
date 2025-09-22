package service;

import org.bukkit.Bukkit;
import redis.RedisManager;
import redis.clients.jedis.Jedis;

import java.util.UUID;

public class BalanceService {
    private final Model model;
    private static final String BALANCE_KEY = "player_balances";
    public BalanceService(Model model) {
        this.model = model;
    }

    public int getBalance(UUID uuid) {
        try (Jedis jedis = RedisManager.getJedis()) {
            String str = jedis.hget(BALANCE_KEY, uuid.toString());  // ✅ 수정됨
            int balance = (str != null) ? Integer.parseInt(str) : 0;
            model.getPlayerBalances().put(uuid, balance);
            return balance;
        }
    }

    public void setBalance(UUID uuid, int newBalance) {
        model.getPlayerBalances().put(uuid, newBalance);

        Bukkit.getScheduler().runTaskAsynchronously(model.getPlugin(), () -> {
            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.hset(BALANCE_KEY, uuid.toString(), String.valueOf(newBalance));  // ✅ 수정됨
                jedis.publish("serverUpdates", "syncData|" + uuid + "|" + newBalance);
                model.getPlayerBalances().put(uuid, newBalance);

                Bukkit.getScheduler().runTaskAsynchronously(model.getPlugin(), () -> {
                    model.getDataBase().saveSingleBalance(uuid, newBalance);
                });
            } catch (Exception e) {
                System.err.println("[Redis ERROR] Balance 저장 실패: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    // 잔액 증가
    public void add(UUID uuid, int amount) {
        int redisBalance = getBalance(uuid); // Redis에서 최신값
        int newBalance = redisBalance + amount;

        // 캐시도 갱신
        model.getPlayerBalances().put(uuid, newBalance);

        // Redis 반영
        try (Jedis jedis = RedisManager.getJedis()) {
            jedis.hset("player_balances", uuid.toString(), String.valueOf(newBalance));
            jedis.publish("serverUpdates", "moneyUpdate|" + uuid + "|" + newBalance);
        }
    }

    // 잔액 차감
    public void subtract(UUID uuid, int amount) {
        int balance = getBalance(uuid);
        setBalance(uuid, balance - amount);
    }

    // 잔액 충분한지 체크
    public boolean hasEnough(UUID uuid, int amount) {
        return getBalance(uuid) >= amount;
    }
}
