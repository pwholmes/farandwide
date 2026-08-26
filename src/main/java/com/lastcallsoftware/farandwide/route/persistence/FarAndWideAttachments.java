package com.lastcallsoftware.farandwide.route.persistence;

import java.util.function.Supplier;

import com.lastcallsoftware.farandwide.FarAndWide;
import com.mojang.serialization.Codec;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Persistent identity owned by Far and Wide, independent of Entity#getId(). */
/**
 * Registers and accesses the stable ID attached to assignable entities.
 *
 * <p>Minecraft runtime entity IDs are not stable across unloads or restarts, so
 * they cannot key persisted assignments. The attachment stores an independently
 * allocated ID that survives serialization. Network snapshots translate back to
 * runtime IDs only after the corresponding entity is loaded.
 */
public final class FarAndWideAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FarAndWide.MODID);
    public static final Supplier<AttachmentType<Integer>> ASSIGNEE_ID = ATTACHMENT_TYPES.register(
            "assignee_id",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("value")).build());

    private FarAndWideAttachments() {
    }

    public static int getOrCreateAssigneeId(Entity entity, FarAndWideSavedData data) {
        int assigneeId = entity.getData(ASSIGNEE_ID.get());
        if (assigneeId > 0) {
            return assigneeId;
        }
        assigneeId = data.allocateAssigneeId();
        entity.setData(ASSIGNEE_ID.get(), assigneeId);
        return assigneeId;
    }
}
