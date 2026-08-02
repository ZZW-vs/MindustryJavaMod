// FXAA 3.11 后处理片段着色器
// 专优化3D模型边缘抗锯齿 (Y轴旋转时波浪状锯齿修复)
// GLSL ES 1.00 兼容 (Mindustry WebGL/Desktop)
precision mediump float;

varying vec4 v_col;
varying vec2 v_uv;

uniform sampler2D u_texture;
uniform vec2 u_texelSize;

#define FXAA_EDGE_THRESHOLD 0.125
#define FXAA_EDGE_THRESHOLD_MIN 0.0312
#define FXAA_SUBPIX_QUALITY 0.75

float luma(vec3 rgb){
    return dot(rgb, vec3(0.299, 0.587, 0.114));
}

void main(){
    vec4 colorM = texture2D(u_texture, v_uv);
    float lumaM = luma(colorM.rgb);

    // 采样4个直接邻居
    float lumaS = luma(texture2D(u_texture, v_uv + vec2(0.0, -u_texelSize.y)).rgb);
    float lumaN = luma(texture2D(u_texture, v_uv + vec2(0.0,  u_texelSize.y)).rgb);
    float lumaW = luma(texture2D(u_texture, v_uv + vec2(-u_texelSize.x, 0.0)).rgb);
    float lumaE = luma(texture2D(u_texture, v_uv + vec2( u_texelSize.x, 0.0)).rgb);

    float lumaMin = min(lumaM, min(min(lumaS, lumaN), min(lumaW, lumaE)));
    float lumaMax = max(lumaM, max(max(lumaS, lumaN), max(lumaW, lumaE)));
    float lumaRange = lumaMax - lumaMin;

    // 非边缘像素直接输出 (性能优化: 跳过 ~80% 像素)
    if(lumaRange < max(FXAA_EDGE_THRESHOLD_MIN, lumaMax * FXAA_EDGE_THRESHOLD)){
        gl_FragColor = colorM * v_col;
        return;
    }

    // 采样4个角点
    float lumaSW = luma(texture2D(u_texture, v_uv + vec2(-u_texelSize.x, -u_texelSize.y)).rgb);
    float lumaSE = luma(texture2D(u_texture, v_uv + vec2( u_texelSize.x, -u_texelSize.y)).rgb);
    float lumaNW = luma(texture2D(u_texture, v_uv + vec2(-u_texelSize.x,  u_texelSize.y)).rgb);
    float lumaNE = luma(texture2D(u_texture, v_uv + vec2( u_texelSize.x,  u_texelSize.y)).rgb);

    // 边缘方向检测
    float lumaNS = lumaN + lumaS;
    float lumaWE = lumaW + lumaE;

    float lumaSWS = lumaSW + lumaS;
    float lumaSES = lumaSE + lumaS;
    float lumaNWN = lumaNW + lumaN;
    float lumaNEN = lumaNE + lumaN;

    float edgeHoriz = abs(-2.0 * lumaW + (lumaSW + lumaNW))
                    + abs(-2.0 * lumaM + lumaNS) * 2.0
                    + abs(-2.0 * lumaE + (lumaSE + lumaNE));

    float edgeVert  = abs(-2.0 * lumaS + (lumaSW + lumaSE))
                    + abs(-2.0 * lumaM + lumaWE) * 2.0
                    + abs(-2.0 * lumaN + (lumaNW + lumaNE));

    bool isHorizontal = edgeHoriz >= edgeVert;

    // 选择步进方向
    float stepLength = isHorizontal ? u_texelSize.y : u_texelSize.x;
    float luma1 = isHorizontal ? lumaS : lumaW;
    float luma2 = isHorizontal ? lumaN : lumaE;

    float gradient1 = luma1 - lumaM;
    float gradient2 = luma2 - lumaM;
    bool is1Steepest = abs(gradient1) >= abs(gradient2);
    float gradientScaled = 0.25 * max(abs(gradient1), abs(gradient2));

    float lumaLocalAverage;
    if(is1Steepest){
        stepLength = -stepLength;
        lumaLocalAverage = 0.5 * (luma1 + lumaM);
    }else{
        lumaLocalAverage = 0.5 * (luma2 + lumaM);
    }

    // 偏移UV到边缘中点
    vec2 currentUv = v_uv;
    if(isHorizontal){
        currentUv.y += stepLength * 0.5;
    }else{
        currentUv.x += stepLength * 0.5;
    }

    // 沿边缘步进搜索 (2次迭代, 平衡精度与性能)
    vec2 offset = isHorizontal ? vec2(u_texelSize.x, 0.0) : vec2(0.0, u_texelSize.y);

    float lumaEnd1 = luma(texture2D(u_texture, currentUv).rgb) - lumaLocalAverage;
    float lumaEnd2 = luma(texture2D(u_texture, currentUv + offset).rgb) - lumaLocalAverage;

    bool reached1 = abs(lumaEnd1) >= gradientScaled;
    bool reached2 = abs(lumaEnd2) >= gradientScaled;

    if(!reached1) currentUv -= offset;
    if(!reached2) currentUv += offset;

    // 第2次迭代
    if(!reached1 || !reached2){
        lumaEnd1 = luma(texture2D(u_texture, currentUv - offset).rgb) - lumaLocalAverage;
        lumaEnd2 = luma(texture2D(u_texture, currentUv + offset).rgb) - lumaLocalAverage;

        if(!reached1) reached1 = abs(lumaEnd1) >= gradientScaled;
        if(!reached2) reached2 = abs(lumaEnd2) >= gradientScaled;

        if(!reached1) currentUv -= offset;
        if(!reached2) currentUv += offset;
    }

    // 计算像素到边缘的距离
    float distance1, distance2;
    if(isHorizontal){
        distance1 = v_uv.y - currentUv.y;
        distance2 = currentUv.y - v_uv.y;
    }else{
        distance1 = v_uv.x - currentUv.x;
        distance2 = currentUv.x - v_uv.x;
    }

    float edgeDistance = (distance1 < 0.0) ? distance1 : distance2;
    float pixelOffset = 0.5 - edgeDistance / (distance1 + distance2 + 1e-6);
    pixelOffset = clamp(pixelOffset, 0.0, 1.0);
    pixelOffset = pixelOffset * (1.0 - FXAA_SUBPIX_QUALITY);

    // 子像素混合
    float lumaAverage = (1.0 / 12.0) * (2.0 * (lumaNS + lumaWE) + (lumaSW + lumaSE + lumaNW + lumaNE));
    float subpix1 = clamp(abs(lumaAverage - lumaM) / (lumaRange + 1e-6), 0.0, 1.0);
    float subpix2 = (-2.0 * subpix1 + 3.0) * subpix1 * subpix1;
    float subpixFinal = subpix2 * subpix2 * FXAA_SUBPIX_QUALITY;

    float finalOffset = max(pixelOffset, subpixFinal);

    vec2 finalUv = v_uv;
    if(isHorizontal){
        finalUv.y += finalOffset * stepLength;
    }else{
        finalUv.x += finalOffset * stepLength;
    }

    gl_FragColor = texture2D(u_texture, finalUv) * v_col;
}
