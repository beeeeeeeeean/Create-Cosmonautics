package dev.devce.rocketnautics.mixin;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method= "setupFog", at = @At("HEAD"), cancellable = true)
    private static void rocketnautics$removeSkyFog(Camera camera, FogRenderer.FogMode mode, float farPlaneDistance, boolean shouldCreateFog, float partialTick, CallbackInfo ci) {
        // TODO: bring back biome fog

        FogType type = camera.getFluidInCamera();
        if (type != FogType.NONE) return;

        RenderSystem.setShaderFogShape(FogShape.CYLINDER);
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);

        ci.cancel();
    }
}
