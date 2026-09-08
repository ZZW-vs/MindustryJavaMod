package zzw.content.units.type;

import arc.graphics.Color;
import arc.math.Interp;
import mindustry.content.StatusEffects;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import zzw.content.units.bullet.LightningTurretBulletType;
import zzw.content.units.type.decal.WingDecorationType;
import zzw.content.units.type.decal.WingDecorationType.Wing;
import zzw.content.type.UnityUnitType;

public class DeviationUnitType extends UnityUnitType{
    public DeviationUnitType(String name){
        super(name);
        health = 8000f;
        speed = 2.7f;
        accel = 0.07f;
        drag = 0.04f;
        hitSize = 96f;
        engineOffset = 38f;
        engineSize = 4.75f;
        flying = true;
        lowAltitude = true;
        outlineColor = Color.valueOf("464a61");

        weapons.add(new Weapon(name + "-mount"){{
            x = 28f;
            y = -17.5f;
            shootY = 10.25f;
            rotate = true;
            rotateSpeed = 5f;
            reload = 80f;
            inaccuracy = 1f;

            bullet = new LightningTurretBulletType(6f, 30f){{
                range = 120f;
                trailLength = 12;
                trailColor = color = lightningColor = Pal.lancerLaser;
                lightningDamage = 20f;
                lightning = 5;
                splashDamage = 20f;
                splashDamageRadius = 35f;
                status = StatusEffects.shocked;
                reload = 30f;
                duration = 5f * 60f;
            }};
        }});

        decorations.add(new WingDecorationType(name + "-wing", 4){{
            flapScl = 120f;
            flapAnimation = new Interp.ExpOut(2, 2.5f);
            wings.add(new Wing(0, 19f, -35.25f, 0.75f, 19f),
                new Wing(1, 24.75f, -28.75f, 0.5f, 18f),
                new Wing(2, 24.25f, -8f, 0.25f, 17f),
                new Wing(3, 18f, 0.25f, 0f, 16f));
        }});
    }
}