#version 410

#include rocketnautics:common_math
#include veil:space_helper

uniform sampler2D sDiffuse;
uniform sampler2D sDiffuseDepth;
uniform sampler2D sOpticalDepth;

uniform int shape; // 0 - sphere, 1 - cube

uniform float rPlanet;
uniform float rAtmosphere;

uniform vec3 sunDir;

uniform float intensity;
uniform float opacity;

uniform float falloff;

uniform vec3 betaRayleigh;
uniform vec3 betaMie;

uniform mat4 transform;
uniform mat4 invTransform;
vec3 origin = transform[3].xyz;

uniform float voxelSz;

uniform int samples;

uniform int depthTest;
uniform int disableSurfaceCulling;

in vec2 texCoord;
out vec4 fragColor;

float sampleOpticalDepth(vec3 rO, vec3 rD) {
    vec3 p = (invTransform * vec4(rO, 1.0)).xyz;

    float h;
    if (shape == 0) h = length(p) - rPlanet;
    if (shape == 1) h = max(max(abs(p.x), abs(p.y)), abs(p.z)) - rPlanet;

    float fAltitude = clamp(h / (rAtmosphere - rPlanet), 0.0, 1.0);

    const float epsilon = 0.01;
    if (1 - fAltitude < epsilon) return 0;

    float mu = dot(normalize(rO - origin), rD);
    float uvX = (1.0 - mu) * 0.5;

    return texture(sOpticalDepth, vec2(uvX, fAltitude)).r;
}

float sampleOpticalDepthSegment(vec3 rO, vec3 rD, float rL) {
    vec3 endPoint = rO + rD * rL;

    float tauClose = sampleOpticalDepth(rO, rD);
    float tauFar = sampleOpticalDepth(endPoint, rD);

    return max(tauClose - tauFar, 0.0);
}

float reyleighPhase(float mu) {
    return 3.0 / (16.0 * PI) * (1.0 + mu * mu);
}

float miePhase(float mu, float g) {
    float g2 = g * g;
    return 3.0 / (8.0 * PI) * ((1.0 - g2) * (mu*mu + 1.0)) / (pow(1.0 + g2 - 2.0 * mu * g, 1.5) * (2.0 +g2));
}

vec3 light(vec3 rO, vec3 rD, float rL, vec4 colorIn, mat4 invTransform) {
    if (rL <= 0.0) return colorIn.rgb;

    float stepSz = rL / float(samples);

    float mu = dot(rD, sunDir);
    float rPhase = reyleighPhase(mu);
    float mPhase = miePhase(mu, 0.3);

    vec3 rInScatter = vec3(0.0);
    vec3 mInScatter = vec3(0.0);

    float tauRView = 0.0;
    for (int i = 0; i < samples; i++) {
        float t = (float(i) + 0.5) * stepSz;
        vec3 p = voxelize(rO + rD * t, voxelSz);

        float tauRSun = sampleOpticalDepth(p, sunDir);

        float localDensity = densityAtP(shape, p, rAtmosphere, rPlanet, falloff, voxelSz, invTransform);

        tauRView = sampleOpticalDepthSegment(rO, rD, stepSz * i);

        vec3 transmittance = exp(-(tauRSun + tauRView) * (betaRayleigh + betaMie));

        rInScatter += localDensity * transmittance * rPhase;
        mInScatter += localDensity * transmittance * mPhase;
    }

    vec3 scattering = (rInScatter * betaRayleigh + mInScatter * betaMie) * intensity * stepSz / rPlanet;

    vec3 inTransmittance = exp(-tauRView * (betaRayleigh + betaMie));
    return colorIn.rgb * inTransmittance + scattering;
}

void main() {
    vec4 col = texture(sDiffuse, texCoord);
    float depth = texture(sDiffuseDepth, texCoord).r;

    vec3 rO = screenToWorldSpace(texCoord, 0).xyz;
    vec3 rD = viewDirFromUv(texCoord);

    fragColor = col;

    float dTerrain = INFINITY;
    if (depthTest == 1 && depth < 1.0) {
        vec3 pTerrain = screenToWorldSpace(texCoord, depth).xyz;
        dTerrain = length(pTerrain - rO);
    }

    vec2 hitSurface = rayShape(shape, rPlanet, invTransform, rO, rD);
    vec2 hitAtmo = rayShape(shape, rAtmosphere, invTransform, rO, rD);

    float dSurface = hitSurface.x;
    float dSurfaceTerrain = min(dSurface, dTerrain);

    float dAtmo = hitAtmo.x;
    float dtAtmo = min(hitAtmo.y, dSurfaceTerrain - dAtmo);

    if (dtAtmo > 0) {
        const float epsilon = 0.0001;
        vec3 p = rO + rD * (dAtmo + epsilon);
        fragColor = vec4(light(p, rD, dtAtmo - epsilon * 2, col, invTransform), 1);
    }

    fragColor = vec4(mix(col.rgb, fragColor.rgb, opacity), 1.0);
}