package zzw.content.units;

import mindustry.gen.UnitEntity;

/**
 * 腿单位防作弊基类 (移植自 PU132 EndLegsUnit, 简化版)
 * - 集成 AntiCheatBase
 * - 重写 damage() 实现单次伤害上限
 * - 重写 kill()/destroy()/remove() 实现死亡拒绝
 */
public class EndLegsUnit extends UnitEntity {
    private final AntiCheatBase antiCheat = new AntiCheatBase();
    // private float lastMaxHealth;

    @Override
    public void setType(mindustry.type.UnitType type) {
        super.setType(type);
        antiCheat.lastHealth = type.health;
        // lastMaxHealth = type.health;
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        antiCheat.lastHealth = this.health;
        antiCheat.lastMaxHealth = this.maxHealth;
    }

    @Override
    public void update() {
        // 血量保护
        if (antiCheat.lastHealth > this.health) this.health = antiCheat.lastHealth;
        if (antiCheat.lastMaxHealth > this.maxHealth) this.maxHealth = antiCheat.lastMaxHealth;
        if (antiCheat.lastHealth > 0f) this.dead = false;
        antiCheat.lastHealth = this.health;
        antiCheat.lastMaxHealth = this.maxHealth;

        super.update();

        antiCheat.updateAntiCheat();
    }

    @Override
    public void damage(float amount) {
        float trueDamage = antiCheat.applyAntiCheatDamage(amount);
        if (trueDamage <= 0f) return;
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
}
