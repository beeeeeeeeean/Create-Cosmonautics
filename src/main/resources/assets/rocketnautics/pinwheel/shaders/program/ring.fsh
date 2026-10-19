#version 410

in vec2 texCoord;
out vec4 fragColor;

struct GradientStop {
    float position;
    vec4 color;
};

struct GradientSegment {
    GradientStop start;
    GradientStop end;

    float midpoint;
};

layout(std430) buffer bGradient { GradientSegment segments[]; };
uniform int segmentCount;

float interpolateMidpoint(float x, float midpoint) {
    midpoint = clamp(midpoint, 0.0001, 0.9999);
    x = clamp(x, 0.0, 1.0);

    if (x < midpoint) {
        return 0.5 * (x / midpoint);
    } else return 0.5 + 0.5 * ((x - midpoint) / (1.0 - midpoint));
}

vec4 sampleGradient(GradientSegment[segmentCount] gradient, float x) {
    if (segmentCount == 0) return vec4(0.0);

    if (x <= gradient[0].start.position) return gradient[0].start.color;

    for (int i = 0; i < segmentCount; i++) {
        GradientSegment seg = gradient[i];

        if (x <= seg.end.position) {
            float range = seg.end.position - seg.start.position;

            if (abs(range) < 0.000001) return seg.end.color;

            float t = (x - seg.start.position) / range;
            t = interpolateMidpoint(t, seg.midpoint);

            return mix(seg.start.color, seg.end.color, t);
        }
    }

    return gradient[segmentCount - 1].end.color;
}

void main() {

}