package me.npc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Settings {
    public Difficulty difficulty = Difficulty.NORMAL;
    public int chunkRadius = 4;
    public Formation formation = Formation.GRID;
    public double spacing = 2.0;
    public int maxNpcs = 50;
    public int spawnCount = 10;
    public int spawnPerTick = 2;
    public boolean friendlyFire = false;
    public boolean respawn = true;
    public int respawnDelay = 60;
    public boolean silent = true;
    public String gear = "none";

    public void load(FileConfiguration c) {
        Difficulty d = Difficulty.parse(c.getString("difficulty", "NORMAL"));
        difficulty = d == null ? Difficulty.NORMAL : d;
        chunkRadius = Math.max(1, Math.min(32, c.getInt("chunk-radius", 4)));
        Formation f = Formation.parse(c.getString("formation", "GRID"));
        formation = f == null ? Formation.GRID : f;
        spacing = c.getDouble("spacing", 2.0);
        maxNpcs = c.getInt("max-npcs", 50);
        spawnCount = c.getInt("spawn-count", 10);
        spawnPerTick = Math.max(1, c.getInt("spawn-per-tick", 2));
        friendlyFire = c.getBoolean("friendly-fire", false);
        respawn = c.getBoolean("respawn", true);
        respawnDelay = c.getInt("respawn-delay-ticks", 60);
        silent = c.getBoolean("silent", true);
        gear = c.getString("gear", "none");
    }

    public void save(JavaPlugin p) {
        FileConfiguration c = p.getConfig();
        c.set("difficulty", difficulty.name());
        c.set("chunk-radius", chunkRadius);
        c.set("formation", formation.name());
        c.set("spacing", spacing);
        c.set("max-npcs", maxNpcs);
        c.set("spawn-count", spawnCount);
        c.set("spawn-per-tick", spawnPerTick);
        c.set("friendly-fire", friendlyFire);
        c.set("respawn", respawn);
        c.set("respawn-delay-ticks", respawnDelay);
        c.set("silent", silent);
        c.set("gear", gear);
        p.saveConfig();
    }
}
