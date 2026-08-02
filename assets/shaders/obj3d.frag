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
    // ★ alpha测试: 丢弃几乎透明的片段, 避免写入深度缓冲遮挡后续像素
    if(col.a < 0.01) discard;
    gl_FragColor = col;
}
