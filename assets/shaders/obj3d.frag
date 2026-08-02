// WavefrontObject 3D GPU渲染着色器
// 片段着色器: 顶点插值颜色 * 纹理采样 + alpha测试
varying vec4 v_col;
varying vec2 v_uv;

uniform sampler2D u_texture;
uniform int u_hasTexture;

void main(){
    vec4 col = v_col;
    if(u_hasTexture == 1){
        col *= texture2D(u_texture, v_uv);
    }
    // alpha测试: 阈值0.5 (经典alpha clip), 消除贴图半透明边缘导致的深度写入错误
    // 阈值过小(<0.1)会让半透明像素写入深度, 遮挡后面的像素, 视觉上呈透明效果
    if(col.a < 0.5) discard;
    gl_FragColor = col;
}
