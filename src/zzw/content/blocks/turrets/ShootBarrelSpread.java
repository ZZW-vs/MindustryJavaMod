package zzw.content.blocks.turrets;

import arc.util.Nullable;
import mindustry.entities.pattern.ShootPattern;

/**
 * 多管散射射击模式。
 *
 * <p>每次开火时, {@link #barrels} 根炮管各射出 {@link #barrelShots} 发子弹;
 * 同一根炮管的子弹以 {@link #spread} 度为间隔扇形展开, 不同炮管以 {@link #barrelSpacing}
 * 横向错位, 使子弹分别从各自炮口发出 (对应 {@code handler.shoot()} 的 xOffset / angleOffset)。</p>
 *
 * <p>示例: {@code new ShootBarrelSpread(4, 6f, 3, 20f)} = 4 根炮管, 每根 3 发间隔 20° 的散射,
 * 一次开火共 12 发。</p>
 *
 * <p>注意: 父类字段 {@link ShootPattern#shots} 表示"一次开火的总弹数", 被详情面板的
 * 射速 / DPS 估算使用, 因此这里在构造时自动设为 {@code barrels * barrelShots}。
 * 若之后手动改动 barrels / barrelShots, 请同步维护 shots。</p>
 */
public class ShootBarrelSpread extends ShootPattern{
    /** 炮管数量 */
    public int barrels = 2;
    /** 每根炮管每次开火的子弹数 */
    public int barrelShots = 1;
    /** 相邻炮管之间的横向间距 (世界单位) */
    public float barrelSpacing = 6f;
    /** 炮口前向偏移 (世界单位, 0 表示使用炮台默认 shootY) */
    public float barrelY = 0f;
    /** 每根炮管内相邻子弹的角度间隔 (度) */
    public float spread = 20f;

    public ShootBarrelSpread(int barrels, float barrelSpacing, int barrelShots, float spread){
        this.barrels = barrels;
        this.barrelSpacing = barrelSpacing;
        this.barrelShots = barrelShots;
        this.spread = spread;
        this.shots = barrels * barrelShots;
    }

    public ShootBarrelSpread(){}

    @Override
    public void shoot(int totalShots, BulletHandler handler, @Nullable Runnable barrelIncrementer){
        for(int b = 0; b < barrels; b++){
            // 炮管以中心对称分布
            float xOffset = (b - (barrels - 1) / 2f) * barrelSpacing;
            for(int i = 0; i < barrelShots; i++){
                // 每根炮管的子弹扇形展开, 角度以中心对称
                float angleOffset = i * spread - (barrelShots - 1) * spread / 2f;
                handler.shoot(xOffset, barrelY, angleOffset, firstShotDelay + shotDelay * i);
                if(barrelIncrementer != null) barrelIncrementer.run();
            }
        }
    }
}
