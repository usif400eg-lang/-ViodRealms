package com.voxelpanel.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and opens a written book that lists every player command available in the plugin.
 * The book is generated dynamically so it always reflects the current command set.
 */
public final class BookUtil {

    private BookUtil() {
    }

    /** A single command entry: the command syntax and a short description. */
    private record CommandEntry(String usage, String description) {}

    private static final List<CommandEntry> PLAYER_COMMANDS = List.of(
            new CommandEntry("/tpu", "فتح قائمة الـ Waypoints"),
            new CommandEntry("/tpu <name>", "الانتقال لنقطة بالاسم"),
            new CommandEntry("/tpubook", "الحصول على كتاب الأوامر"),
            new CommandEntry("/waypoint set <name>", "إنشاء نقطة جديدة"),
            new CommandEntry("/waypoint del <name>", "حذف نقطة"),
            new CommandEntry("/waypoint list", "عرض قائمة نقاطك"),
            new CommandEntry("/waypoint rem <old> <new>", "تغيير اسم نقطة"),
            new CommandEntry("/waypoint category <name> <cat>", "تغيير فئة نقطة"),
            new CommandEntry("/waypoint icon <name> <material>", "تغيير أيقونة نقطة"),
            new CommandEntry("/waypoint public <name> <on|off>", "جعل نقطة عامة/خاصة"),
            new CommandEntry("/waypoint share <name> <player>", "مشاركة نقطة مع لاعب"),
            new CommandEntry("/compass track <name>", "تتبع نقطة بالبوصلة"),
            new CommandEntry("/compass reset", "إيقاف تتبع البوصلة"),
            new CommandEntry("/tpe <player>", "طلب انتقال للاعب"),
            new CommandEntry("/tpeaccept", "قبول طلب انتقال"),
            new CommandEntry("/tpedeny", "رفض طلب انتقال"),
            new CommandEntry("/back", "العودة لآخر موقع"),
            new CommandEntry("/waypoint export <file>", "تصدير نقاطك"),
            new CommandEntry("/waypoint import <file>", "استيراد نقاط"),
            new CommandEntry("/shareaccept", "قبول مشاركة نقطة"),
            new CommandEntry("/sharedeny", "رفض مشاركة نقطة"),
            new CommandEntry("/language", "فتح قائمة اختيار اللغة"),
            new CommandEntry("/language <ar|en>", "تغيير اللغة مباشرة")
    );

    /**
     * Gives the player a written book that lists all commands. The book opens
     * automatically on the client when created via {@link Player#openBook(ItemStack)}.
     */
    public static void giveCommandBook(Player player, String title, String author) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.title(Component.text(title));
        meta.author(Component.text(author));

        List<Component> pages = new ArrayList<>();
        Component page = Component.text(title, NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.newline());

        int entriesOnPage = 0;
        for (CommandEntry entry : PLAYER_COMMANDS) {
            Component entryComponent = Component.text(entry.usage(), NamedTextColor.DARK_BLUE, TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text(entry.description(), NamedTextColor.DARK_GRAY))
                    .append(Component.newline())
                    .append(Component.newline());

            // A written book page holds a limited amount of text; split every 5 entries.
            if (entriesOnPage >= 5) {
                pages.add(page);
                page = Component.empty();
                entriesOnPage = 0;
            }
            page = page.append(entryComponent);
            entriesOnPage++;
        }
        pages.add(page);

        meta.pages(pages);
        book.setItemMeta(meta);

        player.openBook(book);
    }
}
