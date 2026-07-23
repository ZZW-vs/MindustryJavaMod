package zzw.content.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.Block;
import zzw.util.WavefrontObject;

/**
 * 通用 3D 模型展示方块 (伪 3D, 使用 WavefrontObject 渲染 .obj 文件)
 *
 * 机制:
 * - 加载任意 .obj 模型文件 (通过 ZObjs 配置)
 * - 模型自动旋转 (绕 Z 轴或 Y 轴)
 * - 支持底座贴图 + 阴影
 * - 支持缩放和颜色配置
 *
 * 用途:
 * - 装饰性方块, 展示 3D 模型
 * - 可用于展示机械零件 (飞轮/齿轮/阀门等)
 *
 * 配置:
 * - object: WavefrontObject 实例 (由 ZObjs 加载)
 * - rotateSpeed: 旋转速度 (度/帧), 0=静止
 * - rotateAxis: 旋转轴 ('Z' 或 'Y'), 默认 'Z' (俯视旋转)
 * - modelScale: 模型缩放 (覆盖 WavefrontObject.size)
 * - baseOffset: 模型 Y 偏移 (向上偏移, 避免穿底座)
 * - spinSpeed: 摇摆速度 (可设0关闭)
 *
 * 参考: ObjPowerTurret.java (cube 炮台的 3D 渲染逻辑)
 */
public class ObjDisplayBlock extends Block {
    public WavefrontObject object;
    /** 旋转速度 (度/帧), 正=顺时针, 0=静止 */
    public float rotateSpeed = 1f;
    /** 旋转轴: 'Z' (俯视旋转, 默认) 或 'Y' (侧视翻转) */
    public char rotateAxis = 'Z';
    /** 模型 Y 偏移 (向上偏移, 避免穿底座), 单位: 像素 */
    public float baseOffset = 0f;
    /** 初始旋转角度随机范围 (度) */
    public float randomRotation = 360f;
    /** 是否绘制阴影 */
    public boolean drawShadow = true;
    /** 阴影半径倍数 (相对 size*tilesize) */
    public float shadowScale = 1f;

    public TextureRegion baseRegion;

    public ObjDisplayBlock(String name) {
        super(name);
        update = false;
        solid = true;
        destructible = true;
        allowDiagonal = true;
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
    }

    @Override
    public TextureRegion[] icons() {
        return baseRegion.found()
            ? new TextureRegion[]{baseRegion}
            : new TextureRegion[]{region};
    }

    public class ObjDisplayBuild extends Building {
        /** 当前旋转角度 (度) */
        float angle = Mathf.random(randomRotation);
        /** 摇摆相位 */
        float wobble = Mathf.random(360f);

        @Override
        public void updateTile() {
            // 自动旋转
            angle += rotateSpeed * Time.delta;
        }

        @Override
        public void draw() {
            // 1. 底座
            if (baseRegion.found()) {
                Draw.rect(baseRegion, x, y);
            } else if (region.found()) {
                Draw.rect(region, x, y);
            }
            Draw.color();

            // 2. 阴影
            if (drawShadow) {
                Draw.z(Layer.blockBuilding - 1f);
                Drawf.shadow(x, y, size * 12f * shadowScale);
            }

            // 3. 3D 模型
            if (object != null && object.faces != null && object.faces.size > 0) {
                float rX, rY, rZ;
                if (rotateAxis == 'Y') {
                    // 绕 Y 轴旋转 (侧视翻转, 飞轮效果)
                    rX = 0f;
                    rY = angle;
                    rZ = 0f;
                } else {
                    // 绕 Z 轴旋转 (俯视旋转, 默认)
                    // ★ Vec3.mul(Mat) 行向量乘矩阵, rotate(Vec3.Z, +deg) = 顺时针
                    rX = 0f;
                    rY = 0f;
                    rZ = angle;
                }

                // 轻微 X 轴倾斜, 增加立体感 (模拟俯视角度)
                rX += -25f;

                // ★ 多实例 Z 轴偏移: 基于实例 id, 避免不同方块的 face 在 batch 中穿插
                // 0.1f 的间距确保 batch z 排序能区分不同实例
                object.zOffset = (id % 100) * 0.1f;
                object.draw(x, y + baseOffset, rX, rY, rZ);
                object.zOffset = 0f;  // 重置
            }
        }
    }
}
