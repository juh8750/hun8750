package DAO;

import ActionHouse.ActionHouseDTO;
import ActionHouse.ActionHouseItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import playerinfo.PlayerDTO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import playerinfo.PlayerInfo;
import redis.RedisManager;
import redis.RedisSubscriber;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import service.Contoller;
import service.Model;

public class DataBase {
    private static Connection conn;
    private String url = "jdbc:mariadb://192.168.0.3:3306/minecraft_db?autoReconnect=true&useSSL=false"; // MariaDB URL
    private String username = "minecraft_user"; // MariaDB 사용자
    private String password = "password123"; // MariaDB 비밀번호
    private Map<UUID, PlayerInfo> playerInfoMap = new HashMap<>();


    ActionHouseDTO actionHouse;
    PlayerDTO playerDTO;
   private Model model;

    public DataBase(ActionHouseDTO actionHouse, PlayerDTO playerDTO) {
        // 생성자
        connect();
        this.playerDTO = playerDTO;
        this.actionHouse = actionHouse;

    }

    public int connect() {

        try {
            Class.forName("org.mariadb.jdbc.Driver");
            conn = DriverManager.getConnection(url, username, password);

            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS player_balances (uuid VARCHAR(36) PRIMARY KEY, balance INT);");
                 PreparedStatement stmt2 = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS auction_items (id INT PRIMARY KEY AUTO_INCREMENT, owner VARCHAR(36), item TEXT, price INT);");
                 PreparedStatement stmt3 = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS shop_items (category VARCHAR(36), item TEXT, price INT);");
                 PreparedStatement stmt4 = conn.prepareStatement("CREATE TABLE IF NOT EXISTS guilds (" +
                         "leader_uuid VARCHAR(36) PRIMARY KEY, " +
                         "guild_name VARCHAR(255), " +
                         "pvp_enabled BOOLEAN" +
                         ");");
                 PreparedStatement stmt5 = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS guildsmembers (" +
                                 "member_uuid VARCHAR(36) PRIMARY KEY, " +
                                 "leader_uuid VARCHAR(36), " +
                                 "position VARCHAR(10)," +
                                 "foreign key (leader_uuid) references guilds(leader_uuid) on delete cascade" +
                                 ");");
                 PreparedStatement stmt6 = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS offline_payments (uuid VARCHAR(36), amount INT);");
                 PreparedStatement stmt7 = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS stocks (" +
                                 "    symbol VARCHAR(16) PRIMARY KEY," +
                                 "    name VARCHAR(64)," +
                                 "    price DECIMAL(15, 2)," +
                                 "    change_rate DECIMAL(5, 2)," +
                                 "    delisted BOOLEAN DEFAULT FALSE," +
                                 "    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP" +
                                 ");");
                 PreparedStatement stmt8 = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS stock_portfolio (" +
                                 "    uuid VARCHAR(36)," +
                                 "    stock_symbol VARCHAR(16)," +
                                 "    quantity INT," +
                                 "    avg_price DECIMAL(15, 2)," +
                                 "    PRIMARY KEY (uuid, stock_symbol)" +
                                 ");");

            ) {
                stmt.execute();
                stmt2.execute();
                stmt3.execute();
                stmt4.execute();
                stmt5.execute();
                stmt6.execute();
                stmt7.execute();
                stmt8.execute();
                return 0; // 정상 연결

            } catch (SQLException e) {
                return 2; // 연결 오류
            }

        } catch (ClassNotFoundException e) {
            return 1; // 데이터베이스 클래스 못찾음
        } catch (SQLException e) {
            return 2; // 데이터베이스 연결 오류
        } catch (Exception e) {
            return 3; //몰라 슈발 오류
        }

    }

    public void disconnect() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }


    // Redis에서 최신 데이터를 가져와 로컬 데이터를 동기화
    public void syncDataFromRedis(UUID playerUUID, Model model) {
        try (Jedis jedis = RedisManager.getJedis()) {
            // 플레이어의 소지금 동기화
            String balanceStr = jedis.hget("player_balances", playerUUID.toString());
            if (balanceStr != null) {
                int balance = Integer.parseInt(balanceStr);
                model.getPlayerBalances().put(playerUUID, balance);
            }

//            // 플레이어의 길드 멤버십 동기화
//            Set<String> guildKeys = jedis.keys("guild:*:members");
//            for (String guildKey : guildKeys) {
//                if (jedis.sismember(guildKey, playerUUID.toString())) {
//                    String[] parts = guildKey.split(":");
//                    if (parts.length >= 2) {
//                        UUID leaderUUID = UUID.fromString(parts[1]);
//                        model.getGuildsMember().put(playerUUID, leaderUUID);
//
//                        // 길드 이름 동기화
//                        String guildName = jedis.hget("guild:" + leaderUUID.toString(), "guild_name");
//                        if (guildName != null) {
//                            model.getGuildNames().put(leaderUUID, guildName);
//                        }
//                    }
//                }
//            }
        } catch (Exception e) {
            System.err.println("[ERROR] Redis에서 데이터 동기화 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public int payOfflineMoney(Player player) {
        UUID playerUUID = player.getUniqueId();
        int allamount = 0;

        try (PreparedStatement stmt = conn.prepareStatement("SELECT amount FROM offline_payments WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int amount = rs.getInt("amount");
                    allamount += amount;
                    player.sendMessage(ChatColor.GREEN + "오프라인 중에 벌어들인 " + amount + "원이 지급되었습니다.");
                }
            }

            removeOfflinePaymentFromDatabase(playerUUID);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Jedis jedis = RedisManager.getJedis()) {
            String redisKey = "offline_payments:" + playerUUID.toString();
            Object redisResult = jedis.del(redisKey); // 반환값 저장
            if (!(redisResult instanceof Long)) { // 안전한 타입 확인
                Bukkit.getLogger().warning("[WARN] Redis 키 삭제 실패. 예상하지 못한 타입: " + redisResult.getClass());
            }
            jedis.publish("serverUpdates", "moneyUpdate|" + playerUUID);
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

        return allamount;
    }



    private boolean removeOfflinePaymentFromDatabase(UUID playerUUID) {
        String sql = "DELETE FROM offline_payments WHERE uuid = ?";
        String redisKey = "offline_payments:" + playerUUID.toString();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            int rowsAffected = stmt.executeUpdate();

            // SQL에서 삭제된 경우 Redis에서도 데이터 삭제
            if (rowsAffected > 0) {
                try (Jedis jedis = RedisManager.getJedis()) {

                    //jedis.del(redisKey); // Redis에서 데이터 삭제

                    jedis.publish("serverUpdates", "offlinePaymentCleared|" + playerUUID);

                    // 서버 동기화 메시지 전송
                }
            }

            return rowsAffected > 0; // 삭제 성공 여부 반환
        } catch (SQLException e) {
            System.err.println("[ERROR] SQL error during offline payment removal: " + e.getMessage());
            e.printStackTrace();
            return false; // 실패 시 false 반환
        } catch (Exception e) {
            System.err.println("[ERROR] Redis error during offline payment removal: " + e.getMessage());
            e.printStackTrace();
            return false; // Redis 작업 실패 시도 동일하게 처리
        }
    }

    public void saveSingleBalance(UUID uuid, int balance) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "REPLACE INTO player_balances (uuid, balance) VALUES (?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB ERROR] balance 저장 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // 소지금 데이터를 일괄 저장하는 메서드 // 이거 모르겠음 아마 초기화 하는거 같은데 흠..
    public synchronized int saveBalanceData(Map<UUID, Integer> playerBalance) {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_balances");
             PreparedStatement insertStmt = conn.prepareStatement(
                     "INSERT INTO player_balances (uuid, balance) VALUES (?, ?)")) {

            // 1. 기존 데이터 삭제
            stmt.executeUpdate();

            // 2. 새로운 데이터 삽입
            for (Map.Entry<UUID, Integer> entry : playerBalance.entrySet()) {
                insertStmt.setString(1, entry.getKey().toString());
                insertStmt.setInt(2, entry.getValue());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();

            // 3. Redis 동기화
            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.del("player_balances");

                for (Map.Entry<UUID, Integer> entry : playerBalance.entrySet()) {
                    jedis.hset("player_balances", entry.getKey().toString(), entry.getValue().toString());

                    // 새로운 잔액을 포함한 메시지 발행
                    jedis.publish("serverUpdates", "moneyUpdate|" + entry.getKey().toString() + "|" + entry.getValue());
                }

            }

        } catch (SQLException e) {
            System.err.println("[ERROR] SQL error during balance data save: " + e.getMessage());
            e.printStackTrace();
            return 1; // SQL 오류 발생
        } catch (Exception e) {
            System.err.println("[ERROR] Redis error during balance data save: " + e.getMessage());
            e.printStackTrace();
            return 2; // Redis 작업 오류 발생
        }

        return 0; // 성공
    }



    // 오프라인 플레이어의 돈 지급을 저장하는 함수
    public int saveOfflinePayment(UUID ownerUUID, long amount) {
        String sql = "INSERT INTO offline_payments (uuid, amount) VALUES (?, ?) ON DUPLICATE KEY UPDATE amount = amount + ?";
        String redisKey = "offline_payments:" + ownerUUID.toString();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // SQL 데이터 삽입 또는 업데이트
            stmt.setString(1, ownerUUID.toString());
            stmt.setLong(2, amount);
            stmt.setLong(3, amount);
            int rowsAffected = stmt.executeUpdate();
            System.out.println("[DEBUG] SQL 업데이트 완료: offline_payments(" + ownerUUID + ", " + amount + "). Rows affected: " + rowsAffected);

            // Redis 데이터 업데이트
            try (Jedis jedis = RedisManager.getJedis()) {
                // Redis 데이터 갱신
                long updatedAmount = jedis.hincrBy(redisKey, "amount", amount);
                System.out.println("[DEBUG] Redis 업데이트 완료: 키=" + redisKey + ", 새로운 값=" + updatedAmount);

                // Redis 동기화 메시지 전송
                jedis.publish("serverUpdates", "offlinePaymentUpdated|" + ownerUUID + "|" + amount);
                System.out.println("[DEBUG] Redis 동기화 메시지 전송 완료: " + "offlinePaymentUpdated|" + ownerUUID + "|" + amount);
            }

        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 오류 발생 (saveOfflinePayment): " + e.getMessage());
            e.printStackTrace();
            return 1; // SQL 오류
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 발생 (saveOfflinePayment): " + e.getMessage());
            e.printStackTrace();
            return 2; // Redis 작업 오류
        }

        return 0; // 성공
    }

    public int removeAuctionItemFromDatabase(int auctionId) {
        String redisKey = "auction:" + auctionId;

        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM auction_items WHERE id = ?")) {
            // SQL 데이터 삭제
            stmt.setInt(1, auctionId);
            int rowsAffected = stmt.executeUpdate();
            System.out.println("[DEBUG] SQL 삭제 완료: auction_items(auctionId=" + auctionId + "). Rows affected: " + rowsAffected);

            // Redis 데이터 삭제 및 동기화
            if (rowsAffected > 0) {
                try (Jedis jedis = RedisManager.getJedis()) {
                    jedis.del(redisKey); // Redis 데이터 삭제
                    System.out.println("[DEBUG] Redis 데이터 삭제 완료: 키=" + redisKey);

                    jedis.publish("serverUpdates", "auctionItemRemoved|" + auctionId); // 동기화 메시지 전송
                    System.out.println("[DEBUG] Redis 동기화 메시지 전송 완료: " + "auctionItemRemoved|" + auctionId);
                }
            } else {
                System.out.println("[DEBUG] SQL에서 데이터가 삭제되지 않았습니다. (auctionId=" + auctionId + ")");
            }

            return 0; // 성공
        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 오류 발생 (removeAuctionItemFromDatabase): " + e.getMessage());
            e.printStackTrace();
            return 1; // SQL 오류
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 발생 (removeAuctionItemFromDatabase): " + e.getMessage());
            e.printStackTrace();
            return 2; // Redis 작업 오류
        }
    }



    // 경매 데이터를 저장하는 메서드
    public synchronized void saveAuctionData(int auctionHouseItemID, ActionHouseItem actionHouseItem) {
        String redisKey = "auction:" + auctionHouseItemID;

        try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO auction_items (id, owner, item, price) VALUES (?, ?, ?, ?)")) {
            // SQL에 데이터 저장
            insertStmt.setInt(1, auctionHouseItemID);
            insertStmt.setString(2, actionHouseItem.getOwner().toString());
            insertStmt.setString(3, serializeItemStack(actionHouseItem.getItem()));
            insertStmt.setInt(4, actionHouseItem.getPrice());
            int rowsAffected = insertStmt.executeUpdate();
            System.out.println("[DEBUG] SQL 경매 데이터 저장 완료: auctionHouseItemID=" + auctionHouseItemID + ", rowsAffected=" + rowsAffected);

            // Redis 캐싱
            try (Jedis jedis = RedisManager.getJedis()) {
                System.out.println("[DEBUG] Redis에 경매 데이터 저장 중: 키=" + redisKey);
                jedis.hset(redisKey, "owner", actionHouseItem.getOwner().toString());
                jedis.hset(redisKey, "item", serializeItemStack(actionHouseItem.getItem()));
                jedis.hset(redisKey, "price", String.valueOf(actionHouseItem.getPrice()));
                System.out.println("[DEBUG] Redis 경매 데이터 저장 완료: 키=" + redisKey);

                // 동기화 메시지 전송
                jedis.publish("serverUpdates", "auctionDataSaved|" + auctionHouseItemID);
                System.out.println("[DEBUG] Redis 동기화 메시지 전송 완료: " + "auctionDataSaved|" + auctionHouseItemID);
            }

        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (saveAuctionData): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (saveAuctionData): " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 경매 아이템 불러오는 함수
    public HashMap<Integer, ActionHouseItem> loadAuctionData() {
        HashMap<Integer, ActionHouseItem> actionHouseItemHashMap = new HashMap<>();

        try (Jedis jedis = RedisManager.getJedis()) {
            Set<String> keys = jedis.keys("auction:*");
            System.out.println("[DEBUG] Redis에서 경매 키 불러오기 완료. 키 개수: " + keys.size());

            for (String key : keys) {
                Map<String, String> data = jedis.hgetAll(key);
                if (data.isEmpty()) continue;

                int id = Integer.parseInt(key.split(":")[1]);
                UUID ownerUUID = UUID.fromString(data.get("owner"));
                ItemStack item = deserializeItemStack(data.get("item"));
                int price = Integer.parseInt(data.get("price"));

                actionHouseItemHashMap.put(id, new ActionHouseItem(ownerUUID, item, price));
            }

            if (actionHouseItemHashMap.isEmpty()) {
                System.out.println("[DEBUG] Redis에 경매 데이터가 없어 SQL에서 불러옵니다.");
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM auction_items");
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        UUID ownerUUID = UUID.fromString(rs.getString("owner"));
                        ItemStack item = deserializeItemStack(rs.getString("item"));
                        int price = rs.getInt("price");

                        actionHouseItemHashMap.put(id, new ActionHouseItem(ownerUUID, item, price));

                        // Redis에 캐싱
                        String redisKey = "auction:" + id;
                        jedis.hset(redisKey, "owner", ownerUUID.toString());
                        jedis.hset(redisKey, "item", rs.getString("item"));
                        jedis.hset(redisKey, "price", String.valueOf(price));
                        System.out.println("[DEBUG] Redis에 경매 데이터 캐싱 완료: 키=" + redisKey);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (loadAuctionData): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (loadAuctionData): " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[DEBUG] 경매 데이터 로드 완료. 총 개수: " + actionHouseItemHashMap.size());
        return actionHouseItemHashMap;
    }

    // 소지금 데이터를 불러오는 함수
    public HashMap<UUID, Integer> getloadBalanceData() {
        HashMap<UUID, Integer> balanceMap = new HashMap<>();

        try (Jedis jedis = RedisManager.getJedis()) {
            Map<String, String> cachedBalances = jedis.hgetAll("player_balances");
            System.out.println("[DEBUG] Redis에서 소지금 데이터 불러오기 완료. 데이터 개수: " + cachedBalances.size());

            if (!cachedBalances.isEmpty()) {
                for (Map.Entry<String, String> entry : cachedBalances.entrySet()) {
                    UUID uuid = UUID.fromString(entry.getKey());
                    int balance = Integer.parseInt(entry.getValue());
                    balanceMap.put(uuid, balance);
                }
                return balanceMap;
            }

            System.out.println("[DEBUG] Redis에 소지금 데이터가 없어 SQL에서 불러옵니다.");
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_balances");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int balance = rs.getInt("balance");
                    balanceMap.put(uuid, balance);

                    jedis.hset("player_balances", uuid.toString(), String.valueOf(balance));
                    System.out.println("[DEBUG] Redis에 소지금 데이터 캐싱 완료: UUID=" + uuid + ", Balance=" + balance);
                }
            }

        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (getloadBalanceData): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (getloadBalanceData): " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[DEBUG] 소지금 데이터 로드 완료. 총 개수: " + balanceMap.size());
        return balanceMap;
    }



    // 상점 아이템 저장 - 보류
//    private synchronized void saveShopItems() {
//        try (PreparedStatement stmt = conn.prepareStatement(
//                "DELETE FROM shop_items");
//             PreparedStatement insertStmt = connection.prepareStatement(
//                     "INSERT INTO shop_items (category, item, price) VALUES (?, ?, ?)")) {
//            stmt.executeUpdate();
//            saveItemsInShop(farmShop, "농장", insertStmt);
//            saveItemsInShop(blockShop, "블록", insertStmt);
//            saveItemsInShop(miscShop, "기타", insertStmt);
//            saveItemsInShop(mineralshop, "광물", insertStmt);
//            insertStmt.executeBatch();
//        } catch (SQLException e) {
//            getLogger().severe("상점 아이템을 저장하는 동안 오류가 발생했습니다: " + e.getMessage());
//        }
//    }

    // 상점 아이템 로드 - 보류
//    private void loadShopItems() {
//        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM shop_items");
//             ResultSet rs = stmt.executeQuery()) {
//            while (rs.next()) {
//                String category = rs.getString("category");
//                ItemStack item = deserializeItemStack(rs.getString("item"));
//                int price = rs.getInt("price");
//
//                ItemMeta meta = item.getItemMeta();
//                meta.setDisplayName(ChatColor.GOLD + item.getType().name() + " - " + price + "원");
//                item.setItemMeta(meta);
//
//                switch (category) {
//                    case "농장":
//                        farmShop.addItem(item);
//                        break;
//                    case "블록":
//                        blockShop.addItem(item);
//                        break;
//                    case "기타":
//                        miscShop.addItem(item);
//                        break;
//                    case "광물":
//                        mineralshop.addItem(item);
//                        break;
//                }
//            }
//        } catch (SQLException e) {
//            getLogger().severe("상점 아이템을 불러오는 동안 오류가 발생했습니다: " + e.getMessage());
//        }
//    }


    // ItemStack 직렬화 함수
    private String serializeItemStack(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("ItemStack 직렬화 실패", e);
        }
    }

    // ItemStack 역직렬화 함수
    private ItemStack deserializeItemStack(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("ItemStack 역직렬화 실패", e);
        }
    }

    /////////////// 길드

//    public String getGuildNameFromDatabase(Player player) {
//        UUID playerUUID = player.getUniqueId();
//
//        String guildName = null;
//        try (PreparedStatement stmt = conn.prepareStatement(
//                "SELECT guild_name FROM guilds WHERE leader_uuid = ? OR members LIKE ?")) {
//            stmt.setString(1, playerUUID.toString());
//            stmt.setString(2, "%" + playerUUID.toString() + "%");
//            try (ResultSet rs = stmt.executeQuery()) {
//                if (rs.next()) {
//                    guildName = rs.getString("guild_name");
//                }
//            }
//        } catch (SQLException e) {
//
//        }
//
//        // 디버그: 데이터베이스에서 가져온 길드 이름 확인
//
//        return guildName;
//    }


    public void updateGuildMembersInDatabase(UUID leaderUUID, UUID membersUUID) {
        String redisKey = "guild:" + leaderUUID + ":members";

        try (PreparedStatement updateStmt = conn.prepareStatement("INSERT INTO guildsmembers (member_uuid, leader_uuid, position) VALUES (?, ?, ?)")) {
            updateStmt.setString(1, membersUUID.toString());
            updateStmt.setString(2, leaderUUID.toString());
            updateStmt.setString(3, "길드원");
            int rowsAffected = updateStmt.executeUpdate();
            System.out.println("[DEBUG] 길드 멤버 DB 업데이트 완료: leaderUUID=" + leaderUUID + ", membersUUID=" + membersUUID + ", rowsAffected=" + rowsAffected);

            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.sadd(redisKey, membersUUID.toString());
                jedis.publish("serverUpdates", "guildMemberUpdated|" + leaderUUID + "|" + membersUUID);
                System.out.println("[DEBUG] 길드 멤버 Redis 업데이트 완료: key=" + redisKey);
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (updateGuildMembersInDatabase): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (updateGuildMembersInDatabase): " + e.getMessage());
        }
    }



    public void deleteGuild(UUID leaderUUID) {
        String redisKey = "guild:" + leaderUUID.toString();
        String redisMembersKey = redisKey + ":members"; // Redis에서 멤버 데이터 키

        try (PreparedStatement deleteMembersStmt = conn.prepareStatement(
                "DELETE FROM guildsmembers WHERE leader_uuid = ? OR member_uuid = ?");
             PreparedStatement deleteGuildStmt = conn.prepareStatement(
                     "DELETE FROM guilds WHERE leader_uuid = ?")) {

            // SQL: 멤버 데이터 삭제
            deleteMembersStmt.setString(1, leaderUUID.toString());
            deleteMembersStmt.setString(2, leaderUUID.toString()); // 길드장이 멤버로 등록된 경우도 처리
            int memberRowsAffected = deleteMembersStmt.executeUpdate();
            System.out.println("[DEBUG] 길드 멤버 삭제 DB 완료: leaderUUID=" + leaderUUID + ", rowsAffected=" + memberRowsAffected);

            // SQL: 길드 데이터 삭제
            deleteGuildStmt.setString(1, leaderUUID.toString());
            int guildRowsAffected = deleteGuildStmt.executeUpdate();
            System.out.println("[DEBUG] 길드 삭제 DB 완료: leaderUUID=" + leaderUUID + ", rowsAffected=" + guildRowsAffected);

            // Redis: 멤버 데이터 및 길드 데이터 삭제
            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.del(redisMembersKey); // 멤버 데이터 삭제
                jedis.del(redisKey); // 길드 데이터 삭제
                jedis.publish("serverUpdates", "guildDeleted|" + leaderUUID); // 동기화 메시지 전송

                System.out.println("[DEBUG] 길드 삭제 Redis 완료: key=" + redisKey);
                System.out.println("[DEBUG] 길드 멤버 삭제 Redis 완료: key=" + redisMembersKey);
            }

        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (deleteGuild): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (deleteGuild): " + e.getMessage());
            e.printStackTrace();
        }
    }



    public boolean createGuild(UUID leaderUUID, String name) {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO guilds (leader_uuid, guild_name, pvp_enabled) VALUES (?, ?, ?)")) {
            stmt.setString(1, leaderUUID.toString());
            stmt.setString(2, name);
            stmt.setBoolean(3, false);
            int rowsAffected = stmt.executeUpdate();

            System.out.println("[DEBUG] 길드 생성 DB 완료: leaderUUID=" + leaderUUID + ", name=" + name + ", rowsAffected=" + rowsAffected);

            if (rowsAffected > 0) {
                // Redis에 길드 데이터 저장 및 동기화 메시지 발행
                String redisKey = "guild:" + leaderUUID;
                try (Jedis jedis = RedisManager.getJedis()) {
                    Map<String, String> guildData = new HashMap<>();
                    guildData.put("guild_name", name);
                    guildData.put("pvp_enabled", "false");
                    jedis.hmset(redisKey, guildData);

                    // 메시지 발행 시 guildName을 포함하도록 수정
                    jedis.publish("serverUpdates", "guildCreated|" + leaderUUID.toString() + "|" + name);

                    System.out.println("[DEBUG] 길드 생성 Redis 완료: key=" + redisKey);
                }
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] 길드 생성 중 SQL 오류: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] 길드 생성 중 Redis 오류: " + e.getMessage());
            return false;
        }
    }



    public void outGuild(UUID memberUUID) {
        try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM guildsmembers WHERE member_uuid = ?")) {
            deleteStmt.setString(1, memberUUID.toString());
            int rowsAffected = deleteStmt.executeUpdate();
            System.out.println("[DEBUG] 길드 멤버 삭제 DB 완료: memberUUID=" + memberUUID + ", rowsAffected=" + rowsAffected);

            try (Jedis jedis = RedisManager.getJedis()) {
                Set<String> guildKeys = jedis.keys("guild:*:members");
                for (String guildKey : guildKeys) {
                    if (jedis.sismember(guildKey, memberUUID.toString())) {
                        jedis.srem(guildKey, memberUUID.toString());
                        jedis.publish("serverUpdates", "guildMemberRemoved|" + guildKey + "|" + memberUUID);
                        System.out.println("[DEBUG] 길드 멤버 삭제 Redis 완료: key=" + guildKey);
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (outGuild): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (outGuild): " + e.getMessage());
        }
    }


    public boolean createGuildMem(UUID leaderUUID) {
        String redisKey = "guild:" + leaderUUID.toString() + ":members";

        try (PreparedStatement createStmtMem = conn.prepareStatement("INSERT INTO guildsmembers (member_uuid, leader_uuid, position) VALUES (?, ?, ?)")) {
            createStmtMem.setString(1, leaderUUID.toString());
            createStmtMem.setString(2, leaderUUID.toString());
            createStmtMem.setString(3, "길드장");
            int rowsAffected = createStmtMem.executeUpdate();
            System.out.println("[DEBUG] 길드 멤버 생성 DB 완료: leaderUUID=" + leaderUUID + ", rowsAffected=" + rowsAffected);

            try (Jedis jedis = RedisManager.getJedis()) {
                // Set 타입으로 길드장 추가
                jedis.sadd(redisKey, leaderUUID.toString());
                jedis.publish("serverUpdates", "guildLeaderCreated|" + leaderUUID);
                System.out.println("[DEBUG] 길드장 생성 Redis 완료: key=" + redisKey);
            }
            return true;
        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (createGuildMem): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (createGuildMem): " + e.getMessage());
        }
        return false;
    }



    public void setPVP(UUID leaderUUID, boolean pvp) {
        String redisKey = "guild:" + leaderUUID.toString();

        try (PreparedStatement createStmt = conn.prepareStatement("UPDATE guilds SET pvp_enabled = ? WHERE leader_uuid = ?")) {
            createStmt.setBoolean(1, pvp);
            createStmt.setString(2, leaderUUID.toString());
            int rowsAffected = createStmt.executeUpdate();
            System.out.println("[DEBUG] PVP 설정 DB 업데이트 완료: leaderUUID=" + leaderUUID + ", pvp=" + pvp + ", rowsAffected=" + rowsAffected);

            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.hset(redisKey, "pvp_enabled", String.valueOf(pvp));
                jedis.publish("serverUpdates", "guildPVPUpdated|" + leaderUUID + "|" + pvp);
                System.out.println("[DEBUG] PVP 설정 Redis 업데이트 완료: key=" + redisKey);
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (setPVP): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (setPVP): " + e.getMessage());
        }
    }


    /////////////// 길드 리더
    public HashMap<UUID, String> getloadGuildsName() {
        HashMap<UUID, String> guildNameMap = new HashMap<>();

        try (Jedis jedis = RedisManager.getJedis()) {
            // Redis에서 모든 길드 이름 불러오기
            Set<String> keys = jedis.keys("guild:*");
            for (String key : keys) {
                // 멤버 키는 스킵
                if (key.endsWith(":members")) {
                    continue;
                }

                String type = jedis.type(key);
                if (!type.equals("hash")) {
                    System.err.println("[ERROR] Redis 키 타입 오류: " + key + " 타입=" + type);
                    continue; // 예상한 타입이 아니면 스킵
                }

                // 키에서 리더 UUID 추출
                String[] keyParts = key.split(":");
                if (keyParts.length < 2) {
                    System.err.println("[ERROR] 잘못된 길드 키 형식: " + key);
                    continue;
                }
                String leaderUUID = keyParts[1];

                String guildName = jedis.hget(key, "guild_name");
                if (guildName != null) {
                    guildNameMap.put(UUID.fromString(leaderUUID), guildName);
                }
            }

            // Redis에 데이터가 없으면 SQL에서 불러오기
            if (guildNameMap.isEmpty()) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT leader_uuid, guild_name FROM guilds");
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID leaderUUID = UUID.fromString(rs.getString(1));
                        String guildName = rs.getString(2);

                        // HashMap에 추가
                        guildNameMap.put(leaderUUID, guildName);

                        // Redis에 캐싱
                        String redisKey = "guild:" + leaderUUID;
                        jedis.hset(redisKey, "guild_name", guildName);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("SQL 오류 발생 (getloadGuildsName): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Redis 작업 중 오류 발생 (getloadGuildsName): " + e.getMessage());
        }

        return guildNameMap;
    }


    public HashMap<UUID, UUID> getloadGuildsMembers() {
        HashMap<UUID, UUID> guildMemberMap = new HashMap<>();

        try (Jedis jedis = RedisManager.getJedis()) {
            Set<String> keys = jedis.keys("guild:*:members");
            System.out.println("[DEBUG] Redis에서 검색된 키 목록: " + keys);

            for (String key : keys) {
                System.out.println("[DEBUG] 처리 중인 Redis 키: " + key);

                String type = jedis.type(key);
                System.out.println("[DEBUG] Redis 키 타입: " + type);

                if ("set".equals(type)) { // 올바른 Set 타입인 경우에만 처리
                    String leaderUUID = key.split(":")[1];
                    System.out.println("[DEBUG] Redis 키 리더 UUID: " + leaderUUID);

                    Set<String> members = jedis.smembers(key);
                    System.out.println("[DEBUG] Redis에서 로드된 멤버 목록: " + members);

                    for (String memberUUID : members) {
                        guildMemberMap.put(UUID.fromString(memberUUID), UUID.fromString(leaderUUID));
                        System.out.println("[DEBUG] 멤버 추가 완료: memberUUID=" + memberUUID + ", leaderUUID=" + leaderUUID);
                    }
                } else {
                    System.err.println("[ERROR] Redis 키 타입 오류: " + key + " 타입=" + type);
                    // 필요 시 Redis와 SQL 동기화
                    synchronizeRedisWithSQL(key, UUID.fromString(key.split(":")[1]));
                }
            }

            if (guildMemberMap.isEmpty()) {
                // Redis에 데이터가 없으면 SQL에서 불러오기
                try (PreparedStatement stmt = conn.prepareStatement("SELECT leader_uuid, member_uuid FROM guildsmembers");
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID leaderUUID = UUID.fromString(rs.getString("leader_uuid"));
                        UUID memberUUID = UUID.fromString(rs.getString("member_uuid"));
                        guildMemberMap.put(memberUUID, leaderUUID);

                        // Redis에 캐싱
                        String redisKey = "guild:" + leaderUUID + ":members";
                        jedis.sadd(redisKey, memberUUID.toString());
                        System.out.println("[DEBUG] Redis에 길드 멤버 데이터 캐싱 완료: key=" + redisKey + ", memberUUID=" + memberUUID);
                    }
                } catch (SQLException e) {
                    System.err.println("SQL 오류 발생 (getloadGuildsMembers): " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (getloadGuildsMembers): " + e.getMessage());
            e.printStackTrace();
        }

        return guildMemberMap;
    }

    // pvp여부
    public HashMap<UUID, Boolean> getloadGuildsPVP() {
        HashMap<UUID, Boolean> guildPvpMap = new HashMap<>();

        try (Jedis jedis = RedisManager.getJedis()) {
            // Redis에서 모든 길드 PVP 데이터 불러오기
            Set<String> keys = jedis.keys("guild:*");
            for (String key : keys) {
                if ("hash".equals(jedis.type(key))) { // 키 타입 확인
                    String leaderUUID = key.split(":")[1];
                    String pvpEnabled = jedis.hget(key, "pvp_enabled");

                    if (pvpEnabled != null) {
                        guildPvpMap.put(UUID.fromString(leaderUUID), Boolean.parseBoolean(pvpEnabled));
                    }
                } else {
                    System.err.println("[ERROR] Redis 키 타입 오류: " + key + " 타입=" + jedis.type(key));
                }
            }

            // Redis에 데이터가 없으면 SQL에서 불러오기
            if (guildPvpMap.isEmpty()) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT leader_uuid, pvp_enabled FROM guilds");
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID leaderUUID = UUID.fromString(rs.getString(1));
                        boolean pvpEnabled = rs.getBoolean(2);

                        // HashMap에 추가
                        guildPvpMap.put(leaderUUID, pvpEnabled);

                        // Redis에 캐싱
                        String redisKey = "guild:" + leaderUUID;
                        jedis.hset(redisKey, "pvp_enabled", String.valueOf(pvpEnabled));
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("SQL 오류 발생 (getloadGuildsPVP): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Redis 작업 중 오류 발생 (getloadGuildsPVP): " + e.getMessage());
        }

        return guildPvpMap;
    }

    public static void synchronizeRedisWithSQL(String redisKey, UUID leaderUUID) {
        System.out.println("[DEBUG] synchronizeRedisWithSQL 호출됨: redisKey=" + redisKey + ", leaderUUID=" + leaderUUID);

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT member_uuid FROM guildsmembers WHERE leader_uuid = ?")) {
            stmt.setString(1, leaderUUID.toString());
            System.out.println("[DEBUG] MariaDB 쿼리 실행: leaderUUID=" + leaderUUID);

            try (ResultSet rs = stmt.executeQuery();
                 Jedis jedis = RedisManager.getJedis()) {
                jedis.del(redisKey); // Redis 키 삭제
                System.out.println("[DEBUG] Redis 키 삭제 완료: " + redisKey);

                while (rs.next()) {
                    String memberUUID = rs.getString("member_uuid");
                    jedis.sadd(redisKey, memberUUID); // Redis에 데이터 추가
                    System.out.println("[DEBUG] Redis에 멤버 추가: redisKey=" + redisKey + ", memberUUID=" + memberUUID);
                }

                // Redis와 SQL 데이터가 일치하는지 검증
                Set<String> redisMembers = jedis.smembers(redisKey);
                System.out.println("[DEBUG] Redis 동기화 검증 완료: redisKey=" + redisKey + ", members=" + redisMembers);
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] SQL 작업 오류 (synchronizeRedisWithSQL): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR] Redis 작업 오류 (synchronizeRedisWithSQL): " + e.getMessage());
        }
    }

    //플레이어 강퇴
    public void kickMember(UUID kickMemberUUID, UUID leaderUUID){
        String redisKey = "guild:" + leaderUUID.toString() + ":members";

        try (PreparedStatement kickMem = conn.prepareStatement("DELETE FROM guildsmembers WHERE member_uuid = ?")) {
            kickMem.setString(1, kickMemberUUID.toString());
            kickMem.executeUpdate();
            System.out.println("오따씨끼끼끼끼끼씨끼씨씼씨씨씨씨씨씨");
            // jedis로 삭제 및 전송
            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.srem(redisKey, kickMemberUUID.toString());

                jedis.publish("serverUpdates", "guildMemberRemoved|" + redisKey + "|" + kickMemberUUID); // 동기화 메시지 전송
                System.out.println("[DEBUG] 길드 맴버 강퇴 Redis 완료: key=" + redisKey);
            }

        }catch (SQLException e) {

        }
    }

    // 부길마
    public HashMap<UUID, Set<UUID>> getGuildsSubLeader(){
        HashMap<UUID, Set<UUID>> subLeaderMap = new HashMap<>();

        try (Jedis jedis = RedisManager.getJedis()) {
            // 서브길마 가져오기
            for (String key : jedis.keys("guild:*:subLeader")) {
                // 길마 정보
                UUID leader = UUID.fromString(key.split(":")[1]);

                for (String subLeaderMember : jedis.smembers(key)) {
                    UUID subLeader = UUID.fromString(subLeaderMember);
                    subLeaderMap.putIfAbsent(leader, new HashSet<>());
                    subLeaderMap.get(leader).add(subLeader); // 서브길마 추가
                }
            }

            if (subLeaderMap.isEmpty()) {
                try (PreparedStatement subLeader = conn.prepareStatement("SELECT leader_uuid, member_uuid FROM guildsmembers WHERE position = ?")) {
                    subLeader.setString(1, "부길마");
                    ResultSet rs = subLeader.executeQuery();
                    while (rs.next()) {
                        UUID leaderUUID = UUID.fromString(rs.getString(1));
                        UUID subLeaderUUID = UUID.fromString(rs.getString(2));

                        subLeaderMap.putIfAbsent(leaderUUID, new HashSet<>());
                        subLeaderMap.get(leaderUUID).add(subLeaderUUID);

                        // Redis에 캐싱
                        String redisKey = "guild:" + leaderUUID + ":subLeader";
                        jedis.sadd(redisKey, subLeaderUUID.toString());
                        System.out.println("[DEBUG] Redis에 길드 부길마 데이터 캐싱: key=" + redisKey + ", memberUUID=" + subLeaderUUID);
                    }
                } catch (SQLException e) {
                    System.err.println("SQL 오류 발생 (getloadGuildsName): " + e.getMessage());
                }
            }
        }

        return subLeaderMap;
    }

    public void updateGuildsSubLeader(UUID leaderUUID, UUID subLeaderUUID, boolean giveSubLeader){
        try(PreparedStatement subLeader = conn.prepareStatement("UPDATE guildsmembers SET position = ? WHERE member_uuid = ?")){
            if(giveSubLeader){
                //길드원 -> 부길마
                subLeader.setString(1, "부길마");
            }else{
                //부길마 -> 길드원
                subLeader.setString(1, "길드원");
            }
            subLeader.setString(2, subLeaderUUID.toString());
            subLeader.executeUpdate(); // 업데이트

            System.out.println("[DEBUG] PVP 설정 DB 업데이트 완료: Member=" + subLeaderUUID + ", 직책=" + (giveSubLeader?"부길마":"길드원"));

            try (Jedis jedis = RedisManager.getJedis()) {
                String redisKey = "guild:" + leaderUUID.toString() + ":subLeader";
                if(giveSubLeader) {
                    jedis.sadd(redisKey, subLeaderUUID.toString());
                    jedis.publish("serverUpdates", "giveSubLeader|" + leaderUUID + "|" + subLeaderUUID);
                    System.out.println("[DEBUG] 길드 직책 설정 완료: key=" + redisKey);
                }else{
                    jedis.srem(redisKey, subLeaderUUID.toString());
                    jedis.publish("serverUpdates", "exitSubLeader|" + leaderUUID + "|" + subLeaderUUID);
                    System.out.println("[DEBUG] 길드 직책 설정 완료: key=" + redisKey);
                }
            }

        }catch (SQLException e){
            System.err.println("SQL 오류 발생 (getloadGuildsName): " + e.getMessage());
        }
    }

//
//    private void saveGuildsToDatabase() {
//        try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM guilds")) {
//            deleteStmt.execute(); // 기존 데이터를 모두 삭제
//            // getLogger().info("기존 길드 데이터가 삭제되었습니다.");
//
//            String insertGuild = "INSERT INTO guilds (leader_uuid, members, guild_name, pvp_enabled) VALUES (?, ?, ?, ?)";
//            try (PreparedStatement insertStmt = conn.prepareStatement(insertGuild)) {
//                for (UUID leaderUUID : guildLeaders.keySet()) {
//                    List<UUID> members = guilds.get(leaderUUID);
//                    StringBuilder membersString = new StringBuilder();
//                    if (members != null) {
//                        for (UUID memberUUID : members) {
//                            if (membersString.length() > 0) {
//                                membersString.append(",");
//                            }
//                            membersString.append(memberUUID.toString());
//                        }
//                    }
//
//                    String guildName = guildNames.get(leaderUUID); // 길드 이름 가져오기
//                    getLogger().info("길드: " + guildName + " 저장 중...");
//
//                    insertStmt.setString(1, leaderUUID.toString());
//                    insertStmt.setString(2, membersString.toString());
//                    insertStmt.setString(3, guildName); // 길드 이름 저장
//                    insertStmt.setBoolean(4, guildPvpSettings.getOrDefault(leaderUUID, false));
//                    insertStmt.executeUpdate();
//
//                    getLogger().info("길드: " + guildName + " 저장 완료.");
//                }
//            }
//        } catch (SQLException e) {
//            getLogger().severe("길드 데이터를 저장하는 동안 오류가 발생했습니다: " + e.getMessage());
//        }
//    }
public Connection getConnection() {
    return this.conn; // 또는 내부 connection 객체
}

}