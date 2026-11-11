package net.melvin.rocktool;

/** Plans a stack of related, independently distorted polygon contours. */
public final class RockGenerator {

    private RockGenerator() {}

    public static RockInstance create(RockSpec spec) {
        int layerCount = spec.layers();
        double[] horizons = new double[layerCount + 1];
        double[] thickness = new double[layerCount];
        double total = 0.0;
        for (int i = 0; i < layerCount; i++) {
            thickness[i] = 0.58 + 0.84 * unit(spec.seed(), 10L + i);
            total += thickness[i];
        }
        for (int i = 0; i < layerCount; i++) {
            horizons[i + 1] = horizons[i] + thickness[i] / total;
        }

        int vertices = switch (spec.type()) {
            case SANDSTONE_FIN -> 7;
            case STEPPED_BUTTE -> 9;
            case FRACTURED_BOULDER -> 7;
            case TAPERED_SPIRE -> 7;
            case TRIANGULAR_WEDGE, PYRAMIDAL_PEAK -> 5;
            case TABLE_ROCK, LAYERED_SLAB -> 8;
            case TILTED_MONOLITH, SPLIT_CROWN -> 6;
            case BLADE_RIDGE -> 7;
            case TALUS_CONE -> 10;
        };
        double[] angles = new double[vertices];
        double[] baseRadii = new double[vertices];
        double phase = unit(spec.seed(), 180L) * Math.PI * 2.0;
        for (int i = 0; i < vertices; i++) {
            angles[i] = phase + Math.PI * 2.0 / vertices
                    * (i + 0.24 * (unit(spec.seed(), 200L + i) - 0.5));
            double radialSpread = switch (spec.type()) {
                case TALUS_CONE, PYRAMIDAL_PEAK, LAYERED_SLAB -> 0.59;
                default -> 0.48;
            };
            baseRadii[i] = 0.78 + radialSpread * unit(spec.seed(), 300L + i);
        }

        // Distinct bends at these heights yield connected planar faces.
        // Radius and center changes are seeded together so successive
        // contours still describe one rock instead of detached objects.
        double[] levels;
        double[] widths;
        switch (spec.type()) {
            case SANDSTONE_FIN -> {
                levels = new double[]{0.0, 0.24, 0.48, 0.72, 1.0};
                widths = new double[]{1.00, 1.08, 0.91, 0.83, 0.55};
            }
            case STEPPED_BUTTE -> {
                levels = new double[]{0.0, 0.20, 0.34, 0.36,
                        0.55, 0.57, 0.75, 1.0};
                widths = new double[]{1.05, 1.13, 1.16, 0.96,
                        0.98, 0.78, 0.79, 0.68};
            }
            case FRACTURED_BOULDER -> {
                levels = new double[]{0.0, 0.19, 0.39, 0.67, 1.0};
                widths = new double[]{0.78, 1.02, 1.11, 0.88, 0.54};
            }
            case TAPERED_SPIRE -> {
                levels = new double[]{0.0, 0.23, 0.48, 0.71, 1.0};
                widths = new double[]{1.07, 1.04, 0.87, 0.66, 0.28};
            }
            case TRIANGULAR_WEDGE -> {
                levels = new double[]{0.0, 0.20, 0.46, 0.72, 1.0};
                widths = new double[]{1.09, 1.02, 0.88, 0.72, 0.51};
            }
            case TABLE_ROCK -> {
                levels = new double[]{0.0, 0.18, 0.52, 0.70, 0.75, 1.0};
                widths = new double[]{1.04, 0.94, 0.79, 0.80, 1.06, 0.97};
            }
            case TILTED_MONOLITH -> {
                levels = new double[]{0.0, 0.15, 0.43, 0.76, 1.0};
                widths = new double[]{0.91, 1.02, 1.00, 0.93, 0.76};
            }
            case LAYERED_SLAB -> {
                levels = new double[]{0.0, 0.17, 0.34, 0.37,
                        0.63, 0.67, 0.87, 1.0};
                widths = new double[]{0.88, 1.07, 1.04, 0.93,
                        0.96, 1.14, 1.06, 0.91};
            }
            case SPLIT_CROWN -> {
                levels = new double[]{0.0, 0.23, 0.51, 0.76, 1.0};
                widths = new double[]{1.03, 1.09, 0.96, 0.84, 0.74};
            }
            case BLADE_RIDGE -> {
                levels = new double[]{0.0, 0.18, 0.40, 0.69, 1.0};
                widths = new double[]{1.02, 0.94, 0.72, 0.43, 0.16};
            }
            case TALUS_CONE -> {
                levels = new double[]{0.0, 0.19, 0.42, 0.68, 1.0};
                widths = new double[]{1.23, 1.10, 0.88, 0.58, 0.19};
            }
            case PYRAMIDAL_PEAK -> {
                levels = new double[]{0.0, 0.24, 0.49, 0.74, 1.0};
                widths = new double[]{1.15, 1.04, 0.78, 0.48, 0.12};
            }
            default -> throw new IllegalStateException(
                    "Unknown rock type: " + spec.type());
        }

        double[][] contours = new double[levels.length][vertices];
        double[] centerX = new double[levels.length];
        double[] centerZ = new double[levels.length];
        double direction = unit(spec.seed(), 400L) * Math.PI * 2.0;
        double chipAngle = direction + 1.2 + unit(spec.seed(), 401L) * 1.3;
        for (int ring = 0; ring < levels.length; ring++) {
            double variation = ring == 0 ? 0.07
                    : switch (spec.type()) {
                case STEPPED_BUTTE -> 0.10;
                case TABLE_ROCK, PYRAMIDAL_PEAK -> 0.18;
                case LAYERED_SLAB, TALUS_CONE -> 0.27;
                case TRIANGULAR_WEDGE -> 0.14;
                default -> 0.21;
            };
            for (int vertex = 0; vertex < vertices; vertex++) {
                // Shared baseline plus local fracture: broad faces continue
                // through multiple levels, while some corners protrude.
                double local = (unit(spec.seed(),
                        1000L + ring * 37L + vertex) - 0.5) * 2.0;
                double width = widths[ring];
                if (spec.type() == RockType.BLADE_RIDGE) {
                    // The narrow X section tapers sharply. The long Z crest
                    // retains its length, rather than shrinking to a spire.
                    double longAxis = Math.abs(Math.sin(angles[vertex]));
                    width += (0.82 - width) * levels[ring] * levels[ring]
                            * longAxis * longAxis;
                }
                if (spec.type() == RockType.LAYERED_SLAB) {
                    width *= 1.0 + 0.10 * Math.sin(
                            angles[vertex] * 3.0 + ring * 1.9);
                }
                if (spec.type() == RockType.TALUS_CONE) {
                    width *= 1.0 + (1.0 - levels[ring]) * 0.13
                            * Math.sin(angles[vertex] * 3.0 + 0.5);
                }
                contours[ring][vertex] = baseRadii[vertex] * width
                        * (1.0 + local * variation);
                if (spec.type() == RockType.TRIANGULAR_WEDGE
                        || spec.type() == RockType.BLADE_RIDGE) {
                    // Pinch one flank into a blade, leaving the other broad.
                    double flank = Math.sin(angles[vertex] - direction);
                    contours[ring][vertex] *= 1.0 - 0.22 * Math.max(0.0, flank);
                }
            }
            double drift = levels[ring] * switch (spec.type()) {
                case TAPERED_SPIRE -> 0.24;
                case TILTED_MONOLITH -> 0.32;
                case TRIANGULAR_WEDGE, PYRAMIDAL_PEAK -> 0.23;
                case TALUS_CONE -> 0.20;
                default -> 0.13;
            };
            centerX[ring] = Math.cos(direction) * drift
                    + (unit(spec.seed(), 2000L + ring * 2L) - 0.5) * 0.08;
            centerZ[ring] = Math.sin(direction) * drift
                    + (unit(spec.seed(), 2001L + ring * 2L) - 0.5) * 0.08;
            if (spec.type() == RockType.TRIANGULAR_WEDGE) {
                centerX[ring] -= 0.48 * levels[ring];
            }
        }

        // Each oblique clipping plane exposes one broad fracture face.
        // Different slopes make the cut vary with height, while retaining
        // one connected solid rock. These are part of the object geometry.
        int cuts = switch (spec.type()) {
            case SANDSTONE_FIN, STEPPED_BUTTE, TABLE_ROCK,
                 LAYERED_SLAB, TALUS_CONE -> 2;
            case FRACTURED_BOULDER, TAPERED_SPIRE, TRIANGULAR_WEDGE,
                 TILTED_MONOLITH, SPLIT_CROWN, BLADE_RIDGE,
                 PYRAMIDAL_PEAK -> 3;
        };
        double[] faceAngles = new double[cuts];
        double[] faceThresholds = new double[cuts];
        double[] faceSlopes = new double[cuts];
        double[][] benchShifts = new double[2][vertices];
        if (spec.type() == RockType.STEPPED_BUTTE
                || spec.type() == RockType.LAYERED_SLAB
                || spec.type() == RockType.TABLE_ROCK) {
            double contactWander = spec.type() == RockType.LAYERED_SLAB
                    ? 1.2 : spec.type() == RockType.TABLE_ROCK ? 2.0 : 5.0;
            for (int vertex = 0; vertex < vertices; vertex++) {
                // Keep thin slab contacts under a block of vertical wander.
                // Adjacent corners interpolate, avoiding sudden seams.
                benchShifts[0][vertex] =
                        (unit(spec.seed(), 4100L + vertex) - 0.5)
                                * contactWander / spec.height();
                benchShifts[1][vertex] =
                        (unit(spec.seed(), 4200L + vertex) - 0.5)
                                * contactWander / spec.height();
            }
        }
        for (int i = 0; i < cuts; i++) {
            faceAngles[i] = direction + i * Math.PI * 2.0 / cuts
                    + (unit(spec.seed(), 3000L + i) - 0.5) * 0.55;
            faceThresholds[i] = 0.73
                    + 0.15 * unit(spec.seed(), 3100L + i);
            faceSlopes[i] = (unit(spec.seed(), 3200L + i) - 0.5) * 0.42;
            if (spec.type() == RockType.FRACTURED_BOULDER && i == 0) {
                faceThresholds[i] = 0.53
                        + 0.07 * unit(spec.seed(), 3300L);
                faceSlopes[i] = -0.08
                        - 0.10 * unit(spec.seed(), 3301L);
            }
            if (spec.type() == RockType.TRIANGULAR_WEDGE && i == 0) {
                faceThresholds[i] = 0.59;
                faceSlopes[i] = -0.30;
            }
            if (spec.type() == RockType.SPLIT_CROWN && i == 0) {
                faceThresholds[i] = 0.64;
                faceSlopes[i] = -0.19;
            }
        }
        return new RockInstance(spec, horizons, angles, levels,
                contours, centerX, centerZ, direction, chipAngle,
                faceAngles, faceThresholds, faceSlopes, benchShifts);
    }

    static double unit(long seed, long salt) {
        long value = seed ^ (salt * 42317861L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
