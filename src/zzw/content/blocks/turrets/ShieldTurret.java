package zzw.content.blocks.turrets;

import arc.math.Mathf;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import zzw.content.Z_Bullets.ShieldBulletType;

/**
 * 护盾炮台 (PU_V8 ShieldTurret 移植版)
 * 瞄准友方受损建筑发射护盾子弹, 在目标周围生成护盾抵挡敌方子弹
 * 简化: 移除 baseRegion 自定义查找 (v158 用 @Load 注解自动加载)
 * 参考: PU_V8 main/src/unity/world/blocks/defense/turrets/ShieldTurret.java
 */
public class ShieldTurret extends PowerTurret {

    public ShieldTurret(String name) {
        super(name);
    }

    public class ShieldTurretBuild extends PowerTurretBuild {
        public boolean shield;

        @Override
        public void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, mindustry.entities.Mover mover) {
            // 根据距离调整子弹速度 (PU_V8 原版逻辑)
            float spdScl = Mathf.clamp(Mathf.dst(x, y, targetPos.x, targetPos.y) / range, 0, 1);
            float
                bulletX = x + arc.math.Angles.trnsx(rotation - 90, shootX + xOffset, shootY + yOffset),
                bulletY = y + arc.math.Angles.trnsy(rotation - 90, shootX + xOffset, shootY + yOffset),
                shootAngle = rotation + angleOffset;
            handleBullet(type.create(this, team, bulletX, bulletY, shootAngle, spdScl, 1f), xOffset, yOffset, shootAngle - rotation);
        }

        @Override
        protected void findTarget() {
            this.target = Units.findAllyTile(team, x, y, range, e -> targetShield(e) && e != this);
        }

        @Override
        public boolean validateTarget() {
            return this.target != null || isControlled() || logicControlled();
        }

        public boolean targetShield(Building t) {
            shield = false;
            Groups.bullet.intersect(t.x - 10f, t.y - 10f, 20f, 20f, e -> {
                if (e != null && e.team == team && e.type instanceof ShieldBulletType) {
                    shield = true;
                }
            });
            // 已有护盾则不再覆盖; 仅当建筑受损且无护盾时锁定
            return t.damaged() && !shield;
        }
    }
}
