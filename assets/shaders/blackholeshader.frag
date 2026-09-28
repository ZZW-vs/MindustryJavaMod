#define MAX_HOLES 8

uniform sampler2D u_texture;
uniform vec2 u_texsize;
uniform vec4 u_holes[MAX_HOLES];
uniform int u_holes_count;
uniform vec2 u_invsize;
uniform float u_camscl;

varying vec2 v_texCoords;

vec2 trns(vec2 pos, float deg){
    float rad = deg * 0.0174533;
    float c = cos(rad);
    float s = sin(rad);
    return vec2(pos.x * c - pos.y * s, pos.x * s + pos.y * c);
}

void main(){
    vec2 tp = v_texCoords;
    float core = 0.0;
    float ring = 0.0;

    for(int i = 0; i < MAX_HOLES; i++){
        vec2 pos = u_holes[i].xy;
        float intensity = u_holes[i].z;
        float swirl = u_holes[i].w;

        // 影响半径: intensity 1.5~4.0 -> 约 62~142 px (进一步收窄)
        float range = (14.0 + intensity * 32.0) * u_camscl;

        vec2 vp = (v_texCoords * u_texsize) - pos;
        float dist = length(vp);

        // 事件视界半径, 与强度同步: intensity -> 0 时收缩到 0
        float coreR = intensity * 4.2 * u_camscl;

        // 不透明黑核
        core = max(core, 1.0 - smoothstep(coreR * 0.92, coreR * 1.08, dist));
        // 外侧光子环
        ring = max(ring, smoothstep(coreR * 1.02, coreR * 1.12, dist)
                        * (1.0 - smoothstep(coreR * 1.12, coreR * 1.40, dist)));

        if(dist < range){
            float f = (range - dist) / range;          // 1 = 中心, 0 = 边缘
            vec2 dir = normalize(vp + vec2(0.0001));

            // 向内吸: 采样点向外拉, 视觉上空间被吸进去
            vec2 warped = vp + dir * (intensity * 13.0 * f * f);

            // 螺旋: f 二次衰减, 越靠中心扭得越狠, 外缘几乎不扭
            float ang = intensity * swirl * 3.0 * f * f;
            vec2 sw = trns(warped, ang);

            tp += (sw - vp) * u_invsize;
        }

        if(i >= u_holes_count - 1){
            break;
        }
    }

    vec4 c = texture2D(u_texture, tp);

    // 黑核: 强制不透明, 不会再透出背景
    c = mix(c, vec4(0.0, 0.0, 0.0, 1.0), clamp(core, 0.0, 1.0));

    // 光子环
    c.rgb += vec3(0.55, 0.78, 1.0) * ring;
    c.a = max(c.a, ring * 0.9);

    gl_FragColor = c;
}