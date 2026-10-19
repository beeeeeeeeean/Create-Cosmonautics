package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Camera;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;

import static dev.devce.rocketnautics.client.render.spaceRenderer.UniverseRenderData.FULLSCREEN_VAO;

public class AtmosphereRenderer {
    private static Pair<Integer, Integer> savedDepthMap = null;

    private static final float rPlanet = 6_371_000f;
    private static final float rAtmosphere = 1.0235f;
    private static final float falloff = 0.238f;

    private static Vector3f lightDirection = new Vector3f();

    public static void updateLightDirection(Vector3f lightDir) {
        lightDirection.set(lightDir);
    }

    public static void render(CubePlanet planet, Camera camera) {
        if (planet.atmosphere() != null) {
            if (savedDepthMap == null || savedDepthMap.getKey() != planet.id()) {
                final int texSz = 256;

                ResourceLocation kernelId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "atmosphere_tex");
                ShaderProgram kernel = VeilRenderSystem.setShader(kernelId);
                if (kernel != null && kernel.isValid() && kernel.isCompute()) {
                    int tex = GlStateManager._genTexture();
                    GlStateManager._bindTexture(tex);

                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F,
                            texSz, texSz,
                            0,
                            GL11.GL_RED, GL11.GL_FLOAT,
                            (ByteBuffer)null
                    );

                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_WRAP_S,
                            GL12.GL_CLAMP_TO_EDGE);

                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_WRAP_T,
                            GL12.GL_CLAMP_TO_EDGE);

                    GL11.glTexParameteri(
                            GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_MIN_FILTER,
                            GL11.GL_LINEAR
                    );

                    GL11.glTexParameteri(
                            GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_MAG_FILTER,
                            GL11.GL_LINEAR
                    );

                    GL42.glBindImageTexture(0, tex, 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_R32F);

                    kernel.bind();

                    kernel.getUniformSafe("shape").setInt(0);

                    kernel.getUniformSafe("fAtmosphere").setFloat(rAtmosphere);
                    kernel.getUniformSafe("falloff").setFloat(falloff);

                    kernel.getUniformSafe("texSz").setInt(texSz);
                    kernel.getUniformSafe("samples").setInt(100);

                    int groupsX = (texSz + 7) / 8;
                    int groupsY = (texSz + 7) / 8;

                    GL43.glDispatchCompute(groupsX, groupsY, 1);

                    GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);

                    ShaderProgram.unbind();

                    savedDepthMap = new ImmutablePair<>(planet.id(), tex);
                }
            }

            ResourceLocation shaderId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "atmosphere");

            RenderSystem.disableCull();
            RenderSystem.disableBlend();

            ShaderProgram shader = VeilRenderSystem.setShader(shaderId);
            if (shader != null && shader.isValid()) {
                ResourceLocation fboId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "atmosphere_fbo");
                AdvancedFbo atmosphereFBO = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(fboId);

                ResourceLocation hdrId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "hdr_fbo");
                AdvancedFbo HDR_FBO = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(hdrId);

                if (atmosphereFBO != null) {
                    atmosphereFBO.bind(true);
                    shader.bind();

                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, HDR_FBO.getColorTextureAttachment(0).getId());
                    shader.getUniformSafe("sDiffuse").setInt(0);

                    RenderSystem.activeTexture(GL13.GL_TEXTURE1);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, HDR_FBO.getDepthTextureAttachment().getId());
                    shader.getUniformSafe("sDiffuseDepth").setInt(1);

                    RenderSystem.activeTexture(GL13.GL_TEXTURE2);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedDepthMap.getValue());
                    shader.getUniformSafe("sOpticalDepth").setInt(2);

                    shader.getUniformSafe("shape").setInt(0);

                    shader.getUniformSafe("rPlanet").setFloat(rPlanet);
                    shader.getUniformSafe("rAtmosphere").setFloat(rPlanet * rAtmosphere);

                    shader.getUniformSafe("sunDir").setVector(lightDirection);

                    shader.getUniformSafe("intensity").setFloat(13f);

                    float opacity = Math.clamp((400.0f - ((float)camera.getPosition().y - 63.0f))/400.0f, 0.0f, 1.0f);
                    shader.getUniformSafe("opacity").setFloat(opacity);

                    shader.getUniformSafe("falloff").setFloat(falloff);

                    Vector3f wavelengths = planet.atmosphere().wavelengths();
                    Vector3f scatteringCoefficients = new Vector3f(4.90923848e-6f, 1.61210348e-5f, 2.54292978e-5f).mul(1500000f);
                    shader.getUniformSafe("betaRayleigh").setVector(scatteringCoefficients);
                    shader.getUniformSafe("betaMie").setVector(new Vector3f(4.0e-10f));

                    Matrix4f transform = new Matrix4f()
                            .translate(camera.getPosition().toVector3f()
                                    .mul(1, 0, 1)
                                    .sub(new Vector3f(0, rPlanet, 0))
                            );

                    Matrix4f invTransform = new Matrix4f(transform).invert();
                    shader.getUniformSafe("transform").setMatrix(transform);
                    shader.getUniformSafe("invTransform").setMatrix(invTransform);

                    shader.getUniformSafe("voxelSz").setFloat(0);

                    shader.getUniformSafe("samples").setInt(10);

                    shader.getUniformSafe("depthTest").setInt(1);
                    shader.getUniformSafe("disableSurfaceCulling").setInt(1);

                    GL30.glBindVertexArray(FULLSCREEN_VAO);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                    GL30.glBindVertexArray(0);

                    ShaderProgram.unbind();

                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, atmosphereFBO.getId());
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, HDR_FBO.getId());

                    GL30.glBlitFramebuffer(
                            0, 0,
                            atmosphereFBO.getWidth(), atmosphereFBO.getHeight(),
                            0, 0,
                            HDR_FBO.getWidth(), HDR_FBO.getHeight(),
                            GL11.GL_COLOR_BUFFER_BIT,
                            GL11.GL_NEAREST
                    );

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, HDR_FBO.getId());
                }
            }
        }
    }
}
