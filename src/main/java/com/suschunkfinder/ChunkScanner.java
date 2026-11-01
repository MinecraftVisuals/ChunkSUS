package com.suschunkfinder;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads loaded chunks on the main thread (time-budgeted per tick, because chunk storage must not
 * be read from other threads), then analyses the copied data on a background thread.
 */
public final class ChunkScanner {
    public record Flagged(ChunkPos pos, double score, int minY, int maxY) {}

    private static final long BUDGET_NANOS = 3_000_000L; // 3 ms per tick
    private static final int AUTO_RESCAN_TICKS = 2400;   // re-read loaded chunks every ~2 min

    // main thread only
    private static final Deque<ChunkPos> QUEUE = new ArrayDeque<>();
    private static final Set<ChunkPos> QUEUED = new HashSet<>();
    private static final Set<ChunkPos> ANNOUNCED = new HashSet<>();
    private static ClientWorld lastWorld;
    private static int lastConfigVersion = -1;
    private static int ticks;

    // shared
    private static final Set<ChunkPos> LOADED = ConcurrentHashMap.newKeySet();
    private static final Map<ChunkPos, ChunkAnalysis> RESULTS = new ConcurrentHashMap<>();
    private static volatile Map<ChunkPos, Flagged> flagged = Map.of();
    private static volatile boolean dirty;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SusChunkFinder-Analysis");
        t.setDaemon(true);
        return t;
    });

    private ChunkScanner() {}

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            ensureWorld(world);
            ChunkPos p = chunk.getPos();
            LOADED.add(p);
            enqueue(p);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            ChunkPos p = chunk.getPos();
            LOADED.remove(p);
            RESULTS.remove(p);
            QUEUED.remove(p);
            dirty = true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(ChunkScanner::tick);
    }

    // ---- public API ----

    public static Map<ChunkPos, Flagged> flagged() {
        return flagged;
    }

    public static int scannedCount() {
        return RESULTS.size();
    }

    public static List<Flagged> top(int n) {
        return flagged.values().stream()
                .sorted(Comparator.comparingDouble(Flagged::score).reversed())
                .limit(n)
                .toList();
    }

    /** Re-read every currently loaded chunk (call from the main thread). */
    public static void rescanAll() {
        for (ChunkPos p : LOADED) enqueue(p);
    }

    // ---- internals ----

    private static void ensureWorld(ClientWorld world) {
        if (world != lastWorld) {
            QUEUE.clear();
            QUEUED.clear();
            LOADED.clear();
            RESULTS.clear();
            ANNOUNCED.clear();
            flagged = Map.of();
            lastWorld = world;
        }
    }

    private static void enqueue(ChunkPos p) {
        if (QUEUED.add(p)) QUEUE.add(p);
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        ensureWorld(world);
        if (world == null) return;

        SusConfig cfg = SusConfig.get();
        if (cfg.overworldOnly && !world.getRegistryKey().equals(World.OVERWORLD)) {
            QUEUE.clear();
            QUEUED.clear();
            if (!flagged.isEmpty()) flagged = Map.of();
            return;
        }
        if (!cfg.enabled) return;

        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (!QUEUE.isEmpty() && System.nanoTime() < deadline) {
            ChunkPos pos = QUEUE.poll();
            QUEUED.remove(pos);
            if (!LOADED.contains(pos)) continue;
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
            if (chunk != null) scan(chunk, pos, cfg.maxScanY);
        }

        if (++ticks % AUTO_RESCAN_TICKS == 0) rescanAll();

        if (dirty || lastConfigVersion != SusConfig.version()) {
            dirty = false;
            lastConfigVersion = SusConfig.version();
            refreshFlags(cfg);
        }
    }

    private static void scan(WorldChunk chunk, ChunkPos pos, int maxScanY) {
        final int bottom = chunk.getBottomY();
        final int top = Math.min(maxScanY, bottom + chunk.getHeight() - 1);
        final int rows = top - bottom + 1;
        if (rows < 4) return;

        byte[] cls = new byte[rows << 8];
        BlockPos.Mutable m = new BlockPos.Mutable();
        int sx = pos.getStartX();
        int sz = pos.getStartZ();
        for (int y = 0; y < rows; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    m.set(sx + x, bottom + y, sz + z);
                    cls[(y << 8) | (z << 4) | x] = BlockClassifier.classify(chunk.getBlockState(m));
                }
            }
        }

        EXECUTOR.execute(() -> {
            try {
                ChunkAnalysis a = ChunkAnalysis.analyze(cls, rows, bottom);
                if (LOADED.contains(pos)) {
                    RESULTS.put(pos, a);
                    dirty = true;
                }
            } catch (Throwable t) {
                SusChunkFinderClient.LOGGER.warn("Chunk analysis failed for {}", pos, t);
            }
        });
    }

    private static void refreshFlags(SusConfig cfg) {
        double threshold = cfg.threshold();
        Map<ChunkPos, Flagged> out = new HashMap<>();
        for (Map.Entry<ChunkPos, ChunkAnalysis> e : RESULTS.entrySet()) {
            ChunkPos p = e.getKey();
            ChunkAnalysis a = e.getValue();
            double score = a.rawScore();
            if (score <= 0) continue;
            if (cfg.ignoreNaturalStructures && naturalNearby(p)) score *= 0.4;
            if (score >= threshold) {
                out.put(p, new Flagged(p, score, a.minY, a.maxY));
                if (ANNOUNCED.add(p)) announce();
            }
        }
        flagged = out;
    }

    private static void announce() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Text msg = Text.literal("[DYLAN]").styled(s -> s.withColor(0xF050F8))
                .append(Text.literal(" Sus Chunk Found!").formatted(Formatting.WHITE));
        mc.player.sendMessage(msg, false);
    }

    private static boolean naturalNearby(ChunkPos p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkAnalysis n = RESULTS.get(new ChunkPos(p.x + dx, p.z + dz));
                if (n != null && n.natural) return true;
            }
        }
        return false;
    }
}
