package service;

import ActionHouse.ActionHouseDTO;
import ActionHouse.ActionHouseItem;
import DAO.DataBase;
import Guild.GuildDTO;
import Store.StoreDTO;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import playerinfo.PlayerDTO;
import redis.RedisManager;
import redis.clients.jedis.Jedis;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;

public class Model {
    private HashMap<UUID, UUID> tradeRequests = new HashMap<>(); // 플레이어 거래
    private Map<UUID, Integer> playerBalances = new ConcurrentHashMap<>(); // 플레이어들 소지금
    private Map<UUID, Scoreboard> playerScoreboards = new HashMap<>(); // 플레이어들 스코어보드
    private HashMap<UUID, String> guildNames; // 길드 이름을 저장할 HashMap 추가
    private HashMap<UUID, UUID> guildsMember; // 길드 맴버
    private HashMap<UUID, Boolean> guildPvpSettings;
    private HashMap<UUID, UUID> invitedGuild = new HashMap<>();
    private HashSet<UUID> createGuild = new HashSet<>();
    private PlayerDTO playerDTO = new PlayerDTO();
    private StoreDTO storeDTO = new StoreDTO();
    private ActionHouseDTO actionHouseDTO;
    private HashMap<UUID, Set<UUID>> subLeaders = new HashMap<>(); // 부길마
    private final JavaPlugin plugin;

    private DataBase dataBase;

    public Model(JavaPlugin plugin){
        dataBase = new DataBase(actionHouseDTO,playerDTO);

        // 소지금 로드
        playerBalances = dataBase.getloadBalanceData();

        // 길드 관련 로드
        guildNames = dataBase.getloadGuildsName();
        guildsMember = dataBase.getloadGuildsMembers();
        guildPvpSettings = dataBase.getloadGuildsPVP();

        actionHouseDTO = new ActionHouseDTO(dataBase);
        subLeaders = dataBase.getGuildsSubLeader();
        this.plugin = plugin;
    }

    public HashMap<UUID, Set<UUID>> getSubLeaders() {
        return subLeaders;
    }

    public PlayerDTO getPlayerDTO() {
        return playerDTO;
    }

    public ActionHouseDTO getActionHouseDTO() {
        return actionHouseDTO;
    }

    public HashSet<UUID> getCreateGuild() {
        return createGuild;
    }

    public HashMap<UUID, Boolean> getGuildPvpSettings() {
        return guildPvpSettings;
    }

    public HashMap<UUID, UUID> getInvitedGuild() {
        return invitedGuild;
    }

    public HashMap<UUID, UUID> getGuildsMember() {
        return guildsMember;
    }

    public HashMap<UUID, String> getGuildNames() {
        return guildNames;
    }

    public Map<UUID, Scoreboard> getPlayerScoreboards() {
        return playerScoreboards;
    }

    public HashMap<UUID, UUID> getTradeRequests() {
        return tradeRequests;
    }

    public void setTradeRequests(HashMap<UUID, UUID> tradeRequests) {
        this.tradeRequests = tradeRequests;
    }

    public void addTradeRequest(UUID uuid, UUID tradeRequest) {
        tradeRequests.put(uuid, tradeRequest);
    }

    public HashMap<UUID, Integer> getPlayerBalances() {
        return new HashMap<>(playerBalances); // 복사본으로 반환
    }

    public JavaPlugin getPlugin() {
        return this.plugin;
    }

    public DataBase getDataBase() {
        return dataBase;
    }

    public void addMoney(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int newBalance = playerBalances.getOrDefault(uuid, 0) + amount;

        // 메모리 반영 (즉시)
        playerBalances.put(uuid, newBalance);

        // DB & Redis 비동기 처리
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dataBase.saveSingleBalance(uuid, newBalance); // ✅ 개선된 단일 저장 메서드 사용

            try (Jedis jedis = RedisManager.getJedis()) {
                String redisKey = "player_data:" + uuid;
                jedis.hset(redisKey, "balance", String.valueOf(newBalance));
                jedis.publish("serverUpdates", "syncData|" + uuid + "|" + newBalance);
            } catch (Exception e) {
                System.err.println("[Redis ERROR] " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // playerBalances의 setter 추가
    public void setPlayerBalances(HashMap<UUID, Integer> newBalances) {
        this.playerBalances = newBalances;
    }

    public void minusMoney(Player player, int amount){
        UUID uuid = player.getUniqueId();
        int newBalance = playerBalances.getOrDefault(uuid, 0) - amount;
        playerBalances.put(uuid, newBalance);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dataBase.saveSingleBalance(uuid, newBalance);

            try (Jedis jedis = RedisManager.getJedis()) {
                String redisKey = "player_data:" + uuid;
                jedis.hset(redisKey, "balance", String.valueOf(newBalance));
                jedis.publish("serverUpdates", "syncData|" + uuid + "|" + newBalance);
            } catch (Exception e) {
                System.err.println("[Redis ERROR] " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void addMoneyOffline(UUID uuid,long amount){
        dataBase.saveOfflinePayment(uuid, amount);
    }

    public void checkPayOfflineMoney(Player player) {
        addMoney(player, dataBase.payOfflineMoney(player));
    }

    // 길드정보 가져오기
    public void getGuildInfo(){
        guildNames = dataBase.getloadGuildsName();
        guildsMember = dataBase.getloadGuildsMembers();
        guildPvpSettings = dataBase.getloadGuildsPVP();
    }

    public void getGuildListInfo(){
        guildNames = dataBase.getloadGuildsName();
    }

    // 플레이어 정보 가져오기
    public void getPlayerInfo(){
        playerBalances = dataBase.getloadBalanceData();
    }

    public void addInvitedGuild(Player sender, Player receiver){
        invitedGuild.put(receiver.getUniqueId(), sender.getUniqueId());
    }

    public void addGuildsMember(UUID memberUUID, UUID leaderUUID) {
        guildsMember.put(memberUUID, leaderUUID);
        //데이터베이스 갱신
        dataBase.updateGuildMembersInDatabase(leaderUUID, memberUUID);
    }

    //길드삭제
    public void deleteGuild(UUID leaderUUID){
        dataBase.deleteGuild(leaderUUID);
        getGuildInfo();
    }

    //길드 탈퇴
    public void outGuild(UUID memeberUUID){
        dataBase.outGuild(memeberUUID);
       // getGuildInfo();
    }

    public void createReady(Player player){
        createGuild.add(player.getUniqueId());
    }

    public boolean createReadyCheck(UUID leaderUUID){
        return createGuild.contains(leaderUUID);
    }

    public boolean createGuild(Player player,String name){
        if(!guildsMember.containsKey(player.getUniqueId())) {
            if (dataBase.createGuild(player.getUniqueId(), name) && dataBase.createGuildMem(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public void setPVP(Player player){
        boolean currentSetting = guildPvpSettings.getOrDefault(player.getUniqueId(), false);
        guildPvpSettings.put(player.getUniqueId(), !currentSetting);
        dataBase.setPVP(player.getUniqueId(), !currentSetting);
    }

    // 길드생성 대기 삭제
    public void cancellCreateGuild(UUID leaderUUID){
        createGuild.remove(leaderUUID);
    }

    public void getActionHouseInfo(){
        actionHouseDTO.getActionHouseInfo();
    }

    public void kickMember(String kickMemberName, Player player){
        kickMemberName = ChatColor.stripColor(kickMemberName);
        for(UUID memberUUID : guildsMember.keySet()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberUUID);
            if(offlinePlayer != null){
                String playerName = offlinePlayer.getName();
                if(playerName != null && playerName.equals(kickMemberName)){
                    System.out.println("유경빵빵빵빠아빵빵빠아ㅃ아ㅃ아ㅃ아ㅃㅇ");
                    // 데이터베이스에서 멤버 제거
                    dataBase.kickMember(memberUUID, player.getUniqueId());
                    // 메모리에서 멤버 제거

                    // 플레이어가 온라인인 경우 메시지 전송
                    if(offlinePlayer.isOnline()){
                        Player onlinePlayer = offlinePlayer.getPlayer();
                        onlinePlayer.sendMessage(ChatColor.RED + "길드에서 강퇴되었습니다.");
                    }

                    // 로그 출력
                    System.out.println("[DEBUG] " + kickMemberName + " 님이 길드에서 강퇴되었습니다.");

                    break;
                }
            }
        }
    }

    public void setSubLeaders(Player leaders, String memberName){
        memberName = ChatColor.stripColor(memberName);
        for (UUID member : guildsMember.keySet()) {
            Player player = Bukkit.getPlayer(member);
            if(player == null){
                // 만약 온라인이 아니라면
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member);
                System.out.println(offlinePlayer.getName() + "," + memberName);
                if (offlinePlayer.getName().equals(memberName)) {
                    if (subLeaders.get(leaders.getUniqueId()) != null && subLeaders.get(leaders.getUniqueId()).contains(member)) {
                        //이미 부길마라면
                        dataBase.updateGuildsSubLeader(leaders.getUniqueId(), offlinePlayer.getUniqueId(), false);
                    } else {
                        dataBase.updateGuildsSubLeader(leaders.getUniqueId(), offlinePlayer.getUniqueId(), true);
                    }
                }
            }else {
                System.out.println(player.getName() + "," + memberName);
                if (player.getName().equals(memberName)) {
                    if (subLeaders.get(leaders.getUniqueId()) != null && subLeaders.get(leaders.getUniqueId()).contains(member)) {
                        //이미 부길마라면
                        dataBase.updateGuildsSubLeader(leaders.getUniqueId(), player.getUniqueId(), false);
                    } else {
                        dataBase.updateGuildsSubLeader(leaders.getUniqueId(), player.getUniqueId(), true);
                    }
                }
            }
        }
    }


//
//    public void getloadBalanceData(){
//        dataBase.getloadBalanceData();
//    }
}
