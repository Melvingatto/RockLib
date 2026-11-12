# Rock Tool Library — quickstart

**Target:** Minecraft 1.21.1, NeoForge 21.1.250, Java 21. The public Java package is `net.melvin.rocktool`. This standalone library generates procedural rock shapes; it does not register a mod feature or place rocks by itself.

## What you need to do

1. Download the published Rock Tool Library JAR from CurseForge. Add it to your mod's compile classpath and install it alongside your mod at runtime (see **Add it to a mod**).
2. In your mod, register a rock `Feature` and add configured-feature, placed-feature, and biome-modifier JSON. You can copy the complete example files below; set their `rockexamples` mod ID and Java package to your own.
3. Choose deterministic rock positions, IDs, shapes, sizes, and materials. For large rocks, have every intersected chunk reconstruct the same plan and write only its own portion.
4. Supply a first-air terrain height to the planner. The included `RockTerrainSampler` reads the generator's base height consistently across chunk borders; use your own deterministic height field if your generator needs different terrain detail.
5. Generate **new chunks** to check placement, chunk borders, and decorations. Adjust your feature's placement and the rock parameters as needed.

Start with **Complete consuming-mod example files** for copyable code and JSON. The sections before it explain the arguments and placement rules.

## Add it to a mod

Download the Rock Tool Library JAR from CurseForge and place it in your mod project's `libs/` directory. Add that JAR to the compile classpath in your mod's `build.gradle`:

```gradle
dependencies {
    compileOnly files('libs/RockToolLibrary.jar') // Use the downloaded JAR's actual filename.
}
```

Install the published library JAR alongside your mod when running the game, including in your development run. `compileOnly` makes its classes available to your code without bundling a second copy inside your mod. Use the same library version for compilation and testing.

## World generation call flow

1. Your mod registers a `Feature`, defines its configured and placed feature JSON, and adds the placed feature to the desired biomes at an appropriate generation step.
2. In the feature, choose stable object IDs, positions, rock types, dimensions and a block material callback.
3. Call `RockPlacementHelper.plan(requests, variants, surfaceHeight)`. The height callback returns the **first air block above terrain** for each X/Z. Planning checks relief and spacing without writing blocks.
4. In **every chunk intersected by a rock**, reconstruct its owner's same deterministic plan and call `RockPlacementHelper.placeInChunk(chunk, placements, minY, maxY)` for that chunk. Use the overload `placeInChunk(level, chunk, placements, minY, maxY)` when using plants, vines or summit structures. Choose one overload per placement pass. The library clips writes to the supplied chunk; it does not schedule placement in neighboring chunks.

Minimal feature-side example (with `context`, `level`, and `chunk` supplied by your feature):

```java
RockVariantCache variants = new RockVariantCache(worldSeed);
RockMaterialField stone = (rock, x, y, z) -> Blocks.STONE.defaultBlockState();

var rock = new RockPlacementHelper.Request(
        objectId, RockType.FRACTURED_BOULDER, -1, centerX, centerZ,
        0.0, stone, 2.0, 12.0, 0.0, 6.0, 6.0, 8.0);

var placements = RockPlacementHelper.plan(
        List.of(rock), variants, RockTerrainSampler.from(context));

RockPlacementHelper.placeInChunk(
        chunk, placements, level.getMinBuildHeight(),
        level.getMaxBuildHeight() - 1);
```

### Where the values in that example come from

The library does **not** assign rock IDs or pick spawn positions. Your mod's worldgen planner does that. Inside a registered `Feature`, `level` normally comes from `context.level()`, and `chunk` is the chunk currently being decorated (for a per-chunk feature, `level.getChunk(context.origin())`). Your planner chooses `centerX` and `centerZ` as **world block coordinates**, not chunk indices. For example, the center of a 128-block owner cell is `cellX * 128 + 64, cellZ * 128 + 64`.

`objectId` is a `long` identifying **one planned rock**. It is not a registry name, a UUID supplied by Minecraft, or the rock type. You create it from stable owner coordinates and a stable rock number. With exactly one rock per owner cell, this preserves both signed cell coordinates without collisions:

```java
int cellX = Math.floorDiv(centerX, 128);
int cellZ = Math.floorDiv(centerZ, 128);
long objectId = ((long) cellX << 32) | (cellZ & 0xffffffffL);
long worldSeed = level.getSeed();
```

If a cell owns multiple rocks, also mix a fixed per-cell candidate index into the ID using a deterministic 64-bit hash. The same rock must receive the **same** ID when a neighboring chunk reconstructs it. Do not derive it from the *currently visiting* chunk, a new random draw, `System.nanoTime()`, or an object identity hash. `RockVariantCache(worldSeed)` uses the world seed together with the ID and type when `variantIndex == -1`; use one cache for that world seed rather than constructing it for every rock. The ID alone does not choose where a rock spawns.

The `RockPlacementHelper.Request(...)` arguments, in their exact order, are:

| Argument | Meaning and units | Where to get or choose it |
| --- | --- | --- |
| `objectId` | Stable `long` for this rock; used for automatic variant selection and deterministic decoration choices. | Derive from its owner cell/region and fixed candidate index as above. |
| `type` | One `RockType` silhouette. | Pick from the enum, such as `RockType.FRACTURED_BOULDER`; your biome or feature chooses it. |
| `variantIndex` | Cached shape number `0`–`255`; `-1` derives one from the world seed, type, and `objectId`. | Use `-1` normally, or choose an explicit index when you need a particular variant. |
| `x`, `z` | Rock center in world block coordinates; decimals are allowed. | Your region or feature planner's chosen spawn position. |
| `orientationRadians` | Horizontal rotation in **radians**; `0` leaves the cached orientation, `Math.PI / 2` makes a quarter-turn. | Choose a fixed angle or calculate one deterministically from the owner's seed/ID. |
| `material` | `RockMaterialField` returning a `BlockState` for each solid rock block. Its callback receives the rock and world `x, y, z` automatically. | Supply a uniform lambda like `stone`, a `Palette`, or your own material field. |
| `embedDepth` | Blocks by which the base is lowered below the sampled surface; must be nonnegative. | Choose for desired ground contact; `2.0` sinks the base two blocks. |
| `maximumSurfaceRelief` | Largest allowed difference in blocks between the heights sampled around this footprint; must be nonnegative. A request is rejected when the difference exceeds it. | Choose a small value for flat sites, larger for sloped sites; `12.0` permits up to 12 blocks of sampled relief. |
| `clearance` | Approximate extra separation in blocks from **other requests in the same `plan` call**. Zero permits touching, a negative value allows overlap. | Choose for the local group; this does not inspect rocks planned in other calls or existing world blocks. |
| `width`, `length`, `height` | Requested rock dimensions in blocks. Width/length use the shape's local horizontal axes, before rotation and lean. All three must be at least `4.0`, or all three `0.0` for natural cached dimensions. | Choose exact sizes, such as `6, 6, 8`, or omit all three by using the shorter constructor. These are not guaranteed bounding-box extents. |
| `decorations` | Optional rock decoration rules, after the dimensions in the full constructor. | Build with `RockDecorationRules.builder()`; the shorter constructors use `RockDecorationRules.NONE`. |

The other calls in the example take these inputs:

| Call / argument | Meaning and source |
| --- | --- |
| `new RockVariantCache(worldSeed)` | Reusable collection of shape variants keyed by the world's seed. Obtain `worldSeed` from your worldgen context, such as `level.getSeed()`; keep the same cache for all chunks of that world. |
| `RockPlacementHelper.plan(requests, variants, surfaceHeight)` | `requests` is a stable-ordered collection of candidates; `variants` is the cache above; `surfaceHeight` is your `(worldX, worldZ) -> first air Y above terrain` callback. Planning returns accepted `Placement` objects without changing blocks. Candidates earlier in the list win spacing conflicts. |
| `RockTerrainSampler.from(context)` | Complete example below: calls the active generator's `getBaseHeight` for each world X/Z without loading a neighboring chunk. The `x, z` values are supplied by `plan`, including center and footprint samples. It samples **base terrain before decorations**, so structures, earlier features, and other later terrain changes are not reflected. |
| `RockPlacementHelper.placeInChunk(chunk, placements, minY, maxY)` | `chunk` is the **current** `ChunkAccess`; `placements` is the deterministic plan to clip into it; `minY`/`maxY` are inclusive build-range limits. In the example they come from `level.getMinBuildHeight()` and `level.getMaxBuildHeight() - 1`. This overload writes shape and top soil, not vines, plants, or summit structures. |

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

In this example, `128` is a **cell size selected by the consuming mod**, not a library setting. `chunk.getPos().getMinBlockX/Z()` gives this chunk's world-space origin; `Math.floorDiv` correctly handles negative coordinates. `ownerX/Z` select a neighboring candidate cell, `+64` places its center halfway across that cell, and `id` is the same 64-bit packed cell ID each visiting chunk calculates. `dx/dz = -1..1` visit a 3×3 cell neighborhood. `bounds().intersectsChunk(chunkX, chunkZ)` takes **chunk indices** from `chunk.getPos()` and filters candidates before the current chunk is written. `stableSurfaceY` is a single chosen first-air Y for this flat demonstration; replace it with an owner-stable terrain height callback for real terrain. Increase the searched cell radius if the largest rock or cluster can extend farther.

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

| Palette argument | Meaning and source |
| --- | --- |
| `Palette(seed, patchScale, patchInfluence, bands...)` | `seed` is a stable `long`, commonly `worldSeed ^ a fixed salt` to give this palette an independent pattern. `patchScale` is the spatial size in blocks for coherent material patches (`>= 1`); `patchInfluence` is `0..1`, mixing fine per-block variation with that patch field. Supply one or more `Band` objects in height order. |
| `Band(from, to, components...)` | `from` and `to` are normalized rock height fractions, base `0.0` to top `1.0`. Adjacent bands must meet exactly and cover the entire range. Supply the material options for this range. |
| `Component(state, weight)` | `state` is the Minecraft `BlockState` to write, for example `Blocks.TUFF.defaultBlockState()` or a registered mod block's default state. `weight` is a positive **relative** weight among the components in its band, not a percentage. |
| `RockMaterialField.blockAt(rock, x, y, z)` | For a custom field, the library calls this for each solid block. `rock` is its placed geometry, and `x, y, z` are world block coordinates; your lambda returns a `BlockState`. You do not pass these four values when creating the field. |

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

For the cluster example, choose `centerX/Z` with your region planner and derive a distinct, stable `clusterSeed` from the world seed and the cluster owner's ID (for example `worldSeed ^ (ownerId * 0x9E3779B97F4A7C15L)`). Reconstruct the same seed and recipe in every affected chunk. `surfaceHeight` is the same first-air callback described above. `minY` and `maxY` are the inclusive world build bounds, such as `level.getMinBuildHeight()` and `level.getMaxBuildHeight() - 1`.

| Cluster call / argument | Meaning and source |
| --- | --- |
| `builder(seed, x, z)` | Stable per-cluster `long` seed and world-space center coordinates chosen by your planner. The seed drives member selection, placement, scale, and orientation. |
| `.count(12)` | Target number of connected members; allowed `2..200`. The planner may return fewer if candidates fail its bounded connection attempts. |
| `.radius(36)` | Maximum **member-center** distance from the cluster center in blocks, not the final extent of all member geometry. Allow room beyond this radius when searching intersecting chunks. |
| `.dimensions(16, 20, 14)` | Base width, length, height of each member in blocks, before its random scale multiplier; each final dimension must be at least four. |
| `.scale(minimum, maximum)` | Inclusive range of deterministic multipliers applied to those three dimensions; minimum must be positive, maximum at least minimum. For example, `0.8..1.3` gives members at 80%–130% of base dimensions. |
| `.embed(3)` | Blocks that ground-based members sink below the sampled first-air surface; nonnegative. Members stacked on another member instead use the overlap rule. |
| `.stackChance(0.35)` | Probability `0..1` that a new member attempts to rest on its parent rock rather than the ground. |
| `.overlap(value)` | Fraction `0.20..0.80` controlling how far a stacked member sinks into its support. It also sets an internal negative spacing value for cluster components; it is not an extra number of blocks. |
| `.types(types...)` | Eligible `RockType` silhouettes; choose one or more enum constants. The seed selects members from this list. |
| `.material(field, weight)` | Add one `RockMaterialField` with a positive relative weight. The library picks a field **per member**; within a `Palette`, component weights separately select blocks. Omit this call for the default stone field. |
| `.decorations(rules)` / `.build()` | Optional `RockDecorationRules` for the assembled cluster; `.build()` validates and returns an immutable recipe (`Spec`). Omit decorations for `NONE`. |
| `RockClusterPlanner.plan(spec, variants, ground)` | `spec` is that recipe; `variants` is the world-seed cache; `ground` is a deterministic first-air Y callback. It returns a planned cluster without writing blocks. |
| `RockPlacementHelper.placeClusterInChunk(level, chunk, cluster, minY, maxY)` | `level` is `WorldGenLevel` for decoration reads/writes, `chunk` is the current chunk, `cluster` is the **same** deterministic plan in each intersecting chunk, and Y bounds are inclusive. It writes geometry and optional decorations only for the current chunk. |

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

| Decoration call / argument | Meaning and source |
| --- | --- |
| `RockDecorationRules.builder()` | Creates an optional rule set; call only the families you want, then `.build()`. `RockDecorationRules.NONE` skips all of them. |
| `.topSoil(surface, subsurface, depth, maximumRelief, coverage)` | `surface` and `subsurface` are block states chosen by your mod (e.g. grass above dirt); `depth` is `1..8` rock layers; `maximumRelief` is a nonnegative allowed difference among nearby top heights; `coverage` is a `0..1` probability per suitable exposed column. The one-state overload supplies its own subsurface (`DIRT` for `GRASS_BLOCK`). |
| `.topSoilFor(types, surface, subsurface, depth, maximumRelief, coverage)` | Same values, plus a `Set<RockType>` of eligible shapes selected by your mod. An empty set means all types; a regular `.topSoil(...)` also applies to all types. |
| `.plant(state, chance, maximumRelief)` | `state` is the plant block state to try above a rock (e.g. `Blocks.SHORT_GRASS.defaultBlockState()`); `chance` is `0..1` per suitable exposed column; `maximumRelief` is the nearby-height tolerance in blocks. Placement also requires air and `state.canSurvive(...)`. |
| `.vines(chance, maximumLength)` | `chance` is `0..1` per sampled wall candidate, **not** a percentage of all blocks; `maximumLength` is the upper limit `1..12` blocks for a vine run. A run may stop sooner if space or survival conditions fail. |
| `.structure(types, chance, flatRadius, maximumPerCluster, templates...)` | `types` is a nonempty set of eligible `RockType`s; `chance` is `0..1` per eligible member; `flatRadius` (`1..8` blocks) is the radius of the fully level top required by the template; `maximumPerCluster` (`1..200`) limits accepted sites **within each chunk's placement pass** in the current implementation; `templates` are one or more summit designs created by your code or by `cairn(...)`. A cluster spanning chunks can therefore exceed that number overall. |
| `RockDecorationRules.cairn(block, height)` | Makes a vertical `Template`: `block` is a Minecraft `BlockState`, and `height` is `1..12` blocks. A custom `Template` is a nonempty `List<Part>`; each `Part(offset, state)` uses a relative `BlockPos` and a block state chosen by your mod. |

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
        RockTerrainSampler.from(context));
RockPlacementHelper.placeClusterInChunk(level, chunk, cluster,
        level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
```

The two material weights choose a palette for each member in a 3:1 ratio; the palette's own weights distribute blocks within that member. Top soil affects suitable exposed columns of the **assembled** cluster, plants and vines are optional, and the cairn is attempted only on flat `LAYERED_SLAB` members. Reuse the same world-seed cache and deterministic cluster recipe for every intersecting chunk, and call the placement method once for each such chunk. Your mod still decides which biome and generation step invoke this code.

## Complete consuming-mod example files

The following files go in an existing NeoForge 1.21.1 mod with mod ID
`rockexamples`. Add the library dependency shown above, and use the same mod ID
in your mod metadata. Both examples use owner cells: every chunk checks nearby
owners and writes only the portion of each rock inside that chunk. The feature
must run in every biome it can cross, so the biome modifier covers the whole
Overworld. Change `#c:is_overworld` if your mod targets another dimension.

The included terrain sampler queries the active chunk generator's
`getBaseHeight` for each X/Z, using `WORLD_SURFACE_WG`. That gives the first
air block above **base generated terrain** without looking up neighboring
chunks, so the same rock gets the same elevation from every intersecting chunk.
It does not include decorations, structures, or terrain changes made after
base generation. A custom generator can supply its own deterministic height
field instead. These examples also use a seed-keyed variant cache; scope or
evict caches appropriately in a long-running server.

**`src/main/java/net/example/rockexamples/RockExamplesMod.java`**

```java
package net.example.rockexamples;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RockExamplesMod.MOD_ID)
public final class RockExamplesMod {
    public static final String MOD_ID = "rockexamples";

    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MOD_ID);

    static {
        FEATURES.register("single_rock", () ->
                new RockExampleFeatures.SingleRockFeature(
                        NoneFeatureConfiguration.CODEC));
        FEATURES.register("decorated_cluster", () ->
                new RockExampleFeatures.DecoratedClusterFeature(
                        NoneFeatureConfiguration.CODEC));
    }

    public RockExamplesMod(IEventBus modBus) {
        FEATURES.register(modBus);
    }
}
```

**`src/main/java/net/example/rockexamples/RockTerrainSampler.java`**

```java
package net.example.rockexamples;

import net.melvin.rocktool.RockPlacementHelper;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Stable pre-decoration terrain height, including queries outside this chunk. */
public final class RockTerrainSampler {
    private RockTerrainSampler() {}

    public static RockPlacementHelper.SurfaceHeight from(FeaturePlaceContext<?> context) {
        WorldGenLevel level = context.level();
        var generator = context.chunkGenerator();
        var randomState = level.getLevel().getChunkSource().randomState();
        return (x, z) -> generator.getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
    }
}
```

**`src/main/java/net/example/rockexamples/RockExampleFeatures.java`**

```java
package net.example.rockexamples;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.melvin.rocktool.RockClusterPlanner;
import net.melvin.rocktool.RockDecorationRules;
import net.melvin.rocktool.RockMaterialField;
import net.melvin.rocktool.RockPlacementHelper;
import net.melvin.rocktool.RockType;
import net.melvin.rocktool.RockVariantCache;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Two independent examples using the generator's base terrain height. */
public final class RockExampleFeatures {
    private static final int CELL_SIZE = 256;
    private static final ConcurrentMap<Long, RockVariantCache> VARIANTS =
            new ConcurrentHashMap<>();
    private static final RockMaterialField STONE =
            (rock, x, y, z) -> Blocks.STONE.defaultBlockState();

    private RockExampleFeatures() {}

    private static RockVariantCache variants(long worldSeed) {
        return VARIANTS.computeIfAbsent(worldSeed, RockVariantCache::new);
    }

    private static long ownerId(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xffffffffL);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static boolean selected(long seed, long id, long salt) {
        return (mix(seed ^ id ^ salt) & 3L) == 0L; // One in four cells.
    }

    public static final class SingleRockFeature extends Feature<NoneFeatureConfiguration> {
        public SingleRockFeature(Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            WorldGenLevel level = context.level();
            ChunkAccess chunk = level.getChunk(context.origin());
            int cellX = Math.floorDiv(chunk.getPos().getMinBlockX(), CELL_SIZE);
            int cellZ = Math.floorDiv(chunk.getPos().getMinBlockZ(), CELL_SIZE);
            long worldSeed = level.getSeed();
            RockVariantCache cache = variants(worldSeed);
            var surfaceHeight = RockTerrainSampler.from(context);
            List<RockPlacementHelper.Placement> intersecting = new ArrayList<>();

            // Each chunk discovers owners whose rocks might enter it.
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int ownerX = cellX + dx, ownerZ = cellZ + dz;
                    long id = ownerId(ownerX, ownerZ);
                    if (!selected(worldSeed, id, 0x51A9L)) continue;
                    int x = ownerX * CELL_SIZE + 64;
                    int z = ownerZ * CELL_SIZE + 64;
                    long objectId = mix(id ^ 0x51A9L);
                    double angle = (mix(worldSeed ^ objectId) >>> 11)
                            * 0x1.0p-53 * Math.PI * 2.0;
                    var request = new RockPlacementHelper.Request(
                            objectId, RockType.TILTED_MONOLITH, -1,
                            x, z, angle, STONE, 4.0, 0.0, 0.0,
                            72.0, 64.0, 68.0);
                    var planned = RockPlacementHelper.plan(
                            List.of(request), cache, surfaceHeight);
                    for (var placement : planned) {
                        if (placement.rock().bounds().intersectsChunk(
                                chunk.getPos().x, chunk.getPos().z)) {
                            intersecting.add(placement);
                        }
                    }
                }
            }
            if (intersecting.isEmpty()) return false;
            RockPlacementHelper.placeInChunk(chunk, intersecting,
                    level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            return true;
        }
    }

    public static final class DecoratedClusterFeature extends Feature<NoneFeatureConfiguration> {
        public DecoratedClusterFeature(Codec<NoneFeatureConfiguration> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            WorldGenLevel level = context.level();
            ChunkAccess chunk = level.getChunk(context.origin());
            int cellX = Math.floorDiv(chunk.getPos().getMinBlockX(), CELL_SIZE);
            int cellZ = Math.floorDiv(chunk.getPos().getMinBlockZ(), CELL_SIZE);
            long worldSeed = level.getSeed();
            RockVariantCache cache = variants(worldSeed);
            var surfaceHeight = RockTerrainSampler.from(context);
            boolean wrote = false;

            RockMaterialField palette = new RockMaterialField.Palette(
                    worldSeed ^ 0x6351L, 7.0, 0.65,
                    new RockMaterialField.Band(0.0, 1.0,
                            new RockMaterialField.Component(
                                    Blocks.TUFF.defaultBlockState(), 8.0),
                            new RockMaterialField.Component(
                                    Blocks.COBBLESTONE.defaultBlockState(), 2.0)));
            RockDecorationRules rules = RockDecorationRules.builder()
                    .topSoil(Blocks.GRASS_BLOCK.defaultBlockState(),
                            Blocks.DIRT.defaultBlockState(), 2, 1, 0.7)
                    .plant(Blocks.SHORT_GRASS.defaultBlockState(), 0.1, 1)
                    .vines(0.03, 4)
                    .structure(Set.of(RockType.LAYERED_SLAB), 0.2, 2, 1,
                            RockDecorationRules.cairn(
                                    Blocks.COBBLESTONE.defaultBlockState(), 2))
                    .build();

            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int ownerX = cellX + dx, ownerZ = cellZ + dz;
                    long id = ownerId(ownerX, ownerZ);
                    if (!selected(worldSeed, id, 0xC1A5L)) continue;
                    int x = ownerX * CELL_SIZE + 192;
                    int z = ownerZ * CELL_SIZE + 192;
                    long clusterSeed = mix(worldSeed ^ id ^ 0xC1A5L);
                    var recipe = RockClusterPlanner.builder(clusterSeed, x, z)
                            .count(8).radius(30).dimensions(18, 22, 14)
                            .scale(0.85, 1.25).embed(3)
                            .stackChance(0.4).overlap(0.5)
                            .types(RockType.FRACTURED_BOULDER,
                                    RockType.LAYERED_SLAB)
                            .material(palette, 3.0).material(STONE, 1.0)
                            .decorations(rules).build();
                    var cluster = RockClusterPlanner.plan(recipe, cache,
                            surfaceHeight);
                    boolean intersects = cluster.components().stream()
                            .anyMatch(part -> part.rock().bounds().intersectsChunk(
                                    chunk.getPos().x, chunk.getPos().z));
                    if (!intersects) continue;
                    RockPlacementHelper.placeClusterInChunk(level, chunk,
                            cluster, level.getMinBuildHeight(),
                            level.getMaxBuildHeight() - 1);
                    wrote = true;
                }
            }
            return wrote;
        }
    }
}
```

Add these five data files so NeoForge registers and places the
examples. The configured features refer to the two feature IDs registered by
`RockExamplesMod`. The placed features invoke each feature once per eligible
chunk; the Java code makes the sparse owner-cell decision. An existing mod
still needs its normal `neoforge.mods.toml` and Gradle setup.

**`src/main/resources/data/rockexamples/worldgen/configured_feature/single_rock.json`**

```json
{
  "type": "rockexamples:single_rock",
  "config": {}
}
```

**`src/main/resources/data/rockexamples/worldgen/configured_feature/decorated_cluster.json`**

```json
{
  "type": "rockexamples:decorated_cluster",
  "config": {}
}
```

**`src/main/resources/data/rockexamples/worldgen/placed_feature/single_rock.json`**

```json
{
  "feature": "rockexamples:single_rock",
  "placement": [
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:heightmap",
      "heightmap": "WORLD_SURFACE_WG"
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
```

**`src/main/resources/data/rockexamples/worldgen/placed_feature/decorated_cluster.json`**

```json
{
  "feature": "rockexamples:decorated_cluster",
  "placement": [
    {
      "type": "minecraft:in_square"
    },
    {
      "type": "minecraft:heightmap",
      "heightmap": "WORLD_SURFACE_WG"
    },
    {
      "type": "minecraft:biome"
    }
  ]
}
```

**`src/main/resources/data/rockexamples/neoforge/biome_modifier/add_example_rocks.json`**

```json
{
  "type": "neoforge:add_features",
  "biomes": "#c:is_overworld",
  "features": [
    "rockexamples:single_rock",
    "rockexamples:decorated_cluster"
  ],
  "step": "local_modifications"
}
```

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

### Arguments for the lower-level API

The examples above use `Request` and the cluster builder. These notes cover the additional signatures in the public API table if you work directly with generated geometry:

| Call / arguments | Meaning and source |
| --- | --- |
| `RockSpec(type, seed, x, baseY, z, width, length, height, rotation, edgeSharpness, smoothing, erosion, leanX, leanZ, layers)` | You supply the shape enum, a stable per-rock seed, world center `x/z`, **base** Y, dimensions in blocks, horizontal rotation in radians, `edgeSharpness`, `smoothing`, and `erosion` each in `0..1`, full-height horizontal lean offsets `leanX/Z` in blocks, and `layers` in `2..16`. Use `RockSpec.example(type, seed, x, baseY, z)` for starting parameters rather than assembling all fifteen by hand. The `baseY` is **not** the first-air surface height; choose embed depth yourself. |
| `RockGenerator.create(spec)` | Accepts your `RockSpec`; returns immutable geometry only, with no block placement or material assignment. |
| `RockVariantCache.template(type, index)` / `indexFor(type, objectId)` | `type` is a `RockType`; `index` is an integer `0..255`. `indexFor` maps your stable `objectId` to such an index in the world's cache. `template` returns a rock centered at the origin for reuse; it is not placed in the world. |
| `RockVariantCache.place(type, index, x, baseY, z, rotation[, width, length, height])` | `type/index` select a cached template, `x/baseY/z` position its **base** in world coordinates, `rotation` is radians. The optional three dimensions rescale that template and must each be at least four. This returns a `RockInstance`; it does not write blocks. |
| `RockInstance.solid(x, y, z)` / `solidAt(x, y, z)` | Query whether an **integer block position** or a **continuous world-space point**, respectively, is inside the rock. `highestSolidY(x, z)` finds the top rock block at a world-space column; it returns `Integer.MIN_VALUE` when empty. `stratumAt(y)` takes a world Y and returns a layer index or `-1` outside the rock. |
| `RockInstance.Bounds(minX, minY, minZ, maxX, maxY, maxZ)` / `intersectsChunk(chunkX, chunkZ)` | Inclusive world block-space bounds for the rock. Pass **chunk indices**, not world block coordinates, to `intersectsChunk`, normally `chunk.getPos().x` and `.z`. Bounds are conservative and may include empty space. |
| `RockSurfaceErosionField.maximumAbs(spec)` / `sample(spec, x, y, z, t)` | `spec` is your `RockSpec`. `maximumAbs` gives a conservative displacement bound in blocks. For `sample`, `x/y/z` are **rock-local** coordinates and `t` is normalized height `0..1` from base to top. The result is a signed surface displacement in blocks; the generator normally handles this for you. |
| `placeInChunk(chunk, placements, minY, maxY, profile)` | Same arguments as the unprofiled overload, plus an optional `long[6]` to collect geometry checks, solid writes, sampled check nanoseconds/count, and sampled write nanoseconds/count in indices `0..5`. This is for instrumentation; omit it for ordinary use. |
| `decorateInChunk(level, chunk, placements, minY, maxY)` | Run the plant, vine, and summit-structure pass **after** writing geometry with the non-`level` `placeInChunk` overload. Use the same placements and inclusive Y limits. Do not also use the `level` overload for that same placement pass, or you will attempt decorations twice. |

This is version `0.1.0-dev`. A public license and release channel have not yet been chosen.
