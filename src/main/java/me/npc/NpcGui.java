package me.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class NpcGui implements Listener {

    private static class Holder implements InventoryHolder {
        final String type;
        Inventory inv;
        Holder(String type) { this.type = type; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final NpcPlugin plugin;

    public NpcGui(NpcPlugin plugin) { this.plugin = plugin; }

    private Settings s() { return plugin.settings(); }
    private NpcManager m() { return plugin.manager(); }

    // ------------------------------------------------------------------ open
    public void openMain(Player p) {
        Holder h = new Holder("main");
        h.inv = Bukkit.createInventory(h, 36, Component.text("NPC Settings"));
        renderMain(h.inv);
        p.openInventory(h.inv);
    }

    public void openTeams(Player p) {
        Holder h = new Holder("teams");
        h.inv = Bukkit.createInventory(h, 27, Component.text("NPC Teams"));
        renderTeams(h.inv);
        p.openInventory(h.inv);
    }

    // ------------------------------------------------------------------ render
    private void fill(Inventory inv) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private void renderMain(Inventory inv) {
        fill(inv);
        Settings s = s();
        inv.setItem(10, item(Material.IRON_SWORD, "Difficulty: " + s.difficulty, NamedTextColor.GOLD,
                "EASY / NORMAL / HARD / EXPERT",
                "Controls aim, reaction time, crits, strafing.",
                "Left click: next   Right click: previous",
                "Applies to every bot right now."));
        inv.setItem(11, item(Material.GRASS_BLOCK, "Chunk radius: " + s.chunkRadius, NamedTextColor.GREEN,
                "How many chunks each bot loads (1-32).",
                "Left +1  Right -1  Shift = 4 at a time",
                "Capped by server.properties view-distance.",
                "Paper may raise 1 to 2."));
        inv.setItem(12, item(Material.COMPASS, "Formation: " + s.formation, NamedTextColor.AQUA,
                "GRID LINE COLUMN CIRCLE WEDGE TRIANGLE SQUARE SPIRAL",
                "Left click: next   Right click: previous"));
        inv.setItem(13, item(Material.LADDER, "Spacing: " + s.spacing, NamedTextColor.AQUA,
                "Blocks between bots in a formation.",
                "Left +0.5  Right -0.5"));
        inv.setItem(14, item(Material.CHEST, "Max bots: " + s.maxNpcs, NamedTextColor.YELLOW,
                "Left +10  Right -10  (more bots = more lag)"));
        inv.setItem(15, item(Material.EGG, "Spawn count: " + s.spawnCount, NamedTextColor.YELLOW,
                "How many bots the Spawn button adds.",
                "Left +1  Right -1  Shift = 5 at a time"));
        inv.setItem(16, item(Material.DIAMOND_CHESTPLATE, "Gear: " + s.gear, NamedTextColor.WHITE,
                "none / wood / stone / iron / diamond / netherite",
                "Left: next   Right: previous",
                "Applies to every bot right now."));

        inv.setItem(19, toggle(Material.GOLDEN_SWORD, "Friendly fire", s.friendlyFire,
                "Can teammates hurt each other?"));
        inv.setItem(20, toggle(Material.TOTEM_OF_UNDYING, "Auto-respawn", s.respawn,
                "Bots come back at their start point after dying."));
        inv.setItem(21, toggle(Material.PAPER, "Silent join/quit", s.silent,
                "Hide bot join, quit and death messages."));
        inv.setItem(22, item(Material.WHITE_BANNER, "Teams...", NamedTextColor.LIGHT_PURPLE,
                "Open the team menu."));

        inv.setItem(28, item(Material.EMERALD, "Spawn " + s.spawnCount + " bots", NamedTextColor.GREEN,
                "Spawned in front of you in the chosen formation."));
        inv.setItem(29, item(Material.ENDER_PEARL, "Apply formation", NamedTextColor.AQUA,
                "Left click: arrange all bots in front of you",
                "Right click: arrange them AND make them keep",
                "the formation while you walk around."));
        inv.setItem(30, item(Material.DIAMOND_SWORD, "FIGHT", NamedTextColor.RED,
                "All bots attack anyone not on their team.",
                "(With no teams that means everyone, bots too.)"));
        inv.setItem(31, item(Material.RED_DYE, "Stop all bots", NamedTextColor.RED,
                "Cancel attacking, following and formations."));
        inv.setItem(32, item(Material.BARRIER, "Remove all bots", NamedTextColor.DARK_RED,
                "Removes every bot."));
    }

    private void renderTeams(Inventory inv) {
        fill(inv);
        inv.setItem(4, toggle(Material.GOLDEN_SWORD, "Friendly fire", s().friendlyFire,
                "Can teammates hurt each other?"));
        inv.setItem(10, item(Material.RED_BANNER, "Join Red", NamedTextColor.RED, "Put yourself on Red."));
        inv.setItem(11, item(Material.BLUE_BANNER, "Join Blue", NamedTextColor.BLUE, "Put yourself on Blue."));
        inv.setItem(12, item(Material.RED_WOOL, "All bots -> Red", NamedTextColor.RED, "Every bot joins Red."));
        inv.setItem(13, item(Material.BLUE_WOOL, "All bots -> Blue", NamedTextColor.BLUE, "Every bot joins Blue."));
        inv.setItem(14, item(Material.WHITE_WOOL, "Split bots evenly", NamedTextColor.WHITE, "Half Red, half Blue."));
        inv.setItem(15, item(Material.LIME_BANNER, "Split EVERYONE", NamedTextColor.GREEN,
                "Players and bots, shuffled into Red and Blue."));
        inv.setItem(16, item(Material.BARRIER, "Disband Red & Blue", NamedTextColor.DARK_RED, "Deletes both teams."));
        inv.setItem(22, item(Material.ARROW, "Back", NamedTextColor.GRAY, ""));
    }

    private ItemStack toggle(Material m, String name, boolean on, String... lore) {
        List<String> l = new ArrayList<>(List.of(lore));
        l.add("Click to toggle.");
        return item(m, name + ": " + (on ? "ON" : "OFF"), on ? NamedTextColor.GREEN : NamedTextColor.RED, l.toArray(new String[0]));
    }

    private ItemStack item(Material m, String name, NamedTextColor color, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String line : lore) if (!line.isEmpty())
            l.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ clicks
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;

        int dir = e.isLeftClick() ? 1 : e.isRightClick() ? -1 : 0;
        if (dir == 0) return;
        boolean shift = e.isShiftClick();
        int slot = e.getSlot();

        if (h.type.equals("main")) {
            mainClick(p, slot, dir, shift, e.isRightClick());
            renderMain(h.inv);
        } else {
            teamClick(p, slot);
            if (slot == 22) { openMain(p); return; }
            renderTeams(h.inv);
        }
        s().save(plugin);
    }

    private void mainClick(Player p, int slot, int dir, boolean shift, boolean right) {
        Settings s = s();
        switch (slot) {
            case 10 -> {
                s.difficulty = dir > 0 ? s.difficulty.next() : s.difficulty.prev();
                m().all().forEach(n -> n.difficulty = s.difficulty);
            }
            case 11 -> {
                s.chunkRadius = Math.max(1, Math.min(32, s.chunkRadius + dir * (shift ? 4 : 1)));
                m().all().forEach(n -> m().applyChunks(n, s.chunkRadius));
            }
            case 12 -> s.formation = dir > 0 ? s.formation.next() : s.formation.prev();
            case 13 -> s.spacing = Math.max(1.0, Math.min(10.0, s.spacing + dir * 0.5));
            case 14 -> s.maxNpcs = Math.max(1, Math.min(500, s.maxNpcs + dir * 10));
            case 15 -> s.spawnCount = Math.max(1, Math.min(200, s.spawnCount + dir * (shift ? 5 : 1)));
            case 16 -> {
                int i = NpcManager.GEAR.indexOf(s.gear);
                int n = NpcManager.GEAR.size();
                s.gear = NpcManager.GEAR.get(((i + dir) % n + n) % n);
                m().all().forEach(b -> m().giveGear(b, s.gear));
            }
            case 19 -> { s.friendlyFire = !s.friendlyFire; Teams.applyFriendlyFire(s.friendlyFire); }
            case 20 -> s.respawn = !s.respawn;
            case 21 -> s.silent = !s.silent;
            case 22 -> openTeams(p);
            case 28 -> m().spawnMany(p, s.spawnCount, "Bot", null,
                    n -> p.sendMessage("Spawned " + n + " bots (total " + m().count() + ")."));
            case 29 -> m().applyFormation(p, new ArrayList<>(m().all()), s.formation, s.spacing, right);
            case 30 -> m().all().forEach(n -> { n.attackSpec = "@enemy"; n.followTarget = null; });
            case 31 -> m().all().forEach(NpcPlayer::stopAll);
            case 32 -> p.sendMessage("Removed " + m().removeAll() + " bots.");
            default -> { }
        }
    }

    private void teamClick(Player p, int slot) {
        Settings s = s();
        List<Player> bots = new ArrayList<>();
        m().all().forEach(n -> bots.add(n.getBukkitEntity()));
        switch (slot) {
            case 4 -> { s.friendlyFire = !s.friendlyFire; Teams.applyFriendlyFire(s.friendlyFire); }
            case 10 -> Teams.add("Red", p, s.friendlyFire);
            case 11 -> Teams.add("Blue", p, s.friendlyFire);
            case 12 -> bots.forEach(b -> Teams.add("Red", b, s.friendlyFire));
            case 13 -> bots.forEach(b -> Teams.add("Blue", b, s.friendlyFire));
            case 14 -> Teams.split("Red", "Blue", bots, s.friendlyFire);
            case 15 -> {
                List<Player> everyone = new ArrayList<>(Bukkit.getOnlinePlayers());
                Teams.split("Red", "Blue", everyone, s.friendlyFire);
            }
            case 16 -> { Teams.delete("Red"); Teams.delete("Blue"); }
            default -> { }
        }
    }
}
