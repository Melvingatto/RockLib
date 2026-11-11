package net.melvin.rocktool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.melvin.rocktool.RockPlacementHelper.Placement;
import net.melvin.rocktool.RockPlacementHelper.SurfaceHeight;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Combines cached variants into one connected, procedurally stacked mass. */
public final class RockClusterPlanner {
    private RockClusterPlanner() {}

    public static Builder builder(long seed, double x, double z) {
        return new Builder(seed, x, z);
    }

    /** Tunable cluster recipe; all values are deterministic for its seed. */
    public static final class Builder {
        private final long seed;
        private final double x, z;
        private int count = 6;
        private double radius = 48.0;
        private double width = 24.0, length = 28.0, height = 20.0;
        private double minScale = 0.65, maxScale = 1.35;
        private double embedDepth = 3.0, stackChance = 0.35;
        private double overlap = 0.45;
        private List<RockType> types = List.of(RockType.FRACTURED_BOULDER,
                RockType.LAYERED_SLAB, RockType.STEPPED_BUTTE);
        private final List<MaterialChoice> materials = new ArrayList<>();
        private RockDecorationRules decorations = RockDecorationRules.NONE;

        private Builder(long seed, double x, double z) {
            this.seed = seed;
            this.x = x;
            this.z = z;
        }

        public Builder count(int value) { count = value; return this; }
        public Builder radius(double value) { radius = value; return this; }
        public Builder dimensions(double width, double length, double height) {
            this.width = width;
            this.length = length;
            this.height = height;
            return this;
        }
        public Builder scale(double minimum, double maximum) {
            minScale = minimum;
            maxScale = maximum;
            return this;
        }
        public Builder embed(double value) { embedDepth = value; return this; }
        public Builder stackChance(double value) {
            stackChance = value;
            return this;
        }
        public Builder overlap(double value) { overlap = value; return this; }
        public Builder types(RockType... value) {
            types = List.of(value);
            return this;
        }
        public Builder material(RockMaterialField field, double weight) {
            materials.add(new MaterialChoice(field, weight));
            return this;
        }
        public Builder decorations(RockDecorationRules value) {
            decorations = Objects.requireNonNull(value);
            return this;
        }
        public Spec build() {
            List<MaterialChoice> palette = materials.isEmpty()
                    ? List.of(new MaterialChoice(
                    ((rock, x, y, z) -> net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), 1.0))
                    : List.copyOf(materials);
            return new Spec(seed, x, z, count, radius, width, length,
                    height, minScale, maxScale, embedDepth, stackChance,
                    overlap, types, palette, decorations);
        }
    }

    /** A material or custom palette assigned to a cluster member by weight. */
    public record MaterialChoice(RockMaterialField material, double weight) {
        public MaterialChoice {
            Objects.requireNonNull(material, "material");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("Invalid cluster material weight");
            }
        }
    }

    public record Spec(long seed, double x, double z,
                       int count, double radius,
                       double width, double length, double height,
                       double minimumScale, double maximumScale,
                       double embedDepth, double stackChance,
                       double overlap, List<RockType> types,
                       List<MaterialChoice> materials,
                       RockDecorationRules decorations) {
        public Spec(long seed, double x, double z,
                    int count, double radius,
                    double width, double length, double height,
                    double minimumScale, double maximumScale,
                    double embedDepth, double stackChance,
                    double overlap, List<RockType> types,
                    RockMaterialField material,
                    RockDecorationRules decorations) {
            this(seed, x, z, count, radius, width, length, height,
                    minimumScale, maximumScale, embedDepth, stackChance,
                    overlap, types,
                    List.of(new MaterialChoice(material, 1.0)), decorations);
        }

        public Spec {
            types = List.copyOf(types);
            materials = List.copyOf(materials);
            Objects.requireNonNull(decorations, "decorations");
            if (types.isEmpty() || materials.isEmpty()
                    || count < 2 || count > 200
                    || !Double.isFinite(x) || !Double.isFinite(z)
                    || !Double.isFinite(radius) || radius < 1.0
                    || !Double.isFinite(width) || width < 4.0
                    || !Double.isFinite(length) || length < 4.0
                    || !Double.isFinite(height) || height < 4.0
                    || !Double.isFinite(minimumScale) || minimumScale <= 0.0
                    || !Double.isFinite(maximumScale)
                    || maximumScale < minimumScale
                    || width * minimumScale < 4.0
                    || length * minimumScale < 4.0
                    || height * minimumScale < 4.0
                    || !Double.isFinite(embedDepth) || embedDepth < 0.0
                    || !Double.isFinite(stackChance)
                    || stackChance < 0.0 || stackChance > 1.0
                    || !Double.isFinite(overlap)
                    || overlap < 0.20 || overlap > 0.80) {
                throw new IllegalArgumentException("Invalid rock cluster spec");
            }
        }
    }

    /** The union is sampled as a single mass; components retain their material. */
    public record Cluster(long seed, List<Placement> components,
                          RockDecorationRules decorations) {
        public Cluster {
            components = List.copyOf(components);
            Objects.requireNonNull(decorations, "decorations");
            if (components.isEmpty()) {
                throw new IllegalArgumentException("Empty rock cluster");
            }
        }

        public boolean solid(int x, int y, int z) {
            for (Placement component : components) {
                if (component.rock().solid(x, y, z)) {
                    return true;
                }
            }
            return false;
        }

        public int highestSolidY(int x, int z) {
            int highest = Integer.MIN_VALUE;
            for (Placement component : components) {
                highest = Math.max(highest,
                        component.rock().highestSolidY(x, z));
            }
            return highest;
        }

        /** Later components own material where two rocks share a block. */
        public Placement topOwner(int x, int z) {
            Placement owner = null;
            int highest = Integer.MIN_VALUE;
            for (Placement component : components) {
                int top = component.rock().highestSolidY(x, z);
                if (top >= highest && top != Integer.MIN_VALUE) {
                    highest = top;
                    owner = component;
                }
            }
            return owner;
        }
    }

    public static Cluster plan(Spec spec, RockVariantCache variants,
                               SurfaceHeight ground) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(ground, "ground");
        List<Placement> parts = new ArrayList<>();
        // Invalid joins are retried with fresh deterministic candidates.
        // The work bound keeps very large clusters predictable to generate.
        for (int i = 0; parts.size() < spec.count()
                && i < spec.count() * 8; i++) {
            // The parent makes each new member overlap the existing mass.
            int parentIndex = i == 0 ? -1
                    : (int) (unit(spec.seed(), 31L + i * 17L) * parts.size());
            RockInstance parent = parentIndex < 0
                    ? null : parts.get(parentIndex).rock();
            RockType type = spec.types().get((int) (unit(spec.seed(),
                    32L + i * 17L) * spec.types().size()));
            double scale = spec.minimumScale()
                    + (spec.maximumScale() - spec.minimumScale())
                    * unit(spec.seed(), 33L + i * 17L);
            double width = spec.width() * scale;
            double length = spec.length() * scale;
            double height = spec.height() * scale;
            double angle = unit(spec.seed(), 34L + i * 17L) * Math.PI * 2.0;
            double distance = parent == null ? 0.0 : Math.min(spec.radius(),
                    (Math.min(parent.spec().width(), parent.spec().length())
                            + Math.min(width, length)) * 0.24)
                    * (0.35 + 0.65 * unit(spec.seed(), 35L + i * 17L));
            double x = parent == null ? spec.x()
                    : parent.spec().x() + Math.cos(angle) * distance;
            double z = parent == null ? spec.z()
                    : parent.spec().z() + Math.sin(angle) * distance;
            double fromCenter = Math.hypot(x - spec.x(), z - spec.z());
            if (fromCenter > spec.radius()) {
                double factor = spec.radius() / fromCenter;
                x = spec.x() + (x - spec.x()) * factor;
                z = spec.z() + (z - spec.z()) * factor;
            }
            int ix = (int) Math.round(x), iz = (int) Math.round(z);
            boolean stack = parent != null
                    && unit(spec.seed(), 36L + i * 17L) < spec.stackChance();
            int supportY = stack ? parent.highestSolidY(ix, iz)
                    : Integer.MIN_VALUE;
            double baseY = supportY == Integer.MIN_VALUE
                    ? ground.at(ix, iz) - spec.embedDepth()
                    : supportY - height * spec.overlap();
            long id = mix(spec.seed() ^ (long) i * 0x9E3779B97F4A7C15L);
            int variant = variants.indexFor(type, id);
            double orientation = angle + unit(spec.seed(), 37L + i * 17L)
                    * Math.PI;
            RockInstance member = variants.place(type, variant,
                    x, baseY, z, orientation, width, length, height);
            if (parent != null && !intersects(parent, member)) {
                x = parent.spec().x() + (x - parent.spec().x()) * 0.45;
                z = parent.spec().z() + (z - parent.spec().z()) * 0.45;
                baseY = parent.spec().baseY()
                        + parent.spec().height() * 0.35
                        - height * spec.overlap();
                member = variants.place(type, variant,
                        x, baseY, z, orientation, width, length, height);
                if (!intersects(parent, member)) {
                    continue;
                }
            }
            parts.add(new Placement(id, member,
                    chooseMaterial(spec.materials(), spec.seed(),
                            parts.size()),
                    -Math.min(width, length) * spec.overlap(),
                    RockDecorationRules.NONE));
        }
        return new Cluster(spec.seed(), parts, spec.decorations());
    }

    /** Write the cluster union and its optional decorations in one call. */
    public static void placeInChunk(WorldGenLevel level, ChunkAccess chunk,
                                    Cluster cluster, int minimumY,
                                    int maximumY) {
        RockPlacementHelper.placeClusterInChunk(level, chunk, cluster,
                minimumY, maximumY);
    }

    private static RockMaterialField chooseMaterial(
            List<MaterialChoice> choices, long seed, int index) {
        double total = 0.0;
        for (MaterialChoice choice : choices) {
            total += choice.weight();
        }
        double roll = unit(seed, 271L + index * 47L) * total;
        for (MaterialChoice choice : choices) {
            roll -= choice.weight();
            if (roll < 0.0) {
                return choice.material();
            }
        }
        return choices.get(choices.size() - 1).material();
    }

    private static boolean intersects(RockInstance a, RockInstance b) {
        var first = a.bounds();
        var second = b.bounds();
        int minX = Math.max(first.minX(), second.minX());
        int maxX = Math.min(first.maxX(), second.maxX());
        int minZ = Math.max(first.minZ(), second.minZ());
        int maxZ = Math.min(first.maxZ(), second.maxZ());
        int minY = Math.max(first.minY(), second.minY());
        int maxY = Math.min(first.maxY(), second.maxY());
        if (minX > maxX || minZ > maxZ || minY > maxY) {
            return false;
        }
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                for (int y = minY; y <= maxY; y += 2) {
                    if (a.solid(x, y, z) && b.solid(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double unit(long seed, long salt) {
        return (mix(seed ^ salt * 42317861L) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
