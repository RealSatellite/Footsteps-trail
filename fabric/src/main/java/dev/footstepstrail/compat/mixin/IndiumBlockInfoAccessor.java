package dev.footstepstrail.compat.mixin;

import link.infra.indium.renderer.render.BlockRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "link.infra.indium.renderer.render.AbstractBlockRenderContext", remap = false)
public interface IndiumBlockInfoAccessor {
    @Accessor("blockInfo")
    BlockRenderInfo footsteps$blockInfo();
}
