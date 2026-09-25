#define MAX_HOLES 8

uniform sampler2D u_texture;
uniform vec2 u_texsize;
uniform vec4 u_holes[MAX_HOLES];
uniform int u_holes_count;
uniform vec2 u_invsize;
uniform float u_camscl;

// 自定义余弦函数 - 用于平滑插值
float qcos(float x) {
    float xabs = abs(x);
    float x2 = xabs * xabs;
    float x4 = x2 * x2;
    float x6 = x4 * x2;
    return 1.0 - x2 * 0.5 + x4 * 0.041666666 - x6 * 0.0013888889;
}

// 旋转变换函数
vec2 trns(vec2 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

void main() {
    vec2 coord = v_texcoord * u_texsize;
    vec2 pos = coord;
    
    // 应用每个黑洞的扭曲效果
    for(int i = 0; i < MAX_HOLES; i++) {
        if(i >= u_holes_count) break;
        
        vec4 hole = u_holes[i];
        vec2 hole_pos = hole.xy;
        float intensity = hole.z;
        float swirl = hole.w;
        
        vec2 vp = pos - hole_pos;
        float dist = length(vp);
        
        // 计算黑洞影响范围
        float range = (30.0 + intensity * 170.0) * u_camscl;
        
        if(dist < range) {
            // 计算强度衰减
            float f = (range - dist) / range;
            f = clamp(f, 0.0, 1.0);
            
            // 径向扭曲
            float radial_strength = intensity * 90.0 * f * f;
            vec2 nvp = normalize(vp);
            vec2 off = nvp * radial_strength;
            
            // 旋转扭曲
            float rot_strength = (trns(vp + off * u_camscl, intensity * swirl * f * f * f) - vp) / u_camscl;
            
            // 应用扭曲
            pos += off + rot_strength;
        }
    }
    
    // 采样纹理
    vec4 color = texture2D(u_texture, pos * u_invsize);
    
    // 应用颜色混合
    color.rgb *= v_color.rgb;
    color.a *= v_color.a;
    
    gl_FragColor = color;
}