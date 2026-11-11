package net.melvin.rocktool;

import java.util.Objects;
import java.util.Set;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in material cap and decoration passes for a placed rock. */
public record RockDecorationRules(
        TopSoil topSoil, WallVines wallVines,
        SurfacePlant surfacePlant, TopStructure topStructure) {

    public static final RockDecorationRules NONE =
            new RockDecorationRules(null, null, null, null);

    public static Builder builder() { return new Builder(); }

    public record TopSoil(BlockState surface, BlockState subsurface, int depth,
                          int maximumRelief, double coverage,
                          Set<RockType> allowedTypes) {
        public TopSoil {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(subsurface, "subsurface");
            allowedTypes = Set.copyOf(allowedTypes);
            if (depth < 1 || depth > 8 || maximumRelief < 0
                    || !validChance(coverage)) {
                throw new IllegalArgumentException("Invalid top soil");
            }
        }

        public TopSoil(BlockState state, int depth,
                       int maximumRelief, double coverage) {
            this(state, state.is(Blocks.GRASS_BLOCK)
                            ? Blocks.DIRT.defaultBlockState() : state,
                    depth, maximumRelief, coverage, Set.of());
        }

        public TopSoil(BlockState surface, BlockState subsurface,
                       int depth, int maximumRelief, double coverage) {
            this(surface, subsurface, depth, maximumRelief,
                    coverage, Set.of());
        }

        public boolean appliesTo(RockType type) {
            return allowedTypes.isEmpty() || allowedTypes.contains(type);
        }
    }

    public record WallVines(double chance, int maximumLength) {
        public WallVines {
            if (!validChance(chance) || maximumLength < 1 || maximumLength > 12) {
                throw new IllegalArgumentException("Invalid vine rule");
            }
        }
    }

    public record SurfacePlant(BlockState state, double chance,
                               int maximumRelief) {
        public SurfacePlant {
            Objects.requireNonNull(state, "state");
            if (!validChance(chance) || maximumRelief < 0) {
                throw new IllegalArgumentException("Invalid plant rule");
            }
        }
    }

    /** Only listed types attempt the feature; the footprint must be flat. */
    public record TopStructure(Set<RockType> allowedTypes,
                               double chance, int flatRadius,
                               int maximumPerCluster,
                               List<Template> templates) {
        public TopStructure {
            allowedTypes = Set.copyOf(allowedTypes);
            templates = List.copyOf(templates);
            if (allowedTypes.isEmpty() || !validChance(chance)
                    || flatRadius < 1 || flatRadius > 8
                    || maximumPerCluster < 1 || maximumPerCluster > 200
                    || templates.isEmpty()) {
                throw new IllegalArgumentException("Invalid top structure");
            }
            for (Template template : templates) {
                for (Part part : template.parts()) {
                    if (Math.abs(part.offset().getX()) > flatRadius
                            || Math.abs(part.offset().getZ()) > flatRadius) {
                        throw new IllegalArgumentException(
                                "Structure footprint exceeds flat radius");
                    }
                }
            }
        }
    }

    /** A block relative to a summit anchor, with nonnegative height. */
    public record Part(BlockPos offset, BlockState state) {
        public Part {
            Objects.requireNonNull(offset, "offset");
            Objects.requireNonNull(state, "state");
            if (Math.abs(offset.getX()) > 8 || Math.abs(offset.getZ()) > 8
                    || offset.getY() < 0 || offset.getY() > 12) {
                throw new IllegalArgumentException("Structure part outside limits");
            }
        }
    }

    /** Immutable small structure; generated once and clipped per chunk. */
    public record Template(List<Part> parts) {
        public Template {
            parts = List.copyOf(parts);
            if (parts.isEmpty()) {
                throw new IllegalArgumentException("Empty rock-top structure");
            }
        }
    }

    /** Simple built-in summit marker; custom templates use the same format. */
    public static Template cairn(BlockState block, int height) {
        if (height < 1 || height > 12) {
            throw new IllegalArgumentException("Invalid cairn height");
        }
        java.util.ArrayList<Part> parts = new java.util.ArrayList<>();
        for (int y = 0; y < height; y++) {
            parts.add(new Part(new BlockPos(0, y, 0), block));
        }
        return new Template(parts);
    }

    private static boolean validChance(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    public static final class Builder {
        private TopSoil topSoil;
        private WallVines wallVines;
        private SurfacePlant surfacePlant;
        private TopStructure topStructure;

        private Builder() {}

        public Builder topSoil(BlockState state, int depth,
                               int maximumRelief, double coverage) {
            topSoil = new TopSoil(state, depth, maximumRelief, coverage);
            return this;
        }

        public Builder topSoil(BlockState surface, BlockState subsurface,
                               int depth, int maximumRelief,
                               double coverage) {
            topSoil = new TopSoil(surface, subsurface, depth,
                    maximumRelief, coverage);
            return this;
        }

        public Builder topSoilFor(Set<RockType> types,
                                  BlockState surface,
                                  BlockState subsurface,
                                  int depth, int maximumRelief,
                                  double coverage) {
            topSoil = new TopSoil(surface, subsurface, depth,
                    maximumRelief, coverage, types);
            return this;
        }

        public Builder vines(double chance, int maximumLength) {
            wallVines = new WallVines(chance, maximumLength);
            return this;
        }

        public Builder plant(BlockState state, double chance,
                             int maximumRelief) {
            surfacePlant = new SurfacePlant(state, chance, maximumRelief);
            return this;
        }

        public Builder structure(Set<RockType> types, double chance,
                                 int flatRadius, int maximumPerCluster,
                                 Template... templates) {
            topStructure = new TopStructure(types, chance,
                    flatRadius, maximumPerCluster, List.of(templates));
            return this;
        }

        public RockDecorationRules build() {
            return new RockDecorationRules(topSoil, wallVines,
                    surfacePlant, topStructure);
        }
    }
}
