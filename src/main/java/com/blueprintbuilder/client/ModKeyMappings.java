package com.blueprintbuilder.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.blueprintbuilder";

    public static final KeyMapping OPEN_LIBRARY = new KeyMapping(
            "key.blueprintbuilder.open_library",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);

    private ModKeyMappings() {
    }
}
