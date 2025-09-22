package ActionHouse;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ActionHouseItem {
    // 경매 아이템 클래스 정의
    private UUID owner;
    private ItemStack item;
    private int price;

    public ActionHouseItem(UUID owner, ItemStack item, int price) {
        this.owner = owner;
        this.item = item;
        this.price = price;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public int getPrice() {
        return this.price;
    }

}
