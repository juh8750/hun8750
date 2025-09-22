package ActionHouse;

import DAO.DataBase;

import java.util.HashMap;

public class ActionHouseDTO {
    private HashMap<Integer, ActionHouseItem> auctionItems = new HashMap<>();
    private DataBase dataBase;

    public ActionHouseDTO(DataBase dataBase) {
        this.dataBase = dataBase;
    }

    public HashMap<Integer, ActionHouseItem> getAuctionItems() {
        return auctionItems;
    }

    public int getActionHouseItemID(){
        for(int i=1; i<100000; i++){
            //해당 키가 존재하지 않으면 반환
            if(!auctionItems.containsKey(i)){
                return i;
            }
        }

        return -1;
    }

    public void removeActionItem(int actionHouseItemID){
        auctionItems.remove(actionHouseItemID);
        dataBase.removeAuctionItemFromDatabase(actionHouseItemID);
    }

    public void addActionItem(int actionHouseItemID, ActionHouseItem actionHouseItem){
        auctionItems.put(actionHouseItemID, actionHouseItem);
        dataBase.saveAuctionData(actionHouseItemID,actionHouseItem);
    }

    public void getActionHouseInfo(){
        auctionItems = dataBase.loadAuctionData();
    }

}
