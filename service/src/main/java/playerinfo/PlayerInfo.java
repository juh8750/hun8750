package playerinfo;

import org.bukkit.entity.Player;

public class PlayerInfo {
    private Player player;
    private long money;
    private String guild;

    public PlayerInfo(Player player, long money, String guild) {

    }

    public Player getPlayer() {
        return player;
    }

    public long getMoney() {
        return money;
    }

    public String getGuild() {
        return guild;
    }

    public void setMoney(long money) {
        this.money = money;
    }

    public void setGuild(String guild) {
        this.guild = guild;
    }

    public void addMoney(long money) {
        this.money += money;
    }
}
