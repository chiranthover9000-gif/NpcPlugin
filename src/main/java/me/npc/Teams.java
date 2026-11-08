package me.npc;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.List;

/** Teams are plain scoreboard teams, so name colours and friendly-fire rules are handled by vanilla. */
public final class Teams {
    private Teams() {}

    public static Scoreboard board() { return Bukkit.getScoreboardManager().getMainScoreboard(); }

    public static Team of(Player p) { return board().getEntryTeam(p.getName()); }

    public static boolean same(Player a, Player b) {
        Team x = of(a);
        return x != null && x.equals(of(b));
    }

    public static Team ensure(String name, boolean friendlyFire) {
        Team t = board().getTeam(name);
        if (t == null) {
            t = board().registerNewTeam(name);
            NamedTextColor c = NamedTextColor.NAMES.value(name.toLowerCase());
            t.color(c == null ? NamedTextColor.WHITE : c);
        }
        t.setAllowFriendlyFire(friendlyFire);
        return t;
    }

    public static void add(String team, Player p, boolean friendlyFire) {
        ensure(team, friendlyFire).addEntry(p.getName());
    }

    public static void leave(Player p) {
        Team t = of(p);
        if (t != null) t.removeEntry(p.getName());
    }

    public static boolean delete(String name) {
        Team t = board().getTeam(name);
        if (t == null) return false;
        t.unregister();
        return true;
    }

    public static void applyFriendlyFire(boolean ff) {
        for (Team t : board().getTeams()) t.setAllowFriendlyFire(ff);
    }

    /** Shuffles the given players and alternates them between two teams. */
    public static void split(String a, String b, List<Player> players, boolean ff) {
        List<Player> list = new java.util.ArrayList<>(players);
        Collections.shuffle(list);
        for (int i = 0; i < list.size(); i++) add(i % 2 == 0 ? a : b, list.get(i), ff);
    }
}
