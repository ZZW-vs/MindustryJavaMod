package zzw.content.units.types;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import zzw.content.units.entities.CopterUnitEntity;
import zzw.content.units.rotor.Rotor;
import zzw.content.units.rotor.RotorMount;

/**
 * 直升机单位类型 (移植自 PU_V8 UnityUnitType 的旋翼部分)
 *
 * 替代 PU_V8 UnityUnitType + Copterc 接口,
 * 直接继承 vanilla UnitType 并添加 rotors 序列和 drawRotors() 方法.
 *
 * 使用方式:
 *   CopterUnitType type = new CopterUnitType("caelifera") {{
 *       rotors.add(new Rotor(name + "-rotor") {{
 *           x = 0f; y = 6f;
 *       }});
 *       ...
 *   }};
 *
 * 在 init() 中会自动处理 mirror 旋翼 (复制并镜像 x/speed/shadeSpeed/rotOffset),
 * 在 load() 中加载所有旋翼贴图, 在 draw() 中调用 drawRotors() 渲染旋翼.
 *
 * 单位实体必须是 CopterUnitEntity (通过 constructor 指定).
 */
public class CopterUnitType extends UnitType {
    public final Seq<Rotor> rotors = new Seq<>(4);
    public float rotorDeathSlowdown = 0.01f;
    public float fallRotateSpeed = 2.5f;

    public CopterUnitType(String name) {
        super(name);
        outlines = false;
    }

    @Override
    public void load() {
        super.load();
        rotors.each(Rotor::load);
    }

    @Override
    public void init() {
        super.init();

        // 处理 mirror 旋翼: 复制一份并镜像 x/speed/shadeSpeed/rotOffset (PU_V8 UnityUnitType.init L230-242)
        Seq<Rotor> mapped = new Seq<>();
        rotors.each(rotor -> {
            mapped.add(rotor);
            if (rotor.mirror) {
                Rotor copy = rotor.copy();
                copy.x *= -1f;
                copy.speed *= -1f;
                copy.shadeSpeed *= -1f;
                copy.rotOffset += 360f / (copy.bladeCount * 2);
                mapped.add(copy);
            }
        });
        rotors.set(mapped);
    }

    @Override
    public Unit create(Team team) {
        // rotors 在 CopterUnitEntity.add() 中初始化, 无需在此重复
        return super.create(team);
    }

    @Override
    public void draw(Unit unit) {
        super.draw(unit);
        // 渲染旋翼 (PU_V8 UnityUnitType.draw L396-399)
        if (unit instanceof CopterUnitEntity copter) {
            drawRotors(copter);
        }
    }

    /**
     * 渲染旋翼 (移植自 PU_V8 UnityUnitType.drawRotors L751-800)
     */
    protected void drawRotors(CopterUnitEntity unit) {
        applyColor(unit);

        RotorMount[] rotors = unit.rotors;
        for (RotorMount mount : rotors) {
            Rotor rotor = mount.rotor;
            float x = unit.x + Angles.trnsx(unit.rotation - 90f, rotor.x, rotor.y);
            float y = unit.y + Angles.trnsy(unit.rotation - 90f, rotor.x, rotor.y);

            float alpha = Mathf.curve(unit.rotorSpeedScl, 0.2f, 1f);
            Draw.color(0f, 0f, 0f, rotor.shadowAlpha);
            float size = Math.max(rotor.bladeRegion.width, rotor.bladeRegion.height) * Draw.scl;

            if (softShadowRegion.found()) {
                Draw.rect(softShadowRegion, x, y, size * 1.2f * Draw.xscl, size * 1.2f * Draw.yscl);
            }

            Draw.color();
            Draw.alpha(alpha * rotor.ghostAlpha);
            if (rotor.bladeGhostRegion.found()) {
                Draw.rect(rotor.bladeGhostRegion, x, y, mount.rotorRot);
            }
            if (rotor.bladeShadeRegion.found()) {
                Draw.rect(rotor.bladeShadeRegion, x, y, mount.rotorShadeRot);
            }

            Draw.alpha(1f - alpha * rotor.bladeFade);
            for (int j = 0; j < rotor.bladeCount; j++) {
                if (rotor.bladeOutlineRegion.found()) {
                    Draw.rect(rotor.bladeOutlineRegion, x, y, (unit.rotation + (
                        unit.id * 24f + mount.rotorRot +
                        (360f / rotor.bladeCount) * j
                    )) % 360);
                }
            }
        }

        for (RotorMount mount : rotors) {
            Rotor rotor = mount.rotor;
            float x = unit.x + Angles.trnsx(unit.rotation - 90f, rotor.x, rotor.y);
            float y = unit.y + Angles.trnsy(unit.rotation - 90f, rotor.x, rotor.y);

            Draw.alpha(1f - Mathf.curve(unit.rotorSpeedScl, 0.2f, 1f) * rotor.bladeFade);
            for (int j = 0; j < rotor.bladeCount; j++) {
                Draw.rect(rotor.bladeRegion, x, y, (unit.rotation + (
                    unit.id * 24f + mount.rotorRot +
                    (360f / rotor.bladeCount) * j
                )) % 360);
            }

            Draw.alpha(1f);
            Draw.rect(rotor.topRegion, x, y, unit.rotation - 90f);
        }

        Draw.reset();
    }
}
