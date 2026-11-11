# Rock Tool Library — quickstart

**Target:** Minecraft 1.21.1, NeoForge 21.1.250, Java 21. The public Java package is `net.melvin.rocktool`. This standalone library generates procedural rock shapes; it does not register a mod feature or place rocks by itself.

## Add it to a mod

Build the library with `./gradlew build` (or `./gradlew.bat build` on Windows). Then run `./gradlew publishToMavenLocal` to publish `net.melvin:rock-tool-library:0.1.0-dev` locally. In your mod's Gradle build, add:

```gradle
repositories {
    mavenLocal()
}

dependencies {
    jarJar(implementation('net.melvin:rock-tool-library:0.1.0-dev'))
}
```

The library JAR's manifest must retain `FMLModType: GAMELIBRARY`: it uses Minecraft classes and needs the game module layer. A plain `implementation` dependency alone does not package the library into a distributable mod.

## World generation call flow

1. Your mod registers a `Feature`, defines its configured and placed feature JSON, and adds the placed feature to the desired biomes at an appropriate generation step.
2. In the feature, choose stable object IDs, positions, rock types, dimensions and a block material callback.
3. Call `RockPlacementHelper.plan(requests, variants, surfaceHeight)`. The height callback returns the **first air block above terrain** for each X/Z. Planning checks relief and spacing without writing blocks.
4. In **every chunk intersected by a rock**, reconstruct its owner's same deterministic plan and call `RockPlacementHelper.placeInChunk(chunk, placements, minY, maxY)` for that chunk. Use the overload `placeInChunk(level, chunk, placements, minY, maxY)` when using plants, vines or summit structures. Choose one overload per placement pass. The library clips writes to the supplied chunk; it does not schedule placement in neighboring chunks.

Minimal feature-side example (with `level` and `chunk` supplied by your worldgen code):

```java
RockVariantCache variants = new RockVariantCache(worldSeed);
RockMaterialField stone = (rock, x, y, z) -> Blocks.STONE.defaultBlockState();

var rock = new RockPlacementHelper.Request(
        objectId, RockType.FRACTURED_BOULDER, -1, centerX, centerZ,
        0.0, stone, 2.0, 12.0, 0.0, 6.0, 6.0, 8.0);

var placements = RockPlacementHelper.plan(
        List.of(rock), variants,
        (x, z) -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));

RockPlacementHelper.placeInChunk(
        chunk, placements, level.getMinBuildHeight(),
        level.getMaxBuildHeight() - 1);
```

`objectId` selects a stable cached variant when `variantIndex` is `-1`. `orientationRadians` rotates the shape. `embedDepth` lowers it into the ground; `maximumSurfaceRelief` rejects steep locations; `clearance` sets spacing from other rocks. The last three numbers set exact width, length and height, each at least four blocks. Use the shorter `Request` constructor without them for natural cached dimensions.

### Large rocks across chunk and biome boundaries

The minimal example writes **only the current chunk's slice**. If its center is in another chunk, that chunk must independently discover and plan the same rock; otherwise the shape ends at a chunk edge. `placeInChunk` deliberately never writes into a neighbor. A center-only check in the current chunk is insufficient for rocks that lean, rotate, or span several chunks.

For a cell-based test, each visited chunk can reconstruct candidate rocks from its own and neighboring owner cells, then keep only the placements whose bounds intersect it:

```java
int cellX = Math.floorDiv(chunk.getPos().getMinBlockX(), 128);
int cellZ = Math.floorDiv(chunk.getPos().getMinBlockZ(), 128);
List<RockPlacementHelper.Placement> intersecting = new ArrayList<>();

for (int dz = -1; dz <= 1; dz++) {
    for (int dx = -1; dx <= 1; dx++) {
        int ownerX = cellX + dx;
        int ownerZ = cellZ + dz;
        long id = ((long) ownerX << 32) ^ (ownerZ & 0xffffffffL);
        var request = new RockPlacementHelper.Request(
                id, RockType.PYRAMIDAL_PEAK, -1,
                ownerX * 128 + 64, ownerZ * 128 + 64,
                0.0, stone, 4.0, 0.0, 0.0, 76.0, 72.0, 56.0);
        var planned = RockPlacementHelper.plan(
                List.of(request), variants, (x, z) -> stableSurfaceY);
        for (var placement : planned) {
            if (placement.rock().bounds().intersectsChunk(
                    chunk.getPos().x, chunk.getPos().z)) {
                intersecting.add(placement);
            }
        }
    }
}
RockPlacementHelper.placeInChunk(chunk, intersecting, minY, maxY);
```

Here `stone`, `variants`, `stableSurfaceY`, `minY`, and `maxY` come from the surrounding worldgen code; add `java.util.ArrayList` and `java.util.List` imports. The constant height is for illustrating cross-chunk placement. For natural terrain, supply a deterministic height field whose result for an owner's rock is identical regardless of which chunk is currently being generated. Avoid a neighbor-chunk height lookup during decoration if it would trigger or depend on another chunk's generation. Search enough owner cells to cover the largest possible footprint, including lean and rotation; the 3×3 search above fits this particular 128-block-cell example, not every size or cluster.

The consuming mod must also invoke the placed feature in **all biomes a rock can cross**. A placed feature restricted to Plains, for example, cannot write the rock's remaining slice in an adjacent river or badlands chunk. If a biome placement filter is used, cover the whole intended footprint (the NeoForge `#c:is_overworld` tag is suitable for an unrestricted Overworld test). Keep IDs, seed, variant, dimensions, orientation, materials and surface height stable across visits, and test in newly generated chunks after changing placement rules.

## Choose shapes and materials

`RockType` controls **silhouette**, while `RockMaterialField` controls **blocks**. Available types are `SANDSTONE_FIN`, `STEPPED_BUTTE`, `FRACTURED_BOULDER`, `TAPERED_SPIRE`, `TRIANGULAR_WEDGE`, `TABLE_ROCK`, `TILTED_MONOLITH`, `LAYERED_SLAB`, `SPLIT_CROWN`, `BLADE_RIDGE`, `TALUS_CONE`, and `PYRAMIDAL_PEAK`. Pass the chosen type to `Request`; for a connected cluster, select several with `.types(...)`. Type names do not force a material: a `SANDSTONE_FIN` can be made of tuff, for example.

For one uniform material, use `(rock, x, y, z) -> Blocks.TUFF.defaultBlockState()`. For height bands and weighted patches, construct a palette:

```java
RockMaterialField palette = new RockMaterialField.Palette(
        worldSeed, 7.0, 0.65,
        new RockMaterialField.Band(0.0, 0.75,
                new RockMaterialField.Component(Blocks.TUFF.defaultBlockState(), 8.0),
                new RockMaterialField.Component(Blocks.STONE.defaultBlockState(), 2.0)),
        new RockMaterialField.Band(0.75, 1.0,
                new RockMaterialField.Component(Blocks.STONE.defaultBlockState(), 1.0)));
```

Bands run from the base (`0.0`) to the summit (`1.0`), must be contiguous, and must cover the entire interval. A band's weights set **relative** proportions: 8 to 2 favors tuff over stone. `patchScale` (`7.0` here, minimum `1`) controls patch size; larger values produce broader areas. `patchInfluence` ranges from `0` (fine speckling) to `1` (coherent patches). Give the palette a stable seed to keep results repeatable. Pass `palette` as the `material` argument of each `Request`.

For a connected mass of several rocks, choose types and optionally assign a different palette to each member:

```java
var recipe = RockClusterPlanner.builder(clusterSeed, centerX, centerZ)
        .count(12).radius(36).dimensions(16, 20, 14)
        .types(RockType.FRACTURED_BOULDER, RockType.LAYERED_SLAB)
        .material(palette, 3.0)
        .material((rock, x, y, z) -> Blocks.STONE.defaultBlockState(), 1.0)
        .build();
var cluster = RockClusterPlanner.plan(recipe, variants, surfaceHeight);
RockPlacementHelper.placeClusterInChunk(level, chunk, cluster, minY, maxY);
```

Here, 3:1 chooses a material field **per cluster member**; proportions inside a member are set separately by its palette. Cluster count accepts 2–200. Discover and plan the same cluster in every intersecting chunk, including chunks outside its center cell.

## Add a top surface and decorations

Use `RockDecorationRules.builder()` for optional layers. Top soil replaces the rock's exposed top blocks; a surface plant occupies an air block above a suitable summit. Vines and structures use additional decoration passes.

```java
RockDecorationRules rules = RockDecorationRules.builder()
        .topSoil(Blocks.GRASS_BLOCK.defaultBlockState(),
                 Blocks.DIRT.defaultBlockState(), 2, 1, 0.8)
        .plant(Blocks.SHORT_GRASS.defaultBlockState(), 0.12, 1)
        .vines(0.04, 4)
        .structure(Set.of(RockType.LAYERED_SLAB), 0.08, 2, 1,
                   RockDecorationRules.cairn(Blocks.COBBLESTONE.defaultBlockState(), 2))
        .build();
```

In `.topSoil(surface, subsurface, depth, maximumRelief, coverage)`, `depth` is 1–8 layers, `maximumRelief` is the allowed height difference in nearby samples, and `coverage` is a probability from 0 to 1. Use `.topSoilFor(Set.of(RockType.LAYERED_SLAB), ...)` to restrict the cap to particular shapes. The plant's chance and vine chance also range from 0 to 1; plants must pass Minecraft's survival check. The structure example places a two-block cairn only on selected flat slabs. Omit any builder call you do not want.

To attach `rules` to a single rock, use the full `Request` constructor with `rules` **after** width, length and height. To attach them to a cluster, call `.decorations(rules)` on its builder. For individual rocks, `placeInChunk(chunk, ...)` writes geometry and top soil, while `placeInChunk(level, chunk, ...)` also adds plants, vines and structures. For clusters, `placeClusterInChunk(level, chunk, ...)` decorates the exposed surface of the assembled mass. If you only need stone shapes, leave decorations at their default `RockDecorationRules.NONE`.

### Complete example: decorated rock cluster

Use this inside your mod's world generation code after it has selected a stable cluster seed and center. `level` is a `WorldGenLevel`, and `chunk` is the `ChunkAccess` currently being generated. The relevant imports include `java.util.Set`, `net.minecraft.world.level.block.Blocks`, and `net.minecraft.world.level.levelgen.Heightmap` alongside the `net.melvin.rocktool` classes.

```java
RockVariantCache variants = new RockVariantCache(worldSeed);

RockMaterialField tuffPalette = new RockMaterialField.Palette(
        worldSeed ^ 0x527A1L, 8.0, 0.7,
        new RockMaterialField.Band(0.0, 0.8,
                new RockMaterialField.Component(Blocks.TUFF.defaultBlockState(), 9),
                new RockMaterialField.Component(Blocks.STONE.defaultBlockState(), 1)),
        new RockMaterialField.Band(0.8, 1.0,
                new RockMaterialField.Component(Blocks.TUFF.defaultBlockState(), 6),
                new RockMaterialField.Component(Blocks.COBBLESTONE.defaultBlockState(), 4)));

RockDecorationRules decorations = RockDecorationRules.builder()
        .topSoil(Blocks.GRASS_BLOCK.defaultBlockState(),
                 Blocks.DIRT.defaultBlockState(), 2, 1, 0.8)
        .plant(Blocks.SHORT_GRASS.defaultBlockState(), 0.1, 1)
        .vines(0.03, 4)
        .structure(Set.of(RockType.LAYERED_SLAB), 0.15, 2, 2,
                   RockDecorationRules.cairn(
                           Blocks.COBBLESTONE.defaultBlockState(), 2))
        .build();

var recipe = RockClusterPlanner.builder(clusterSeed, centerX, centerZ)
        .count(12).radius(36).dimensions(16, 20, 14)
        .scale(0.8, 1.3).embed(3).stackChance(0.35)
        .types(RockType.FRACTURED_BOULDER, RockType.LAYERED_SLAB)
        .material(tuffPalette, 3)
        .material((rock, x, y, z) -> Blocks.STONE.defaultBlockState(), 1)
        .decorations(decorations)
        .build();

var cluster = RockClusterPlanner.plan(recipe, variants,
        (x, z) -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));
RockPlacementHelper.placeClusterInChunk(level, chunk, cluster,
        level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
```

The two material weights choose a palette for each member in a 3:1 ratio; the palette's own weights distribute blocks within that member. Top soil affects suitable exposed columns of the **assembled** cluster, plants and vines are optional, and the cairn is attempted only on flat `LAYERED_SLAB` members. Reuse the same world-seed cache and deterministic cluster recipe for every intersecting chunk, and call the placement method once for each such chunk. Your mod still decides which biome and generation step invoke this code.

## Complete feature and public API reference (`0.1.0-dev`)

The library provides twelve deterministic rock silhouettes, 256 cached variants per type and world seed, exact custom dimensions, terrain relief checks, overlap and clearance rules, chunk-clipped block placement, connected and stacked clusters of up to 200 rocks, weighted materials, height-banded palettes, surface erosion, optional top soil, plants, vines, and summit structures. It registers **no** blocks, biomes, configured features, placed features, or biome modifiers; those belong to the consuming mod.

All names below are in `net.melvin.rocktool`. Signatures omit Java package qualifiers. Java records also provide public component accessors with the same names as their fields, such as `request.type()` and `spec.height()`. Constructors for records are public unless a shorter overload is noted.

### Shape and geometry

| Type | Public constructors, functions, and fields | Purpose |
| --- | --- | --- |
| `RockType` | The twelve enum constants listed under “Choose shapes and materials” | Selects the shape, independently of block material. |
| `RockSpec` | `RockSpec(type, seed, x, baseY, z, width, length, height, rotation, edgeSharpness, smoothing, erosion, leanX, leanZ, layers)`; `example(type, seed, x, baseY, z)` | Full shape parameters or a starting shape for a type. Dimensions must be at least four; the three surface controls range from 0 to 1; `layers` is 2–16. |
| `RockGenerator` | `create(RockSpec)` | Generates an immutable `RockInstance` directly from a spec. Use this for manual shape creation; the variant cache handles reusable generation. |
| `RockVariantCache` | `RockVariantCache(worldSeed)`; `VARIANTS_PER_TYPE` (`256`); `template(type, index)`; `indexFor(type, objectId)`; `place(type, index, x, baseY, z, rotation)`; `place(type, index, x, baseY, z, rotation, width, length, height)` | Creates and reuses seeded shapes. Indices are 0–255; `indexFor` chooses one deterministically; `template` is centered at the origin; `place` positions it with natural or custom dimensions. |
| `RockInstance` | `spec()`; `bounds()`; `solid(x, y, z)`; `solidAt(x, y, z)`; `highestSolidY(x, z)`; `stratumAt(y)` | Inspects a generated shape without writing blocks. `highestSolidY` returns `Integer.MIN_VALUE` for an empty column; `stratumAt` returns `-1` outside the rock. |
| `RockInstance.Bounds` | `Bounds(minX, minY, minZ, maxX, maxY, maxZ)`; `intersectsChunk(chunkX, chunkZ)` | World-space bounding box and chunk intersection test; chunk coordinates are chunk indices. |
| `RockSurfaceErosionField` | `maximumAbs(spec)`; `sample(spec, x, y, z, t)` | Surface-relief helper. `sample` uses rock-local coordinates and normalized height `t`; positive values add rock and negative values remove it. |

### Materials and decoration rules

| Type | Public constructors and functions | Purpose |
| --- | --- | --- |
| `RockMaterialField` | `blockAt(rock, x, y, z)` | Functional interface returning the block state for one solid rock block. A lambda supplies a uniform or custom field. |
| `RockMaterialField.Component` | `Component(state, weight)` | Weighted block choice; weight must be positive. |
| `RockMaterialField.Band` | `Band(from, to, List<Component>)`; `Band(from, to, Component...)` | Height fraction with inclusive lower and exclusive upper bound. Bands must tile 0–1 without gaps. |
| `RockMaterialField.Palette` | `Palette(seed, patchScale, patchInfluence, Band...)`; `blockAt(rock, x, y, z)` | Seeded, weighted patches in height bands; scale is at least 1, influence is 0–1. |
| `RockDecorationRules` | `NONE`; `builder()`; `RockDecorationRules(topSoil, wallVines, surfacePlant, topStructure)`; `cairn(block, height)` | Combines four optional rule families. `cairn` creates a 1–12 block vertical summit template. |
| `RockDecorationRules.Builder` | `topSoil(state, depth, maximumRelief, coverage)`; `topSoil(surface, subsurface, depth, maximumRelief, coverage)`; `topSoilFor(types, surface, subsurface, depth, maximumRelief, coverage)`; `vines(chance, maximumLength)`; `plant(state, chance, maximumRelief)`; `structure(types, chance, flatRadius, maximumPerCluster, Template...)`; `build()` | Fluent optional decoration configuration. A `types` set restricts top soil or summit structures to specified silhouettes. |
| `RockDecorationRules.TopSoil` | `TopSoil(surface, subsurface, depth, maximumRelief, coverage, allowedTypes)`; overloads with `(state, depth, maximumRelief, coverage)` and `(surface, subsurface, depth, maximumRelief, coverage)`; `appliesTo(type)` | Replaces top layers of exposed rock. Empty `allowedTypes` means all types. |
| `RockDecorationRules.WallVines` | `WallVines(chance, maximumLength)` | Vine chance and maximum length (1–12). |
| `RockDecorationRules.SurfacePlant` | `SurfacePlant(state, chance, maximumRelief)` | Plant block state, chance, and allowed top relief. |
| `RockDecorationRules.TopStructure` | `TopStructure(allowedTypes, chance, flatRadius, maximumPerCluster, List<Template>)` | Chooses a summit template for eligible, sufficiently flat rocks; radius is 1–8 and limit is 1–200 per cluster. |
| `RockDecorationRules.Part` | `Part(offset, state)` | One structure block at a relative `BlockPos`; vertical offset is 0–12. |
| `RockDecorationRules.Template` | `Template(List<Part>)` | Nonempty collection of summit structure parts. |

All decoration chances and top-soil coverage are fractions from 0 to 1. The builder makes the record constructors optional for ordinary use.

### Planning and placement

| Type | Public constructors and functions | Purpose |
| --- | --- | --- |
| `RockPlacementHelper.SurfaceHeight` | `at(x, z)` | Functional interface for the first air Y above terrain. |
| `RockPlacementHelper.Request` | `Request(objectId, type, variantIndex, x, z, orientationRadians, material, embedDepth, maximumSurfaceRelief, clearance)`; overload adding `width, length, height`; full overload adding `decorations` after dimensions | Candidate rock. Use variant `-1` for a stable automatic choice. Custom dimensions must all be at least four, or all zero for natural size. |
| `RockPlacementHelper.Placement` | `Placement(objectId, rock, material, clearance)`; full overload adding `decorations` | Accepted planned rock with geometry, material, spacing and optional rules. |
| `RockPlacementHelper` | `plan(requests, variants, surface)`; `placeInChunk(chunk, placements, minY, maxY)`; `placeInChunk(chunk, placements, minY, maxY, long[] profile)`; `placeInChunk(level, chunk, placements, minY, maxY)`; `decorateInChunk(level, chunk, placements, minY, maxY)`; `placeClusterInChunk(level, chunk, cluster, minY, maxY)` | Plans accepted rocks, writes only their intersecting chunk portions, and optionally decorates. `decorateInChunk` is the separate pass for callers that already wrote geometry. The optional profiling array tracks sampled geometry and write timings and must have at least six entries. |
| `RockClusterPlanner.Builder` | `count(value)`; `radius(value)`; `dimensions(width, length, height)`; `scale(minimum, maximum)`; `embed(value)`; `stackChance(value)`; `overlap(value)`; `types(RockType...)`; `material(field, weight)`; `decorations(rules)`; `build()` | Defines a connected cluster recipe. Defaults include six members, stone material, and three rock types. `stackChance` is 0–1 and `overlap` is 0.20–0.80. |
| `RockClusterPlanner` | `builder(seed, x, z)`; `plan(spec, variants, ground)`; `placeInChunk(level, chunk, cluster, minY, maxY)` | Builds, plans, and places connected clusters. Its `placeInChunk` delegates to `RockPlacementHelper.placeClusterInChunk`. |
| `RockClusterPlanner.MaterialChoice` | `MaterialChoice(material, weight)` | One weighted material field chosen per cluster member. |
| `RockClusterPlanner.Spec` | Full record constructor with `List<MaterialChoice>`; overload with a single `RockMaterialField` instead; component accessors | Immutable cluster recipe returned by the builder. It holds seed, center, count, radius, dimensions, scale range, embed depth, stack chance, overlap, types, materials and rules. |
| `RockClusterPlanner.Cluster` | `Cluster(seed, components, decorations)`; `solid(x, y, z)`; `highestSolidY(x, z)`; `topOwner(x, z)` | Assembled group. `topOwner` returns the member highest in that column, or `null` if empty. |

For the direct `RockPlacementHelper.Request`, positive `clearance` separates nearby rocks, zero allows contact, and negative clearance permits overlap. The first valid request in iteration order wins if footprints conflict. Cluster placement instead connects and may stack its generated members. Plan before placement, then repeat the same deterministic plan in every intersecting chunk.

This is version `0.1.0-dev`. A public license and release channel have not yet been chosen.
