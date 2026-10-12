#version 150

// Distant Horizons' depth, moved into the game's own (docs/DECISIONS.md K104): each pixel of its far terrain is put
// back where it is in the world (through the inverse of its projection) and given the depth the game's projection
// would give it. Drawn with the depth test LESS, so it only lands where the game's own terrain is further or absent.
uniform sampler2D Sampler0;
uniform mat4 FarInvProj;
uniform mat4 ProjMat;
uniform float FarReverse;
uniform float FarZeroToOne;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(Sampler0, texCoord).r;
    if ((FarReverse > 0.5 && depth <= 0.0) || (FarReverse < 0.5 && depth >= 1.0)) {
        discard;
    }
    float ndcZ = FarZeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = FarInvProj * vec4(texCoord * 2.0 - 1.0, ndcZ, 1.0);
    view /= view.w;
    vec4 clip = ProjMat * vec4(view.xyz, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 0.99999);
    fragColor = vec4(0.0);
}
