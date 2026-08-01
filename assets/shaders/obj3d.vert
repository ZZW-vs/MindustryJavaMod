// WavefrontObject 3D GPU渲染着色器
// 顶点着色器: MVP变换 + 方向光照 (Gouraud着色)
attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec4 a_color;

uniform mat4 u_proj;
uniform mat4 u_trans;
uniform vec3 u_lightDir;
uniform vec3 u_lightColor;
uniform vec3 u_shadeColor;
uniform float u_maxShade;

varying vec4 v_col;

void main(){
    // 使用模型矩阵的3x3部分变换法线 (均匀缩放时正确)
    vec3 worldNormal = normalize(mat3(u_trans) * a_normal);
    // shade因子: 法线与光源同向=0(亮), 反向=1(暗), 垂直=0.5(中)
    float shade = clamp((1.0 - dot(worldNormal, u_lightDir)) * 0.5, 0.0, u_maxShade);
    vec3 baseCol = a_color.rgb * u_lightColor;
    v_col = vec4(mix(baseCol, u_shadeColor, shade), a_color.a);
    gl_Position = u_proj * u_trans * vec4(a_position, 1.0);
}
