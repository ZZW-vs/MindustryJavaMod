package zzw.content.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import zzw.util.WavefrontObject;

import java.util.Arrays;

/**
 * 3x3 万能模型展示台
 * 支持多种内置3D模型切换, 玩家可调整位置/颜色/大小
 */
public class ObjDisplayBlock extends Block {

    public ObjDisplayBlock(String name) {
        super(name);
        size = 3;
        update = true;
        solid = true;
        destructible = true;
        allowDiagonal = true;
        configurable = true;
        buildVisibility = BuildVisibility.shown;
    }

    public class DisplayBuild extends Building {
        float angle = 0f;
        float currentScale = 1f;
        Color currentColor = Color.white.cpy();
        char currentAxis = 'Z';
        float currentOffset = 0f;
        int currentModel = 0;

        // 内置模型列表
        private final WavefrontObject[] MODELS = {
            zzw.util.ZObjs.flywheel,
            zzw.util.ZObjs.waterWheel,
            zzw.util.ZObjs.crushingWheel,
            zzw.util.ZObjs.cube,
            zzw.util.ZObjs.prism,
            zzw.util.ZObjs.wavefront
        };

        private final String[] MODEL_NAMES = {
            "飞轮", "水车", "粉碎轮", "方块", "棱镜", "波前"
        };

        @Override
        public void updateTile() {
            angle += Time.delta * 0.5f;
            if (angle > 360f) angle -= 360f;
        }

        @Override
        public void draw() {
            // 底座
            if (region.found()) {
                Draw.rect(region, x, y, size * Vars.tilesize, size * Vars.tilesize);
            }

            // 阴影 - 增强地面阴影让模型有"落地感"
            Draw.z(Layer.blockBuilding - 1f);
            Drawf.shadow(x, y, size * 14f);
            // 第二层更暗的阴影增强3D感
            Draw.color(0, 0, 0, 0.3f);
            Draw.rect(Core.atlas.find("circle-shadow"), x, y - Vars.tilesize * 0.5f, size * 10f, size * 6f);
            Draw.color();

            drawModel();
        }

        private void drawModel() {
            WavefrontObject obj = MODELS[currentModel];
            if (obj == null || obj.faces == null || obj.faces.size == 0) return;

            // 保存原始渲染参数
            Color origLight = obj.lightColor.cpy();
            Color origShade = obj.shadeColor.cpy();
            float origSize = obj.size;
            float origMaxShade = obj.maxShade;

            // 应用玩家选择的颜色和缩放
            obj.lightColor.set(currentColor);
            // shadeColor = currentColor 的暗色版本
            obj.shadeColor.set(currentColor.r * 0.2f, currentColor.g * 0.2f, currentColor.b * 0.2f);
            obj.size = currentScale;
            // 增强明暗对比提升3D感
            obj.maxShade = 0.85f;

            // 旋转角度
            float rX = -25f; // 固定俯视角倾斜
            float rY = 0f;
            float rZ = 0f;

            if (currentAxis == 'Y') {
                rY = angle;
            } else {
                rZ = angle;
            }

            // Z偏移避免多实例穿插
            obj.zOffset = (id % 100) * 0.1f;

            // 居中绘制
            float centerX = x;
            float centerY = y + currentOffset;

            obj.draw(centerX, centerY, rX, rY, rZ);

            // 恢复原始参数
            obj.lightColor.set(origLight);
            obj.shadeColor.set(origShade);
            obj.size = origSize;
            obj.maxShade = origMaxShade;
            obj.zOffset = 0f;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();

            // ===== 折叠菜单: 1.模型 2.位置 3.颜色 4.大小 =====

            // 1. 模型选择
            table.table(Tex.pane, t -> {
                t.add("模型选择").pad(4f).row();
                Table modelTable = new Table();
                modelTable.defaults().pad(2f);
                Button[] modelButtons = new Button[MODELS.length];
                for (int i = 0; i < MODELS.length; i++) {
                    final int idx = i;
                    modelButtons[i] = modelTable.button(MODEL_NAMES[i], Styles.flatTogglet, () -> {
                        currentModel = idx;
                        Arrays.stream(modelButtons).forEach(b -> b.setChecked(false));
                        modelButtons[idx].setChecked(true);
                    }).size(70f, 28f).get();
                    modelButtons[i].setChecked(idx == currentModel);
                    if ((i + 1) % 3 == 0) modelTable.row();
                }
                t.add(modelTable);
            }).growX().padBottom(4f);
            table.row();

            // 2. 位置 (高度偏移 + 旋转轴)
            table.table(Tex.pane, t -> {
                t.add("位置").pad(4f).row();
                Table posTable = new Table();
                posTable.defaults().pad(2f);

                // 旋转轴选择
                Button[] axisButtons = new Button[2];
                axisButtons[0] = posTable.button("Z轴旋转", Styles.flatTogglet, () -> {
                    currentAxis = 'Z';
                    axisButtons[0].setChecked(true);
                    axisButtons[1].setChecked(false);
                }).size(80f, 28f).get();
                axisButtons[1] = posTable.button("Y轴旋转", Styles.flatTogglet, () -> {
                    currentAxis = 'Y';
                    axisButtons[1].setChecked(true);
                    axisButtons[0].setChecked(false);
                }).size(80f, 28f).get();
                axisButtons[0].setChecked(currentAxis == 'Z');
                axisButtons[1].setChecked(currentAxis == 'Y');
                posTable.row();

                // 高度调整
                posTable.button("-", Styles.flatt, () -> {
                    currentOffset = Mathf.clamp(currentOffset - 2f, -20f, 40f);
                }).size(30f, 28f);
                posTable.label(() -> "高度:" + Strings.fixed(currentOffset, 0)).width(70f);
                posTable.button("+", Styles.flatt, () -> {
                    currentOffset = Mathf.clamp(currentOffset + 2f, -20f, 40f);
                }).size(30f, 28f);

                t.add(posTable);
            }).growX().padBottom(4f);
            table.row();

            // 3. 颜色
            table.table(Tex.pane, t -> {
                t.add("颜色").pad(4f).row();
                Table colorTable = new Table();
                colorTable.defaults().pad(2f);

                String[] colorNames = {"白色", "金色", "红色", "蓝色", "绿色", "灰色"};
                Color[] colors = {Color.white, Color.valueOf("FFD700"), Color.red, Color.blue, Color.green, Color.gray};
                Button[] colorButtons = new Button[colors.length];

                for (int i = 0; i < colors.length; i++) {
                    final int idx = i;
                    colorButtons[idx] = colorTable.button(colorNames[idx], Styles.flatTogglet, () -> {
                        currentColor.set(colors[idx]);
                        updateToggleButtons(colorButtons, idx);
                    }).size(55f, 28f).get();
                    colorButtons[idx].setChecked(idx == 0);
                    if ((i + 1) % 3 == 0) colorTable.row();
                }
                t.add(colorTable);
            }).growX().padBottom(4f);
            table.row();

            // 4. 大小
            table.table(Tex.pane, t -> {
                t.add("大小").pad(4f).row();
                Table scaleTable = new Table();
                scaleTable.defaults().pad(2f);

                scaleTable.button("-", Styles.flatt, () -> {
                    currentScale = Mathf.clamp(currentScale - 0.2f, 0.5f, 3.0f);
                }).size(30f, 28f);
                scaleTable.label(() -> Strings.fixed(currentScale, 1) + "x").width(50f);
                scaleTable.button("+", Styles.flatt, () -> {
                    currentScale = Mathf.clamp(currentScale + 0.2f, 0.5f, 3.0f);
                }).size(30f, 28f);

                t.add(scaleTable);
            }).growX();
        }

        private void updateToggleButtons(Button[] buttons, int selected) {
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].setChecked(i == selected);
            }
        }

        @Override
        public boolean configTapped() {
            return true;
        }
    }
}
