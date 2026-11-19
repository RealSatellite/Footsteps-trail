package dev.footstepstrail.deformation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

final class MaterialSounds {
    static DeformationMaterial family(BlockState state, ClientLevel level, BlockPos pos) {
        SoundType sound = state.getSoundType(level, pos, Minecraft.getInstance().player);
        return sound == SoundType.WOOD ? DeformationMaterial.WOOD
                : sound == SoundType.METAL ? DeformationMaterial.METAL
                : sound == SoundType.GLASS ? DeformationMaterial.GLASS : null;
    }
    private MaterialSounds() {}
}
