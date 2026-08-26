package com.lastcallsoftware.farandwide.command.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public interface FarAndWideCommand {

    InputConstants.Key getMenuKey();

    Component getName();

    Component getDescription();

    void execute();
}
