package zzw.content.units.type;

import arc.graphics.Color;
import mindustry.content.StatusEffects;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import zzw.content.units.bullet.LightningTurretBulletType;
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

        // 创建武器
        LightningTurretBulletType bullet = new LightningTurretBulletType(6f, 30f);
        bullet.range = 120f;
        bullet.trailLength = 12;
        bullet.trailColor = bullet.color = bullet.lightningColor = Pal.lancerLaser;
        bullet.lightningDamage = 20f;
        bullet.lightning = 5;
        bullet.splashDamage = 20f;
        bullet.splashDamageRadius = 35f;
        bullet.status = StatusEffects.shocked;
        bullet.reload = 30f;
        bullet.duration = 5f * 60f;
        
        weapons.add(new Weapon("create-deviation-weapon") {{
            x = 28f;
            y = -17.5f;
            shootY = 10.25f;
            rotate = true;
            rotateSpeed = 5f;
            reload = 80f;
            inaccuracy = 1f;
            this.bullet = bullet;
        }});
    }
}