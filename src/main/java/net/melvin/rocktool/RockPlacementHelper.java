package net.melvin.rocktool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Plans and writes reusable rock objects without coupling their geometry to a
 * particular biome. Supply requests in a stable order: the first valid object
 * keeps its place when two candidates need the same space.
 */
public final class RockPlacementHelper {

    private static final int GROUND_SAMPLES = 12;

    private RockPlacementHelper() {}

    /** Surface Y is the first air block above the terrain at the given X/Z. */
    @FunctionalInterface
    public interface SurfaceHeight {
        int at(int x, int z);
    }

    /**
     * One candidate. A variantIndex of -1 chooses a stable cached variant
     * from objectId. Clearance is the minimum gap to other rocks in blocks;
     * negative values deliberately allow neighboring rocks to interlock.
     * Width, length, and height are either all zero (cached dimensions) or
     * all at least four blocks (exact custom dimensions).
     */
    public record Request(
            long objectId,
            RockType type,
            int variantIndex,
            double x, double z,
            double orientationRadians,
            RockMaterialField material,
            double embedDepth,
            double maximumSurfaceRelief,
            double clearance,
            double width, double length, double height,
            RockDecorationRules decorations) {

        public Request(long objectId, RockType type, int variantIndex,
                       double x, double z, double orientationRadians,
                       RockMaterialField material, double embedDepth,
                       double maximumSurfaceRelief, double clearance,
                       double width, double length, double height) {
            this(objectId, type, variantIndex, x, z, orientationRadians,
                    material, embedDepth, maximumSurfaceRelief, clearance,
                    width, length, height, RockDecorationRules.NONE);
        }

        /** Keeps existing requests at their cached variant's natural size. */
        public Request(
                long objectId, RockType type, int variantIndex,
                double x, double z, double orientationRadians,
                RockMaterialField material, double embedDepth,
                double maximumSurfaceRelief, double clearance) {
            this(objectId, type, variantIndex, x, z,
                    orientationRadians, material, embedDepth,
                    maximumSurfaceRelief, clearance, 0.0, 0.0, 0.0,
                    RockDecorationRules.NONE);
        }

        public Request {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(decorations, "decorations");
            if (variantIndex < -1
                    || variantIndex >= RockVariantCache.VARIANTS_PER_TYPE
                    || !Double.isFinite(x) || !Double.isFinite(z)
                    || !Double.isFinite(orientationRadians)
                    || !Double.isFinite(embedDepth)
                    || !Double.isFinite(maximumSurfaceRelief)
                    || !Double.isFinite(clearance)
                    || embedDepth < 0.0 || maximumSurfaceRelief < 0.0
                    || !Double.isFinite(width)
                    || !Double.isFinite(length)
                    || !Double.isFinite(height)
                    || !((width == 0.0 && length == 0.0 && height == 0.0)
                    || (width >= 4.0 && length >= 4.0 && height >= 4.0))) {
                throw new IllegalArgumentException("Invalid rock placement request");
            }
        }
    }

    /** One accepted, immutable placement with a shared cached rock geometry. */
    public record Placement(
            long objectId, RockInstance rock,
            RockMaterialField material, double clearance,
            RockDecorationRules decorations) {

        public Placement(long objectId, RockInstance rock,
                         RockMaterialField material, double clearance) {
            this(objectId, rock, material, clearance,
                    RockDecorationRules.NONE);
        }

        public Placement {
            Objects.requireNonNull(rock, "rock");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(decorations, "decorations");
        }
    }

    /**
     * Plan the complete group once per terrain owner and reuse the returned
     * list in every intersecting chunk. No world blocks are changed here.
     */
    public static List<Placement> plan(
            Iterable<Request> requests, RockVariantCache variants,
            SurfaceHeight surface) {
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(surface, "surface");

        List<Placement> accepted = new ArrayList<>();
        for (Request request : requests) {
            int index = request.variantIndex() >= 0
                    ? request.variantIndex()
                    : variants.indexFor(request.type(), request.objectId());
            RockSpec shape = variants.template(request.type(), index).spec();
            double width = request.width() == 0.0
                    ? shape.width() : request.width();
            double length = request.length() == 0.0
                    ? shape.length() : request.length();
            double height = request.height() == 0.0
                    ? shape.height() : request.height();
            double minGround = Double.POSITIVE_INFINITY;
            double maxGround = Double.NEGATIVE_INFINITY;

            int centerX = (int) Math.round(request.x());
            int centerZ = (int) Math.round(request.z());
            int centerHeight = surface.at(centerX, centerZ);
            minGround = Math.min(minGround, centerHeight);
            maxGround = Math.max(maxGround, centerHeight);

            double cos = Math.cos(request.orientationRadians());
            double sin = Math.sin(request.orientationRadians());
            for (int i = 0; i < GROUND_SAMPLES; i++) {
                double angle = i * Math.PI * 2.0 / GROUND_SAMPLES;
                double localX = Math.cos(angle) * width * 0.65;
                double localZ = Math.sin(angle) * length * 0.65;
                int x = (int) Math.round(request.x()
                        + localX * cos - localZ * sin);
                int z = (int) Math.round(request.z()
                        + localX * sin + localZ * cos);
                int groundY = surface.at(x, z);
                minGround = Math.min(minGround, groundY);
                maxGround = Math.max(maxGround, groundY);
            }
            if (maxGround - minGround > request.maximumSurfaceRelief()) {
                continue;
            }

            RockInstance rock = variants.place(request.type(), index,
                    request.x(), minGround - request.embedDepth(),
                    request.z(), request.orientationRadians(),
                    width, length, height);
            Placement candidate = new Placement(request.objectId(), rock,
                    request.material(), request.clearance(),
                    request.decorations());
            boolean fits = true;
            for (Placement earlier : accepted) {
                if (conflicts(candidate, earlier)) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                accepted.add(candidate);
            }
        }
        return List.copyOf(accepted);
    }

    /**
     * Conservative spacing of oriented footprints. Positive clearance keeps
     * them separate, zero lets them touch, and negative clearance permits
     * limited overlap for an enclosure or connected rock cluster.
     */
    private static boolean conflicts(Placement a, Placement b) {
        RockSpec first = a.rock().spec();
        RockSpec second = b.rock().spec();
        double dx = second.x() - first.x();
        double dz = second.z() - first.z();
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0e-6) {
            return true;
        }
        double ux = dx / distance;
        double uz = dz / distance;
        double reach = footprintReach(first, ux, uz)
                + footprintReach(second, ux, uz)
                + Math.max(a.clearance(), b.clearance());
        return distance < reach;
    }

    private static double footprintReach(
            RockSpec rock, double ux, double uz) {
        double cos = Math.cos(rock.rotation());
        double sin = Math.sin(rock.rotation());
        double localX = ux * cos + uz * sin;
        double localZ = -ux * sin + uz * cos;
        double width = rock.width() * 0.78;
        double length = rock.length() * 0.78;
        double lean = Math.hypot(rock.leanX(), rock.leanZ()) * 0.5;
        return Math.hypot(localX * width, localZ * length) + lean;
    }

    /**
     * Writes only the part of the planned group inside this chunk. Call at a
     * generation stage after fluid springs if their blocks must not cut the
     * rocks. The caller decides where the group is allowed to generate.
     */
    public static void placeInChunk(
            ChunkAccess chunk, List<Placement> placements,
            int minimumY, int maximumY) {
        placeInChunk(chunk, placements, minimumY, maximumY, null);
    }

    /** Optional profiling: checks, writes, sampled check ns/count, sampled write ns/count.
     * Only every 32nd geometry check is timed to limit measurement overhead. */
    public static void placeInChunk(
            ChunkAccess chunk, List<Placement> placements,
            int minimumY, int maximumY, long[] profile) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(placements, "placements");
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Placement placement : placements) {
            RockInstance rock = placement.rock();
            RockInstance.Bounds bounds = rock.bounds();
            if (!bounds.intersectsChunk(chunk.getPos().x,
                    chunk.getPos().z)) {
                continue;
            }
            int startX = Math.max(minX, bounds.minX());
            int endX = Math.min(minX + 15, bounds.maxX());
            int startZ = Math.max(minZ, bounds.minZ());
            int endZ = Math.min(minZ + 15, bounds.maxZ());
            int startY = Math.max(minimumY + 1, bounds.minY());
            int endY = Math.min(maximumY - 1, bounds.maxY());

            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    for (int y = startY; y <= endY; y++) {
                        boolean sampled = profile != null
                                && (++profile[0] & 31L) == 0L;
                        boolean solid;
                        if (sampled) {
                            long started = System.nanoTime();
                            solid = rock.solid(x, y, z);
                            profile[2] += System.nanoTime() - started;
                            profile[3]++;
                        } else {
                            solid = rock.solid(x, y, z);
                        }
                        if (solid) {
                            if (profile != null) profile[1]++;
                            long started = sampled ? System.nanoTime() : 0L;
                            chunk.setBlockState(pos.set(x, y, z),
                                    placement.material().blockAt(
                                            rock, x, y, z), false);
                            if (sampled) {
                                profile[4] += System.nanoTime() - started;
                                profile[5]++;
                            }
                        }
                    }
                }
            }
            placeTopSoil(chunk, placement, startX, endX,
                    startZ, endZ, minimumY, maximumY, pos);
        }
    }

    /** Convenience for callers that decorate immediately after rock placement. */
    public static void placeInChunk(
            WorldGenLevel level, ChunkAccess chunk,
            List<Placement> placements, int minimumY, int maximumY) {
        placeInChunk(chunk, placements, minimumY, maximumY);
        decorateInChunk(level, chunk, placements, minimumY, maximumY);
    }

    /** Writes a connected cluster, then decorates only its exposed union. */
    public static void placeClusterInChunk(
            WorldGenLevel level, ChunkAccess chunk,
            RockClusterPlanner.Cluster cluster,
            int minimumY, int maximumY) {
        placeInChunk(chunk, cluster.components(), minimumY, maximumY);
        RockDecorationRules rules = cluster.decorations();
        if (rules.equals(RockDecorationRules.NONE)) {
            return;
        }
        UnionSurface surface = new UnionSurface(cluster.components());
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int startX = Math.max(minX, surface.minX);
        int endX = Math.min(minX + 15, surface.maxX);
        int startZ = Math.max(minZ, surface.minZ);
        int endZ = Math.min(minZ + 15, surface.maxZ);
        if (startX > endX || startZ > endZ) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int y = surface.highest(x, z);
                if (y < minimumY + 1 || y >= maximumY - 13
                        || !level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
                    continue;
                }
                var soil = rules.topSoil();
                RockType topType = soil == null
                        ? null : surface.topType(x, y, z);
                if (soil != null
                        && topType != null && soil.appliesTo(topType)
                        && flat(surface, x, z, y, 1, soil.maximumRelief())
                        && unit(cluster.seed() ^ 0x42BDE918L,
                        x, y, z) < soil.coverage()) {
                    for (int below = 0; below < soil.depth(); below++) {
                        int sy = y - below;
                        if (sy <= minimumY || !surface.solid(x, sy, z)) {
                            break;
                        }
                        chunk.setBlockState(pos.set(x, sy, z),
                                below == 0 ? soil.surface()
                                        : soil.subsurface(), false);
                    }
                }
                var plant = rules.surfacePlant();
                if (plant != null
                        && flat(surface, x, z, y, 1,
                        plant.maximumRelief())
                        && unit(cluster.seed() ^ 0x7614E9B2L,
                        x, y, z) < plant.chance()) {
                    pos.set(x, y + 1, z);
                    if (plant.state().canSurvive(level, pos)) {
                        level.setBlock(pos, plant.state(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        if (rules.wallVines() != null) {
            placeClusterVines(level, cluster.seed(), rules.wallVines(),
                    surface, minX, minZ, startX, endX, startZ, endZ,
                    minimumY, maximumY);
        }
        if (rules.topStructure() != null) {
            placeClusterStructures(level, cluster, surface,
                    minX, minZ, minimumY, maximumY);
        }
    }

    private static void placeClusterVines(
            WorldGenLevel level, long seed,
            RockDecorationRules.WallVines rule, UnionSurface surface,
            int minX, int minZ, int startX, int endX,
            int startZ, int endZ, int minimumY, int maximumY) {
        Direction[] sides = {Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST};
        for (int x = startX; x <= endX; x += 2) {
            for (int z = startZ; z <= endZ; z += 2) {
                for (int y = Math.max(minimumY + 1, surface.minY);
                     y <= Math.min(maximumY - 2, surface.maxY); y += 3) {
                    if (unit(seed ^ 0x234A7B9CL, x, y, z) >= rule.chance()
                            || !surface.solid(x, y, z)) {
                        continue;
                    }
                    Direction side = sides[(int) (unit(seed ^ 0x6CE231ABL,
                            x, y, z) * sides.length)];
                    int px = x + side.getStepX(), pz = z + side.getStepZ();
                    if (px < minX || px >= minX + 16
                            || pz < minZ || pz >= minZ + 16
                            || surface.solid(px, y, pz)) {
                        continue;
                    }
                    var facing = switch (side.getOpposite()) {
                        case NORTH -> VineBlock.NORTH;
                        case EAST -> VineBlock.EAST;
                        case SOUTH -> VineBlock.SOUTH;
                        case WEST -> VineBlock.WEST;
                        default -> throw new IllegalStateException();
                    };
                    BlockState vine = Blocks.VINE.defaultBlockState()
                            .setValue(facing, true);
                    int length = 1 + (int) (unit(seed ^ 0x6D4178BEL,
                            px, y, pz) * rule.maximumLength());
                    for (int k = 0; k < length && y - k > minimumY; k++) {
                        BlockPos at = new BlockPos(px, y - k, pz);
                        if (!level.getBlockState(at).isAir()
                                || !vine.canSurvive(level, at)) {
                            break;
                        }
                        level.setBlock(at, vine, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void placeClusterStructures(
            WorldGenLevel level, RockClusterPlanner.Cluster cluster,
            UnionSurface surface, int minX, int minZ,
            int minimumY, int maximumY) {
        var rule = cluster.decorations().topStructure();
        List<Site> accepted = new ArrayList<>();
        for (Placement component : cluster.components()) {
            if (accepted.size() >= rule.maximumPerCluster()) {
                break;
            }
            RockInstance rock = component.rock();
            if (!rule.allowedTypes().contains(rock.spec().type())
                    || unit(component.objectId() ^ 0x4EB92C71L,
                    (int) rock.spec().x(), 0,
                    (int) rock.spec().z()) >= rule.chance()) {
                continue;
            }
            Site site = siteForUnion(rock, surface, rule.flatRadius(),
                    minimumY, maximumY);
            if (site == null) {
                continue;
            }
            boolean clear = true;
            for (Site earlier : accepted) {
                if (Math.hypot(earlier.x() - site.x(),
                        earlier.z() - site.z()) < rule.flatRadius() * 2 + 3) {
                    clear = false;
                    break;
                }
            }
            if (!clear) {
                continue;
            }
            accepted.add(site);
            int choice = (int) (unit(component.objectId() ^ 0x74C1D69EL,
                    site.x(), site.y(), site.z()) * rule.templates().size());
            writeTemplate(level, minX, minZ, site,
                    rule.templates().get(choice), maximumY);
        }
    }

    private static Site siteForUnion(RockInstance rock,
                                     UnionSurface surface, int radius,
                                     int minimumY, int maximumY) {
        int centerX = (int) Math.floor(rock.spec().x());
        int centerZ = (int) Math.floor(rock.spec().z());
        int[] offsets = {0, 2, -2, 4, -4};
        for (int dx : offsets) {
            for (int dz : offsets) {
                int x = centerX + dx, z = centerZ + dz;
                int y = surface.highest(x, z);
                if (y > minimumY && y < maximumY - 13
                        && rock.solid(x, y, z)
                        && flat(surface, x, z, y, radius, 0)) {
                    return new Site(x, y + 1, z);
                }
            }
        }
        return null;
    }

    private static boolean flat(UnionSurface surface, int x, int z, int y,
                                int radius, int relief) {
        for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
            for (int dz = -radius; dz <= radius;
                 dz += Math.max(1, radius)) {
                int near = surface.highest(x + dx, z + dz);
                if (near == Integer.MIN_VALUE || Math.abs(near - y) > relief) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Cached union columns avoid a full 200-member search for every probe. */
    private static final class UnionSurface {
        private final List<Placement> parts;
        private final Map<Long, Integer> heights = new HashMap<>();
        private final int minX, maxX, minY, maxY, minZ, maxZ;

        UnionSurface(List<Placement> parts) {
            this.parts = parts;
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE;
            int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
            int z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
            for (Placement part : parts) {
                var b = part.rock().bounds();
                x0 = Math.min(x0, b.minX()); x1 = Math.max(x1, b.maxX());
                y0 = Math.min(y0, b.minY()); y1 = Math.max(y1, b.maxY());
                z0 = Math.min(z0, b.minZ()); z1 = Math.max(z1, b.maxZ());
            }
            minX = x0; maxX = x1; minY = y0; maxY = y1;
            minZ = z0; maxZ = z1;
        }

        int highest(int x, int z) {
            long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            Integer cached = heights.get(key);
            if (cached != null) {
                return cached;
            }
            int highest = Integer.MIN_VALUE;
            for (Placement part : parts) {
                var bounds = part.rock().bounds();
                if (x >= bounds.minX() && x <= bounds.maxX()
                        && z >= bounds.minZ() && z <= bounds.maxZ()) {
                    highest = Math.max(highest,
                            part.rock().highestSolidY(x, z));
                }
            }
            heights.put(key, highest);
            return highest;
        }

        boolean solid(int x, int y, int z) {
            for (Placement part : parts) {
                var b = part.rock().bounds();
                if (x >= b.minX() && x <= b.maxX()
                        && y >= b.minY() && y <= b.maxY()
                        && z >= b.minZ() && z <= b.maxZ()
                        && part.rock().solid(x, y, z)) {
                    return true;
                }
            }
            return false;
        }

        RockType topType(int x, int y, int z) {
            for (int i = parts.size() - 1; i >= 0; i--) {
                RockInstance rock = parts.get(i).rock();
                var b = rock.bounds();
                if (x >= b.minX() && x <= b.maxX()
                        && y >= b.minY() && y <= b.maxY()
                        && z >= b.minZ() && z <= b.maxZ()
                        && rock.solid(x, y, z)) {
                    return rock.spec().type();
                }
            }
            return null;
        }
    }

    private static void placeTopSoil(
            ChunkAccess chunk, Placement placement,
            int startX, int endX, int startZ, int endZ,
            int minimumY, int maximumY, BlockPos.MutableBlockPos pos) {
        var soil = placement.decorations().topSoil();
        if (soil == null || soil.coverage() == 0.0) {
            return;
        }
        RockInstance rock = placement.rock();
        if (!soil.appliesTo(rock.spec().type())) {
            return;
        }
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int top = rock.highestSolidY(x, z);
                if (top < minimumY + 1 || top >= maximumY - 1
                        || !flat(rock, x, z, top, 1, soil.maximumRelief())
                        || unit(placement.objectId() ^ 0x42BDE918L,
                        x, top, z) >= soil.coverage()
                        || !chunk.getBlockState(pos.set(x, top + 1, z)).isAir()) {
                    continue;
                }
                for (int y = top; y > top - soil.depth()
                        && y > minimumY; y--) {
                    if (rock.solid(x, y, z)) {
                        chunk.setBlockState(pos.set(x, y, z),
                                y == top ? soil.surface()
                                        : soil.subsurface(), false);
                    }
                }
            }
        }
    }

    /**
     * Runs after all rocks in a chunk are written. Call at the decoration
     * stage with the same placements. Only air-exposed positions are used.
     * Summit templates are clipped to this chunk after geometry checks.
     */
    public static void decorateInChunk(
            WorldGenLevel level, ChunkAccess chunk,
            List<Placement> placements, int minimumY, int maximumY) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (Placement placement : placements) {
            RockDecorationRules rules = placement.decorations();
            if (rules.equals(RockDecorationRules.NONE)) {
                continue;
            }
            RockInstance rock = placement.rock();
            RockInstance.Bounds bounds = rock.bounds();
            if (!bounds.intersectsChunk(chunk.getPos().x, chunk.getPos().z)) {
                continue;
            }
            int startX = Math.max(minX, bounds.minX());
            int endX = Math.min(minX + 15, bounds.maxX());
            int startZ = Math.max(minZ, bounds.minZ());
            int endZ = Math.min(minZ + 15, bounds.maxZ());
            if (rules.surfacePlant() != null) {
                placePlants(level, placement, startX, endX,
                        startZ, endZ, minimumY, maximumY);
            }
            if (rules.wallVines() != null) {
                placeVines(level, placement, minX, minZ, startX,
                        endX, startZ, endZ, minimumY, maximumY);
            }
            if (rules.topStructure() != null) {
                placeStructure(level, placement, minX, minZ,
                        minimumY, maximumY);
            }
        }
    }

    private static void placePlants(
            WorldGenLevel level, Placement placement,
            int startX, int endX, int startZ, int endZ,
            int minimumY, int maximumY) {
        RockInstance rock = placement.rock();
        var rule = placement.decorations().surfacePlant();
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                int y = rock.highestSolidY(x, z);
                if (y < minimumY || y >= maximumY - 1
                        || !flat(rock, x, z, y, 1, rule.maximumRelief())
                        || unit(placement.objectId() ^ 0x7614E9B2L,
                        x, y, z) >= rule.chance()) {
                    continue;
                }
                BlockPos plant = new BlockPos(x, y + 1, z);
                if (level.getBlockState(plant).isAir()
                        && rule.state().canSurvive(level, plant)) {
                    level.setBlock(plant, rule.state(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void placeVines(
            WorldGenLevel level, Placement placement,
            int minX, int minZ, int startX, int endX,
            int startZ, int endZ, int minimumY, int maximumY) {
        RockInstance rock = placement.rock();
        var rule = placement.decorations().wallVines();
        Direction[] sides = {Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST};
        for (int x = startX; x <= endX; x += 2) {
            for (int z = startZ; z <= endZ; z += 2) {
                for (int y = Math.max(minimumY + 1, rock.bounds().minY());
                     y <= Math.min(maximumY - 2, rock.bounds().maxY()); y += 3) {
                    if (!rock.solid(x, y, z)
                            || unit(placement.objectId() ^ 0x234A7B9CL,
                            x, y, z) >= rule.chance()) {
                        continue;
                    }
                    Direction side = sides[(int) (unit(
                            placement.objectId() ^ 0x6CE231ABL,
                            x, y, z) * sides.length)];
                    int px = x + side.getStepX(), pz = z + side.getStepZ();
                    if (px < minX || px >= minX + 16
                            || pz < minZ || pz >= minZ + 16
                            || rock.solid(px, y, pz)) {
                        continue;
                    }
                    var facing = switch (side.getOpposite()) {
                        case NORTH -> VineBlock.NORTH;
                        case EAST -> VineBlock.EAST;
                        case SOUTH -> VineBlock.SOUTH;
                        case WEST -> VineBlock.WEST;
                        default -> throw new IllegalStateException();
                    };
                    BlockState vine = Blocks.VINE.defaultBlockState()
                            .setValue(facing, true);
                    int length = 1 + (int) (unit(
                            placement.objectId() ^ 0x6D4178BEL,
                            px, y, pz) * rule.maximumLength());
                    for (int k = 0; k < length && y - k > minimumY; k++) {
                        BlockPos at = new BlockPos(px, y - k, pz);
                        if (!level.getBlockState(at).isAir()
                                || !vine.canSurvive(level, at)) {
                            break;
                        }
                        level.setBlock(at, vine, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void placeStructure(
            WorldGenLevel level, Placement placement,
            int minX, int minZ, int minimumY, int maximumY) {
        RockInstance rock = placement.rock();
        var rule = placement.decorations().topStructure();
        if (!rule.allowedTypes().contains(rock.spec().type())
                || unit(placement.objectId() ^ 0x4EB92C71L,
                (int) rock.spec().x(), 0,
                (int) rock.spec().z()) >= rule.chance()) {
            return;
        }
        Site site = siteForRock(rock, rule.flatRadius(),
                minimumY, maximumY);
        if (site == null) {
            return;
        }
        int choice = (int) (unit(placement.objectId() ^ 0x74C1D69EL,
                site.x(), site.y(), site.z()) * rule.templates().size());
        writeTemplate(level, minX, minZ, site,
                rule.templates().get(choice), maximumY);
    }

    private static Site siteForRock(RockInstance rock, int radius,
                                    int minimumY, int maximumY) {
        int centerX = (int) Math.floor(rock.spec().x());
        int centerZ = (int) Math.floor(rock.spec().z());
        int[] offsets = {0, 2, -2, 4, -4};
        for (int dx : offsets) {
            for (int dz : offsets) {
                int x = centerX + dx, z = centerZ + dz;
                int y = rock.highestSolidY(x, z);
                if (y > minimumY && y < maximumY - 13
                        && flat(rock, x, z, y, radius, 0)) {
                    return new Site(x, y + 1, z);
                }
            }
        }
        return null;
    }

    private static void writeTemplate(WorldGenLevel level,
                                      int minX, int minZ,
                                      Site site,
                                      RockDecorationRules.Template template,
                                      int maximumY) {
        // Check this chunk's entire portion before writing any of it.
        for (var part : template.parts()) {
            int x = site.x() + part.offset().getX();
            int y = site.y() + part.offset().getY();
            int z = site.z() + part.offset().getZ();
            if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16
                    || y >= maximumY) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                return;
            }
        }
        for (var part : template.parts()) {
            int x = site.x() + part.offset().getX();
            int y = site.y() + part.offset().getY();
            int z = site.z() + part.offset().getZ();
            if (x >= minX && x < minX + 16 && z >= minZ
                    && z < minZ + 16 && y < maximumY) {
                level.setBlock(new BlockPos(x, y, z), part.state(),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    private record Site(int x, int y, int z) {}

    private static boolean flat(RockInstance rock, int x, int z, int y,
                                int radius, int relief) {
        for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
            for (int dz = -radius; dz <= radius;
                 dz += Math.max(1, radius)) {
                int neighbor = rock.highestSolidY(x + dx, z + dz);
                if (neighbor == Integer.MIN_VALUE
                        || Math.abs(neighbor - y) > relief) {
                    return false;
                }
            }
        }
        return true;
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
}
