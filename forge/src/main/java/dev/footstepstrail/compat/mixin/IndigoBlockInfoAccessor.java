package dev.footstepstrail.compat.mixin;

import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext", remap = false)
public interface IndigoBlockInfoAccessor {
    @Accessor("blockInfo")
    BlockRenderInfo footsteps$blockInfo();
}
