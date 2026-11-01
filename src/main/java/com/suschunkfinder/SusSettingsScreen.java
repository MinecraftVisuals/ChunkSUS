package com.suschunkfinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.List;
import java.util.function.IntConsumer;

public class SusSettingsScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PINK = 0xFFF050F8;
    private static final int GRAY = 0xFFAAAAAA;

    private final Screen parent;
    private boolean needsRescan;

    public SusSettingsScreen(Screen parent) {
        super(Text.literal("Sus Chunk Finder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        SusConfig cfg = SusConfig.get();
        int cx = this.width / 2;
        int left = cx - 100;
        int y = 32;
        int step = 24;

        addDrawableChild(new ConfigSlider(left, y, 200, 20, "Sensitivity", 1, 100, cfg.sensitivity, "", v -> {
            cfg.sensitivity = v;
            SusConfig.changed();
        }));
        y += step;

        addDrawableChild(new ConfigSlider(left, y, 200, 20, "Max scan Y", -48, 120, cfg.maxScanY, "", v -> {
            cfg.maxScanY = v;
            needsRescan = true;
            SusConfig.changed();
        }));
        y += step;

        addDrawableChild(new ConfigSlider(left, y, 200, 20, "Highlight opacity", 5, 80, cfg.opacityPercent, "%", v -> {
            cfg.opacityPercent = v;
            SusConfig.changed();
        }));
        y += step;

        addDrawableChild(ButtonWidget.builder(toggleText("Highlight", cfg.enabled), b -> {
            cfg.enabled = !cfg.enabled;
            b.setMessage(toggleText("Highlight", cfg.enabled));
            SusConfig.changed();
            if (cfg.enabled) ChunkScanner.rescanAll();
        }).dimensions(left, y, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(toggleText("Full column", cfg.fullColumn), b -> {
            cfg.fullColumn = !cfg.fullColumn;
            b.setMessage(toggleText("Full column", cfg.fullColumn));
            SusConfig.changed();
        }).dimensions(left + 102, y, 98, 20).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(toggleText("Skip natural", cfg.ignoreNaturalStructures), b -> {
            cfg.ignoreNaturalStructures = !cfg.ignoreNaturalStructures;
            b.setMessage(toggleText("Skip natural", cfg.ignoreNaturalStructures));
            SusConfig.changed();
        }).dimensions(left, y, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(toggleText("Overworld only", cfg.overworldOnly), b -> {
            cfg.overworldOnly = !cfg.overworldOnly;
            b.setMessage(toggleText("Overworld only", cfg.overworldOnly));
            SusConfig.changed();
            ChunkScanner.rescanAll();
        }).dimensions(left + 102, y, 98, 20).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Rescan"), b -> ChunkScanner.rescanAll())
                .dimensions(left, y, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(left + 102, y, 98, 20).build());
    }

    private static Text toggleText(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 14, PINK);

        int y = 32 + 24 * 6 + 8;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Chunks scanned: " + ChunkScanner.scannedCount()
                        + "  |  Suspicious: " + ChunkScanner.flagged().size()),
                cx, y, WHITE);
        y += 12;

        List<ChunkScanner.Flagged> top = ChunkScanner.top(5);
        if (top.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("No suspicious chunks (yet)"), cx, y, GRAY);
        } else {
            for (ChunkScanner.Flagged f : top) {
                ChunkPos p = f.pos();
                String line = String.format("Chunk [%d, %d]  x %d  z %d  score %.0f",
                        p.x, p.z, p.getStartX() + 8, p.getStartZ() + 8, f.score());
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(line), cx, y, WHITE);
                y += 10;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (needsRescan) {
            ChunkScanner.rescanAll();
            needsRescan = false;
        }
        if (this.client != null) this.client.setScreen(parent);
    }

    private static final class ConfigSlider extends SliderWidget {
        private final String label;
        private final int min;
        private final int max;
        private final String suffix;
        private final IntConsumer onChange;

        ConfigSlider(int x, int y, int width, int height, String label, int min, int max,
                     int current, String suffix, IntConsumer onChange) {
            super(x, y, width, height, Text.empty(), (current - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.suffix = suffix;
            this.onChange = onChange;
            updateMessage();
        }

        private int intValue() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + intValue() + suffix));
        }

        @Override
        protected void applyValue() {
            onChange.accept(intValue());
        }
    }
}
