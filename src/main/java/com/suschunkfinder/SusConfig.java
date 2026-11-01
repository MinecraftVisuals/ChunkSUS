package com.suschunkfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SusConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SusConfig instance = new SusConfig();
    private static volatile int version = 0;

    /** Master switch for scanning + highlighting. */
    public boolean enabled = true;
    /** 1 (strict) .. 100 (flags almost anything). */
    public int sensitivity = 50;
    /** Only blocks at or below this Y are scanned ("underground"). */
    public int maxScanY = 40;
    /** Highlight opacity in percent. */
    public int opacityPercent = 22;
    /** true: tall column through the whole chunk. false: only around the suspicious blocks. */
    public boolean fullColumn = true;
    /** Lower scores near natural structures (spawners, sculk, strongholds...). */
    public boolean ignoreNaturalStructures = true;
    public boolean overworldOnly = true;
    public int renderDistanceChunks = 24;

    public static SusConfig get() {
        return instance;
    }

    public static int version() {
        return version;
    }

    /** Call after changing any value. */
    public static void changed() {
        version++;
        save();
    }

    /** Score a chunk needs to be flagged. Higher sensitivity = lower threshold. */
    public double threshold() {
        return 52.0 - 0.46 * sensitivity;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("suschunkfinder.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader reader = Files.newBufferedReader(p)) {
                SusConfig loaded = GSON.fromJson(reader, SusConfig.class);
                if (loaded != null) instance = loaded;
            } catch (Exception e) {
                SusChunkFinderClient.LOGGER.warn("Could not read config, using defaults", e);
            }
        }
        instance.clamp();
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(path())) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            SusChunkFinderClient.LOGGER.warn("Could not save config", e);
        }
    }

    private void clamp() {
        sensitivity = Math.max(1, Math.min(100, sensitivity));
        maxScanY = Math.max(-48, Math.min(120, maxScanY));
        opacityPercent = Math.max(5, Math.min(80, opacityPercent));
        renderDistanceChunks = Math.max(2, Math.min(64, renderDistanceChunks));
    }
}
