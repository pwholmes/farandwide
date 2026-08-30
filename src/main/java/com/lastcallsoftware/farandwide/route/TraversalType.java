package com.lastcallsoftware.farandwide.route;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public enum TraversalType {
    ONE_WAY("one_way"),
    LOOP("loop"),
    REVERSE("reverse");

    public static final Codec<TraversalType> CODEC = Codec.STRING.xmap(
            value -> TraversalType.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
            value -> value.name().toLowerCase(java.util.Locale.ROOT));

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
