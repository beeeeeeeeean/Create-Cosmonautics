#version 410

#include rocketnautics:common_math
#include veil:space_helper

uniform int shape; // 0 - sphere, 1 - cube

uniform float rCore;
uniform float rPlasma;

uniform float intensity;

uniform float falloff;

uniform vec3 color;

uniform mat4 transform;
uniform mat4 invTransform;

uniform float voxelSz;

uniform int samples;

in vec2 texCoord;
out vec4 fragColor;

vec3 light(vec3 rO, vec3 rD, float rL, mat4 invTransform) {
    if (rL <= 0.0 || rPlasma <= rCore) return vec3(0.0);

    vec3 ro = (invTransform * vec4(rO, 1.0)).xyz;
    vec3 rd = (invTransform * vec4(rD, 0.0)).xyz;

    float stepSz = rL / float(samples);
    vec3 accumulated = vec3(0.0);
    float weight = 0.0;

    for (int i = 0; i < samples; i++) {
        float t = (float(i) + 0.5) * stepSz;
        vec3 p = voxelize(ro + rd * t, voxelSz);

        float d = (shape == 0) ? length(p) : max(max(abs(p.x), abs(p.y)), abs(p.z));

        if (d >= rPlasma) continue;

        float localDensity = 1.0;
        if (d > rCore) {
            float x = (d - rCore) / (rPlasma - rCore);
            localDensity = (exp(-falloff * x) - exp(-falloff)) / (1.0 - exp(-falloff));
        }

        accumulated += color * intensity * localDensity * stepSz;
        weight += stepSz;
    }

    return accumulated / weight;
}

void main() {
    vec3 rO = screenToWorldSpace(texCoord, 0).xyz;
    vec3 rD = viewDirFromUv(texCoord);

    vec2 hitInfo = rayShape(shape, rPlasma, invTransform, rO, rD);

    if (hitInfo.y > 0) {
        const float epsilon = 0.0001;
        vec3 p = rO + rD * (hitInfo.x + epsilon);
        vec3 light = light(p, rD, hitInfo.y - epsilon * 2, invTransform);

        fragColor = vec4(light, 1.0);
    }
}