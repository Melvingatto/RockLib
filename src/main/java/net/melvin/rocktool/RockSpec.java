package net.melvin.rocktool;

import java.util.Objects;

/** A deterministic request for one rock object, in world coordinates. */
public record RockSpec(
        RockType type,
        long seed,
        double x, double baseY, double z,
        double width, double length, double height,
        double rotation,
        double edgeSharpness,
        double smoothing,
        double erosion,
        double leanX, double leanZ,
        int layers) {

    public RockSpec {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(x) || !Double.isFinite(baseY)
                || !Double.isFinite(z) || !Double.isFinite(width)
                || !Double.isFinite(length) || !Double.isFinite(height)
                || !Double.isFinite(rotation)
                || !Double.isFinite(edgeSharpness)
                || !Double.isFinite(smoothing)
                || !Double.isFinite(erosion)
                || !Double.isFinite(leanX) || !Double.isFinite(leanZ)
                || width < 4.0 || length < 4.0 || height < 4.0
                || edgeSharpness < 0.0 || edgeSharpness > 1.0
                || smoothing < 0.0 || smoothing > 1.0
                || erosion < 0.0 || erosion > 1.0
                || layers < 2 || layers > 16) {
            throw new IllegalArgumentException("Invalid rock parameters");
        }
    }

    /** Useful starting dimensions; every parameter remains adjustable. */
    public static RockSpec example(RockType type, long seed,
                                   double x, double baseY, double z) {
        return switch (type) {
            case SANDSTONE_FIN -> new RockSpec(type, seed, x, baseY, z,
                    13, 48, 58, 0, 0.96, 0.10, 0.28, 3, -1, 5);
            case STEPPED_BUTTE -> new RockSpec(type, seed, x, baseY, z,
                    58, 68, 35, 0, 0.91, 0.12, 0.28, 3, 0, 5);
            case FRACTURED_BOULDER -> new RockSpec(type, seed, x, baseY, z,
                    35, 30, 20, 0, 0.96, 0.08, 0.32, 1, 0, 3);
            case TAPERED_SPIRE -> new RockSpec(type, seed, x, baseY, z,
                    27, 31, 47, 0, 0.96, 0.10, 0.30, 4, -3, 5);
            case TRIANGULAR_WEDGE -> new RockSpec(type, seed, x, baseY, z,
                    52, 34, 29, 0, 0.98, 0.04, 0.19, -2, 0, 4);
            case TABLE_ROCK -> new RockSpec(type, seed, x, baseY, z,
                    51, 44, 40, 0, 0.96, 0.06, 0.26, 1, 0, 6);
            case TILTED_MONOLITH -> new RockSpec(type, seed, x, baseY, z,
                    30, 27, 58, 0, 0.98, 0.04, 0.20, 17, -3, 5);
            case LAYERED_SLAB -> new RockSpec(type, seed, x, baseY, z,
                    58, 38, 19, 0, 0.97, 0.04, 0.29, 0, 0, 4);
            case SPLIT_CROWN -> new RockSpec(type, seed, x, baseY, z,
                    44, 40, 47, 0, 0.98, 0.03, 0.22, 1, 0, 5);
            case BLADE_RIDGE -> new RockSpec(type, seed, x, baseY, z,
                    19, 66, 56, 0, 0.98, 0.04, 0.25, 1, 0, 5);
            case TALUS_CONE -> new RockSpec(type, seed, x, baseY, z,
                    59, 54, 33, 0, 0.93, 0.10, 0.40, 1, 0, 5);
            case PYRAMIDAL_PEAK -> new RockSpec(type, seed, x, baseY, z,
                    47, 42, 57, 0, 0.98, 0.03, 0.23, 3, -1, 6);
        };
    }
}
