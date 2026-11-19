package dev.footstepstrail.deformation;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

final class MaterialSounds {
    static String blockId(BlockState state) { return Registries.BLOCK.getId(state.getBlock()).toString(); }
    static DeformationMaterial family(BlockState state, ClientWorld level, BlockPos pos) {
        BlockSoundGroup sound = state.getSoundGroup();
        return sound == BlockSoundGroup.WOOD ? DeformationMaterial.WOOD
                : sound == BlockSoundGroup.METAL ? DeformationMaterial.METAL
                : sound == BlockSoundGroup.GLASS ? DeformationMaterial.GLASS : null;
    }
    private MaterialSounds() {}
}
