package com.brochacho.dmzkiaspects.capability;

import com.brochacho.dmzkiaspects.DMZKiAspectsMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ICapabilityProvider para KiAspectsData.
 * Estructura calcada de com.dragonminez.common.stats.StatsProvider para
 * mantener el mismo estilo que el mod base.
 */
public class KiAspectsProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static final ResourceLocation ID = new ResourceLocation(DMZKiAspectsMod.MODID, "ki_aspects");

    private final KiAspectsData data;
    private final LazyOptional<KiAspectsData> optional;

    public KiAspectsProvider(Player player) {
        this.data = new KiAspectsData(player);
        this.optional = LazyOptional.of(() -> this.data);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return cap == KiAspectsCapability.INSTANCE ? optional.cast() : LazyOptional.empty();
    }

    /** Se llama desde el listener de invalidación registrado en AttachCapabilitiesEvent. */
    void invalidate() {
        optional.invalidate();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.load(nbt);
    }
}
