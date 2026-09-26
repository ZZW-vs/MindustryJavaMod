package zzw.content.graphics;

import arc.graphics.gl.Shader;
import arc.util.Log;

public class BlackHoleShader {
    public static Shader shader;

    private static final String VERT =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = u_projTrans * a_position;\n" +
        "}\n";

    private static final String FRAG =
        "uniform vec4 u_holes[8];\n" +
        "uniform float u_time;\n" +
        "varying vec2 v_texCoords;\n" +
        "\n" +
        "float hash(vec2 p){\n" +
        "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n" +
        "}\n" +
        "\n" +
        "float valueNoise(vec2 p){\n" +
        "    vec2 i = floor(p);\n" +
        "    vec2 f = fract(p);\n" +
        "    vec2 u = f * f * (3.0 - 2.0 * f);\n" +
        "    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),\n" +
        "               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);\n" +
        "}\n" +
        "\n" +
        "float fbm(vec2 p){\n" +
        "    float v = 0.0;\n" +
        "    float a = 0.5;\n" +
        "    for(int i = 0; i < 5; i++){\n" +
        "        v += a * valueNoise(p);\n" +
        "        p *= 2.0;\n" +
        "        a *= 0.5;\n" +
        "    }\n" +
        "    return v;\n" +
        "}\n" +
        "\n" +
        "void main(){\n" +
        "    vec2 center = vec2(0.5);\n" +
        "    vec2 p = v_texCoords - center;\n" +
        "    float r = length(p);\n" +
        "    float a = atan(p.y, p.x);\n" +
        "    \n" +
        "    float intensity = u_holes[0].z;\n" +
        "    float swirl = u_holes[0].w;\n" +
        "    \n" +
        "    // 1/r 引力透镜：越靠中心，向外推力越大\n" +
        "    // 这就是空间被压缩的来源（参考 Shadertoy 黑洞的 1/r² 引力）\n" +
        "    float pull = intensity * 0.18 / (r + 0.05);\n" +
        "    float spin = intensity * swirl * 0.6 / (r * 4.0 + 0.12) + u_time * 0.2;\n" +
        "    \n" +
        "    float newR = r + pull;\n" +
        "    float newA = a + spin;\n" +
        "    vec2 sp = vec2(cos(newA), sin(newA)) * newR + center;\n" +
        "    \n" +
        "    // 动态流体\n" +
        "    float t = u_time * 0.18;\n" +
        "    float n1 = fbm(sp * 6.0 + vec2(t, t * 0.7));\n" +
        "    float n2 = fbm(sp * 14.0 - vec2(t * 1.5, t * 0.9));\n" +
        "    float n = n1 * 0.65 + n2 * 0.35;\n" +
        "    \n" +
        "    vec3 cold = vec3(0.35, 0.65, 1.3);\n" +
        "    vec3 col = cold * (n * 0.9 + 0.3);\n" +
        "    \n" +
        "    float dd = r * 2.0;\n" +
        "    float inner = smoothstep(0.10, 0.22, dd);\n" +
        "    float outer = 1.0 - smoothstep(0.65, 1.0, dd);\n" +
        "    float mask = inner * outer;\n" +
        "    \n" +
        "    gl_FragColor = vec4(col, clamp(mask * 1.8, 0.0, 1.0));\n" +
        "}\n";

    public static void load() {
        try {
            shader = new Shader(VERT, FRAG);
            shader.bind();
        } catch (Exception e) {
            shader = null;
            Log.err("BlackHoleShader compile failed", e);
        }
    }

    public static void applyUniforms(float radius, float intensity, float swirl, float time) {
        if (shader == null) return;
        shader.setUniformf("u_holes[0]", radius * 0.5f, radius * 0.5f, intensity, swirl);
        shader.setUniformf("u_time", time);
    }

    public static void dispose() {
        if (shader != null) { shader.dispose(); shader = null; }
    }
}