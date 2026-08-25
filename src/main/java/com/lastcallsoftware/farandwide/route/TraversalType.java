package com.lastcallsoftware.farandwide.route;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public enum TraversalType {
    ONE_WAY("one_way"),
    LOOP("loop"),
    REVERSE("reverse");

    public static final int ICON_TEXTURE_SIZE = 1254;

    private final String translationKey;
    private final Identifier icon;

    TraversalType(String name) {
        this.translationKey = "traversal.farandwide." + name;
        this.icon = Identifier.fromNamespaceAndPath(
                "farandwide",
                "textures/gui/traversal/" + name + ".png");
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public Component getDescription() {
        return Component.translatable(translationKey + ".description");
    }

    public Identifier getIcon() {
        return icon;
    }
}
