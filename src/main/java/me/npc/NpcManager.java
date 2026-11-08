package me.npc;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class NpcManager {

    public static final List<String> GEAR = List.of("none", "wood", "stone", "iron", "diamond", "netherite");

    private final NpcPlugin plugin;
    private final Settings settings;
    private final Map<String, NpcPlayer> npcs = new LinkedHashMap<>();
    private final Map<String, Collection<ProfileProperty>> skinCache = new ConcurrentHashMap<>();
    private int nameIdx = 1;

    public NpcManager(NpcPlugin plugin, Settings settings) { this.plugin = plugin; this.settings = settings; }

    public Collection<NpcPlayer> all() { return npcs.values(); }
    public NpcPlayer get(String name) { return npcs.get(name.toLowerCase()); }
    public int count() { return npcs.size(); }
    public int max() { return settings.maxNpcs; }

    public static NpcPlayer asNpc(Player p) {
        return ((CraftPlayer) p).getHandle() instanceof NpcPlayer n ? n : null;
    }
    public static boolean isNpc(Player p) { return asNpc(p) != null; }

    // ------------------------------------------------------------------ skins
    public void withSkin(String skinName, Consumer<Collection<ProfileProperty>> cb) {
        if (skinName == null) { cb.accept(List.of()); return; }
        Collection<ProfileProperty> cached = skinCache.get(skinName.toLowerCase());
        if (cached != null) { cb.accept(cached); return; }
        Bukkit.createProfile(skinName).update().whenComplete((profile, err) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                Collection<ProfileProperty> props = (err != null || profile == null)
                        ? List.of() : new ArrayList<>(profile.getProperties());
                if (!props.isEmpty()) skinCache.put(skinName.toLowerCase(), props);
                cb.accept(props);
            }));
    }

    // ------------------------------------------------------------------ spawning
    /** Main thread only. */
    public NpcPlayer spawn(String name, Location loc, Collection<ProfileProperty> skin) {
        if (name.length() > 16) name = name.substring(0, 16);
        if (npcs.containsKey(name.toLowerCase()) || npcs.size() >= max()) return null;

        Multimap<String, Property> map = LinkedHashMultimap.create();
        for (ProfileProperty p : skin) map.put(p.getName(), new Property(p.getName(), p.getValue(), p.getSignature()));
        GameProfile profile = new GameProfile(UUID.randomUUID(), name, new PropertyMap(map));

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();

        NpcPlayer npc = new NpcPlayer(server, level, profile);
        npc.setPos(loc.getX(), loc.getY(), loc.getZ());
        npc.difficulty = settings.difficulty;
        npc.home = loc.clone();
        npc.skin = skin;
        server.getPlayerList().placeNewPlayer(new NpcConnection(), npc, CommonListenerCookie.createInitial(profile, false));
        npc.getBukkitEntity().teleport(loc);
        npcs.put(name.toLowerCase(), npc);

        applyChunks(npc, settings.chunkRadius);
        giveGear(npc, settings.gear);
        return npc;
    }

    public void remove(NpcPlayer npc) {
        npcs.values().remove(npc);
        ((CraftServer) Bukkit.getServer()).getServer().getPlayerList().remove(npc);
    }

    public int removeAll() {
        List<NpcPlayer> copy = new ArrayList<>(npcs.values());
        copy.forEach(this::remove);
        return copy.size();
    }

    /** Re-creates a dead bot at its home position, keeping its settings. */
    public void respawn(NpcPlayer old) {
        String name = old.getBukkitEntity().getName();
        if (npcs.get(name.toLowerCase()) != old) return;
        remove(old);
        NpcPlayer n = spawn(name, old.home, old.skin);
        if (n == null) return;
        n.difficulty = old.difficulty;
        n.attackSpec = old.attackSpec;
        n.followTarget = old.followTarget;
        n.formationLeader = old.formationLeader;
        n.slot = old.slot;
        applyChunks(n, old.chunkRadius);
        giveGear(n, old.gearTier);
    }

    /** Spawns many bots in the configured formation, a few per tick. */
    public void spawnMany(Player owner, int count, String prefix, String skinName, Consumer<Integer> done) {
        Location base = owner.getLocation();
        List<Location> spots = Formation.toLocations(base,
                Formation.shifted(settings.formation, count, settings.spacing, false));
        withSkin(skinName, skin -> new BukkitRunnable() {
            int i = 0, spawned = 0;
            @Override public void run() {
                for (int n = 0; n < settings.spawnPerTick && i < count; n++, i++) {
                    if (npcs.size() >= max()) { i = count; break; }
                    String name;
                    do { name = prefix + (nameIdx++); } while (npcs.containsKey(name.toLowerCase()));
                    if (spawn(name, spots.get(i), skin) != null) spawned++;
                }
                if (i >= count) { cancel(); done.accept(spawned); }
            }
        }.runTaskTimer(plugin, 0L, 1L));
    }

    // ------------------------------------------------------------------ formations
    public void applyFormation(Player leader, List<NpcPlayer> bots, Formation f, double spacing, boolean hold) {
        if (bots.isEmpty()) return;
        List<double[]> local = Formation.shifted(f, bots.size(), spacing, hold);
        List<Location> spots = Formation.toLocations(leader.getLocation(), local);
        for (int i = 0; i < bots.size(); i++) {
            NpcPlayer n = bots.get(i);
            n.getBukkitEntity().teleport(spots.get(i));
            n.followTarget = null;
            if (hold) { n.formationLeader = leader.getUniqueId(); n.slot = local.get(i); }
            else { n.formationLeader = null; n.slot = null; }
        }
    }

    // ------------------------------------------------------------------ chunks
    /** Returns the radius actually applied (Paper may refuse very small values). */
    public int applyChunks(NpcPlayer npc, int radius) {
        npc.chunkRadius = radius;
        Player p = npc.getBukkitEntity();
        int used = radius;
        try {
            p.setViewDistance(used);
            p.setSendViewDistance(used);
            p.setSimulationDistance(used);
        } catch (IllegalArgumentException ex) {
            used = Math.max(2, radius);
            try {
                p.setViewDistance(used);
                p.setSendViewDistance(used);
                p.setSimulationDistance(used);
            } catch (IllegalArgumentException ignored) { }
        }
        return used;
    }

    // ------------------------------------------------------------------ gear
    public boolean giveGear(NpcPlayer npc, String tier) {
        tier = tier.toLowerCase();
        String tool, armor;
        if (tier.equals("none")) tool = armor = null;
        else if (tier.equals("wood")) { tool = "WOODEN"; armor = "LEATHER"; }
        else if (tier.equals("stone")) { tool = "STONE"; armor = "CHAINMAIL"; }
        else if (tier.equals("iron")) { tool = "IRON"; armor = "IRON"; }
        else if (tier.equals("diamond")) { tool = "DIAMOND"; armor = "DIAMOND"; }
        else if (tier.equals("netherite")) { tool = "NETHERITE"; armor = "NETHERITE"; }
        else return false;

        npc.gearTier = tier;
        PlayerInventory inv = npc.getBukkitEntity().getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        if (tool == null) return true;
        inv.setItemInMainHand(new ItemStack(Material.valueOf(tool + "_SWORD")));
        inv.setHelmet(new ItemStack(Material.valueOf(armor + "_HELMET")));
        inv.setChestplate(new ItemStack(Material.valueOf(armor + "_CHESTPLATE")));
        inv.setLeggings(new ItemStack(Material.valueOf(armor + "_LEGGINGS")));
        inv.setBoots(new ItemStack(Material.valueOf(armor + "_BOOTS")));
        return true;
    }
}
