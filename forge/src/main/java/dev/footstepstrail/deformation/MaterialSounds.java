package dev.footstepstrail.deformation;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;

final class MaterialSounds {
    static String blockId(BlockState state) { return ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString(); }
    static DeformationMaterial family(BlockState state, ClientWorld level, BlockPos pos) {
        BlockSoundGroup sound = state.getSoundType(level, pos, MinecraftClient.getInstance().player);
        return sound == BlockSoundGroup.WOOD ? DeformationMaterial.WOOD
                : sound == BlockSoundGroup.METAL ? DeformationMaterial.METAL
                : sound == BlockSoundGroup.GLASS ? DeformationMaterial.GLASS : null;
    }
    private MaterialSounds() {}
}
