package me.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Slot layouts. Local coords: [0] = right, [1] = forward (relative to the leader's facing). */
public enum Formation {
    GRID, LINE, COLUMN, CIRCLE, WEDGE, TRIANGLE, SQUARE, SPIRAL;

    public Formation next() { return values()[(ordinal() + 1) % values().length]; }
    public Formation prev() { return values()[(ordinal() + values().length - 1) % values().length]; }

    public static Formation parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }

    private double[] slot(int i, int n, double s) {
        switch (this) {
            case LINE:
                return new double[]{(i - (n - 1) / 2.0) * s, 0};
            case COLUMN:
                return new double[]{0, ((n - 1) / 2.0 - i) * s};
            case GRID: {
                int side = (int) Math.ceil(Math.sqrt(n));
                int rows = (int) Math.ceil(n / (double) side);
                return new double[]{(i % side - (side - 1) / 2.0) * s, ((rows - 1) / 2.0 - i / side) * s};
            }
            case CIRCLE: {
                double r = Math.max(s, s * n / (2 * Math.PI));
                double a = 2 * Math.PI * i / n;
                return new double[]{Math.cos(a) * r, Math.sin(a) * r};
            }
            case WEDGE: {
                int k = (i + 1) / 2;
                int side = (i % 2 == 1) ? 1 : -1;
                return new double[]{side * k * s, -k * s};
            }
            case TRIANGLE: {
                int row = 0, used = 0;
                while (used + row + 1 <= i) { used += row + 1; row++; }
                int col = i - used;
                return new double[]{(col - row / 2.0) * s, -row * s};
            }
            case SQUARE: {
                int m = Math.max(1, (int) Math.ceil(n / 4.0));
                int perimeter = 4 * m;
                int p = (int) Math.floor(i * (double) perimeter / n);
                int side = p / m, off = p % m;
                double h = m * s / 2.0;
                switch (side % 4) {
                    case 0: return new double[]{off * s - h, -h};
                    case 1: return new double[]{h, off * s - h};
                    case 2: return new double[]{h - off * s, h};
                    default: return new double[]{-h, h - off * s};
                }
            }
            case SPIRAL:
            default: {
                double r = s * 0.9 * Math.sqrt(i);
                double a = i * 2.39996323;
                return new double[]{Math.cos(a) * r, Math.sin(a) * r};
            }
        }
    }

    /** Slots shifted so the whole formation sits in front of the leader (or behind, if hold). */
    public static List<double[]> shifted(Formation f, int n, double spacing, boolean hold) {
        List<double[]> out = new ArrayList<>();
        double r = 0;
        for (int i = 0; i < n; i++) {
            double[] a = f.slot(i, n, spacing);
            r = Math.max(r, Math.hypot(a[0], a[1]));
            out.add(a);
        }
        double shift = hold ? -(r + 2) : (r + 2);
        for (double[] a : out) a[1] += shift;
        return out;
    }

    public static double[] toWorld(Location base, double[] local) {
        double y = Math.toRadians(base.getYaw());
        double fx = -Math.sin(y), fz = Math.cos(y);       // forward
        double rx = -Math.cos(y), rz = -Math.sin(y);      // right
        return new double[]{base.getX() + rx * local[0] + fx * local[1],
                            base.getZ() + rz * local[0] + fz * local[1]};
    }

    public static List<Location> toLocations(Location base, List<double[]> local) {
        List<Location> out = new ArrayList<>();
        World w = base.getWorld();
        for (double[] l : local) {
            double[] p = toWorld(base, l);
            out.add(new Location(w, p[0], safeY(w, p[0], base.getY(), p[1]), p[1], base.getYaw(), 0));
        }
        return out;
    }

    /** Nudges upward (max 3 blocks) if the slot is inside a block. */
    public static double safeY(World w, double x, double y, double z) {
        for (int dy = 0; dy <= 3; dy++) {
            Block b = w.getBlockAt((int) Math.floor(x), (int) Math.floor(y) + dy, (int) Math.floor(z));
            if (b.isPassable() && b.getRelative(0, 1, 0).isPassable()) return y + dy;
        }
        return y;
    }
}
