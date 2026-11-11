package net.melvin.rocktool;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/** Runs outside Minecraft to preview top, side, and shaded three-quarter views. */
public final class RockPreview {

    private static final int PANEL = 170;
    private static final int MARGIN = 20;
    private static final int ROWS_PER_COLUMN = 6;

    private RockPreview() {}

    /** Pass an optional destination PNG path as the first argument. */
    public static void main(String[] args) throws IOException {
        int columnWidth = PANEL * 3 + MARGIN * 4;
        int width = columnWidth * 2;
        int height = ROWS_PER_COLUMN * (PANEL + MARGIN) + MARGIN;
        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(233, 224, 204));
        graphics.fillRect(0, 0, width, height);

        for (int index = 0; index < RockType.values().length; index++) {
            RockType type = RockType.values()[index];
            RockSpec spec = RockSpec.example(type, 16384L + index,
                    0.0, 0.0, 0.0);
            RockInstance rock = RockGenerator.create(spec);
            int columnX = (index / ROWS_PER_COLUMN) * columnWidth;
            int rowY = MARGIN + (index % ROWS_PER_COLUMN)
                    * (PANEL + MARGIN);
            drawTop(image, rock, columnX + MARGIN, rowY);
            drawSide(image, rock, columnX + PANEL + MARGIN * 2, rowY);
            drawThreeQuarter(image, rock,
                    columnX + PANEL * 2 + MARGIN * 3, rowY);
            graphics.setColor(new Color(35, 34, 31));
            graphics.drawString(type.name(), columnX + MARGIN + 5, rowY + 16);
            graphics.drawString("TOP", columnX + MARGIN + 5,
                    rowY + PANEL - 7);
            graphics.drawString("SIDE", columnX + PANEL + MARGIN * 2 + 5,
                    rowY + PANEL - 7);
            graphics.drawString("THREE-QUARTER",
                    columnX + PANEL * 2 + MARGIN * 3 + 5,
                    rowY + PANEL - 7);
        }
        graphics.dispose();
        File destination = new File(args.length > 0
                ? args[0] : "rock-preview.png");
        ImageIO.write(image, "png", destination);
        System.out.println("Wrote " + destination.getAbsolutePath());
    }

    private static void drawTop(BufferedImage output, RockInstance rock,
                                int left, int top) {
        RockSpec spec = rock.spec();
        double extent = extent(spec);
        for (int px = 0; px < PANEL; px++) {
            for (int pz = 0; pz < PANEL; pz++) {
                double x = spec.x() + (px + 0.5 - PANEL * 0.5)
                        * (2.0 * extent / PANEL);
                double z = spec.z() + (pz + 0.5 - PANEL * 0.5)
                        * (2.0 * extent / PANEL);
                int y = rock.highestSolidY((int) Math.floor(x),
                        (int) Math.floor(z));
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                double height = (y + 1.0 - spec.baseY()) / spec.height();
                int shade = clamp((int) Math.round(70 + height * 115));
                output.setRGB(left + px, top + pz,
                        new Color(shade, shade - 13, shade - 25).getRGB());
            }
        }
    }

    private static void drawSide(BufferedImage output, RockInstance rock,
                                 int left, int top) {
        RockSpec spec = rock.spec();
        boolean alongZ = spec.length() >= spec.width();
        double extent = extent(spec);
        double blocksPerPixel = 2.0 * extent / PANEL;
        for (int px = 0; px < PANEL; px++) {
            double horizontal = (px + 0.5 - PANEL * 0.5)
                    * (2.0 * extent / PANEL);
            for (int py = 0; py < PANEL; py++) {
                double y = spec.baseY() + (PANEL - 18 - py - 0.5)
                        * blocksPerPixel;
                if (y < spec.baseY() || y >= spec.baseY() + spec.height()) {
                    continue;
                }
                // Project the entire object, not just a central slice.
                boolean visible = false;
                double depth = alongZ ? spec.width() : spec.length();
                for (int sample = -6; sample <= 6; sample++) {
                    double offset = sample * depth / 12.0;
                    double x = spec.x() + (alongZ ? offset : horizontal);
                    double z = spec.z() + (alongZ ? horizontal : offset);
                    if (rock.solidAt(x, y, z)) {
                        visible = true;
                        break;
                    }
                }
                if (!visible) continue;
                int stratum = rock.stratumAt((int) Math.floor(y));
                int shade = clamp(82 + stratum * 14);
                output.setRGB(left + px, top + py,
                        new Color(shade, shade - 13, shade - 24).getRGB());
            }
        }
    }

    private static void drawThreeQuarter(BufferedImage output,
                                         RockInstance rock, int left, int top) {
        RockSpec spec = rock.spec();
        double extent = extent(spec);
        double scale = 2.0 * extent / PANEL;
        // Camera direction, screen right, and screen up are orthogonal.
        double vx = 0.57, vy = 0.62, vz = 0.54;
        double rx = 0.688, rz = -0.726;
        double ux = -0.450, uy = 0.784, uz = -0.426;
        double cameraDistance = extent * 2.6;
        double sphereRadius = extent * 1.25;
        double sphereSquared = sphereRadius * sphereRadius;
        double cy = spec.baseY() + spec.height() * 0.47;

        for (int py = 0; py < PANEL; py++) {
            double vertical = (PANEL * 0.5 - py - 0.5) * scale;
            for (int px = 0; px < PANEL; px++) {
                double horizontal = (px + 0.5 - PANEL * 0.5) * scale;
                double offsetSquared = horizontal * horizontal
                        + vertical * vertical;
                if (offsetSquared >= sphereSquared) continue;

                double ox = spec.x() + vx * cameraDistance
                        + rx * horizontal + ux * vertical;
                double oy = cy + vy * cameraDistance + uy * vertical;
                double oz = spec.z() + vz * cameraDistance
                        + rz * horizontal + uz * vertical;
                double reach = Math.sqrt(sphereSquared - offsetSquared);
                for (double distance = cameraDistance - reach;
                     distance <= cameraDistance + reach;
                     distance += 0.75) {
                    double x = ox - vx * distance;
                    double y = oy - vy * distance;
                    double z = oz - vz * distance;
                    if (!rock.solidAt(x, y, z)) continue;

                    // Locate the entry surface within the last ray step.
                    // Shading at the overshot point made concentric stripes.
                    double outside = distance - 0.75;
                    double inside = distance;
                    for (int refinement = 0; refinement < 5; refinement++) {
                        double middle = (outside + inside) * 0.5;
                        if (rock.solidAt(ox - vx * middle,
                                oy - vy * middle, oz - vz * middle)) {
                            inside = middle;
                        } else {
                            outside = middle;
                        }
                    }
                    x = ox - vx * inside;
                    y = oy - vy * inside;
                    z = oz - vz * inside;

                    double epsilon = 2.0;
                    double nx = (rock.solidAt(x - epsilon, y, z) ? 1 : 0)
                            - (rock.solidAt(x + epsilon, y, z) ? 1 : 0);
                    double ny = (rock.solidAt(x, y - epsilon, z) ? 1 : 0)
                            - (rock.solidAt(x, y + epsilon, z) ? 1 : 0);
                    double nz = (rock.solidAt(x, y, z - epsilon) ? 1 : 0)
                            - (rock.solidAt(x, y, z + epsilon) ? 1 : 0);
                    double broad = 3.5;
                    nx += 0.5 * ((rock.solidAt(x - broad, y, z) ? 1 : 0)
                            - (rock.solidAt(x + broad, y, z) ? 1 : 0));
                    ny += 0.5 * ((rock.solidAt(x, y - broad, z) ? 1 : 0)
                            - (rock.solidAt(x, y + broad, z) ? 1 : 0));
                    nz += 0.5 * ((rock.solidAt(x, y, z - broad) ? 1 : 0)
                            - (rock.solidAt(x, y, z + broad) ? 1 : 0));
                    double normalLength = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (normalLength < 0.5) {
                        nx = vx;
                        ny = vy;
                        nz = vz;
                    } else {
                        nx /= normalLength;
                        ny /= normalLength;
                        nz /= normalLength;
                    }
                    double illumination = Math.max(0.36, Math.min(1.16,
                            0.64 + 0.42 * (nx * 0.45 + ny * 0.82 - nz * 0.35)));
                    int stratum = rock.stratumAt((int) Math.floor(y));
                    int value = 120 + Math.max(0, stratum) * 3;
                    int red = clamp((int) Math.round(value * illumination));
                    int green = clamp((int) Math.round((value - 17) * illumination));
                    int blue = clamp((int) Math.round((value - 31) * illumination));
                    output.setRGB(left + px, top + py,
                            new Color(red, green, blue).getRGB());
                    break;
                }
            }
        }
    }

    /** Both projections use the same number of pixels per world block. */
    private static double extent(RockSpec spec) {
        return Math.max(Math.max(spec.width(), spec.length()), spec.height())
                * 0.65 + Math.hypot(spec.leanX(), spec.leanZ()) + 4.0;
    }

    private static int clamp(int value) {
        return Math.max(25, Math.min(225, value));
    }
}
