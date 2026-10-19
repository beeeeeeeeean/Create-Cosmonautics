package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.orekit.time.AbsoluteDate;

import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;

@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public final class SpaceRenderer {
    private static Boolean queuedShaderState = null;
    private static boolean shadersSuppressed = false;

    private static boolean irisLoaded() {
        return FMLLoader.getLoadingModList().getModFileById("iris") != null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        if (queuedShaderState == null) return;
        if (!irisLoaded()) return;
        try {
            Iris.getIrisConfig().setShadersEnabled(queuedShaderState);
            Iris.getIrisConfig().save();

            Iris.reload();
        } catch (Exception ignored) {}

        queuedShaderState = null;
    }
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent e) {
        boolean isModern = dev.devce.rocketnautics.RocketConfig.CLIENT.skyRenderingSystem.get() == dev.devce.rocketnautics.RocketConfig.SkyRenderingSystem.MODERN;
        if (!isModern) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) return;
        ResourceKey<Level> dimension = mc.level.dimension();

        if (UniverseHelper.UNIVERSE == null) return;

        CubePlanet planet = UniverseHelper.UNIVERSE.getPlanetByDimension(dimension);

        boolean renderUniverseInDimension = RocketConfig.CLIENT.enableCustomSky.get() || dimension == RocketDimensions.DEEP_SPACE;
        if (planet != null) {
            if (planet.linkedDimension() != null) {
                renderUniverseInDimension |= planet.linkedDimension().renderUniverseInDimension();
            }
        }

        if (renderUniverseInDimension) {
            if (irisLoaded()) {
                if (Iris.getIrisConfig().areShadersEnabled()) {
                    queuedShaderState = false;
                    shadersSuppressed = true;
                }
            }
        } else {
            if (shadersSuppressed) {
                queuedShaderState = true;
                shadersSuppressed = false;
            }
        }

        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        AbsoluteDate date = UniverseHelper.getPredictedUniverseDate(partial);

        Quaternionf rotation = new Quaternionf();
        if (dimension != RocketDimensions.DEEP_SPACE && date != null) {
            assert planet != null;

            var global = DeepSpaceHelper.localPositionToGlobalPositionAndRotation(new Vector3d(), null, mc.level, planet, date);
            rotation = DeepSpaceHelper.adapt(global.second()).get(new Quaternionf()).conjugate().rotateLocalZ(-(float)Math.PI/2);
        }

        ResourceLocation hdrId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "hdr_fbo");
        AdvancedFbo HDR_FBO = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(hdrId);

        RenderTarget rt = Minecraft.getInstance().getMainRenderTarget();

        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            int textureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            
           LensFlareRenderer.renderQueue(e.getCamera());
            if (renderUniverseInDimension && planet != null) AtmosphereRenderer.render(planet, e.getCamera());

            rt.bindWrite(true);

            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, HDR_FBO.getId());
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, rt.frameBufferId);

            GL30.glBlitFramebuffer(0, 0, HDR_FBO.getWidth(), HDR_FBO.getHeight(), 0, 0, rt.width, rt.height, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            return;
        }

        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            HDR_FBO.bind(true);

            PoseStack ps = e.getPoseStack();

            ps.pushPose();
            ps.mulPose(e.getModelViewMatrix()); // AFTER_SKY renders before the model view matrix is normally applied

            if (dimension == RocketDimensions.DEEP_SPACE) {
                if (UniverseHelper.receivedPositionTick == -1) return;
                date = UniverseHelper.getRenderDate(partial);

                Vec3 pos = e.getCamera().getPosition();
                VoxelShape box = DeepSpaceData.getBoxForPosition(pos);

                if (box.bounds().contains(pos))
                    UniverseRenderer.render(null, ps,
                            e.getPartialTick().getGameTimeDeltaTicks(), partial,
                            UniverseHelper.receivedPosition.getPosition(date),
                            UniverseHelper.receivedPosition.getFrame(), new Quaternionf(),
                            date, e.getCamera());

                ps.pushPose();
                IBRenderer.render(ps, pos);
                ps.popPose();
            } else {
                if (date != null) {
                    UniverseRenderer.render(planet, ps,
                            e.getPartialTick().getGameTimeDeltaTicks(), partial,
                            Vector3D.ZERO,
                            planet.orekitFrame(), rotation,
                            date, e.getCamera());
                }
            }

            ps.popPose();
        }
    }
}
