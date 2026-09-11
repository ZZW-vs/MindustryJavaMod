package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Lightning;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;

/**
 * anomaly 充能激光 (PU132 AnomalyLaserBulletType 移植版)
 *
 * <p>由 EnergyChargeWeapon 发射, 伤害随充能提升 (charge = b.damage - damage)。</p>
 *
 * <p><b>核心机制 (逐步解释):</b>
 * <br>1. init(b): 根据充能 charge 计算 fdata (激光长度)、d (即时伤害)、
 *     size (激光粗细)、lighLength/lighAmount (命中时的闪电规模);
 * <br>2. 激光沿 b.rotation() 方向一次性打击线路上的敌人 (PU132 在 init 即完成碰撞),
 *     对建筑使用 {@link Vars#indexer} 扫描, 对单位用线段距离判定;
 * <br>3. 若命中吸收激光的建筑, 截断 fdata (激光显示长度缩短到命中处);
 * <br>4. 命中末端播放 hitEffect 并生成链状闪电 (Lightning.create)。</p>
 *
 * <p><b>★ v155 适配:</b> PU132 用 Utils.collideLineRawEnemyRatio (带逐块穿行比例衰减),
 * 这里改用 v158 {@link Vars#indexer} + {@link mindustry.entities.Units#nearbyEnemies}
 * 的简化直线判定 (参考 AcceleratingLaserBulletType)。</p>
 */
public class AnomalyLaserBulletType extends BulletType{
    /** 基础激光长度 */
    float length = 250f;
    /** 每层光的长度衰减系数 (仅影响绘制厚度) */
    float lengthFalloff = 0.6f;
    /** 侧向三角羽刺的长度 (未充能时) */
    float sideLength = 29f, sideWidth = 0.7f;
    /** 侧向羽刺相对激光的偏角 */
    float sideAngle = 90f;

    /** 绘制时的多层颜色: 外圈到内圈到白核 */
    Color[] colors = {Pal.lancerLaser.cpy().mul(0.9f).a(0.3f), Pal.lancerLaser, Color.white};

    /**
     * @param damage 基础伤害 (充能越高伤害越高, 见 {@link #init(Bullet)})
     */
    public AnomalyLaserBulletType(float damage){
        super(0f, damage); // 速度 0: 激光原地一次成型
        despawnEffect = Fx.none;
        hitEffect = Fx.lancerLaserShoot;
        keepVelocity = false;
        lifetime = 20f;
    }

    /**
     * v155 适配: range 是字段而非方法, 在 init() 中赋值。
     */
    @Override
    public void init(){
        super.init();
        range = length;
        drawSize = (length + 250f * 1.5f) * 2f;
    }

    /**
     * 子弹生成时: 计算充能激光参数并一次性打击线路上的敌人 (PU132 原版)。
     */
    @Override
    public void init(Bullet b){
        float charge = Math.max(b.damage - damage, 0f);     // 额外充能
        b.fdata = length + (charge * 1.5f);                 // 激光实际长度
        float d = damage * ((charge / 70f) + 1f);           // 即时伤害
        float size = Mathf.sqrt(charge / 3f);               // 粗细基数

        // 命中时的闪电规模随充能提升
        int lighLength = Math.min(Mathf.round((charge - 30f) / 9f), 18),
        lighAmount = Math.min(Mathf.ceil((charge - 30f) / 60), 3);

        // 激光终点 = 起点 + 沿旋转方向的 fdata 长度
        Tmp.v1.trns(b.rotation(), b.fdata).add(b);
        checkLaserCollision(b, b.x, b.y, Tmp.v1.x, Tmp.v1.y, Math.max(size, 8f), d, lighLength, lighAmount);
    }

    /**
     * 简化版激光直线碰撞检测 (替代 PU132 collideLineRawEnemyRatio)。
     *
     * <p>逐步骤解释:
     * <br>1. 用 {@link Vars#indexer} 扫描激光可达范围内的敌方建筑, 命中吸收激光的建筑时
     *     truncate 激光长度到命中处 (终点即命中点);
     * <br>2. 用 {@link mindustry.entities.Units#nearbyEnemies} 找敌方单位,
     *     单位到激光线段的垂距 &lt; 打击宽度 + 单位碰撞半径 即算命中;
     * <br>3. 在最终命中点播放 hitEffect 并按充能生成链状闪电。</p>
     */
    private void checkLaserCollision(Bullet b, float x1, float y1, float x2, float y2, float width, float d, int lighLength, int lighAmount){
        // —— 检测建筑 ——
        Vars.indexer.eachBlock(null, x1, y1, b.fdata + 60f,
            build -> build.team != b.team,
            build -> {
                // 简化: 只当建筑中心距激光线段较近且在其蔓延区间内才判命中
                float dist = Intersector.distanceSegmentPoint(x1, y1, x2, y2, build.x, build.y);
                if(dist > width + build.block.size * Vars.tilesize / 2f) return;

                if(build.block.absorbLasers){
                    // 命中吸收激光的建筑: 截断激光长度到命中处
                    b.fdata = Math.min(b.fdata, b.dst(build) - build.block.size * Vars.tilesize / 2f);
                }
                build.damage(d * buildingDamageMultiplier);
            });

        // —— 检测单位 ——
        mindustry.entities.Units.nearbyEnemies(b.team, x1, y1, b.fdata + 60f, unit -> {
            if(!unit.hittable()) return;
            // 单位中心到激光线段的垂距
            if(Intersector.distanceSegmentPoint(x1, y1, x2, y2, unit.x, unit.y) > width + unit.hitSize / 2f) return;

            // 击退: 沿单位->子弹方向推离
            Tmp.v3.set(unit).sub(b).nor().scl(knockback * 80f);
            if(impact) Tmp.v3.setAngle(b.rotation() + (knockback < 0 ? 180f : 0f));
            unit.impulse(Tmp.v3);
            unit.apply(status, statusDuration);

            unit.damage(d);
        });

        // —— 命中点效果 + 链状闪电 (充能足够时) ——
        Tmp.v1.trns(b.rotation(), b.fdata).add(b);   // 命中末端
        hit(b, Tmp.v1.x, Tmp.v1.y);
        if(lighLength >= 5 && lighAmount > 0){
            for(int i = 0; i < lighAmount; i++){
                int len = Mathf.random(lighLength / 2, lighLength);
                Lightning.create(b.team, lightningColor, d / 5f, Tmp.v1.x, Tmp.v1.y, b.rotation() + Mathf.range(25f), len);
            }
        }
    }

    /**
     * 绘制激光 (PU132 完全保留): 多层彩色细线 + 尽头三角收口 + 侧向羽刺。
     */
    @Override
    public void draw(Bullet b){
        float realLength = b.fdata;
        float f = Mathf.curve(b.fin(), 0f, 0.2f);   // 激光随出膛时间伸展
        float charge = Math.max(b.damage - damage, 0f);
        float width = (Mathf.sqrt(charge / 3f) + 3f) * 2.5f;
        float cw = width / lengthFalloff;
        float compound = 1f;
        float baseLen = realLength * f;
        float sLength = charge / 6f;
        sLength *= sLength / 2f;
        sLength /= 5f;

        Lines.lineAngle(b.x, b.y, b.rotation(), baseLen);
        for(Color color : colors){
            Draw.color(color);
            Lines.stroke((cw *= lengthFalloff) * b.fout());
            Lines.lineAngle(b.x, b.y, b.rotation(), baseLen, false);
            // 激光尽头三角收口
            Tmp.v1.trns(b.rotation(), baseLen).add(b);
            Drawf.tri(Tmp.v1.x, Tmp.v1.y, Lines.getStroke() * 1.22f, cw * 2f + width / 2f, b.rotation());
            Fill.circle(b.x, b.y, cw * b.fout());

            // 侧向羽刺 (随充能增多)
            float offset = Math.min((charge - 40f) / 7f, 30f);

            for(int i : Mathf.signs){
                if(offset <= 0f){
                    Drawf.tri(b.x, b.y, sideWidth * b.fout() * cw, (sideLength + sLength) * compound, b.rotation() + (sideAngle * i));
                }else{
                    for(int s : Mathf.signs){
                        Drawf.tri(b.x, b.y, sideWidth * b.fout() * cw, (sideLength + sLength) * compound, b.rotation() + (sideAngle * i) + (offset * s));
                    }
                }
            }

            compound *= lengthFalloff;
        }
        Draw.reset();

        // 激光光源
        Tmp.v1.trns(b.rotation(), baseLen * 1.1f).add(b);
        Drawf.light(b.x, b.y, Tmp.v1.x, Tmp.v1.y, width * 1.7f * b.fout(), colors[0], 0.6f);
    }

    @Override
    public void drawLight(Bullet b){
        // 光照已在 draw 中处理 (PU132 原版)
    }
}