package com.lastcallsoftware.farandwide.client;

import com.lastcallsoftware.farandwide.Config;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base screen that applies the user's single-player pause preference. */
public abstract class FarAndWideScreen extends Screen {
    protected FarAndWideScreen(Component title) {
        super(title);
    }

    @Override
    public boolean isPauseScreen() {
        return Config.PAUSE_MOD_SCREENS.get();
    }
}
