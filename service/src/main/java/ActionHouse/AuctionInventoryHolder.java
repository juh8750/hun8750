package ActionHouse;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AuctionInventoryHolder implements InventoryHolder {
    private int page;

    public AuctionInventoryHolder(int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null; // 필요에 따라 구현
    }
}