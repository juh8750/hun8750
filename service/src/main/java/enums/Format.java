package enums;

import org.bukkit.ChatColor;

/* loaded from: NickName.jar:kr/cosine/nickname/enums/Format.class */
public enum Format {
    JOIN("§a[+] §f%player%"),
    QUIT("§c[-] §f%player%");

    private String message;

    Format(String message) {
        this.message = message;
    }

    public String getMessage(String nickname) {
        return this.message.replace("%player%", nickname);
    }

    public void setMessage(String message) {
        this.message = colorize(message);
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}