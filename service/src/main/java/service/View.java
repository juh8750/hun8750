package service;

import ActionHouse.ActionHouseDTO;
import ActionHouse.ActionHouseItem;
import ActionHouse.AuctionInventoryHolder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class View {
    private static final int ITEMS_PER_PAGE = 45;
    private final Model model;
    private Map<Player, Integer> auctionViewers = new HashMap<>();
    public Map<Player, Integer> getAuctionViewers() {
        return auctionViewers;
    }
    public View(Model model) {
        this.model = model;
    }

    public void openMainShop(Player player) {
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

    // 거래 GUI 열기
    private void openTradeInventory(Player player1, Player player2) {
        Inventory tradeInventory = Bukkit.createInventory(null, 54, ChatColor.BLUE + "거래");

//        activeTrades.put(player1.getUniqueId(), tradeInventory);
//        activeTrades.put(player2.getUniqueId(), tradeInventory);

        player1.openInventory(tradeInventory);
        player2.openInventory(tradeInventory);
    }

    // 경매장 GUI 열기
    public void openAuctionHouse(Player player, int page, ActionHouseDTO actionHouseDTO) {
        Inventory auctionHouse = createAuctionInventory(page, actionHouseDTO);
        player.openInventory(auctionHouse);
        auctionViewers.put(player, page);
    }

    private Inventory createAuctionInventory(int page, ActionHouseDTO actionHouseDTO) {
        Inventory auctionHouse = Bukkit.createInventory(new AuctionInventoryHolder(page), 54, ChatColor.GOLD + "경매장 - 페이지 " + page);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = startIndex + ITEMS_PER_PAGE;
        int index = 0;

        // ID를 정렬하여 리스트로 가져옵니다.
        List<Integer> sortedIds = new ArrayList<>(actionHouseDTO.getAuctionItems().keySet());
        Collections.sort(sortedIds);

        for (int id : sortedIds) {
            if (index >= startIndex && index < endIndex) {
                ActionHouseItem actionHouseItem = actionHouseDTO.getAuctionItems().get(id);
                if (actionHouseItem.getItem() != null) {
                    ItemStack item = actionHouseItem.getItem().clone();
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(ChatColor.YELLOW + "ID: " + id + " 가격: " + actionHouseItem.getPrice() + "원");
                    item.setItemMeta(meta);
                    auctionHouse.addItem(item);
                }
            }
            index++;
        }

        // 이전 페이지 버튼
        if (page > 1) {
            ItemStack previousPage = new ItemStack(Material.ARROW);
            ItemMeta meta = previousPage.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "이전 페이지");
            previousPage.setItemMeta(meta);
            auctionHouse.setItem(45, previousPage);
        }

        // 다음 페이지 버튼
        if (endIndex < actionHouseDTO.getAuctionItems().size()) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta meta = nextPage.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "다음 페이지");
            nextPage.setItemMeta(meta);
            auctionHouse.setItem(53, nextPage);
        }


        return auctionHouse;
    }

    // 경매장 인벤토리인지 확인하는 메서드
    private boolean isAuctionHouseInventory(Inventory inventory) {
        return inventory.getHolder() instanceof AuctionInventoryHolder;
    }

    // 경매장 GUI 업데이트 메서드
    public void updateAuctionHouseGUI(Player player, int page, Model model) {
        Inventory updatedAuctionHouse = createAuctionInventory(page, model.getActionHouseDTO());
        InventoryView currentView = player.getOpenInventory();

        if (isAuctionHouseInventory(currentView.getTopInventory())) {
            currentView.getTopInventory().setContents(updatedAuctionHouse.getContents());
            player.updateInventory();
        }
    }

    // View 클래스
    public void updateOtherPlayersAuctionHouseGUIs(Player buyer, Model model) {
        Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
            for (Map.Entry<Player, Integer> entry : auctionViewers.entrySet()) {
                Player viewer = entry.getKey();
                int viewerPage = entry.getValue();

                if (!viewer.equals(buyer)) {
                    updateAuctionHouseGUI(viewer, viewerPage, model);
                }
            }
        });
    }

    // 길드 메인 메뉴 (리더)
    public void openMainGuildLeaderMenu(Player player) {
        Inventory mainMenu = Bukkit.createInventory(null, 9, ChatColor.GREEN + "길드 메뉴");

        ItemStack guildMembers = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta guildMembersMeta = guildMembers.getItemMeta();
        guildMembersMeta.setDisplayName(ChatColor.YELLOW + "길드원 목록");
        guildMembers.setItemMeta(guildMembersMeta);
        mainMenu.setItem(0, guildMembers);

        ItemStack guildSettings = new ItemStack(Material.PAPER);
        ItemMeta guildSettingsMeta = guildSettings.getItemMeta();
        guildSettingsMeta.setDisplayName(ChatColor.YELLOW + "길드 설정");
        guildSettings.setItemMeta(guildSettingsMeta);
        mainMenu.setItem(1, guildSettings);

        ItemStack deleteGuild = new ItemStack(Material.BARRIER);
        ItemMeta deleteGuildMeta = deleteGuild.getItemMeta();
        deleteGuildMeta.setDisplayName(ChatColor.RED + "길드 삭제");
        deleteGuild.setItemMeta(deleteGuildMeta);
        mainMenu.setItem(8, deleteGuild);

        player.openInventory(mainMenu);
    }

    // 길드 메인 메뉴 (멤버)
    public void openMainGuildWMemberMenu(Player player) {
        Inventory mainMenu = Bukkit.createInventory(null, 9, ChatColor.GREEN + "길드 메뉴");

        ItemStack guildMembers = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta guildMembersMeta = guildMembers.getItemMeta();
        guildMembersMeta.setDisplayName(ChatColor.YELLOW + "길드원 목록");
        guildMembers.setItemMeta(guildMembersMeta);
        mainMenu.setItem(0, guildMembers);

        ItemStack guildSettings = new ItemStack(Material.PAPER);
        ItemMeta guildSettingsMeta = guildSettings.getItemMeta();
        guildSettingsMeta.setDisplayName(ChatColor.YELLOW + "길드 설정");
        guildSettings.setItemMeta(guildSettingsMeta);
        mainMenu.setItem(1, guildSettings);

        ItemStack deleteGuild = new ItemStack(Material.STRUCTURE_VOID);
        ItemMeta deleteGuildMeta = deleteGuild.getItemMeta();
        deleteGuildMeta.setDisplayName(ChatColor.RED + "길드 탈퇴");
        deleteGuild.setItemMeta(deleteGuildMeta);
        mainMenu.setItem(8, deleteGuild);

        player.openInventory(mainMenu);
    }

    //길드 생성 메뉴
    public void openCreateGuildMenu(Player player) {

        Inventory createGuildMenu = Bukkit.createInventory(null, 9, ChatColor.GREEN + "길드 생성");

        ItemStack createGuild = new ItemStack(Material.ANVIL);
        ItemMeta createGuildMeta = createGuild.getItemMeta();
        createGuildMeta.setDisplayName(ChatColor.YELLOW + "길드 생성");
        createGuild.setItemMeta(createGuildMeta);
        createGuildMenu.setItem(0, createGuild);

        player.openInventory(createGuildMenu);
    }

    //길드 멤버 메뉴
    public void openGuildMembersMenu(Player player, HashMap<UUID, UUID> guildMembers, HashMap<UUID, Set<UUID>> subLeaders) {
        Inventory guildMenu = Bukkit.createInventory(null, 27, ChatColor.GREEN + "길드원 목록");

        if (guildMembers != null) {
            UUID playerUUID = player.getUniqueId();
            UUID leaderUUID = guildMembers.get(playerUUID);

            if (leaderUUID != null) {
                // 길드 리더의 OfflinePlayer 객체 가져오기
                OfflinePlayer leader = Bukkit.getOfflinePlayer(leaderUUID);
                String leaderName = (leader != null && leader.getName() != null) ? leader.getName() : "알 수 없음";

                // 길드 리더의 머리 아이템 생성
                ItemStack leaderHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta leaderMeta = (SkullMeta) leaderHead.getItemMeta();
                leaderMeta.setOwningPlayer(leader);
                leaderMeta.setDisplayName(ChatColor.GOLD + "길드장: " + leaderName);
                leaderHead.setItemMeta(leaderMeta);
                guildMenu.addItem(leaderHead);

                if(subLeaders.containsKey(leaderUUID)) {
                    for (UUID subLeaderUUID : subLeaders.get(leaderUUID)) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(subLeaderUUID);
                        String memberName = (member != null && member.getName() != null) ? member.getName() : "알 수 없음";

                        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) head.getItemMeta();
                        meta.setOwningPlayer(member);
                        meta.setDisplayName(ChatColor.DARK_PURPLE + "부길마: " + memberName);
                        head.setItemMeta(meta);
                        guildMenu.addItem(head);
                    }
                }

                // 길드원 목록 생성
                for (UUID memberUUID : guildMembers.keySet()) {
                    // 리더는 이미 추가했으므로 제외
                    if (!memberUUID.equals(leaderUUID) && guildMembers.get(memberUUID).equals(leaderUUID) ) {

                        if(subLeaders.get(leaderUUID) != null && subLeaders.get(leaderUUID).contains(memberUUID)) {
                            continue;
                        }

                        OfflinePlayer member = Bukkit.getOfflinePlayer(memberUUID);
                        String memberName = (member != null && member.getName() != null) ? member.getName() : "알 수 없음";

                        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) head.getItemMeta();
                        meta.setOwningPlayer(member);
                        meta.setDisplayName(ChatColor.YELLOW + memberName);
                        head.setItemMeta(meta);
                        guildMenu.addItem(head);
                    }
                }
            } else {
                player.sendMessage(ChatColor.RED + "길드 정보를 찾을 수 없습니다.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "길드 정보가 없습니다.");
        }

        player.openInventory(guildMenu);
    }

    //길드원 강퇴
    public void openKickMenu(ItemStack member, Player player){
        Inventory guildKickMenu = Bukkit.createInventory(null, 9, ChatColor.RED + "강퇴");

        ItemMeta meta = member.getItemMeta();
        String name = meta.getDisplayName();
        name = ChatColor.stripColor(name);
        String[] memberName = name.split(" ");
        meta.setDisplayName("강퇴 : " + memberName[memberName.length - 1]);
        member.setItemMeta(meta);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.RED + "뒤로가기");
        back.setItemMeta(backMeta);


        ItemStack set = new ItemStack(Material.LEVER);
        ItemMeta setMeta = back.getItemMeta();
        setMeta.setDisplayName(memberName[memberName.length - 1]);

        if(memberName[0].equals("부길마:")) {
            setMeta.setLore(List.of(ChatColor.BLUE + "직책 : 부길마"));
        }else{
            setMeta.setLore(List.of(ChatColor.BLUE + "직책 : 길드원"));
        }

        set.setItemMeta(setMeta);

        guildKickMenu.setItem(0,set);
        guildKickMenu.setItem(4, member);
        guildKickMenu.setItem(8, back);

        player.openInventory(guildKickMenu);
    }


    // 길드 목록
    public void openGuildListMenu(Player player, HashMap<UUID, String> guilds) {
        Inventory guildListMenu = Bukkit.createInventory(null, 27, ChatColor.GREEN + "길드 목록");
        for (UUID leaderUUID : guilds.keySet()) {
            Player leader = Bukkit.getPlayer(leaderUUID);
            String leaderName = (leader != null) ? leader.getName() : "알 수 없음";
            ItemStack guildItem = new ItemStack(Material.BOOK);
            ItemMeta meta = guildItem.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + guilds.get(leaderUUID));
            meta.setLore(List.of(ChatColor.GOLD + "길드장: " + leaderName));
            guildItem.setItemMeta(meta);
            guildListMenu.addItem(guildItem);
        }

        player.sendMessage(ChatColor.RED + "길드 목록을 불러오는 중 오류가 발생했습니다.");
        player.openInventory(guildListMenu);
    }

    // 길드 셋팅
    public void openGuildSettingsMenu(Player player, HashMap<UUID, Boolean> guildPVP) {
        Inventory settingsMenu = Bukkit.createInventory(null, 9, ChatColor.GREEN + "길드 설정");

        ItemStack pvpToggle = new ItemStack(Material.LEVER);
        ItemMeta pvpToggleMeta = pvpToggle.getItemMeta();
        boolean pvpEnabled = guildPVP.getOrDefault(player.getUniqueId(), false);
        pvpToggleMeta.setDisplayName(ChatColor.YELLOW + "길드원 간 PvP: " + (pvpEnabled ? "활성화" : "비활성화"));
        pvpToggle.setItemMeta(pvpToggleMeta);
        settingsMenu.setItem(0, pvpToggle);

        player.openInventory(settingsMenu);
    }
}
