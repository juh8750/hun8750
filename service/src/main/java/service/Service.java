package service;

import DAO.DataBase;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketListener;
import enums.Format;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import net.wesjd.anvilgui.AnvilGUI;
import redis.RedisManager;
import redis.RedisSubscriber;
import redis.clients.jedis.Jedis;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import stocks.*;
import tphuman.TpnPluginMessageListener;

public final class Service extends JavaPlugin implements Listener, ControllerProvider {
    private Contoller contoller;
    private Model model;
    private View view;
    private static Service instance;
    private DataBase database;
    private RedisSubscriber redisSubscriber;
    private StockService stockService;
    private StockTickerDisplay stockTickerDisplay;
    private BalanceService balanceService;
    private TickerTask tickerTask;


    @Override
    public void onEnable() {
        instance = this;
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[EconomyTradePlugin] 플러그인이 활성화되었습니다.");

        // 필수 초기화
        RedisManager.initialize("192.168.0.3", 6379);
        model = new Model(this);
        view = new View(model);
        balanceService = new BalanceService(model);
        contoller = new Contoller(model, view, balanceService);
        database = new DataBase(model.getActionHouseDTO(), model.getPlayerDTO());
        redisSubscriber = new RedisSubscriber(view, model, contoller);
        redisSubscriber.start();
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        String worldName = getConfig().getString("ticker.world");
        if (worldName != null && !worldName.isEmpty()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                // ✅ 먼저 stockService 생성
                stockService = new StockService(new StockDAO(database.getConnection()), contoller, balanceService);

                // ✅ 이후 나머지 의존 객체 생성
                tickerTask = new TickerTask(this);
                int x = getConfig().getInt("ticker.x");
                int y = getConfig().getInt("ticker.y");
                int z = getConfig().getInt("ticker.z");
                Location loc = new Location(world, x, y, z);
                stockTickerDisplay = new StockTickerDisplay(loc);

                Bukkit.getScheduler().runTaskTimer(
                        this,
                        new DelistRecoveryTask(stockService.getDao(), stockService),
                        0L,
                        20L * 60
                );

                getCommand("주식").setExecutor(new StockCommand(stockService));
                getCommand("전광판위치설정").setExecutor(new TickerCommand(this, tickerTask, stockService));
                getCommand("주식조정").setExecutor(new StockAdjustCommand(stockService, stockService.getDao(), stockTickerDisplay));

                // 주식 가격 시뮬레이션 및 전광판 갱신 루프
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        stockService.simulatePriceChanges();
                        if (stockTickerDisplay != null) {
                            stockTickerDisplay.updateDisplay();
                        }
                    }
                }.runTaskTimer(this, 0L, 6000L);

                getLogger().info("[Stocks] 주식 기능이 활성화되었습니다.");
            } else {
                getLogger().warning("[Stocks] 설정된 월드 '" + worldName + "'를 찾을 수 없습니다. 주식 기능을 비활성화합니다.");
            }
        } else {
            getLogger().info("[Stocks] ticker.world 설정이 없어 주식 기능을 비활성화합니다.");
        }

        // 기타 기능
        new TpnPluginMessageListener(this);
        getLogger().info("닉네임 패킷 리스너 등록됨");
    }


    @Override
    public void onDisable() {
        if (model != null && model.getPlayerBalances() != null) {
            database.saveBalanceData(model.getPlayerBalances());
        }
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[EconomyPlugin] 플러그인이 비활성화되었습니다.");
        RedisManager.close();

    }

    public static Service getInstance() {
        return instance;
    }

    public DataBase getDatabase() {
        return database;
    }

    public Model getModel(){
        return model;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Bukkit.getLogger().info("[DEBUG] " + player.getName() + " 접속");

        String cachedNick = RedisSubscriber.nicknameCache.getOrDefault(uuid, player.getName());
        event.setJoinMessage(Format.JOIN.getMessage(cachedNick));
        setPlayerDisplay(player, cachedNick);

        Bukkit.getScheduler().runTaskAsynchronously(Service.getInstance(), () -> {
            int balance = 0;
            String redisNick = null;

            try (Jedis jedis = RedisManager.getJedis()) {
                String balanceStr = jedis.hget("player_balances", uuid.toString());
                Bukkit.getLogger().info("[DEBUG] Redis 잔액 조회 결과: " + balanceStr); // ✅ 로그 추가
                if (balanceStr != null) balance = Integer.parseInt(balanceStr);

                redisNick = jedis.get("nickname:" + uuid);
                Bukkit.getLogger().info("[DEBUG] Redis 닉네임 조회 결과: " + redisNick); // ✅ 로그 추가
            } catch (Exception e) {
                e.printStackTrace();
            }

            final int finalBalance = balance;
            final String finalNick = (redisNick != null) ? redisNick : cachedNick;

            RedisSubscriber.nicknameCache.put(uuid, finalNick);

            Bukkit.getScheduler().runTask(Service.getInstance(), () -> {
                Bukkit.getLogger().info("[DEBUG] finalBalance = " + finalBalance);
                Bukkit.getLogger().info("[DEBUG] model.getPlayerBalances() 갱신");
                model.getPlayerBalances().put(uuid, finalBalance); // 캐시 갱신
                contoller.payOfflineMoney(player); // 반드시 캐시 세팅 후!
                setPlayerDisplay(player, finalNick);
                setScoreboard(player, finalNick, finalBalance);

                contoller.updateScoreboard(player);

                // ✅ [추가] 주식 상장폐지 오프라인 알림 처리
                List<String> notifications = stockService.getDao().getPendingNotifications(uuid);
                if (!notifications.isEmpty()) {
                    player.sendMessage("§c[알림] §7당신의 포트폴리오에 변화가 발생했습니다.");
                    for (String message : notifications) {
                        player.sendMessage("§7" + message);
                    }
                    stockService.getDao().clearPendingNotifications(uuid);
                }
            });
        });
    }


    private void setPlayerDisplay(Player player, String nick) {
        player.setDisplayName(nick);
        player.setPlayerListName(nick);
        player.setCustomName(nick);
        player.setCustomNameVisible(true);
        applyNameTagToAllViewers(player, nick);
    }

    private void setScoreboard(Player player, String nick, int balance) {
        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
        }

        Objective obj = board.getObjective("balance");
        if (obj == null) {
            obj = board.registerNewObjective("balance", "dummy", ChatColor.GREEN + "숲길 서버");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        board.getEntries().forEach(board::resetScores);
        obj.getScore(ChatColor.YELLOW + "닉네임: " + nick).setScore(3);
        obj.getScore(ChatColor.YELLOW + "소지금: " + balance + "원").setScore(2);
        player.setScoreboard(board);
    }


    public void applyNameTagToAllViewers(Player player, String nickname) {
        // GameProfile 리플렉션 이름 변경
        try {
            Object craftPlayer = player.getClass().cast(player);
            Method getProfile = craftPlayer.getClass().getMethod("getProfile");
            Object profile = getProfile.invoke(craftPlayer);
            Field nameField = profile.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(profile, nickname);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // hide/show 으로 클라이언트 이름 바뀐 것 동일하게 반영
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.hidePlayer(player);
            other.showPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String nickname = RedisSubscriber.getNickname(uuid, player.getName());
        event.setQuitMessage(Format.QUIT.getMessage(nickname));

        // ❗ Redis 기준으로 직접 조회
        int balance = balanceService.getBalance(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(Service.getInstance(), () -> {
            try (Jedis jedis = RedisManager.getJedis()) {
                jedis.srem("onlinePlayers", uuid.toString());
                jedis.hset("player_balances", uuid.toString(), String.valueOf(balance));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Bukkit.getLogger().info("[DEBUG] " + player.getName() + " 종료 시 잔액 저장: " + balance);
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

//            if (label.equalsIgnoreCase("상점")) {
//                //상점 열기
//                contoller.openMainShop(player);
//                return true;
//            }

            if (label.equalsIgnoreCase("거래요청") && args.length == 1) {
                Player target = Bukkit.getPlayer(args[0]);
                contoller.tradeRequest(player, target);
                return true;
            }

            if (label.equalsIgnoreCase("거래수락")) {
                contoller.acceptTradeRequest(player);
                return true;
            }

            if (label.equalsIgnoreCase("경매등록")) {
                contoller.addItemOnActionHouse(player, args);
                return true;
            }

            if (label.equalsIgnoreCase("경매장")) {
                contoller.openActionHouse(player, 1); // 경매장 첫 페이지 열기
                return true;
            }

            if (label.equalsIgnoreCase("송금") && args.length == 2) {
                contoller.sendMoney(player, args);
                return true;
            }

            // 운영자
//            if (label.equalsIgnoreCase("상점추가") && sender.hasPermission("economy.additem")) {
//                if (args.length != 3) {
//                    player.sendMessage(ChatColor.RED + "사용법: /상점추가 <카테고리> <아이템> <가격>");
//                    return true;
//                }
//                String category = args[0];
//                String itemName = args[1];
//                int price;
//                try {
//                    price = Integer.parseInt(args[2]);
//                } catch (NumberFormatException e) {
//                    player.sendMessage(ChatColor.RED + "가격은 숫자여야 합니다.");
//                    return true;
//                }
//
//                Material material = Material.getMaterial(itemName.toUpperCase());
//                if (material == null) {
//                    player.sendMessage(ChatColor.RED + "유효하지 않은 아이템입니다.");
//                    return true;
//                }
//
//                ItemStack item = new ItemStack(material);
//                ItemMeta meta = item.getItemMeta();
//                meta.setDisplayName(ChatColor.GOLD + material.name() + " - " + price + "원");
//                item.setItemMeta(meta);
//
//                switch (category.toLowerCase()) {
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
//
//                    default:
//                        player.sendMessage(ChatColor.RED + "유효하지 않은 카테고리입니다. (농장, 블록, 광물, 기타 중 선택)");
//                        return true;
//                }
//                player.sendMessage(ChatColor.GREEN + "상점에 아이템이 추가되었습니다!");
//                return true;
//            }
//        }

            if (label.equalsIgnoreCase("돈추가") && sender.hasPermission("economy.addmoney")) {
                contoller.addMoney(player, args);
                return true;
            }


            if (label.equalsIgnoreCase("길드")) {
                contoller.openGuildMenu(player);
                return true;
            }
            else if (label.equalsIgnoreCase("길드생성")) {
                contoller.createGuild(player, args[0]);
                return true;
            }
            else if (label.equalsIgnoreCase("길드초대")) {
                contoller.invitesGuild(player, args);
                return true;
            } else if (label.equalsIgnoreCase("길드수락")) {
                contoller.acceptInviteGuild(player);
                return true;
            } else if (label.equalsIgnoreCase("길드목록")) {
                contoller.openGuildListMenu(player);
                return true;
            } else if (label.equalsIgnoreCase("길드삭제")) {
                contoller.deleteGuild(player);
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "플레이어만 이 명령어를 사용할 수 있습니다.");
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();
        String title = ChatColor.stripColor(event.getView().getTitle());
        List<String> customInventories = Arrays.asList("경매장", "메인 상점", "길드 생성", "길드 메뉴", "길드원 목록", "길드 목록", "길드 설정", "강퇴");

        if (customInventories.contains(title)) {
            event.setCancelled(true);
            // 경매장일 때
            // 이후 클릭한 아이템에 따라 처리
            if (title.startsWith("경매장")) {
                contoller.actionHouseEvent(player, inventory, clickedItem);
            } else if (title.equals("길드 메뉴")) {
                if (clickedItem != null) {
                    if (clickedItem.getType() == Material.PLAYER_HEAD) {
                        contoller.openGuildMembersMenu(player);
                    } else if (clickedItem.getType() == Material.PAPER) {
                        contoller.openGuildSettingsMenu(player);
                    } else if (clickedItem.getType() == Material.BARRIER) {
                        contoller.deleteGuild(player);
                    } else if (clickedItem.getType() == Material.STRUCTURE_VOID) {
                        contoller.outGuild(player);
                    }
                }
            } else if (title.equals("길드 설정")) {
                if (clickedItem != null && clickedItem.getType() == Material.LEVER) {
                    contoller.toggleGuildPVP(player);
                }
            } else if (title.equals("길드원 목록")) {
                if (clickedItem != null && clickedItem.getType() == Material.PLAYER_HEAD) {
                    contoller.openKickMenu(clickedItem, player);
                }
            } else if (title.equals("강퇴")) {
                if (clickedItem != null) {
                    if (clickedItem.getType() == Material.LEVER) {
                        contoller.setSubLeader(player, clickedItem);
                    } else if (clickedItem.getType() == Material.PLAYER_HEAD) {
                        // 플레이어 머리 클릭 시
                        contoller.kickMember(player, clickedItem);
                    } else if (clickedItem.getType() == Material.ARROW) {
                        // 뒤로가기 클릭 시
                        contoller.openGuildMembersMenu(player);
                    }
                }
            }

            // 다른 상점 관련 코드 (이전과 동일)
            else if (event.getView().getTitle().equalsIgnoreCase(ChatColor.BLUE + "메인 상점")) {
                event.setCancelled(true);  // 아이템 임의 이동 방지

                // 상점관 ( 킵 )
//            if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().getDisplayName() != null) {
//                String displayName = clickedItem.getItemMeta().getDisplayName();
//                if (displayName.equals(ChatColor.GREEN + "농장 아이템")) {
//                    player.openInventory(farmShop);
//                } else if (displayName.equals(ChatColor.BLUE + "블록 아이템")) {
//                    player.openInventory(blockShop);
//                } else if (displayName.equals(ChatColor.YELLOW + "기타 아이템")) {
//                    player.openInventory(miscShop);
//                } else if (displayName.equals(ChatColor.RED + "광물 아이템")) {
//                    player.openInventory(mineralshop);
//                }
//            }
            }

            // 각 상점에서 아이템 구매 처리
//        else if (event.getView().getTitle().equalsIgnoreCase(ChatColor.GREEN + "농장 아이템 상점") ||
//                event.getView().getTitle().equalsIgnoreCase(ChatColor.BLUE + "블록 상점") ||
//                event.getView().getTitle().equalsIgnoreCase(ChatColor.YELLOW + "기타 아이템 상점") ||
//                event.getView().getTitle().equalsIgnoreCase(ChatColor.RED + "광물 아이템 상점")) {
//            event.setCancelled(true);  // 아이템 임의 이동 방지
//
//            if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
//                String displayName = clickedItem.getItemMeta().getDisplayName();
//                String[] parts = displayName.split(" - ");
//                if (parts.length == 2) {
//                    int price = Integer.parseInt(parts[1].replace("원", ""));
//                    if (playerBalances.getOrDefault(player.getUniqueId(), 0) >= price) {
//                        subtractMoney(player, price);
//                        player.getInventory().addItem(new ItemStack(clickedItem.getType(), 1));
//                        player.sendMessage(ChatColor.GREEN + "아이템을 구매하였습니다!");
//                        updateScoreboard(player);
//                    } else {
//                        player.sendMessage(ChatColor.RED + "소지금이 부족합니다!");
//                    }
//                }
//            }
//        }
        }
    }
    private boolean isValidGuildName(String name) {
        // 이름 길이 제한: 최소 3자, 최대 16자
        if (name.length() < 3 || name.length() > 16) {
            return false;
        }

        // 특수 문자 제외: 한글, 영어, 숫자만 허용
        return name.matches("^[a-zA-Z0-9가-힣]+$");
    }

//    @EventHandler
//    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
//        // 공격자와 피해자가 모두 플레이어인지 확인
//        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
//            Player damager = (Player) event.getDamager();
//            Player target = (Player) event.getEntity();
//
//            if(contoller.checkPVP(damager, target)){
//                event.setCancelled(true);
//            }
//        }
//    }
    public StockService getStockService() {
        return stockService;
    }

    @Override
    public Contoller getController() {
        return contoller;
    }


}
