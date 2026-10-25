package com.blueprintbuilder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Blueprint files are data-pack resources. The plain client resource manager only sees assets,
 * so singleplayer reads them from the integrated server, which has the data pack.
 */
public final class ClientResources {
    private ClientResources() {
    }

    public static ResourceManager manager(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() != null) {
            return minecraft.getSingleplayerServer().getResourceManager();
        }
        return minecraft.getResourceManager();
    }
}
