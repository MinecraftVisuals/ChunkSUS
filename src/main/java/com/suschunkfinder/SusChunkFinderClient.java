package com.suschunkfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SusChunkFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "suschunkfinder";
    public static final Logger LOGGER = LoggerFactory.getLogger("SusChunkFinder");

    private static KeyBinding openSettingsKey;

    @Override
    public void onInitializeClient() {
        SusConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.suschunkfinder.open_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                category
        ));

        ChunkScanner.init();
        SusRenderer.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null && client.world != null) {
                    client.setScreen(new SusSettingsScreen(null));
                }
            }
        });

        LOGGER.info("Sus Chunk Finder loaded. Press F7 for settings.");
    }
}
