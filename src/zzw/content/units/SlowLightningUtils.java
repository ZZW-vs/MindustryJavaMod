package zzw.content.units;

import arc.func.Boolf;
import arc.func.Cons;
import arc.math.geom.Vec2;
import arc.math.geom.Intersector;
import arc.math.geom.Position;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.gen.Posc;
import mindustry.world.Tile;

/**
 * SlowLightning 专用工具方法 (替代 PU132 Utils.findLaserLength / collideLineRawEnemy)
 */
public class SlowLightningUtils {

    /**
     * 查找激光实际长度 (遇到 absorbLasers 的 tile 截断)
     * 替代 PU132 Utils.findLaserLength
     */
    public static float findLaserLength(float x1, float y1, float x2, float y2, Boolf<Tile> checker) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return len;
        // 沿线段每隔 tilesize 步进检测
        float step = Vars.tilesize * 0.5f;
        int steps = (int) Math.ceil(len / step);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float wx = x1 + dx * t, wy = y1 + dy * t;
            Tile tile = Vars.world.tileWorld(wx, wy);
            if (tile != null && checker.get(tile)) {
                // 返回截断长度
                return len * t;
            }
        }
        return len;
    }

    /**
     * 线段碰撞检测 (敌方单位/建筑)
     * 替代 PU132 Utils.collideLineRawEnemy
     * 简化: 用 v150.3 的 Units.nearbyEnemies + Intersector 替代
     */
    public static void collideLineRawEnemy(
            Team team, float x1, float y1, float x2, float y2, float width,
            Cons<Building> buildingCons, Cons<Unit> unitCons,
            Posc target, FloatFloatCons hitCons, boolean direct) {

        float len = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (len < 1f) return;

        // 检测单位
        mindustry.entities.Units.nearbyEnemies(team, x1, y1, len + 50f, unit -> {
            if (!unit.hittable()) return;
            if (unit.checkTarget(true, true)) return;
            if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, unit.x, unit.y) > width + unit.hitSize / 2f) return;
            unitCons.get(unit);
            hitCons.get(unit.x, unit.y);
        });

        // 检测建筑
        for (mindustry.game.Teams.TeamData data : Vars.state.teams.present) {
            if (data.team == team || data.buildings == null) continue;
            for (Building b : data.buildings) {
                if (b.team == team || b.health <= 0) continue;
                if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, b.x, b.y) > width + b.hitSize() / 2f) continue;
                buildingCons.get(b);
                hitCons.get(b.x, b.y);
            }
        }
    }

    /** Float-Float 消费者接口 (兼容 PU132 签名) */
    public interface FloatFloatCons {
        void get(float x, float y);
    }
}
