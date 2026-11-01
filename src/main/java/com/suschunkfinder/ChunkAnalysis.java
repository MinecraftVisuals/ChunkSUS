package com.suschunkfinder;

/**
 * Pure-data analysis of one chunk's classified blocks. Safe to run on a background thread.
 * Layout of the input array: index = (y << 8) | (z << 4) | x, y relative to the scan bottom.
 */
final class ChunkAnalysis {
    static final int MIN_TUNNEL_RUN = 10;
    /** This many PvP leftovers (anchors, glowstone, tnt...) in one chunk = combat zone. */
    static final int COMBAT_ZONE_THRESHOLD = 3;

    final int densestWindow;
    final int totalWeight;
    final int tunnelBlocks;
    final int minY;
    final int maxY;
    final boolean natural;

    private ChunkAnalysis(int densestWindow, int totalWeight, int tunnelBlocks, int minY, int maxY, boolean natural) {
        this.densestWindow = densestWindow;
        this.totalWeight = totalWeight;
        this.tunnelBlocks = tunnelBlocks;
        this.minY = minY;
        this.maxY = maxY;
        this.natural = natural;
    }

    double rawScore() {
        return densestWindow
                + 0.25 * (totalWeight - densestWindow)
                + 0.15 * Math.min(tunnelBlocks, 200);
    }

    static ChunkAnalysis analyze(byte[] cls, int rows, int bottomY) {
        // Pass 1: is this a PvP area?
        int combat = 0;
        for (byte b : cls) {
            if (b == BlockClassifier.COMBAT) combat++;
        }
        boolean combatZone = combat >= COMBAT_ZONE_THRESHOLD;

        int cellRows = (rows + 3) >> 2;
        int[] cells = new int[cellRows * 16];
        int total = 0;
        int minRow = Integer.MAX_VALUE;
        int maxRow = -1;
        int strong = 0, weak = 0, stoneBricks = 0;

        for (int y = 0; y < rows; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    byte c = cls[(y << 8) | (z << 4) | x];
                    if (c < BlockClassifier.FIRST_INDICATOR) continue;
                    if (c == BlockClassifier.STRONG_MARKER) { strong++; continue; }
                    if (c == BlockClassifier.WEAK_MARKER) { weak++; continue; }
                    if (c == BlockClassifier.STONE_BRICK) { stoneBricks++; continue; }
                    if (c == BlockClassifier.COMBAT) continue;
                    int w = (c == BlockClassifier.KIT && combatZone) ? 0 : BlockClassifier.WEIGHTS[c];
                    if (w == 0) continue;
                    cells[((y >> 2) << 4) | ((z >> 2) << 2) | (x >> 2)] += w;
                    total += w;
                    if (y < minRow) minRow = y;
                    if (y > maxRow) maxRow = y;
                }
            }
        }

        // Densest 8x8x8 window (2x2x2 cells of 4x4x4 blocks)
        int best = 0;
        int maxCy0 = Math.max(0, cellRows - 2);
        for (int cy0 = 0; cy0 <= maxCy0; cy0++) {
            for (int cx0 = 0; cx0 <= 2; cx0++) {
                for (int cz0 = 0; cz0 <= 2; cz0++) {
                    int sum = 0;
                    for (int dy = 0; dy < 2; dy++) {
                        int cy = cy0 + dy;
                        if (cy >= cellRows) break;
                        for (int dx = 0; dx < 2; dx++) {
                            for (int dz = 0; dz < 2; dz++) {
                                sum += cells[(cy << 4) | ((cx0 + dx) << 2) | (cz0 + dz)];
                            }
                        }
                    }
                    if (sum > best) best = sum;
                }
            }
        }

        int tunnels = countTunnels(cls, rows);
        boolean natural = strong > 0 || weak >= 8 || stoneBricks >= 120;

        int minY = maxRow < 0 ? bottomY : bottomY + minRow;
        int maxY = maxRow < 0 ? bottomY : bottomY + maxRow;
        return new ChunkAnalysis(best, total, tunnels, minY, maxY, natural);
    }

    /**
     * Counts blocks that belong to long, straight, 1-wide tunnels (2 or 3 high) enclosed by solid blocks.
     */
    private static int countTunnels(byte[] cls, int rows) {
        int total = 0;
        for (int h = 2; h <= 3; h++) {
            for (int axis = 0; axis < 2; axis++) {          // 0 = runs along X, 1 = runs along Z
                for (int y = 1; y + h < rows; y++) {
                    for (int lat = 1; lat <= 14; lat++) {
                        int run = 0;
                        for (int s = 0; s < 16; s++) {
                            int x = axis == 0 ? s : lat;
                            int z = axis == 0 ? lat : s;
                            if (isTunnelCell(cls, x, y, z, axis, h)) {
                                run++;
                            } else {
                                if (run >= MIN_TUNNEL_RUN) total += run;
                                run = 0;
                            }
                        }
                        if (run >= MIN_TUNNEL_RUN) total += run;
                    }
                }
            }
        }
        return total;
    }

    private static boolean isTunnelCell(byte[] cls, int x, int y, int z, int axis, int h) {
        int dx = axis == 0 ? 0 : 1;
        int dz = axis == 0 ? 1 : 0;
        for (int dy = 0; dy < h; dy++) {
            if (at(cls, x, y + dy, z) != BlockClassifier.AIR) return false;
            if (!wall(at(cls, x - dx, y + dy, z - dz))) return false;
            if (!wall(at(cls, x + dx, y + dy, z + dz))) return false;
        }
        return wall(at(cls, x, y - 1, z)) && wall(at(cls, x, y + h, z));
    }

    private static byte at(byte[] cls, int x, int y, int z) {
        return cls[(y << 8) | (z << 4) | x];
    }

    private static boolean wall(byte c) {
        return c != BlockClassifier.AIR && c != BlockClassifier.LIQUID;
    }
}
