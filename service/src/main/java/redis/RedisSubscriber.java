package redis;

import ActionHouse.ActionHouseDTO;
import ActionHouse.ActionHouseItem;
import com.comphenix.protocol.ProtocolLibrary;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import service.Contoller;
import service.Model;
import service.Service;
import service.View;
import service.Contoller;
import java.io.ByteArrayInputStream;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class RedisSubscriber extends Thread {
    private View view; // View 인스턴스 추가
    private Model model; // Model 인스턴스 추가
    private Contoller contoller;
    public static final Map<UUID, String> nicknameCache = new HashMap<>();

    public RedisSubscriber(View view, Model model, Contoller contoller) {
        this.view = view;
        this.model = model;
        this.contoller = contoller;
    }

    public static String getNickname(UUID uuid, String defaultName) {
        return nicknameCache.getOrDefault(uuid, defaultName);
    }

    @Override
    public void run() {
        try (Jedis jedis = new Jedis("192.168.0.3", 6379)) {
            Set<String> keys = jedis.keys("nickname:*");
            for (String key : keys) {
                String uuid = key.substring("nickname:".length());
                String nick = jedis.get(key);
                nicknameCache.put(UUID.fromString(uuid), nick);
            }
            jedis.subscribe(new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    handleMessage(channel, message);
                }
            }, "serverUpdates", "auctionUpdates", "nicknameUpdate", "tpnRequest");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(String channel, String message) {
        if (channel.equals("tpnRequest")) {
            handleTpnRequest(message);
            return;
        }

        String[] parts = message.split("\\|");
        String type = parts[0];

        switch (type) {
            case "moneyUpdate":
                handleMoneyUpdate(parts);
                break;
            case "guildDeleted":
                handleGuildDeleted(parts);
                break;
            case "guildMemberRemoved":
                handleGuildMemberRemoved(parts);
                break;
            case "guildPVPUpdated":
                handleGuildPVPUpdated(parts);
                break;
            case "guildLeaderCreated":
                handleGuildLeaderCreated(parts);
                break;
            case "offlinePaymentUpdated":
                handleOfflinePaymentUpdated(parts);
                break;
            case "auctionItemRemoved":
                handleAuctionItemRemoved(parts);
                break;
            case "guildCreated": // 추가된 메시지 유형
                handleGuildCreated(parts);
                break;
            case "syncData": // 새로운 메시지 유형 추가
                handleSyncData(parts);
                break;
            case "itemBought": // 새로운 메시지 유형 추가
                handleItemBought(parts);
                break;
            case "giveMoney":
                handleGiveMoney(parts);
                break;
            case "auctionItemAdded":
                handleAuctionItemAdded(parts);
                break;
            case "kickMember":
                handleKickMember(parts);
                break;
            case "giveSubLeader":
                handleGiveSubLeader(parts);
                break;
            case "exitSubLeader":
                handleExitSubLeader(parts);
                break;
            case "nicknameUpdate":
                handleNicknameUpdate(parts);
                break;
            case "tpnRequest":
                handleTpnRequest(parts[1]); // message 전체에서 구분 문자 제거해서 넘김
                break;
            default:
                System.out.println("알 수 없는 메시지: " + message);
        }
    }


    private void handleMoneyUpdate(String[] parts) {
        if (parts.length < 3) {
            Bukkit.getLogger().warning("[WARN] 잘못된 메시지 형식: " + Arrays.toString(parts));
            reloadBalancesFromRedis();
            return;
        }
        UUID playerUUID = UUID.fromString(parts[1]);
        int newBalance = Integer.parseInt(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Service.getInstance().getModel().getPlayerBalances().put(playerUUID, newBalance);
        });
    }


    private void reloadBalancesFromRedis() {
        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {

            // Redis에서 모든 잔액을 다시 로드하는 로직
            Map<UUID, Integer> newBalances = Service.getInstance().getDatabase().getloadBalanceData();
            Service.getInstance().getModel().setPlayerBalances((HashMap<UUID, Integer>) newBalances);
        });
    }




    private void handleGuildDeleted(String[] parts) {
        if (parts.length < 2) {
            Bukkit.getLogger().warning("[WARN] 잘못된 길드 삭제 메시지 형식: " + Arrays.toString(parts));
            return;
        }

        UUID leaderUUID = UUID.fromString(parts[1]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Model model = Service.getInstance().getModel();

            // 모델에서 길드 정보 제거
            model.getGuildNames().remove(leaderUUID);
            model.getGuildPvpSettings().remove(leaderUUID);

            // 길드 멤버 목록에서 해당 길드의 멤버 제거
            Iterator<Map.Entry<UUID, UUID>> iterator = model.getGuildsMember().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, UUID> entry = iterator.next();
                if (entry.getValue().equals(leaderUUID)) {
                    iterator.remove();

                    // 해당 플레이어의 스코어보드 업데이트
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        updateScoreboard(player, "길드: 없음");
                    }
                }
            }

            // 길드 리더의 스코어보드 업데이트
            Player leader = Bukkit.getPlayer(leaderUUID);
            if (leader != null && leader.isOnline()) {
                updateScoreboard(leader, "길드: 없음");
            }

            // 디버그 로그 추가
            Bukkit.getLogger().info("[DEBUG] 길드 삭제 처리 완료: leaderUUID=" + leaderUUID);
        });
    }


    private void handleGuildMemberRemoved(String[] parts) {
        String guildKey = parts[1];
        UUID memberUUID = UUID.fromString(parts[2]);
        UUID leaderUUID = model.getGuildsMember().get(memberUUID);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Player player = Bukkit.getPlayer(memberUUID);
            if (player != null && player.isOnline()) {
                updateScoreboard(player, "Guild: 없음");
            }
            if (Bukkit.getPlayer(leaderUUID) != null){
                Service.getInstance().getModel().getGuildsMember().remove(memberUUID);
                view.openGuildMembersMenu(Bukkit.getPlayer(leaderUUID), model.getGuildsMember(), model.getSubLeaders());
            }

        });
    }

    private void handleGuildPVPUpdated(String[] parts) {
        UUID leaderUUID = UUID.fromString(parts[1]);
        boolean pvpEnabled = Boolean.parseBoolean(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getUniqueId().equals(leaderUUID)) {

                }
            }
        });
    }

    private void handleGuildLeaderCreated(String[] parts) {
        UUID leaderUUID = UUID.fromString(parts[1]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Player player = Bukkit.getPlayer(leaderUUID);
            if (player != null && player.isOnline()) {
                updateScoreboard(player, "Guild: Leader");
            }
        });
    }

    private void handleOfflinePaymentUpdated(String[] parts) {
        UUID playerUUID = UUID.fromString(parts[1]);
        int amount = Integer.parseInt(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage("You received an offline payment of " + amount + "!");
            }
        });
    }

    private void handleAuctionItemRemoved(String[] parts) {
        int auctionId = Integer.parseInt(parts[1]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            System.out.println("Auction item removed: ID " + auctionId);
        });
    }

    private void handleGuildCreated(String[] parts) {
        if (parts.length < 3) {
            Bukkit.getLogger().warning("[WARN] 잘못된 길드 생성 메시지 형식: " + Arrays.toString(parts));
            return;
        }
        UUID leaderUUID = UUID.fromString(parts[1]);
        String guildName = parts[2];

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Model model = Service.getInstance().getModel();
            model.getGuildNames().put(leaderUUID, guildName);
            // 길드 리더를 길드 멤버로 추가
            model.getGuildsMember().put(leaderUUID, leaderUUID);

            // 길드 리더의 스코어보드 업데이트
            Player leader = Bukkit.getPlayer(leaderUUID);
            if (leader != null && leader.isOnline()) {
                updateScoreboard(leader, "길드: " + guildName);
                Bukkit.getLogger().info("[DEBUG] 길드 생성 처리 완료: 리더=" + leader.getName() + ", 길드명=" + guildName);
            }
        });
    }

    private void handleItemBought(String[] parts) {
        if (parts.length < 2) {
            Bukkit.getLogger().warning("[WARN] 잘못된 itemBought 메시지 형식: " + Arrays.toString(parts));
            return;
        }

        int auctionId = Integer.parseInt(parts[1]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            // 경매장에서 아이템 제거
            ActionHouseDTO actionHouseDTO = model.getActionHouseDTO();
            actionHouseDTO.removeActionItem(auctionId);

            // 경매장 GUI 업데이트
            updateAllAuctionHouseGUIs();

            // 디버그 로그
            Bukkit.getLogger().info("[DEBUG] 아이템 구매 처리 완료: auctionId=" + auctionId);
        });
    }

    private void updateAllAuctionHouseGUIs() {
        Map<Player, Integer> auctionViewers = view.getAuctionViewers();

        for (Map.Entry<Player, Integer> entry : auctionViewers.entrySet()) {
            Player viewer = entry.getKey();
            int viewerPage = entry.getValue();
            view.updateAuctionHouseGUI(viewer, viewerPage, model);
        }
    }


    private void updateScoreboard(Player player, String guildInfo) {
        String nickname = nicknameCache.getOrDefault(player.getUniqueId(), player.getName());
        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
        }

        Objective objective = board.getObjective("balance");
        if (objective == null) {
            objective = board.registerNewObjective("balance", "dummy", ChatColor.GREEN + "서버 상태");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // 기존 점수 제거
        board.getEntries().forEach(board::resetScores);

        // 새로운 점수 추가
        int balance = Service.getInstance().getModel().getPlayerBalances().getOrDefault(player.getUniqueId(), 0);
        objective.getScore(ChatColor.YELLOW + "닉네임: " + nickname).setScore(3);
        objective.getScore(ChatColor.YELLOW + "소지금: " + balance + "원").setScore(2);
       // objective.getScore(ChatColor.YELLOW + guildInfo).setScore(1);

        // 스코어보드 적용
        player.setScoreboard(board);
    }


    private void handleSyncData(String[] parts) {
        if (parts.length < 3) return;

        UUID playerUUID = UUID.fromString(parts[1]);
        int newBalance = Integer.parseInt(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Model model = Service.getInstance().getModel();
            model.getPlayerBalances().put(playerUUID, newBalance);

            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                contoller.updateScoreboard(player);
            }
        });
    }


    private void handleGiveMoney(String[] parts) {
        if (parts.length < 3) {
            Bukkit.getLogger().warning("[WARN] 잘못된 giveMoney 메시지 형식: " + Arrays.toString(parts));
            return;
        }

        UUID playerUUID = UUID.fromString(parts[1]);
        int amount = Integer.parseInt(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                model.addMoney(player, amount);
                contoller.updateScoreboard(player);
                player.sendMessage(ChatColor.GREEN + "경매에 올린 아이템이 판매되었습니다! " + amount + "원이 지급되었습니다.");
            }
        });
    }

    private void handleAuctionItemAdded(String[] parts) {
        if (parts.length < 5) {
            Bukkit.getLogger().warning("[WARN] 잘못된 auctionItemAdded 메시지 형식: " + Arrays.toString(parts));
            return;
        }

        int auctionId = Integer.parseInt(parts[1]);
        UUID ownerUUID = UUID.fromString(parts[2]);
        int price = Integer.parseInt(parts[3]);
        String serializedItem = parts[4];

        ItemStack item = deserializeItemStack(serializedItem);
        if (item == null) {
            Bukkit.getLogger().warning("[WARN] 아이템 역직렬화 실패: auctionId=" + auctionId);
            return;
        }

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            // 경매 아이템 추가
            ActionHouseItem auctionItem = new ActionHouseItem(ownerUUID, item, price);
            model.getActionHouseDTO().addActionItem(auctionId, auctionItem);

            // 경매장 GUI 업데이트
            updateAllAuctionHouseGUIs();

            // 디버그 로그
            Bukkit.getLogger().info("[DEBUG] 경매 아이템 추가 처리 완료: auctionId=" + auctionId);
        });
    }

    // ItemStack 역직렬화
    public ItemStack deserializeItemStack(String serializedItem) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(serializedItem));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();

            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void handleKickMember(String[] parts) {
        UUID memberUUID = UUID.fromString(parts[1]);
        UUID leaderUUID = UUID.fromString(parts[0].split(":")[1]);
        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            if(Service.getInstance().getModel().getSubLeaders().get(leaderUUID) != null){
                Service.getInstance().getModel().getSubLeaders().get(leaderUUID).remove(memberUUID);
            }
            Service.getInstance().getModel().getGuildsMember().remove(memberUUID);
            Bukkit.getLogger().info("[DEBUG] 길드원 삭제 처리 완료:" + memberUUID.toString());
        });
    }

    //부길마 주기
    private void handleGiveSubLeader(String[] parts){
        UUID leaderUUID = UUID.fromString(parts[1]);
        UUID subLeaderUUID = UUID.fromString(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Service.getInstance().getModel().getSubLeaders().putIfAbsent(leaderUUID, new HashSet<UUID>());
            Service.getInstance().getModel().getSubLeaders().get(leaderUUID).add(subLeaderUUID);
            Bukkit.getLogger().info("[DEBUG] 직책 변경 완료:" + subLeaderUUID + " 길드원 -> 부길마");

            Player player = Bukkit.getPlayer(leaderUUID);
            if(player.isOnline()){
                view.openGuildMembersMenu(player, model.getGuildsMember(), model.getSubLeaders());
            }
        });
    }

    //부길마 뺏기
    private void handleExitSubLeader(String[] parts){
        UUID leaderUUID = UUID.fromString(parts[1]);
        UUID subLeaderUUID = UUID.fromString(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Service.getInstance().getModel().getSubLeaders().get(leaderUUID).remove(subLeaderUUID);
            Bukkit.getLogger().info("[DEBUG] 직책 변경 완료:" + subLeaderUUID + " 부길마 -> 길드원");

            Player player = Bukkit.getPlayer(leaderUUID);
            if(player.isOnline()){
                view.openGuildMembersMenu(player, model.getGuildsMember(), model.getSubLeaders());
            }
        });
    }

    public void handleNicknameUpdate(String[] parts) {
        UUID uuid = UUID.fromString(parts[1]);
        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            try (Jedis jedis = RedisManager.getJedis()) {
                String nickname = jedis.get("nickname:" + uuid.toString());
                Player player = Bukkit.getPlayer(uuid);
                if (nickname != null && player != null && player.isOnline()) {

                    // --- 기존 방식: Bukkit API 적용
                    player.setDisplayName(nickname);
                    player.setPlayerListName(nickname);
                    player.setCustomName(nickname);
                    player.setCustomNameVisible(true);

                    // --- 여기에 추가: GameProfile 리플렉션 방식
                    try {
                        Object craftPlayer = player.getClass().cast(player);
                        Method m = craftPlayer.getClass().getMethod("getProfile");
                        Object profile = m.invoke(craftPlayer);
                        Field nameField = profile.getClass().getDeclaredField("name");
                        nameField.setAccessible(true);
                        nameField.set(profile, nickname);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // hide/show 로 클라이언트에 반영
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        other.hidePlayer(player);
                        other.showPlayer(player);
                    }

                    // 캐시 및 UI 반영
                    nicknameCache.put(uuid, nickname);
                    contoller.updateScoreboard(player);
                    Bukkit.getLogger().info("[DEBUG] 리플렉션 닉네임 변경 완료: " + nickname);
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
    private void handleTpnRequest(String payload) {
        String[] parts = payload.split("\\|");
        if (parts.length != 3) return;

        UUID targetUuid = UUID.fromString(parts[0]);
        String serverName = parts[1];
        UUID senderUuid = UUID.fromString(parts[2]);

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Player target = Bukkit.getPlayer(targetUuid);
            Player sender = Bukkit.getPlayer(senderUuid);

            if (target == null || sender == null) return;
            if (!Bukkit.getServer().getName().equals(serverName)) return;

            sender.teleport(target.getLocation());
            sender.sendMessage(ChatColor.GREEN + "좌표 기반 텔포 완료!");
        });
    }






}