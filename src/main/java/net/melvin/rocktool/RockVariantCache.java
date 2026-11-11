package net.melvin.rocktool;

/**
 * One world seed's reusable bank of 256 immutable shapes per rock type.
 * Stores contour geometry, not blocks or copies of world chunks.
 */
public final class RockVariantCache {

    public static final int VARIANTS_PER_TYPE = 256;

    private final long worldSeed;
    private final RockInstance[][] variants;

    public RockVariantCache(long worldSeed) {
        this.worldSeed = worldSeed;
        RockType[] types = RockType.values();
        this.variants = new RockInstance[types.length][VARIANTS_PER_TYPE];
        for (RockType type : types) {
            for (int index = 0; index < VARIANTS_PER_TYPE; index++) {
                long seed = mix(worldSeed
                        ^ ((long) type.ordinal() * 0xD1B54A32D192ED03L)
                        ^ ((long) index * 0x9E3779B97F4A7C15L));
                RockSpec example = RockSpec.example(type, seed,
                        0.0, 0.0, 0.0);
                double width = example.width() * range(seed, 1L, 0.70, 1.35);
                double length = example.length() * range(seed, 2L, 0.70, 1.35);
                double height = example.height() * range(seed, 3L, 0.68, 1.38);
                double edge = clamp(example.edgeSharpness()
                        + range(seed, 4L, -0.10, 0.08));
                double smoothing = clamp(example.smoothing()
                        + range(seed, 5L, -0.07, 0.07));
                double erosion = clamp(example.erosion()
                        * range(seed, 6L, 0.65, 1.45));
                double leanFactor = height / example.height()
                        * range(seed, 7L, 0.65, 1.40);
                int layers = Math.max(2, Math.min(16, example.layers()
                        + (int) Math.floor(RockGenerator.unit(seed, 8L) * 5.0)
                        - 2));
                RockSpec variant = new RockSpec(type, seed,
                        0.0, 0.0, 0.0, width, length, height, 0.0,
                        edge, smoothing, erosion,
                        example.leanX() * leanFactor,
                        example.leanZ() * leanFactor, layers);
                variants[type.ordinal()][index] = RockGenerator.create(variant);
            }
        }
    }

    /** Geometry template at origin; its spec reports dimensions and parameters. */
    public RockInstance template(RockType type, int index) {
        if (index < 0 || index >= VARIANTS_PER_TYPE) {
            throw new IndexOutOfBoundsException("Rock variant: " + index);
        }
        return variants[type.ordinal()][index];
    }

    /** Lightweight placement sharing its cached contour arrays. */
    public RockInstance place(RockType type, int index,
                              double x, double baseY, double z,
                              double rotation) {
        return template(type, index).at(x, baseY, z, rotation);
    }

    /**
     * Places a cached shape at exact block dimensions. The 256 contour
     * templates remain shared; only the placed rock's dimensions and lean
     * change. Width, length, and height must each be at least four blocks.
     */
    public RockInstance place(RockType type, int index,
                              double x, double baseY, double z,
                              double rotation,
                              double width, double length, double height) {
        return template(type, index).at(
                x, baseY, z, rotation, width, length, height);
    }

    /** Stable variant choice for a planned rock object's own identifier. */
    public int indexFor(RockType type, long objectId) {
        return (int) (mix(worldSeed ^ objectId
                ^ ((long) type.ordinal() * 0xD1B54A32D192ED03L))
                & (VARIANTS_PER_TYPE - 1));
    }

    private static double range(long seed, long salt, double min, double max) {
        return min + (max - min) * RockGenerator.unit(seed, salt);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }
}
