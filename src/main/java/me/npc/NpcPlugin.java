package me.npc;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.function.Consumer;

public class NpcPlugin extends JavaPlugin implements Listener {

    private static NpcPlugin instance;
    private final Settings settings = new Settings();
    private NpcManager npcs;
    private NpcGui gui;

    public static NpcPlugin get() { return instance; }
    public Settings settings() { return settings; }
    public NpcManager manager() { return npcs; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        settings.load(getConfig());
        npcs = new NpcManager(this, settings);
        gui = new NpcGui(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
    }

    @Override
    public void onDisable() {
        if (npcs != null) npcs.removeAll();
    }

    // ------------------------------------------------------------------ events
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        if (settings.silent && NpcManager.isNpc(e.getPlayer())) e.joinMessage(null);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        if (settings.silent && NpcManager.isNpc(e.getPlayer())) e.quitMessage(null);
    }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        NpcPlayer n = NpcManager.asNpc(e.getPlayer());
        if (n == null) return;
        if (settings.silent) e.deathMessage(null);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (settings.respawn) npcs.respawn(n); else npcs.remove(n);
        }, Math.max(1, settings.respawnDelay));
    }

    // ------------------------------------------------------------------ selectors
    /**
     * @a        every real player           @b        every bot
     * @all      players + bots              @b:red    bots on team "red"
     * @t:red    everyone on team "red"      @enemy    everyone not on the asker's team
     * Bot*      name prefix                 Steve     exact name
     */
    public List<Player> resolve(String spec, NpcPlayer asker) {
        String s = spec.toLowerCase();
        List<Player> out = new ArrayList<>();
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();

        if (s.equals("@a")) {
            for (Player p : online) if (!NpcManager.isNpc(p)) out.add(p);
        } else if (s.equals("@b")) {
            for (NpcPlayer n : npcs.all()) out.add(n.getBukkitEntity());
        } else if (s.equals("@all")) {
            out.addAll(online);
        } else if (s.startsWith("@b:")) {
            String team = s.substring(3);
            for (NpcPlayer n : npcs.all()) if (inTeam(n.getBukkitEntity(), team)) out.add(n.getBukkitEntity());
        } else if (s.startsWith("@t:")) {
            String team = s.substring(3);
            for (Player p : online) if (inTeam(p, team)) out.add(p);
        } else if (s.equals("@enemy")) {
            Player me = asker == null ? null : asker.getBukkitEntity();
            for (Player p : online) if (me == null || (!p.equals(me) && !Teams.same(me, p))) out.add(p);
        } else if (s.endsWith("*")) {
            String pre = s.substring(0, s.length() - 1);
            for (Player p : online) if (p.getName().toLowerCase().startsWith(pre)) out.add(p);
        } else {
            Player p = Bukkit.getPlayerExact(spec);
            if (p != null) out.add(p);
        }
        return out;
    }

    private boolean inTeam(Player p, String team) {
        Team t = Teams.of(p);
        return t != null && t.getName().equalsIgnoreCase(team);
    }

    public List<NpcPlayer> bots(String spec) {
        List<NpcPlayer> out = new ArrayList<>();
        for (Player p : resolve(spec, null)) {
            NpcPlayer n = NpcManager.asNpc(p);
            if (n != null) out.add(n);
        }
        return out;
    }

    // ------------------------------------------------------------------ commands
    private static final String HELP = """
        /npc gui                                 - open the settings GUI
        Selectors: @b all bots, @b:red bots on team red, @a players, @all everyone,
                   @t:red team red, @enemy anyone not on your team, Bot* prefix, or a name
        /npc spawn <name> [skin]                 - one bot at your position
        /npc spawnmany <count> [prefix] [skin]   - bots in the current formation
        /npc remove <bots>    /npc list
        /npc attack <victim> [bots=@b]           - e.g. /npc attack _Steve @b
        /npc stop <bots>                         - cancel attack/follow/formation/walk
        /npc difficulty <bots> <easy|normal|hard|expert>
        /npc chunks <bots> <1-32>
        /npc formation <bots> <type> [spacing] [hold]
             types: grid line column circle wedge triangle square spiral
        /npc gear <bots> <none|wood|stone|iron|diamond|netherite>
        /npc team create <team> [color]   /npc team add <team> <selectors...>
        /npc team remove <selectors...>   /npc team delete <team>   /npc team list
        /npc team split <A> <B> [selector=@all]   /npc team ff <on|off>
        /npc tp|walk|sprint|sneak|jump|swing|look|lookme|follow|say|cmd|hold <bots> ...
        """;

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { s.sendMessage(HELP); return true; }
        String sub = a[0].toLowerCase();

        switch (sub) {
            case "gui", "settings" -> {
                if (s instanceof Player p) gui.openMain(p); else s.sendMessage("In-game only.");
            }
            case "spawn" -> {
                if (!(s instanceof Player p) || a.length < 2) { s.sendMessage("Usage: /npc spawn <name> [skin]"); return true; }
                npcs.withSkin(a.length > 2 ? a[2] : null, props -> {
                    NpcPlayer n = npcs.spawn(a[1], p.getLocation(), props);
                    s.sendMessage(n == null ? "Could not spawn (name taken or cap of " + npcs.max() + " reached)." : "Spawned " + a[1]);
                });
            }
            case "spawnmany" -> {
                if (!(s instanceof Player p) || a.length < 2) { s.sendMessage("Usage: /npc spawnmany <count> [prefix] [skin]"); return true; }
                int count;
                try { count = Integer.parseInt(a[1]); } catch (NumberFormatException e) { s.sendMessage("Count must be a number."); return true; }
                npcs.spawnMany(p, count, a.length > 2 ? a[2] : "Bot", a.length > 3 ? a[3] : null,
                        n -> s.sendMessage("Spawned " + n + " bots (total " + npcs.count() + ")."));
            }
            case "remove" -> {
                if (a.length < 2) { s.sendMessage("Usage: /npc remove <bots>"); return true; }
                List<NpcPlayer> t = bots(a[1]);
                t.forEach(npcs::remove);
                s.sendMessage("Removed " + t.size() + " bot(s).");
            }
            case "list" -> s.sendMessage(npcs.count() + "/" + npcs.max() + " bots: " +
                    String.join(", ", npcs.all().stream().map(n -> n.getBukkitEntity().getName()).toList()));
            case "attack" -> {
                if (a.length < 2) { s.sendMessage("Usage: /npc attack <victim> [bots=@b]"); return true; }
                List<NpcPlayer> t = bots(a.length > 2 ? a[2] : "@b");
                if (t.isEmpty()) { s.sendMessage("No bots matched."); return true; }
                for (NpcPlayer n : t) { n.attackSpec = a[1]; n.followTarget = null; n.formationLeader = null; n.slot = null; }
                s.sendMessage(t.size() + " bot(s) now attack " + a[1] + ".");
            }
            case "team" -> teamCmd(s, a);
            default -> control(s, sub, a);
        }
        return true;
    }

    private void control(CommandSender s, String sub, String[] a) {
        if (a.length < 2) { s.sendMessage("Usage: /npc " + sub + " <bots> ..."); return; }
        List<NpcPlayer> t = bots(a[1]);
        if (t.isEmpty()) { s.sendMessage("No bots matched '" + a[1] + "'."); return; }
        String arg = a.length > 2 ? a[2].toLowerCase() : "";

        switch (sub) {
            case "stop" -> t.forEach(NpcPlayer::stopAll);
            case "difficulty" -> {
                Difficulty d = Difficulty.parse(arg);
                if (d == null) { s.sendMessage("Use: easy, normal, hard or expert."); return; }
                t.forEach(n -> n.difficulty = d);
            }
            case "chunks" -> {
                int r;
                try { r = Integer.parseInt(arg); } catch (NumberFormatException e) { s.sendMessage("Give a number 1-32."); return; }
                r = Math.max(1, Math.min(32, r));
                int used = r;
                for (NpcPlayer n : t) used = npcs.applyChunks(n, r);
                if (used != r) s.sendMessage("Paper refused " + r + "; using " + used + " instead.");
            }
            case "formation" -> {
                if (!(s instanceof Player p)) return;
                Formation f = Formation.parse(arg);
                if (f == null) { s.sendMessage("Types: grid line column circle wedge triangle square spiral"); return; }
                double spacing = settings.spacing;
                boolean hold = false;
                for (int i = 3; i < a.length; i++) {
                    if (a[i].equalsIgnoreCase("hold")) hold = true;
                    else try { spacing = Double.parseDouble(a[i]); } catch (NumberFormatException ignored) { }
                }
                npcs.applyFormation(p, t, f, spacing, hold);
            }
            case "gear" -> { if (!npcs.giveGear(t.get(0), arg)) { s.sendMessage("Tiers: " + NpcManager.GEAR); return; }
                             t.forEach(n -> npcs.giveGear(n, arg)); }
            case "tp" -> { if (s instanceof Player p) each(t, n -> n.getBukkitEntity().teleport(p.getLocation())); }
            case "walk" -> each(t, n -> {
                switch (arg) {
                    case "forward" -> { n.forward = 1; n.strafe = 0; }
                    case "back" -> { n.forward = -1; n.strafe = 0; }
                    case "left" -> { n.strafe = 1; n.forward = 0; }
                    case "right" -> { n.strafe = -1; n.forward = 0; }
                    default -> { n.forward = 0; n.strafe = 0; }
                }
            });
            case "sprint" -> each(t, n -> n.sprint = arg.equals("on"));
            case "sneak" -> each(t, n -> n.getBukkitEntity().setSneaking(arg.equals("on")));
            case "jump" -> each(t, n -> { if (arg.equals("once")) n.jumpOnce = true; else n.autoJump = arg.equals("on"); });
            case "swing" -> each(t, n -> {
                Player b = n.getBukkitEntity();
                var e = b.getTargetEntity(4);
                if (e != null) b.attack(e);
                b.swingMainHand();
            });
            case "look" -> {
                if (a.length < 4) { s.sendMessage("Usage: /npc look <bots> <yaw> <pitch>"); return; }
                float yaw, pitch;
                try { yaw = Float.parseFloat(a[2]); pitch = Float.parseFloat(a[3]); }
                catch (NumberFormatException e) { s.sendMessage("Yaw and pitch must be numbers."); return; }
                each(t, n -> { n.setYRot(yaw); n.setXRot(pitch); n.setYHeadRot(yaw); });
            }
            case "lookme" -> { if (s instanceof Player p) each(t, n -> {
                var l = n.getBukkitEntity().getLocation();
                l.setDirection(p.getEyeLocation().toVector().subtract(n.getBukkitEntity().getEyeLocation().toVector()));
                n.setYRot(l.getYaw()); n.setXRot(l.getPitch()); n.setYHeadRot(l.getYaw());
            }); }
            case "follow" -> {
                if (arg.isEmpty() || arg.equals("off")) { each(t, n -> { n.followTarget = null; }); break; }
                Player target = Bukkit.getPlayerExact(a[2]);
                if (target == null) { s.sendMessage("Player not online."); return; }
                each(t, n -> { n.followTarget = target.getUniqueId(); n.formationLeader = null; n.slot = null; });
            }
            case "say" -> {
                String msg = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                each(t, n -> n.getBukkitEntity().chat(msg));
            }
            case "cmd" -> {
                String c = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                each(t, n -> n.getBukkitEntity().performCommand(c));
            }
            case "hold" -> { if (s instanceof Player p) each(t, n ->
                    n.getBukkitEntity().getInventory().setItemInMainHand(p.getInventory().getItemInMainHand().clone())); }
            default -> { s.sendMessage("Unknown subcommand. /npc help"); return; }
        }
        s.sendMessage("OK (" + t.size() + " bot" + (t.size() == 1 ? "" : "s") + ").");
    }

    private void teamCmd(CommandSender s, String[] a) {
        if (a.length < 2) { s.sendMessage("Usage: /npc team <create|add|remove|delete|list|split|ff> ..."); return; }
        boolean ff = settings.friendlyFire;
        switch (a[1].toLowerCase()) {
            case "create" -> {
                if (a.length < 3) return;
                Team t = Teams.ensure(a[2], ff);
                if (a.length > 3) {
                    var c = net.kyori.adventure.text.format.NamedTextColor.NAMES.value(a[3].toLowerCase());
                    if (c != null) t.color(c);
                }
                s.sendMessage("Team " + a[2] + " ready.");
            }
            case "add" -> {
                if (a.length < 4) { s.sendMessage("Usage: /npc team add <team> <selectors...>"); return; }
                int n = 0;
                for (int i = 3; i < a.length; i++)
                    for (Player p : resolve(a[i], null)) { Teams.add(a[2], p, ff); n++; }
                s.sendMessage("Added " + n + " to " + a[2] + ".");
            }
            case "remove" -> {
                int n = 0;
                for (int i = 2; i < a.length; i++)
                    for (Player p : resolve(a[i], null)) { Teams.leave(p); n++; }
                s.sendMessage("Removed " + n + " from their teams.");
            }
            case "delete" -> s.sendMessage(a.length > 2 && Teams.delete(a[2]) ? "Deleted." : "No such team.");
            case "list" -> {
                for (Team t : Teams.board().getTeams())
                    s.sendMessage(t.getName() + " (" + t.getEntries().size() + "): " + String.join(", ", t.getEntries()));
                if (Teams.board().getTeams().isEmpty()) s.sendMessage("No teams.");
            }
            case "split" -> {
                if (a.length < 4) { s.sendMessage("Usage: /npc team split <A> <B> [selector]"); return; }
                List<Player> who = resolve(a.length > 4 ? a[4] : "@all", null);
                Teams.split(a[2], a[3], who, ff);
                s.sendMessage("Split " + who.size() + " into " + a[2] + " and " + a[3] + ".");
            }
            case "ff" -> {
                settings.friendlyFire = a.length > 2 && a[2].equalsIgnoreCase("on");
                Teams.applyFriendlyFire(settings.friendlyFire);
                settings.save(this);
                s.sendMessage("Friendly fire " + (settings.friendlyFire ? "ON" : "OFF"));
            }
            default -> s.sendMessage("Unknown team subcommand.");
        }
    }

    private void each(List<NpcPlayer> list, Consumer<NpcPlayer> c) { list.forEach(c); }

    // ------------------------------------------------------------------ tab complete
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> selectors = new ArrayList<>(List.of("@b", "@a", "@all", "@enemy", "@b:red", "@b:blue", "@t:red", "@t:blue"));
        npcs.all().forEach(n -> selectors.add(n.getBukkitEntity().getName()));
        if (a.length == 1) return List.of("gui", "spawn", "spawnmany", "remove", "list", "attack", "stop", "difficulty",
                "chunks", "formation", "gear", "team", "tp", "walk", "sprint", "sneak", "jump", "swing", "look",
                "lookme", "follow", "say", "cmd", "hold", "help");
        String sub = a[0].toLowerCase();
        if (sub.equals("attack") && a.length == 2) { List<String> r = new ArrayList<>(selectors); Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName())); return r; }
        if (sub.equals("attack") && a.length == 3) return selectors;
        if (sub.equals("team")) {
            if (a.length == 2) return List.of("create", "add", "remove", "delete", "list", "split", "ff");
            if (a.length >= 4 && a[1].equalsIgnoreCase("add")) return selectors;
            return List.of();
        }
        if (a.length == 2 && !List.of("spawn", "spawnmany", "list").contains(sub)) return selectors;
        if (a.length == 3) switch (sub) {
            case "walk": return List.of("forward", "back", "left", "right", "stop");
            case "sprint": case "sneak": return List.of("on", "off");
            case "jump": return List.of("on", "off", "once");
            case "difficulty": return List.of("easy", "normal", "hard", "expert");
            case "formation": return Arrays.stream(Formation.values()).map(f -> f.name().toLowerCase()).toList();
            case "gear": return NpcManager.GEAR;
            case "chunks": return List.of("1", "2", "4", "8", "16", "32");
            default: return List.of();
        }
        if (a.length >= 4 && sub.equals("formation")) return List.of("hold", "2", "3");
        return List.of();
    }
}
