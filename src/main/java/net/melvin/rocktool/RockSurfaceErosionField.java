package net.melvin.rocktool;

/** Coherent, three-scale relief applied to every exposed rock face. */
public final class RockSurfaceErosionField {

    private RockSurfaceErosionField() {}

    /** Conservative bound in world blocks, used for cheap interior tests. */
    public static double maximumAbs(RockSpec spec) {
        double span = Math.min(spec.width(), spec.length());
        return spec.erosion() * (4.2 + span * 0.08) * 1.55;
    }

    /** Positive adds rock, negative removes it. Coordinates are rock-local. */
    public static double sample(RockSpec spec,
                                double x, double y, double z, double t) {
        if (spec.erosion() == 0.0) return 0.0;
        double amplitude = maximumAbs(spec) / 1.55;
        double broad = noise(spec.seed() ^ 0xA4093822299F31D0L,
                x / 18.0, y / 25.0, z / 18.0);
        double medium = noise(spec.seed() ^ 0x13198A2E03707344L,
                x / 8.0, y / 12.0, z / 8.0);
        double detail = noise(spec.seed() ^ 0x243F6A8885A308D3L,
                x / 3.4, y / 5.0, z / 3.4);
        double root = smooth(Math.min(1.0, t / 0.14));
        return amplitude * (0.75 * broad + 0.50 * medium
                + 0.30 * detail) * root;
    }

    private static double noise(long seed, double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double tx = smooth(x - ix), ty = smooth(y - iy);
        double tz = smooth(z - iz);
        double sum = 0.0;
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                for (int c = 0; c <= 1; c++) {
                    double weight = (a == 0 ? 1.0 - tx : tx)
                            * (b == 0 ? 1.0 - ty : ty)
                            * (c == 0 ? 1.0 - tz : tz);
                    sum += weight * random(seed, ix + a, iy + b, iz + c);
                }
            }
        }
        return sum;
    }

    private static double random(long seed, int x, int y, int z) {
        long value = seed ^ ((long) x * 341873128712L)
                ^ ((long) y * 42317861L)
                ^ ((long) z * 132897987541L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }
}
