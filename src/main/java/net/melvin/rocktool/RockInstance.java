package net.melvin.rocktool;

/** One immutable rock object, sampled in world coordinates. */
public final class RockInstance {

    private final RockSpec spec;
    private final double[] horizons;
    private final double[] angles;
    private final double[] angleCos;
    private final double[] angleSin;
    private final double[] levels;
    private final double[][] contours;
    private final double[] contourMaxRadius;
    private final double[] centerX;
    private final double[] centerZ;
    private final double fastBoundX;
    private final double fastBoundZ;
    private final double cutAngle;
    private final double chipAngle;
    private final double cutCos;
    private final double cutSin;
    private final double chipCos;
    private final double chipSin;
    private final double[] faceAngles;
    private final double[] faceCos;
    private final double[] faceSin;
    private final double[] faceThresholds;
    private final double[] faceSlopes;
    private final double[][] benchShifts;
    private final Bounds bounds;
    private final double cosRotation;
    private final double sinRotation;
    private final double halfSpan;
    private final double maximumSideShift;
    private final double maximumTopShift;
    private final double edgeBlend;

    RockInstance(RockSpec spec, double[] horizons, double[] angles,
                 double[] levels, double[][] contours,
                 double[] centerX, double[] centerZ,
                 double cutAngle, double chipAngle,
                 double[] faceAngles, double[] faceThresholds,
                 double[] faceSlopes, double[][] benchShifts) {
        this.spec = spec;
        this.horizons = horizons.clone();
        this.angles = angles.clone();
        this.angleCos = new double[angles.length];
        this.angleSin = new double[angles.length];
        for (int i = 0; i < angles.length; i++) {
            this.angleCos[i] = Math.cos(angles[i]);
            this.angleSin[i] = Math.sin(angles[i]);
        }
        this.levels = levels.clone();
        this.contours = new double[contours.length][];
        this.contourMaxRadius = new double[contours.length];
        for (int i = 0; i < contours.length; i++) {
            this.contours[i] = contours[i].clone();
            double maximum = 0.0;
            for (double radius : this.contours[i]) {
                maximum = Math.max(maximum, radius);
            }
            this.contourMaxRadius[i] = maximum;
        }
        this.centerX = centerX.clone();
        this.centerZ = centerZ.clone();
        this.fastBoundX = fastBound(this.centerX);
        this.fastBoundZ = fastBound(this.centerZ);
        this.cutAngle = cutAngle;
        this.chipAngle = chipAngle;
        this.cutCos = Math.cos(cutAngle);
        this.cutSin = Math.sin(cutAngle);
        this.chipCos = Math.cos(chipAngle);
        this.chipSin = Math.sin(chipAngle);
        this.faceAngles = faceAngles.clone();
        this.faceCos = new double[faceAngles.length];
        this.faceSin = new double[faceAngles.length];
        for (int i = 0; i < faceAngles.length; i++) {
            this.faceCos[i] = Math.cos(faceAngles[i]);
            this.faceSin[i] = Math.sin(faceAngles[i]);
        }
        this.faceThresholds = faceThresholds.clone();
        this.faceSlopes = faceSlopes.clone();
        this.benchShifts = new double[benchShifts.length][];
        for (int i = 0; i < benchShifts.length; i++) {
            this.benchShifts[i] = benchShifts[i].clone();
        }
        this.cosRotation = Math.cos(spec.rotation());
        this.sinRotation = Math.sin(spec.rotation());
        this.halfSpan = Math.min(spec.width(), spec.length()) * 0.5;
        double maximumRelief = RockSurfaceErosionField.maximumAbs(spec);
        this.maximumSideShift = maximumRelief / halfSpan;
        this.maximumTopShift = maximumRelief * 0.65 / spec.height();
        this.edgeBlend = spec.edgeSharpness()
                * (1.0 - 0.35 * spec.smoothing());
        this.bounds = boundsFor(spec);
    }

    /** Repositions a cached immutable shape without rebuilding its contours. */
    RockInstance at(double x, double baseY, double z, double rotation) {
        return at(x, baseY, z, rotation,
                spec.width(), spec.length(), spec.height());
    }

    /** Resizes and repositions a cached shape without rebuilding its contours. */
    RockInstance at(double x, double baseY, double z, double rotation,
                    double width, double length, double height) {
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double leanScale = height / spec.height();
        double placedLeanX = (spec.leanX() * cos - spec.leanZ() * sin)
                * leanScale;
        double placedLeanZ = (spec.leanX() * sin + spec.leanZ() * cos)
                * leanScale;
        RockSpec placed = new RockSpec(
                spec.type(), spec.seed(), x, baseY, z,
                width, length, height, rotation,
                spec.edgeSharpness(), spec.smoothing(), spec.erosion(),
                placedLeanX, placedLeanZ, spec.layers());
        return new RockInstance(this, placed);
    }

    private RockInstance(RockInstance template, RockSpec placed) {
        this.spec = placed;
        // All arrays are private and never mutated after construction.
        this.horizons = template.horizons;
        this.angles = template.angles;
        this.angleCos = template.angleCos;
        this.angleSin = template.angleSin;
        this.levels = template.levels;
        this.contours = template.contours;
        this.contourMaxRadius = template.contourMaxRadius;
        this.centerX = template.centerX;
        this.centerZ = template.centerZ;
        this.fastBoundX = template.fastBoundX;
        this.fastBoundZ = template.fastBoundZ;
        this.cutAngle = template.cutAngle;
        this.chipAngle = template.chipAngle;
        this.cutCos = template.cutCos;
        this.cutSin = template.cutSin;
        this.chipCos = template.chipCos;
        this.chipSin = template.chipSin;
        this.faceAngles = template.faceAngles;
        this.faceCos = template.faceCos;
        this.faceSin = template.faceSin;
        this.faceThresholds = template.faceThresholds;
        this.faceSlopes = template.faceSlopes;
        this.benchShifts = template.benchShifts;
        this.cosRotation = Math.cos(placed.rotation());
        this.sinRotation = Math.sin(placed.rotation());
        this.halfSpan = Math.min(placed.width(), placed.length()) * 0.5;
        double maximumRelief = RockSurfaceErosionField.maximumAbs(placed);
        this.maximumSideShift = maximumRelief / halfSpan;
        this.maximumTopShift = maximumRelief * 0.65 / placed.height();
        this.edgeBlend = placed.edgeSharpness()
                * (1.0 - 0.35 * placed.smoothing());
        this.bounds = boundsFor(placed);
    }

    private static Bounds boundsFor(RockSpec spec) {
        double reach = Math.hypot(spec.width(), spec.length()) * 0.90
                + Math.hypot(spec.leanX(), spec.leanZ()) + 8.0;
        return new Bounds(
                (int) Math.floor(spec.x() - reach),
                (int) Math.floor(spec.baseY()),
                (int) Math.floor(spec.z() - reach),
                (int) Math.ceil(spec.x() + reach),
                (int) Math.ceil(spec.baseY() + spec.height()),
                (int) Math.ceil(spec.z() + reach));
    }

    private static double fastBound(double[] centers) {
        double maximum = 0.0;
        for (double center : centers) {
            maximum = Math.max(maximum, Math.abs(center));
        }
        // Every accepted sample must lie within radius 1.65 of an
        // interpolated center, which never exceeds the endpoint extrema.
        return 1.65 + maximum + 1.0e-9;
    }

    public RockSpec spec() { return spec; }
    public Bounds bounds() { return bounds; }

    /** Tests the center of a Minecraft block. */
    public boolean solid(int x, int y, int z) {
        return solidAt(x + 0.5, y + 0.5, z + 0.5);
    }

    public boolean solidAt(double x, double y, double z) {
        double t = (y - spec.baseY()) / spec.height();
        if (t < 0.0 || t >= 1.0) return false;

        double dx = x - spec.x() - spec.leanX() * t;
        double dz = z - spec.z() - spec.leanZ() * t;
        double lx = dx * cosRotation + dz * sinRotation;
        double lz = -dx * sinRotation + dz * cosRotation;
        double nx = lx / (spec.width() * 0.5);
        double nz = lz / (spec.length() * 0.5);
        if (Math.abs(nx) > fastBoundX || Math.abs(nz) > fastBoundZ) {
            return false;
        }

        // Bend only the two thin bench transitions. The foot and crown keep
        // their original elevation, and every vertical column stays joined.
        double contourT = t;
        if (spec.type() == RockType.STEPPED_BUTTE
                || spec.type() == RockType.LAYERED_SLAB
                || spec.type() == RockType.TABLE_ROCK) {
            double angle = Math.atan2(nz, nx);
            double lowerShift = benchShiftAt(angle, benchShifts[0]);
            double upperShift = benchShiftAt(angle, benchShifts[1]);
            if (spec.type() == RockType.LAYERED_SLAB) {
                contourT -= lowerShift * contactWeight(t, 0.35)
                        + upperShift * contactWeight(t, 0.65);
            } else if (spec.type() == RockType.TABLE_ROCK) {
                contourT -= lowerShift * contactWeight(t, 0.72);
            } else {
                contourT -= lowerShift * contactWeight(t, 0.35)
                        + upperShift * contactWeight(t, 0.56);
            }
            contourT = Math.max(0.0, Math.min(0.999999, contourT));
        }

        int ring = 0;
        while (ring < levels.length - 2
                && contourT >= levels[ring + 1]) ring++;
        double interpolation = (contourT - levels[ring])
                / (levels[ring + 1] - levels[ring]);
        // Smoothing gently rounds level changes but keeps the corner order.
        interpolation = lerp(interpolation, smooth(interpolation),
                spec.smoothing() * 0.35);
        double cx = lerp(centerX[ring], centerX[ring + 1], interpolation);
        double cz = lerp(centerZ[ring], centerZ[ring + 1], interpolation);
        double px = nx - cx;
        double pz = nz - cz;
        double radius = Math.hypot(px, pz);
        if (radius > 1.65) return false;

        // The interpolated polygon lies within the largest vertex radius
        // of either bounding contour. Skip its ray intersections when even
        // that generous boundary plus erosion cannot reach this point.
        double intervalMaximum = Math.max(contourMaxRadius[ring],
                contourMaxRadius[ring + 1]);
        if (radius > lerp(1.0, intervalMaximum, edgeBlend)
                + maximumSideShift + 1.0e-9) return false;

        double angle = Math.atan2(pz, px);
        double polygon = polygonRadius(angle, ring, interpolation);
        double boundary = lerp(1.0, polygon, edgeBlend);
        if (radius > boundary + maximumSideShift) return false;

        double along = nx * cutCos + nz * cutSin;
        double across = nx * chipCos + nz * chipSin;
        double crest = switch (spec.type()) {
            case SANDSTONE_FIN -> Math.min(
                    0.99 - 0.24 * Math.abs(nz + 0.13),
                    0.92 + 0.11 * along);
            case STEPPED_BUTTE -> 0.94 + 0.065 * along;
            case FRACTURED_BOULDER -> 0.88 + 0.12 * along;
            case TAPERED_SPIRE -> Math.min(
                    0.99 - 0.10 * Math.abs(nz + 0.12),
                    0.94 + 0.075 * along);
            case TRIANGULAR_WEDGE -> 0.96 - 0.56 * Math.max(0.0, nx + 0.10);
            case TABLE_ROCK -> 0.92 + 0.055 * along
                    + 0.025 * Math.sin(2.8 * nz + 1.1 * nx);
            case TILTED_MONOLITH -> 0.94 + 0.07 * along;
            case LAYERED_SLAB -> 0.94 + 0.055 * along
                    + 0.018 * Math.sin(3.0 * nz);
            case SPLIT_CROWN -> 0.99 - 0.07 * Math.abs(along);
            case BLADE_RIDGE -> 0.93 - 0.13 * Math.abs(nx)
                    - 0.15 * Math.abs(nz + 0.12)
                    + 0.055 * Math.sin(5.0 * nz);
            case TALUS_CONE -> 0.96 - 0.11 * Math.abs(along)
                    + 0.035 * Math.sin(3.0 * nx - 2.0 * nz);
            case PYRAMIDAL_PEAK -> 0.995 - 0.10 * Math.abs(along)
                    - 0.025 * Math.max(0.0, nz);
        };
        crest = Math.min(0.995, crest - 0.12 * Math.max(0.0, across - 0.22));
        if (t > crest + maximumTopShift) return false;

        // Reject clearly clipped samples and accept the deep interior without
        // evaluating noise. Only the surface band needs the three octaves.
        boolean nearFace = radius > boundary - maximumSideShift
                || t > crest - maximumTopShift;
        for (int i = 0; i < faceAngles.length; i++) {
            double projection = nx * faceCos[i] + nz * faceSin[i];
            double contact = faceThresholds[i]
                    + faceSlopes[i] * (t - 0.5);
            if (projection > contact + maximumSideShift) return false;
            if (projection > contact - maximumSideShift) nearFace = true;
        }

        double splitSeam = 0.0;
        double splitOpening = 0.0;
        boolean withinSplit = spec.type() == RockType.SPLIT_CROWN
                && t > 0.36 && Math.abs(nz) < 1.25;
        if (withinSplit) {
            splitSeam = nx - 0.075 * Math.sin(2.4 * nz);
            splitOpening = 0.015 + (t - 0.36) * 0.48;
            if (Math.abs(splitSeam) < splitOpening - maximumSideShift) {
                return false;
            }
            if (Math.abs(splitSeam) < splitOpening + maximumSideShift) {
                nearFace = true;
            }
        }
        if (!nearFace) return true;

        double erosion = RockSurfaceErosionField.sample(
                spec, lx, y - spec.baseY(), lz, t);
        double sideShift = erosion / halfSpan;
        if (radius > boundary + sideShift
                || t > crest + erosion * 0.65 / spec.height()) {
            return false;
        }

        for (int i = 0; i < faceAngles.length; i++) {
            double projection = nx * faceCos[i] + nz * faceSin[i];
            double contact = faceThresholds[i]
                    + faceSlopes[i] * (t - 0.5);
            if (projection > contact + sideShift) return false;
        }

        if (withinSplit
                && Math.abs(splitSeam + sideShift * 0.45)
                < splitOpening) {
            return false;
        }
        return true;
    }

    /** Highest solid block in this column, or MIN_VALUE if it misses. */
    public int highestSolidY(int x, int z) {
        if (x < bounds.minX() || x > bounds.maxX()
                || z < bounds.minZ() || z > bounds.maxZ()) {
            return Integer.MIN_VALUE;
        }
        for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
            if (solid(x, y, z)) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Layer index for material lookup; neighboring objects can share a palette. */
    public int stratumAt(int y) {
        double t = (y + 0.5 - spec.baseY()) / spec.height();
        if (t < 0.0 || t >= 1.0) return -1;
        for (int i = 0; i < horizons.length - 1; i++) {
            if (t < horizons[i + 1]) return i;
        }
        return horizons.length - 2;
    }

    /** Ray intersection with the interpolated polygon, in normalized XZ. */
    private double polygonRadius(double angle, int ring, double fraction) {
        double vx = Math.cos(angle), vz = Math.sin(angle);
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < angles.length; i++) {
            int j = (i + 1) % angles.length;
            double r0 = lerp(contours[ring][i],
                    contours[ring + 1][i], fraction);
            double r1 = lerp(contours[ring][j],
                    contours[ring + 1][j], fraction);
            double x0 = r0 * angleCos[i];
            double z0 = r0 * angleSin[i];
            double ex = r1 * angleCos[j] - x0;
            double ez = r1 * angleSin[j] - z0;
            double denominator = vx * ez - vz * ex;
            if (Math.abs(denominator) < 1.0e-10) continue;
            double ray = (x0 * ez - z0 * ex) / denominator;
            double edge = (x0 * vz - z0 * vx) / denominator;
            if (ray > 0.0 && edge >= -1.0e-9 && edge <= 1.0 + 1.0e-9) {
                nearest = Math.min(nearest, ray);
            }
        }
        return Double.isFinite(nearest) ? nearest : 0.0;
    }

    private double benchShiftAt(double angle, double[] shifts) {
        double a = angle;
        while (a < angles[0]) a += Math.PI * 2.0;
        while (a >= angles[0] + Math.PI * 2.0) a -= Math.PI * 2.0;
        for (int i = 0; i < angles.length; i++) {
            int next = (i + 1) % angles.length;
            double end = next == 0
                    ? angles[0] + Math.PI * 2.0 : angles[next];
            if (a <= end) {
                double fraction = (a - angles[i]) / (end - angles[i]);
                return lerp(shifts[i], shifts[next], fraction);
            }
        }
        return shifts[0];
    }

    private static double contactWeight(double t, double contact) {
        double distance = (t - contact) / 0.12;
        return Math.exp(-distance * distance);
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public record Bounds(int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ) {
        public boolean intersectsChunk(int chunkX, int chunkZ) {
            int x = chunkX * 16, z = chunkZ * 16;
            return maxX >= x && minX < x + 16
                    && maxZ >= z && minZ < z + 16;
        }
    }
}
