package zzw.content.blocks.turrets;

import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.draw.DrawTurret;
import mindustry.world.meta.Stat;
import zzw.util.WavefrontObject;

/**
 * PU132 ObjPowerTurret 移植版 (cube 炮台) - v155.4 适配
 *
 * 机制:
 * - 使用 WavefrontObject (.obj 文件) 渲染伪 3D 立方体
 * - 受击时产生形变效果 (distortionTime)
 * - 旋转动画随 reload 进度变化
 * - 持续光束攻击: 自动锁定范围内目标，最多同时连接12条光束
 *   光束持续连接目标直到目标死亡或离开范围，每条伤害480，频率为激光的1.8倍
 *
 * v155.4 适配:
 * - reloadTime → reload (Block 字段)
 * - efficiency() → efficiency (字段而非方法)
 * - baseRegion 需自行声明
 *
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/ObjPowerTurret.java
 */
public class ObjPowerTurret extends PowerTurret {
    public WavefrontObject object;

    public ObjPowerTurret(String name) {
        super(name);
    }

    public TextureRegion baseRegion;
    public float width = 2f, height = 7f;

    // 光束攻击参数
    public int maxBeams = 12;           // 最多同时连接的光束数量
    public float beamDamage = 450f;    // 每次光束攻击的伤害
    public float beamRange = 800f;     // 光束攻击范围
    public float beamWidth = 5f;       // 光束宽度
    public Color beamColor = Color.valueOf("4a7a9e");  // 光束颜色 - 暗蓝色

    @Override
    public void load() {
        super.load();
        baseRegion = region;
        if (drawer instanceof DrawTurret dt) {
            dt.base = region;
        }
    }

    @Override
    public void setStats() {
        super.setStats();
        // 光束攻击面板信息
        stats.add(Stat.abilities, "[accent]持续光束攻击[]");
        stats.add(Stat.abilities, "[lightgray]光束上限: [accent]" + maxBeams + " 条");
        stats.add(Stat.abilities, "[lightgray]光束伤害: [accent]" + (int)beamDamage);
        stats.add(Stat.abilities, "[lightgray]光束范围: [accent]" + (int)beamRange);
        stats.add(Stat.abilities, "[lightgray]攻击频率: [accent]" + String.format("%.1f", 60f * 5.4f / reload) + " 次/秒");
    }

    public class ObjPowerTurretBuild extends PowerTurretBuild {
        float time = Mathf.random(0f, 360f * Mathf.degRad);
        float distortionTime = 0f;

        // 光束攻击相关
        float beamTimer = 0f;                        // 光束伤害计时器
        Seq<Teamc> beamTargets = new Seq<>();        // 当前持续连接的光束目标列表

        // 蓄力抖动: 根据reloadCounter进度计算抖动幅度，蓄力越满抖动越厉害
        protected float getChargeShake() {
            float chargeProgress = Mathf.clamp(reloadCounter / reload);  // 0~1
            float baseShake = 0f;  // 基础无抖动
            float chargeShake = chargeProgress * 0.2f;  // 蓄力满时最大抖动
            // 受击时额外抖动
            float hitShake = distortionTime * 0.15f;
            return baseShake + chargeShake + hitShake;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            if (Float.isNaN(time)) time = Mathf.random(0f, 360f * Mathf.degRad);

            // 立方体旋转速度随 reload 进度变化
            float speed = efficiency * (1f + ((reloadCounter * 2.5f) / reload));
            if (speed < 0.1f) speed = 0.1f;
            time += speed * Time.delta;
            distortionTime = Math.max(0f, distortionTime - (Time.delta * 0.2f));

            // 光束攻击逻辑 - 持续连接模式
            if (efficiency > 0) {
                // 光束伤害频率: 激光攻击速度的1.8倍 (reload / 5.4)
                float beamReload = reload / 5.4f;
                beamTimer += Time.delta;

                // 1. 清理无效目标（死亡或离开范围）
                for (int i = beamTargets.size - 1; i >= 0; i--) {
                    Teamc t = beamTargets.get(i);
                    boolean invalid = false;
                    if (t == null) {
                        invalid = true;
                    } else if (t instanceof Unit u) {
                        if (u.dead || !u.isValid() || !u.within(x, y, beamRange)) {
                            invalid = true;
                        }
                    } else if (t instanceof mindustry.gen.Building b) {
                        if (!b.isValid() || !b.within(x, y, beamRange)) {
                            invalid = true;
                        }
                    }
                    if (invalid) {
                        beamTargets.remove(i);
                    }
                }

                // 2. 查找新目标填满光束上限
                if (beamTargets.size < maxBeams) {
                    Units.nearbyEnemies(team, x, y, beamRange, u -> {
                        if (u.isValid() && !u.dead && beamTargets.size < maxBeams) {
                            // 检查是否已经锁定
                            boolean already = false;
                            for (Teamc t : beamTargets) {
                                if (t == u) { already = true; break; }
                            }
                            if (!already) {
                                beamTargets.add(u);
                            }
                        }
                    });
                }

                // 3. 按频率对锁定目标造成伤害
                if (beamTimer >= beamReload) {
                    beamTimer = 0f;
                    for (Teamc t : beamTargets) {
                        if (t instanceof Unit u && !u.dead && u.isValid()) {
                            u.damage(beamDamage);
                        }
                    }
                }
            } else {
                beamTargets.clear();
            }
        }

        @Override
        public void damage(float damage) {
            distortionTime = Mathf.clamp(Mathf.sqrt(Math.max(0f, damage / 20f)), 0f, 3f);
            super.damage(damage);
        }

        protected float getDistortion() {
            return ((Mathf.clamp(1f - (healthf() * 2f)) * 2f) + distortionTime) / 16f;
        }

        @Override
        public void draw() {
            Draw.rect(baseRegion, x, y);
            Draw.color();

            // 模型抖动特效: 持续抖动，幅度随蓄力进度变化
            float shake = getChargeShake();
            Cons<Vec3> distort = v -> {
                v.add(Mathf.range(shake), Mathf.range(shake), Mathf.range(shake));
            };

            object.draw(x, y, Mathf.cos(time, 76f, 120f), Mathf.sin(time, 76f, 120f), rotation, distort);

            // 绘制持续光束 - 从炮台到每个目标
            for (Teamc t : beamTargets) {
                if (t == null) continue;

                float tx = t.getX();
                float ty = t.getY();

                // 光束外层（半透明光晕）
                Draw.color(beamColor.cpy().a(0.3f));
                Lines.stroke(beamWidth * 2f);
                Lines.line(x, y, tx, ty);

                // 光束中层
                Draw.color(beamColor.cpy().a(0.6f));
                Lines.stroke(beamWidth * 1.2f);
                Lines.line(x, y, tx, ty);

                // 光束核心（白色高亮）
                Draw.color(Color.white.cpy().a(0.9f));
                Lines.stroke(beamWidth * 0.5f);
                Lines.line(x, y, tx, ty);

                // 击中点光晕
                Draw.color(beamColor.cpy().a(0.5f));
                Fill.circle(tx, ty, beamWidth * 1.5f);
                Draw.color(Color.white.cpy().a(0.7f));
                Fill.circle(tx, ty, beamWidth * 0.5f);
            }

            Draw.color();
            Draw.reset();
        }
    }
}
