package zzw.content.units;

import arc.math.Angles;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;

public class DensityCalculator {
    private static final float DENSITY_RADIUS = 150f;

    public static Vec2 findDensePosition(float startX, float startY, float angle, float length, Team team) {
        Seq<Vec2> candidates = new Seq<>();
        int steps = Math.max(5, (int)(length / 100f));

        for (int i = 0; i <= steps; i++) {
            float t = (float)i / steps;
            float x = startX + Angles.trnsx(angle, t * length);
            float y = startY + Angles.trnsy(angle, t * length);
            candidates.add(new Vec2(x, y));
        }

        Vec2 best = null;
        float bestScore = 0f;

        for (Vec2 pos : candidates) {
            float score = calculateDensity(pos.x, pos.y, team);
            if (score > bestScore) {
                bestScore = score;
                best = pos;
            }
        }

        return best != null && bestScore > 0.5f ? best : null;
    }

    public static float calculateDensity(float x, float y, Team team) {
        float[] score = {0f};

        Units.nearbyEnemies(team, x, y, DENSITY_RADIUS, unit -> {
            if (unit.hittable()) {
                float dist = unit.dst(x, y);
                float weight = 1f - (dist / DENSITY_RADIUS);
                score[0] += weight * weight;
            }
        });

        Vars.indexer.eachBlock(null, x, y, DENSITY_RADIUS,
            build -> build.team != team && build.health > 0,
            build -> {
                float dist = build.dst(x, y);
                float weight = 1f - (dist / DENSITY_RADIUS);
                score[0] += weight * weight * 0.3f;
            });

        return score[0];
    }

    public static Vec2 findBestPosition(float startX, float startY, float angle, float length, Team team, int count) {
        Seq<ScoredPos> candidates = new Seq<>();
        int steps = Math.max(5, (int)(length / 80f));

        for (int i = 0; i <= steps; i++) {
            float t = (float)i / steps;
            float x = startX + Angles.trnsx(angle, t * length);
            float y = startY + Angles.trnsy(angle, t * length);
            float density = calculateDensity(x, y, team);
            if (density > 0.3f) {
                candidates.add(new ScoredPos(x, y, density));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort((a, b) -> Float.compare(b.score, a.score));

        float totalWeight = 0;
        for (ScoredPos c : candidates) {
            totalWeight += c.score;
        }

        float r = (float)Math.random() * totalWeight;
        float accum = 0;
        for (ScoredPos c : candidates) {
            accum += c.score;
            if (accum >= r) {
                return new Vec2(c.x, c.y);
            }
        }

        return new Vec2(candidates.first().x, candidates.first().y);
    }

    private static class ScoredPos {
        float x, y, score;
        ScoredPos(float x, float y, float score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }
}