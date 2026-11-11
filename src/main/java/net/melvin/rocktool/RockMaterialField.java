package net.melvin.rocktool;

import java.util.List;
import java.util.Objects;

import net.minecraft.world.level.block.state.BlockState;

/** Material sampled from a placed rock's geometry and world position. */
@FunctionalInterface
public interface RockMaterialField {
    BlockState blockAt(RockInstance rock, int x, int y, int z);

    /** A weighted block and its relative frequency within one height band. */
    record Component(BlockState state, double weight) {
        public Component {
            Objects.requireNonNull(state, "state");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("Material weight must be positive");
            }
        }
    }

    /** Inclusive lower and exclusive upper fraction of the rock's height. */
    record Band(double from, double to, List<Component> components) {
        public Band {
            if (!Double.isFinite(from) || !Double.isFinite(to)
                    || from < 0.0 || to > 1.0 || from >= to) {
                throw new IllegalArgumentException("Invalid material band");
            }
            components = List.copyOf(components);
            if (components.isEmpty()) {
                throw new IllegalArgumentException("Empty material band");
            }
        }

        public Band(double from, double to, Component... components) {
            this(from, to, List.of(components));
        }
    }

    /**
     * Custom weighted palette with explicit height bands and controllable
     * patch size. patchInfluence 0 produces fine spray; 1 produces broad
     * coherent material areas. Bands must cover the whole interval [0, 1].
     */
    final class Palette implements RockMaterialField {
        private final long seed;
        private final double patchScale;
        private final double patchInfluence;
        private final List<Band> bands;

        public Palette(long seed, double patchScale,
                       double patchInfluence, Band... bands) {
            if (!Double.isFinite(patchScale) || patchScale < 1.0
                    || !Double.isFinite(patchInfluence)
                    || patchInfluence < 0.0 || patchInfluence > 1.0) {
                throw new IllegalArgumentException("Invalid palette patch settings");
            }
            this.seed = seed;
            this.patchScale = patchScale;
            this.patchInfluence = patchInfluence;
            this.bands = List.of(bands);
            if (this.bands.isEmpty() || this.bands.get(0).from() != 0.0
                    || this.bands.get(this.bands.size() - 1).to() != 1.0) {
                throw new IllegalArgumentException("Bands must cover [0, 1]");
            }
            for (int i = 1; i < this.bands.size(); i++) {
                if (this.bands.get(i - 1).to() != this.bands.get(i).from()) {
                    throw new IllegalArgumentException("Bands must be contiguous");
                }
            }
        }

        @Override
        public BlockState blockAt(RockInstance rock, int x, int y, int z) {
            double height = (y + 0.5 - rock.spec().baseY())
                    / rock.spec().height();
            Band band = bands.get(bands.size() - 1);
            for (Band candidate : bands) {
                if (height < candidate.to()) {
                    band = candidate;
                    break;
                }
            }
            long objectSeed = seed ^ rock.spec().seed();
            // One field per height band keeps the same palette choice over
            // broad connected faces without fixing a horizontal stratum.
            long bandSeed = objectSeed ^ (long) bands.indexOf(band)
                    * 0x9E3779B97F4A7C15L;
            double gx = x / patchScale, gy = y / patchScale,
                    gz = z / patchScale;
            int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy),
                    iz = (int) Math.floor(gz);
            double tx = smooth(gx - ix), ty = smooth(gy - iy),
                    tz = smooth(gz - iz);
            double lower = lerp(
                    lerp(unit(bandSeed, ix, iy, iz),
                            unit(bandSeed, ix + 1, iy, iz), tx),
                    lerp(unit(bandSeed, ix, iy, iz + 1),
                            unit(bandSeed, ix + 1, iy, iz + 1), tx), tz);
            double upper = lerp(
                    lerp(unit(bandSeed, ix, iy + 1, iz),
                            unit(bandSeed, ix + 1, iy + 1, iz), tx),
                    lerp(unit(bandSeed, ix, iy + 1, iz + 1),
                            unit(bandSeed, ix + 1, iy + 1, iz + 1), tx), tz);
            double patch = lerp(lower, upper, ty);
            double grain = unit(bandSeed ^ 0x5E4C29A1L, x, y, z);
            double choice = patchInfluence * patch
                    + (1.0 - patchInfluence) * grain;
            double total = 0.0;
            for (Component component : band.components()) {
                total += component.weight();
            }
            double target = choice * total;
            for (Component component : band.components()) {
                target -= component.weight();
                if (target < 0.0) {
                    return component.state();
                }
            }
            return band.components().get(band.components().size() - 1).state();
        }

        private static double unit(long seed, int x, int y, int z) {
            long value = seed ^ ((long) x * 341873128712L)
                    ^ ((long) y * 42317861L)
                    ^ ((long) z * 132897987541L);
            value ^= value >>> 30;
            value *= 0xBF58476D1CE4E5B9L;
            value ^= value >>> 27;
            value *= 0x94D049BB133111EBL;
            value ^= value >>> 31;
            return (value >>> 11) * 0x1.0p-53;
        }

        private static double smooth(double t) {
            return t * t * (3.0 - 2.0 * t);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }
}
