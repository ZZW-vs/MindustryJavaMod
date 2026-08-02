// FXAA 后处理顶点着色器
// 兼容 arc batch 顶点格式: position(2) + color(4) + texCoord(2)
attribute vec2 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_proj;
uniform mat4 u_transform;

varying vec4 v_col;
varying vec2 v_uv;

void main(){
    v_col = a_color;
    v_uv = a_texCoord0;
    gl_Position = u_proj * u_transform * vec4(a_position, 0.0, 1.0);
}
