package service;

import ActionHouse.ActionHouseDTO;
import ActionHouse.ActionHouseItem;
import DAO.DataBase;
import Guild.GuildDTO;
import Store.StoreDTO;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import redis.RedisManager;
import redis.RedisSubscriber;
import redis.clients.jedis.Jedis;
import java.io.ByteArrayOutputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.util.*;


public class Contoller {
    private GuildDTO guildDTO;
    //    private ActionHouseDTO actionHouseDTO;
    private StoreDTO storeDTO;
    private ActionHouseItem auctionItem;
    private Model model;
    private View view;
    private BalanceService balanceService;

    public Contoller(Model model, View view, BalanceService balanceService) {
        this.model = model;
        this.view = view;
        this.balanceService = new BalanceService(model);


    }
    public Model getModel() {
        return this.model;
    }

    public void openMainShop(Player player){
        Inventory mainShop = Bukkit.createInventory(null, 9, ChatColor.BLUE + "메인 상점");

        ItemStack farmCategory = new ItemStack(Material.WHEAT);
        ItemMeta farmMeta = farmCategory.getItemMeta();
        farmMeta.setDisplayName(ChatColor.GREEN + "농장 아이템");
        farmCategory.setItemMeta(farmMeta);
        mainShop.setItem(0, farmCategory);

        ItemStack blockCategory = new ItemStack(Material.BRICKS);
        ItemMeta blockMeta = blockCategory.getItemMeta();
        blockMeta.setDisplayName(ChatColor.BLUE + "블록 아이템");
        blockCategory.setItemMeta(blockMeta);
        mainShop.setItem(1, blockCategory);

        ItemStack miscCategory = new ItemStack(Material.CHEST);
        ItemMeta miscMeta = miscCategory.getItemMeta();
        miscMeta.setDisplayName(ChatColor.YELLOW + "기타 아이템");
        miscCategory.setItemMeta(miscMeta);
        mainShop.setItem(2, miscCategory);

        ItemStack MineralCategory = new ItemStack(Material.DIAMOND);
        ItemMeta MineralMeta = MineralCategory.getItemMeta();
        MineralMeta.setDisplayName(ChatColor.RED + "광물 아이템");
        MineralCategory.setItemMeta(MineralMeta);
        mainShop.setItem(3, MineralCategory);

        player.openInventory(mainShop);
    }

    public BalanceService getBalanceService() {
        return balanceService;
    }

    // 거래 요청
    public void tradeRequest (Player player,Player target) {
        if (target != null && target.isOnline() && !target.getUniqueId().equals(player.getUniqueId())) {
            model.addTradeRequest(target.getUniqueId(), player.getUniqueId());
            target.sendMessage(ChatColor.GOLD + player.getName() + "님이 거래를 요청하였습니다. /거래수락 명령어로 수락하세요.");
            player.sendMessage(ChatColor.GREEN + target.getName() + "님에게 거래 요청을 보냈습니다.");
        } else {
            player.sendMessage(ChatColor.RED + "해당 플레이어를 찾을 수 없습니다.");
        }
    }

    // 거래 수락
    public void acceptTradeRequest(Player player) {
        UUID requestUUID =  model.getTradeRequests().getOrDefault(player,null);
        if (requestUUID != null) {
            Player requester = Bukkit.getPlayer(requestUUID);
            if (requester != null && requester.isOnline()) {
                model.getTradeRequests().remove(player.getUniqueId());
                openTradeInventory(player, requester);
                player.sendMessage(ChatColor.GREEN + requester.getName() + "님과의 거래를 시작합니다.");
                requester.sendMessage(ChatColor.GREEN + player.getName() + "님이 거래를 수락하였습니다.");
            } else {
                player.sendMessage(ChatColor.RED + "거래 요청을 보낸 플레이어를 찾을 수 없습니다.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "수락할 거래 요청이 없습니다.");
        }
    }

    public int generateAuctionId() {
        try (Jedis jedis = RedisManager.getJedis()) {
            return (int) jedis.incr("auctionIdCounter");
        }
    }

    public void addItemOnActionHouse(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "사용법: /경매등록 <가격> <수량>");
            return;
        }
        try {
            int price = Integer.parseInt(args[0]); // 가격
            int amount = Integer.parseInt(args[1]); // 수량
            ItemStack item = player.getInventory().getItemInMainHand(); // 손에 든 아이템

            // 유효한 아이템인지, 충분한 수량이 있는지 확인
            if (item != null && item.getType() != Material.AIR && item.getAmount() >= amount) {
                // 등록할 아이템 복제 (수량만큼 설정)
                ItemStack auctionItemStack = item.clone();
                auctionItemStack.setAmount(amount);

                // 경매 ID 생성
                int auctionCounter = generateAuctionId();

                if(auctionCounter == -1) {
                    // 너무 많은 아이템
                    player.sendMessage(ChatColor.RED + "자리 없음");
                    return;
                }
                // 경매 등록
                model.getActionHouseDTO().addActionItem(auctionCounter, new ActionHouseItem(player.getUniqueId(), auctionItemStack, price));
                publishAuctionUpdate("auctionItemAdded|" + auctionCounter + "|" + player.getUniqueId().toString() + "|" + price + "|" + serializeItemStack(auctionItemStack));
                // 플레이어 인벤토리에서 해당 수량 차감
                item.setAmount(item.getAmount() - amount);

                // 플레이어에게 경매 등록 성공 메시지
                player.sendMessage(ChatColor.GREEN + "아이템이 경매장에 등록되었습니다! ID: " + auctionCounter);

            } else {
                player.sendMessage(ChatColor.RED + "유효한 아이템이 없거나 충분한 수량이 없습니다.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "가격과 수량은 숫자여야 합니다.");
        }
    }

    // ItemStack 직렬화
    public String serializeItemStack(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeObject(item);
            dataOutput.close();

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public void openActionHouse(Player player, int page){
        model.getActionHouseInfo();
        view.openAuctionHouse(player, page, model.getActionHouseDTO());
    }

    public void openTradeInventory(Player player, Player target) {}

    public void sendMoney(Player player, String[] args){
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "해당 플레이어를 찾을 수 없습니다.");
            return;
        }
        try {
            int amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "송금 금액은 양수여야 합니다.");
                return;
            }

            if (model.getPlayerBalances().getOrDefault(player.getUniqueId(), 0)-amount >= 0) {
                model.minusMoney(player, amount);
                updateScoreboard(player); // 스코어보드 즉시 업데이트
                balanceService.add(target.getUniqueId(), amount);

                updateScoreboard(target); // 스코어보드 즉시 업데이트
                player.sendMessage(ChatColor.GREEN + target.getName() + "님에게 " + amount + "원을 송금하였습니다.");
                target.sendMessage(ChatColor.GREEN + player.getName() + "님이 " + amount + "원을 송금하였습니다.");
            } else {
                player.sendMessage(ChatColor.RED + "소지금이 부족합니다!");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "송금 금액은 숫자여야 합니다.");
        }
    }

    public void addMoney(Player sender, String[] args){
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "사용법: /돈추가 <닉네임> <추가할금액>");
            return;
        }
        try {
            Player target = Bukkit.getPlayer(args[0]);
            int amount = Integer.parseInt(args[1]);
            if (target != null && target.isOnline()) {
                balanceService.add(target.getUniqueId(), amount);
                sender.sendMessage(ChatColor.GREEN + target.getName() + "님의 소지금이 " + amount + "원 추가되었습니다.");
                target.sendMessage(ChatColor.GREEN + "관리자가 소지금 " + amount + "원을 추가하였습니다.");
                updateScoreboard(target);
            } else {
                sender.sendMessage(ChatColor.RED + "해당 플레이어를 찾을 수 없습니다.");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "금액은 숫자여야 합니다.");
        }
    }

    public void loadBalanceData(){
        //생성자로 옮김

    }

    public void updateScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        int balance = balanceService.getBalance(playerUUID);
        UUID leaderUUID = model.getGuildsMember().get(playerUUID);
        String guildName = leaderUUID != null ? model.getGuildNames().getOrDefault(leaderUUID, "없음") : "없음";
        String nickname = RedisSubscriber.getNickname(player.getUniqueId(), player.getName());

        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            Scoreboard board = player.getScoreboard();
            ScoreboardManager manager = Bukkit.getScoreboardManager();

            if (board == null || board == manager.getMainScoreboard()) {
                board = manager.getNewScoreboard();
            }

            Objective objective = board.getObjective("balance");
            if (objective == null) {
                objective = board.registerNewObjective("balance", "dummy", ChatColor.GREEN + "서버 상태");
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            // ❗ 기존 점수 리셋 제거 (변경된 라인만 갱신)
            Map<String, Integer> expected = Map.of(
                    ChatColor.YELLOW + "닉네임: " + nickname, 3,
                    ChatColor.YELLOW + "소지금: " + balance + "원", 2
                    // ChatColor.YELLOW + "길드: " + guildName, 1
            );

            for (String entry : board.getEntries()) {
                if (!expected.containsKey(entry)) {
                    board.resetScores(entry);
                }
            }

            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                objective.getScore(entry.getKey()).setScore(entry.getValue());
            }

            player.setScoreboard(board);
        });
    }


    public void payOfflineMoney(Player player){
        model.checkPayOfflineMoney(player);
    }

    public void outGuild(Player player){
       // model.getGuildInfo();

        if(model.getGuildsMember().containsKey(player.getUniqueId())){
            model.outGuild(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "길드 탈퇴가 되었습니다.");
            updateScoreboard(player);
            player.closeInventory();
        }
    }

    public boolean isPlayerOnline(UUID playerUUID) {
        try (Jedis jedis = RedisManager.getJedis()) {
            return jedis.sismember("onlinePlayers", playerUUID.toString());
        }
    }


    public void actionHouseEvent(Player player, Inventory inventory, ItemStack clickedItem) {
        if (clickedItem != null && clickedItem.hasItemMeta()) {
            ItemMeta itemMeta = clickedItem.getItemMeta();
            String displayName = itemMeta.getDisplayName();

            // 경매 아이템의 displayName이 제대로 된 형식인지 확인
            if (displayName.contains("ID:") && displayName.contains("가격:") && displayName.contains("원")) {
                try {
                    // ID와 가격 추출
                    String[] parts = displayName.split(" ");
                    int auctionId = Integer.parseInt(parts[1].replace("ID:", ""));
                    int price = Integer.parseInt(parts[3].replace("가격:", "").replace("원", ""));

                    UUID playerUUID = player.getUniqueId();
                    auctionItem = model.getActionHouseDTO().getAuctionItems().get(auctionId);

                    if (auctionItem != null) {
                        UUID ownerUUID = auctionItem.getOwner();

                        // 자기가 올린 아이템인지 체크
                        if (ownerUUID.equals(playerUUID)) {
                            player.sendMessage(ChatColor.RED + "자신이 등록한 아이템은 구매할 수 없습니다.");
                            return;
                        }

                        // 소지금 체크
                        if (model.getPlayerBalances().getOrDefault(playerUUID, 0) - price >= 0) {
                            // 돈 차감
                            model.minusMoney(player, price);

                            // 아이템 추가
                            ItemStack boughtItem = clickedItem.clone(); // 구매된 아이템 복제
                            ItemMeta boughtMeta = boughtItem.getItemMeta();

                            // 원래 아이템의 이름 복원
                            ItemStack originalItem = auctionItem.getItem();
                            if (originalItem.hasItemMeta()) {
                                ItemMeta originalMeta = originalItem.getItemMeta();
                                String originalName = originalMeta.getDisplayName();  // 원래 아이템 이름 가져오기
                                boughtMeta.setDisplayName(originalName);  // 원래 이름으로 설정
                            } else {
                                // 만약 아이템 이름이 없었다면 이름을 없앱니다.
                                boughtMeta.setDisplayName(null);
                            }

                            boughtItem.setItemMeta(boughtMeta);  // 변경된 메타데이터 적용
                            player.getInventory().addItem(boughtItem);
                            // 경매장에서 아이템 제거
                            model.getActionHouseDTO().removeActionItem(auctionId);
                            inventory.remove(clickedItem); // 경매장 UI에서 제거

                            // **여기서 판매자에게 돈 지급 로직을 수정합니다.**

                            // 판매자의 온라인 상태 확인
                            if (isPlayerOnline(ownerUUID)) {
                                // 판매자가 온라인이므로 Redis 메시지 발행하여 해당 서버에서 돈 지급 및 알림
                                publishAuctionUpdate("giveMoney|" + ownerUUID.toString() + "|" + price);
                            } else {
                                // 판매자가 오프라인이므로 오프라인 지급 금액에 추가
                                model.addMoneyOffline(ownerUUID, price);
                                publishAuctionUpdate("offlinePaymentUpdated|" + ownerUUID.toString() + "|" + price);
                            }

                            player.sendMessage(ChatColor.GREEN + "아이템을 성공적으로 구매하였습니다!");
                            updateScoreboard(player);

                            // **다른 플레이어들의 경매장 GUI 업데이트**
                            view.updateOtherPlayersAuctionHouseGUIs(player, model);

                            // **멀티 서버 환경에서는 Redis 등을 통해 업데이트 전파**
                            publishAuctionUpdate("itemBought|" + auctionId);

                        } else {
                            player.sendMessage(ChatColor.RED + "소지금이 부족합니다! 이 아이템을 구매할 수 없습니다.");
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "경매 아이템을 찾을 수 없습니다.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "가격 또는 ID를 처리하는 중 오류가 발생했습니다.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "유효한 경매 아이템이 아닙니다. 형식이 맞지 않습니다.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "유효한 아이템이 선택되지 않았습니다.");
        }
    }


    public void publishAuctionUpdate(String message) {
        try (Jedis jedis = RedisManager.getJedis()) {
            jedis.publish("auctionUpdates", message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /////////////// 길드

    public void openGuildMenu(Player player){
        model.getGuildInfo();
        if(model.getGuildsMember().containsKey(player.getUniqueId())){
            // 길드가 존재 하면
            if(model.getGuildNames().containsKey(player.getUniqueId())){
                //리더라면
                view.openMainGuildLeaderMenu(player);
            }else{
                //맴버면
                view.openMainGuildWMemberMenu(player);
            }
        }
    }
    public void deleteGuild(Player player) {
        synchronizeGuildData(); // Redis와 SQL 동기화

        if (model.getGuildsMember().containsKey(player.getUniqueId())) {
            // 리더 확인
            List<UUID> updateMember = new ArrayList<>();
            for (UUID key : model.getGuildsMember().keySet()) {
                if (model.getGuildsMember().get(key).equals(player.getUniqueId())) {
                    // 길드원이면
                    updateMember.add(key);
                }
            }

            // Redis 및 SQL 데이터 삭제
            model.deleteGuild(player.getUniqueId());
            model.getGuildsMember().remove(player.getUniqueId()); // 길드장 데이터 삭제
            player.closeInventory();

            for (UUID member : updateMember) {
                Player playerMember;
                if ((playerMember = Bukkit.getPlayer(member)) != null) {
                    updateScoreboard(playerMember);
                    playerMember.sendMessage(ChatColor.RED + "길드가 삭제되었습니다.");
                }
            }
        } else {
            // 리더가 아니면
            player.sendMessage(ChatColor.RED + "길드장만 길드를 삭제할 수 있습니다.");
        }
    }


    public void synchronizeGuildData() {
        System.out.println("[DEBUG] Redis와 MariaDB 데이터를 동기화합니다.");

        // 예: 길드 데이터를 가져오기 위해 모델 또는 데이터베이스에서 UUID 목록을 가져옴
        for (UUID leaderUUID : model.getGuildNames().keySet()) { // 예: 모델에서 길드 리더 UUID 가져오기
            String redisKey = "guild:" + leaderUUID + ":members"; // Redis 키 생성

            try {
                DataBase dbInstance = new DataBase(model.getActionHouseDTO(), model.getPlayerDTO()); // DataBase 인스턴스 생성
                dbInstance.synchronizeRedisWithSQL(redisKey, leaderUUID); // 동기화 호출
                System.out.println("[DEBUG] 동기화 완료: redisKey=" + redisKey + ", leaderUUID=" + leaderUUID);
            } catch (Exception e) {
                System.err.println("[ERROR] 데이터 동기화 중 오류 발생: redisKey=" + redisKey + ", leaderUUID=" + leaderUUID + ", 오류=" + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    public void openGuildMembersMenu(Player player){
        view.openGuildMembersMenu(player, model.getGuildsMember(), model.getSubLeaders());
    }

    public void openGuildSettingsMenu(Player player){
        view.openGuildSettingsMenu(player, model.getGuildPvpSettings());
    }

    public void toggleGuildPVP (Player player) {
        //리더 확인
        if (model.getGuildNames().containsKey(player.getUniqueId())) {
            model.setPVP(player);
            player.sendMessage(ChatColor.GREEN + "길드원 간 PvP가 " + (model.getGuildPvpSettings().get(player.getUniqueId()) ? "활성화" : "비활성화") + "되었습니다.");
            //디비

            openGuildSettingsMenu(player);
        }
    }

    public boolean isguildname(String name){
        for (String guild : model.getGuildNames().values()){
            if(guild.equals(name)){
                return true;
            }
        }
        return false;
    }

    public void createReadyGuild(Player player){
        player.closeInventory();
        player.sendMessage(ChatColor.AQUA + "생성할 길드 이름을 입력해주세요.");
        model.createReady(player); // 플레이어가 길드 이름을 입력하도록 대기
    }

    // 길드 생성여부 확인
    public boolean isCreateGuild(Player player){
        return model.createReadyCheck(player.getUniqueId());
    }

    // 길드 생성 대기 삭제
    public void cancellCreateGuild(UUID playerUUID){
        model.cancellCreateGuild(playerUUID);
    }

    //길드생성
    public boolean createGuild(Player player, String name){
        if(isguildname(name)){
            player.sendMessage(ChatColor.RED + "이미 존재하는 길드가 있습니다.");
            return false;
        }
        if( model.createGuild(player,name) ){
            player.sendMessage(ChatColor.GREEN +"길드가 생성되었습니다.");
            model.getGuildInfo();
            updateScoreboard(player);
            return true;
        }else{
            player.sendMessage(ChatColor.RED +"가입된 길드가 있습니다.");
            return false;
        }
    }

    //길드간 PVP
//    public boolean checkPVP(Player player1, Player player2){
//        UUID leader = model.getGuildsMember().get(player1.getUniqueId());
//        // 공격자와 피해자가 같은 길드에 속해 있는지 확인
//        if(model.getGuildsMember().get(player2.getUniqueId()).equals(leader)) {
//            // 길드 PVP여부 확인
//            if (model.getGuildPvpSettings().get(leader)) {
//                // 같은 길드일 때 PvP 설정 확인 (true면 때려짐)
//                player1.sendMessage(ChatColor.GREEN + "길드원끼리 PvP가 활성화되어 공격할 수 있습니다.");
//                return false;
//            }
//            player1.sendMessage(ChatColor.RED + "길드원끼리는 PvP가 비활성화되어 있습니다.");
//            return true;
//        }
//        return false;
//    }


    public void openGuildListMenu(Player player){
        model.getGuildListInfo();
        view.openGuildListMenu(player, model.getGuildNames());
    }

    public void invitesGuild(Player player, String[] args){
        if (args.length == 1) {
            Player invitee = Bukkit.getPlayer(args[0]);
            if (invitee != null && invitee.isOnline() && !model.getInvitedGuild().containsKey(invitee.getUniqueId()) && !model.getGuildsMember().containsKey(invitee.getUniqueId())) {
                // 초대 받은적도 없고, 길드에도 들어가있지 않으면
                model.addInvitedGuild(player, invitee);
                player.sendMessage(ChatColor.RED + args[0] + "를(을) 초대하였습니다.");
                invitee.sendMessage(ChatColor.RED + player.getName() + "이(가) 길드로 초대하였습니다.");
            } else {
                player.sendMessage(ChatColor.RED + "초대하려는 플레이어를 찾을 수 없거나 이미 초대를 받았습니다.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "사용법: /길드초대 <닉네임>");
        }
    }

    public void acceptInviteGuild(Player player){
        if(model.getInvitedGuild().containsKey(player.getUniqueId())){
            // 초대받은 이력이 있으면 길드가입 후, 해당 인원 삭제
            UUID guildLeaderUUID = model.getInvitedGuild().get(player.getUniqueId());
            model.addGuildsMember(player.getUniqueId(), guildLeaderUUID);
            model.getInvitedGuild().remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "길드에 가입되었습니다.");
            Player leader = Bukkit.getPlayer(guildLeaderUUID);
            if(leader != null) {
                leader.sendMessage(ChatColor.GREEN +player.getName()+ "이(가) 길드에 가입했습니다.");
            }
            updateScoreboard(player);
        } else {
            player.sendMessage(ChatColor.RED + "받은 길드 초대가 없습니다.");
        }
    }

    //강퇴 메뉴
    public void openKickMenu(ItemStack member, Player player) {
        if (model.getGuildNames().containsKey(player.getUniqueId())) {
            // 한 길드의 길드장이라면
            if (member.getItemMeta() != null && member.getItemMeta().getDisplayName() != null) {
                String name = member.getItemMeta().getDisplayName();
               // System.out.println(name.split(":")[0]);
                if (!name.split(": ")[0].equals(ChatColor.GOLD + "길드장")) {
                    view.openKickMenu(member, player);
                }
            } else {
                player.sendMessage(ChatColor.RED + "wow");
            }
        }
    }


    //멤버 강퇴
    public void kickMember(Player player, ItemStack member){
        if(model.getGuildNames().containsKey(player.getUniqueId())) {
            // 길드장이라면
            String name = member.getItemMeta().getDisplayName();
            String[] a = name.split(" ");
            if(!player.getName().equals(a[a.length-1])) {
                model.kickMember(a[a.length - 1], player);
                System.out.println("ㅋㅋㅋㅋ");
            }
        }
    }

    public void setSubLeader(Player leader, ItemStack itemStack){
        ItemMeta itemMeta = itemStack.getItemMeta();
        String memberName = itemMeta.getDisplayName();
        if(memberName != null){
            model.setSubLeaders(leader, memberName);
            System.out.println("ㅋㅋㅋㅋ");
        }

    }
}
