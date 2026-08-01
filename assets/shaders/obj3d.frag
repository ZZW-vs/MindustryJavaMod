// WavefrontObject 3D GPU渲染着色器
// 片段着色器: 直接输出顶点插值颜色
varying vec4 v_col;

void main(){
    gl_FragColor = v_col;
}
