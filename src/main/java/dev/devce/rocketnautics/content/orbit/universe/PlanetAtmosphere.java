package dev.devce.rocketnautics.content.orbit.universe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

public record PlanetAtmosphere(float radius, float intensity, float falloff, float scatteringStrength, Vector3f wavelengths) {
    public static final StreamCodec<FriendlyByteBuf, PlanetAtmosphere> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeFloat(val.radius);
                buf.writeFloat(val.intensity);
                buf.writeFloat(val.falloff);
                buf.writeFloat(val.scatteringStrength);
                buf.writeVector3f(val.wavelengths);
            },
            buf -> {
                float radius = buf.readFloat();
                float intensity = buf.readFloat();
                float falloff = buf.readFloat();
                float scatteringStrength = buf.readFloat();
                Vector3f wavelengths = buf.readVector3f();
                return new PlanetAtmosphere(radius, intensity, falloff, scatteringStrength, wavelengths);
            }
    );
}
