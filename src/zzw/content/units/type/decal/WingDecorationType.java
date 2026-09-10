package zzw.content.units.type.decal;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import zzw.content.units.util.UnityUtils;

/**
 * 翅膀装饰类型 (PU132 unity.type.decal.WingDecorationType 完整移植)
 *
 * <p>实现翅膀扇动动画的核心算法 (逐步解释):
 * <br>1. 单位移动/转向时累计左右翅膀的"行程" (wd.left / wd.right);
 * <br>2. 行程除以 flapScl (扇动周期长度) 得到动画进度, 加上每片翅膀的 offset 相位差;
 * <br>3. slope() 用两个插值函数 (flapAnimation + flapInterp) 把进度映射为 0~1 扇动量;
 * <br>4. 扇动量乘以翅膀幅度 mag 得到旋转角, 左右翅膀独立驱动 (转向时一侧快一侧慢);
 * <br>5. 静止时左右翅膀缓慢回归同一相位 (lerpDelta 0.08), 避免僵死在同一姿势。</p>
 *
 * <p>★ 贴图名说明: 本 mod 的贴图在 atlas 中自动带 "create-" 前缀,
 * load() 时优先查找带前缀版本, 找不到再退回原名 (并打印警告方便调试)。</p>
 */
public class WingDecorationType extends UnitDecorationType{
    /** 扇动一个完整周期的行程长度 (像素) */
    public float flapScl = 120f;
    /** 扇动动画插值 (行程→进度), flapInterp 把进度映射为扇动量 */
    public Interp flapAnimation = Interp.pow3Out, flapInterp = Interp.sine;
    /** 每片翅膀的贴图 (load 时填充) */
    public TextureRegion[] textures;
    /** 贴图名前缀 (不含 mod 前缀) */
    public String name;
    /** 翅膀列表 */
    public Seq<Wing> wings = new Seq<>();
    /** 贴图变体数 */
    public int textureVariants;

    /**
     * @param name 贴图名前缀 (如 "deviation-wing", 会自动尝试加 "create-" 前缀)
     * @param variants 贴图变体数量
     */
    public WingDecorationType(String name, int variants){
        decalType = WingDecoration::new;
        this.name = name;
        textureVariants = variants;
    }

    /**
     * 加载翅膀贴图 (PU132 原版 + create- 前缀适配 + 调试打印)。
     *
     * <p>★ 调试: 逐张打印贴图加载结果, 未找到时打 [wing-warn] 警告。</p>
     */
    @Override
    public void load(){
        textures = new TextureRegion[textureVariants];
        for(int i = 0; i < textureVariants; i++){
            // 优先找带 mod 前缀的贴图 (本 mod 贴图打包时自动加 "create-")
            String texName = "create-" + name + "-" + i;
            if(!Core.atlas.has(texName)){
                // 退回不带前缀的原名 (兼容直接放原版 atlas 的情况)
                String alt = name + "-" + i;
                if(Core.atlas.has(alt)){
                    texName = alt;
                }else{
                    Log.warn("[wing-warn] 翅膀贴图未找到: create-@ / @ (将显示为错误贴图)", texName, alt);
                }
            }
            textures[i] = Core.atlas.find(texName);
            Log.info("[wing-debug] 加载翅膀贴图 @ -> @ (@x@)", texName, textures[i].found() ? "成功" : "失败!", textures[i].width, textures[i].height);
        }
        Log.info("[wing-debug] WingDecorationType '@' 加载完成, 翅膀数量: @", name, wings.size);
    }

    /**
     * 绘制翅膀 (PU132 原版逻辑)。
     *
     * <p>每片翅膀左右镜像绘制一次 (s = -1 / +1),
     * 位置由翅膀挂点 (x, y) 沿单位旋转坐标变换得到。</p>
     */
    @Override
    public void draw(Unit unit, UnitDecoration deco){
        WingDecoration wd = (WingDecoration)deco;
        unit.type.applyColor(unit);
        for(Wing wing : wings){
            // 左右翅膀各自的扇动角 (见类注释算法步骤 2~4)
            float l = slope(((wd.left / flapScl) + wing.offset) % 1f) * -wing.mag;
            float r = slope(((wd.right / flapScl) + wing.offset) % 1f) * -wing.mag;

            // s = -1 左翼, s = +1 右翼, 镜像绘制
            for(int s : Mathf.signs){
                float side = s > 0 ? r : l,
                rotation = unit.rotation - 90f,
                x = Angles.trnsx(rotation, wing.x * s, wing.y) + unit.x,
                y = Angles.trnsy(rotation, wing.x * s, wing.y) + unit.y,
                wingAngle = rotation + (side * s);
                TextureRegion region = textures[wing.textureIndex];

                Draw.rect(region, x, y, region.width * s * Draw.scl, region.height * Draw.scl, wingAngle);
            }
        }
        Draw.reset();
    }

    /**
     * 把翅膀贴图合成进单位图标 (PU132 原版逻辑)。
     */
    @Override
    public void drawIcon(Func<TextureRegion, Pixmap> prov, Pixmap icon, Func<TextureRegion, TextureRegion> outliner){
        for(Wing w : wings){
            TextureRegion region = outliner.get(textures[w.textureIndex]);

            float scl = Draw.scl / 4f;
            Pixmap pix = prov.get(region);

            icon.draw(pix,
            (int)(w.x / scl + icon.width / 2f - pix.width / 2f),
            (int)(-w.y / scl + icon.height / 2f - pix.height / 2f),
            true);

            icon.draw(pix.flipX(),
            (int)(-w.x / scl + icon.width / 2f - pix.width / 2f),
            (int)(-w.y / scl + icon.height / 2f - pix.height / 2f),
            true);
        }
    }

    /**
     * 行程进度 → 扇动量 (PU132 原版)。
     *
     * <p>flapAnimation 先把进度塑形, 减去中点取绝对值再乘 2 得到往返三角波,
     * 最后 flapInterp 平滑输出 0~1。</p>
     */
    float slope(float in){
        return flapInterp.apply((0.5f - Math.abs(flapAnimation.apply(in) - 0.5f)) * 2f);
    }

    /**
     * 每帧更新翅膀动画状态 (PU132 原版逻辑)。
     */
    @Override
    public void update(Unit unit, UnitDecoration deco){
        WingDecoration wd = (WingDecoration)deco;
        // 移动时左右翅膀同时累计行程
        if(unit.moving()){
            float len = unit.deltaLen();
            wd.left += len;
            wd.right += len;
        }
        // 转向时朝旋转方向的那侧翅膀行程加快 (模拟真实扑翼)
        float angDst = UnityUtils.angleDistSigned(unit.rotation, wd.lastRot);
        if(Math.abs(angDst) > 0.0001f){
            if(angDst > 0){
                wd.right += angDst;
            }else{
                wd.left += -angDst;
            }
        }else{
            // 静止时左右翅膀缓慢同步到相同相位
            float mid = Math.max(wd.left % flapScl, wd.right % flapScl);
            wd.left = Mathf.lerpDelta(wd.left, Mathf.round(wd.left, flapScl) + mid, 0.08f);
            wd.right = Mathf.lerpDelta(wd.right, Mathf.round(wd.right, flapScl) + mid, 0.08f);
        }
        wd.lastRot = unit.rotation;
    }

    /**
     * 单位添加时初始化翅膀朝向 (PU132 原版)。
     */
    @Override
    public void added(Unit unit, UnitDecoration deco){
        WingDecoration wd = (WingDecoration)deco;
        wd.lastRot = unit.rotation;
    }

    /**
     * 单片翅膀配置 (PU132 原版)
     */
    public static class Wing{
        /** 动画相位偏移 (0~1, 让多片翅膀错开扇动) */
        float offset, mag, x, y;
        /** 使用的贴图索引 (textures[textureIndex]) */
        int textureIndex;

        /**
         * @param idx 贴图索引
         * @param x 挂点 x (相对单位中心, 单位坐标系)
         * @param y 挂点 y
         * @param offset 相位偏移
         * @param mag 扇动幅度 (度)
         */
        public Wing(int idx, float x, float y, float offset, float mag){
            textureIndex = idx;
            this.x = x;
            this.y = y;
            this.offset = offset;
            this.mag = mag;
        }
    }

    /**
     * 翅膀装饰状态 (PU132 原版)
     */
    public static class WingDecoration extends UnitDecoration{
        /** 左右翅膀累计行程 (驱动扇动动画) */
        float left = 0f, right = 0f, lastRot;

        /**
         * @param type 对应的翅膀装饰类型
         */
        public WingDecoration(UnitDecorationType type){
            super(type);
        }
    }
}
