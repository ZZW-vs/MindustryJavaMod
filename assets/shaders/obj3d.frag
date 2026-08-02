// WavefrontObject 3D GPU渲染着色器
// 片段着色器: 顶点插值颜色 * 纹理采样
varying vec4 v_col;
varying vec2 v_uv;

uniform sampler2D u_texture;
uniform int u_hasTexture;

void main(){
    vec4 col = v_col;
    if(u_hasTexture == 1){
        col *= texture2D(u_texture, v_uv);
    }
    gl_FragColor = col;
}
