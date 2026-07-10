package zzw.content.units;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.Groups;
import mindustry.gen.UnitEntity;

/**
 * 隐形单位基类 (移植自 PU132 EndInvisibleUnit, 简化版)
 * - 血量高于 50% 时隐身 (透明)
 * - 受到攻击时短暂现身
 * - 简化: 隐身仅做 alpha 渐变, 不移除 Groups.unit (v158 物理引擎会出问题)
 */
public class EndInvisibleUnit extends UnitEntity {
    private final AntiCheatBase antiCheat = new AntiCheatBase();
    private float disabledTime = 0f;
    private float invFrame = 0f;
    private float alphaLerp = 0f;
    private float scanInterval = 0f;
    private float lastHealth = 0f;

    public boolean isInvisible = false;

    @Override
    public void setType(mindustry.type.UnitType type) {
        super.setType(type);
        antiCheat.lastHealth = type.health;
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        lastHealth = health;
    }

    @Override
    public void update() {
        if (health < lastHealth) health = lastHealth;
        lastHealth = health;

        super.update();

        invFrame += Time.delta;
        disabledTime = Math.max(disabledTime - Time.delta, 0f);
        antiCheat.updateAntiCheat();

        // 扫描附近敌人/建筑, 距离过近则现身
        scanInterval += Time.delta;
        if (scanInterval >= 30f) {
            scanInterval = 0f;
            float size = hitSize() * 3f;
            final boolean[] near = {false};
            Groups.unit.intersect(x - size, y - size, size * 2f, size * 2f, u -> {
                if (u.team() != team() && Mathf.within(x, y, u.x, u.y, hitSize() * 3f)) {
                    near[0] = true;
                }
            });
            if (near[0]) {
                disabledTime = 1.2f * 60f;
            }
        }

        // 隐身: 血量高 + 不在攻击 + 没有 disabled
        if (!isShooting() && health > maxHealth / 2f && disabledTime <= 0f) {
            alphaLerp = Mathf.lerpDelta(alphaLerp, 1f, 0.1f);
        } else {
            alphaLerp = Mathf.lerpDelta(alphaLerp, 0f, 0.1f);
        }
        isInvisible = alphaLerp > 0.5f;
    }

    @Override
    public void damage(float amount) {
        if (invFrame < 15f) return;
        float trueDamage = antiCheat.applyAntiCheatDamage(amount);
        if (trueDamage <= 0f) return;
        disabledTime = Math.max(1.4f * 60f, trueDamage / 25f);
        invFrame = 0f;
        super.damage(trueDamage);
    }

    @Override
    public void destroy() {
        if (antiCheat.lastHealth > 0f) {
            antiCheat.immunity += 3500f;
            return;
        }
        super.destroy();
    }

    @Override
    public void kill() {
        if (antiCheat.lastHealth > 0f) {
            antiCheat.immunity += 3500f;
            return;
        }
        super.kill();
    }

    @Override
    public void remove() {
        if (antiCheat.lastHealth > 0f) {
            antiCheat.immunity += 3500f;
            return;
        }
        super.remove();
    }

    public float getAlphaLerp() {
        return alphaLerp;
    }
}
