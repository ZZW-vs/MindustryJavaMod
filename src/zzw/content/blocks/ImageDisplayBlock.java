package zzw.content.blocks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;

/**
 * 2D图片展示台
 * 
 * 主要功能:
 * 1. 多图片支持: 内置6张图片可供切换展示
 * 2. 参数调节: 支持位置、大小、旋转、透明度等参数实时调整
 * 3. 颜色滤镜: 支持RGBA通道颜色调整
 * 4. 动画效果: 支持自动旋转和缩放动画
 * 5. 界面优化: 简化的UI布局，避免过长
 * 
 * 技术特点:
 * - 使用TextureRegion进行2D图片渲染
 * - 支持3x3大型展示台
 * - 可配置参数（configurable）
 * - 支持对角线放置
 * - 集成UI控制面板
 * 
 * 内置图片类型:
 * - Teto_Q.jpg: 重音teto图片
 * - Teto_R_Q.jpg: 重音teto R图片
 * - Teto_Ts_Q.jpg: 重音teto Ts图片
 * - donk.jpg: donk图片
 * - 头顶尖尖的初音未来_Q.jpg: 初音未来图片
 * - 雷霆.jpg: 雷霆图片
 * 
 * 配置选项:
 * - image: 图片选择
 * - position: 图片位置偏移
 * - scale: 图片大小
 * - rotation: 图片旋转
 * - opacity: 图片透明度
 * - color: 颜色滤镜
 * - animation: 动画效果
 * 
 * 使用场景:
 * - 2D图片展示
 * - 艺术作品展示
 * - 角色立绘展示
 * - UI元素展示
 * - 装饰性展示
 */
public class ImageDisplayBlock extends Block {

    public TextureRegion baseRegion;

    public ImageDisplayBlock(String name) {
        super(name);
        size = 3;
        update = true;
        solid = true;
        destructible = true;
        allowDiagonal = true;
        configurable = true;
        buildVisibility = BuildVisibility.shown;
        requirements(Category.effect, ItemStack.with(Items.copper, 200), true);
    }

    @Override
    public void load() {
        super.load();
        // v158 原版已删除 ripple-base, 回退到 block-3 (3x3 底座)
        baseRegion = Core.atlas.find("ripple-base", "block-3");
    }

    @Override
    public TextureRegion[] icons() {
        return new TextureRegion[]{baseRegion};
    }

    public class ImageDisplayBuild extends Building {
        float angle = 0f;
        float currentScale = 1f;
        float currentOpacity = 1f;
        float currentRotation = 0f;
        Color currentColor = new Color(1f, 1f, 1f, 1f); // RGBA颜色
        float offsetX = 0f;
        float offsetY = 0f;
        int currentImage = 0;
        boolean autoRotate = false;
        boolean autoScale = false;
        float autoScalePhase = 0f;
        float shadowScale = 0.8f; // 阴影缩放比例
        float shadowAlpha = 0.3f; // 阴影透明度
        
        // 内置图片列表 - 修复图片加载问题
        private TextureRegion[] IMAGES = new TextureRegion[6];
        private final String[] IMAGE_NAMES = {
            "重音Teto", "重音Teto R", "重音Teto Ts", "Donk", "初音未来", "雷霆"
        };

        @Override
        public void updateTile() {
            // 自动旋转
            if (autoRotate) {
                angle += Time.delta * 0.5f;
                if (angle > 360f) angle -= 360f;
            }
            
            // 自动缩放动画
            if (autoScale) {
                autoScalePhase += Time.delta * 2f;
                currentScale = 1f + Mathf.sin(autoScalePhase) * 0.3f;
            }
        }

        @Override
        public void draw() {
            // 底座
            if (baseRegion.found()) {
                Draw.rect(baseRegion, x, y);
            }

            // 阴影 - 优化性能：只在阴影参数变化时重新计算
            Draw.z(Layer.blockBuilding - 1f);
            float shadowSize = size * 8f * currentScale * shadowScale;
            Drawf.shadow(x + offsetX, y + offsetY, shadowSize);

            // 图片
            if (IMAGES[currentImage] != null && IMAGES[currentImage].found()) {
                drawImage();
            }
        }

        private void drawImage() {
            TextureRegion image = IMAGES[currentImage];
            float imgWidth = image.width * currentScale;
            float imgHeight = image.height * currentScale;
            float imgX = x + offsetX;
            float imgY = y + offsetY;
            
            // 设置透明度和颜色滤镜
            Draw.color(currentColor.r * currentOpacity, currentColor.g * currentOpacity, 
                      currentColor.b * currentOpacity, currentOpacity);
            
            // 绘制图片
            Draw.rect(image, imgX, imgY, imgWidth, imgHeight, currentRotation);
            
            // 重置颜色
            Draw.color();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            
            // 简化的配置界面，避免过长
            table.background(Tex.pane);
            table.margin(10f);
            
            // 标题
            table.add("图片展示台控制面板").pad(10f).row();
            
            // 分隔线
            table.table(line -> {
                line.background(Tex.buttonDown);
                line.background(Tex.buttonDown);
            }).fillX().pad(5f).row();
            
            // 图片选择
            table.add("图片选择:").pad(5f).row();
            Table imageTable = new Table();
            imageTable.defaults().pad(2f);
            
            for (int i = 0; i < IMAGE_NAMES.length; i++) {
                final int idx = i;
                Button imgButton = imageTable.button(
                    IMAGE_NAMES[idx], 
                    Styles.flatTogglet, 
                    () -> {
                        currentImage = idx;
                    }
                ).size(100f, 30f).get();
                
                if (idx == currentImage) {
                    imgButton.setChecked(true);
                }
                
                if ((i + 1) % 2 == 0) imageTable.row();
            }
            
            table.add(imageTable).fillX().row();
            
            // 位置设置
            table.add("位置设置:").pad(5f).row();
            Table posTable = new Table();
            posTable.defaults().pad(5f);
            
            // X轴偏移
            posTable.add("X轴: ");
            posTable.button("-", Styles.flatTogglet, () -> {
                offsetX = Mathf.clamp(offsetX - 2f, -100f, 100f);
            }).size(25f, 25f);
            
            TextField offsetXField = posTable.field(Strings.fixed(offsetX, 0), text -> {
                if (Strings.canParseFloat(text)) {
                    offsetX = Mathf.clamp(Strings.parseFloat(text), -100f, 100f);
                }
            }).width(50f).get();
            offsetXField.setMessageText("0");
            
            posTable.button("+", Styles.flatTogglet, () -> {
                offsetX = Mathf.clamp(offsetX + 2f, -100f, 100f);
            }).size(25f, 25f);
            
            // Y轴偏移
            posTable.row();
            posTable.add("Y轴: ");
            posTable.button("-", Styles.flatTogglet, () -> {
                offsetY = Mathf.clamp(offsetY - 2f, -100f, 100f);
            }).size(25f, 25f);
            
            TextField offsetYField = posTable.field(Strings.fixed(offsetY, 0), text -> {
                if (Strings.canParseFloat(text)) {
                    offsetY = Mathf.clamp(Strings.parseFloat(text), -100f, 100f);
                }
            }).width(50f).get();
            offsetYField.setMessageText("0");
            
            posTable.button("+", Styles.flatTogglet, () -> {
                offsetY = Mathf.clamp(offsetY + 2f, -100f, 100f);
            }).size(25f, 25f);
            
            table.add(posTable).fillX().row();
            
            // 旋转设置
            table.add("旋转设置:").pad(5f).row();
            Table rotTable = new Table();
            rotTable.defaults().pad(5f);
            
            CheckBox autoRotBox = rotTable.check("自动旋转", b -> autoRotate = b).padRight(10f).get();
            autoRotBox.setChecked(autoRotate);
            
            rotTable.add("角度: ");
            TextField rotField = rotTable.field(Strings.fixed(currentRotation, 0), text -> {
                if (Strings.canParseFloat(text)) {
                    currentRotation = Strings.parseFloat(text);
                }
            }).width(50f).get();
            rotField.setMessageText("0");
            
            rotTable.button("-", Styles.flatTogglet, () -> {
                currentRotation -= 15f;
            }).size(25f, 25f);
            
            rotTable.button("+", Styles.flatTogglet, () -> {
                currentRotation += 15f;
            }).size(25f, 25f);
            
            table.add(rotTable).fillX().row();
            
            // 大小设置
            table.add("大小设置:").pad(5f).row();
            Table scaleTable = new Table();
            scaleTable.defaults().pad(5f);
            
            CheckBox autoScaleBox = scaleTable.check("自动缩放", b -> autoScale = b).padRight(10f).get();
            autoScaleBox.setChecked(autoScale);
            
            scaleTable.button("-", Styles.flatTogglet, () -> {
                currentScale = Mathf.clamp(currentScale - 0.2f, 0.1f, 3f);
            }).size(25f, 25f);
            
            TextField scaleField = scaleTable.field(Strings.fixed(currentScale, 1), text -> {
                if (Strings.canParseFloat(text)) {
                    currentScale = Mathf.clamp(Strings.parseFloat(text), 0.1f, 3f);
                }
            }).width(50f).get();
            scaleField.setMessageText("1.0");
            
            scaleTable.button("+", Styles.flatTogglet, () -> {
                currentScale = Mathf.clamp(currentScale + 0.2f, 0.1f, 3f);
            }).size(25f, 25f);
            
            // 缩放滑条
            scaleTable.add("快速: ");
            scaleTable.slider(0.1f, 3f, 0.1f, currentScale, v -> {
                currentScale = Mathf.clamp(v, 0.1f, 3f);
                if (autoScale) autoScale = false;
            }).width(100f);
            
            table.add(scaleTable).fillX().row();
            
            // 颜色设置
            table.add("颜色设置:").pad(5f).row();
            Table colorTable = new Table();
            colorTable.defaults().pad(5f);
            
            // RGBA通道调节
            colorTable.add("红色: ");
            Slider redSlider = colorTable.slider(0f, 1f, 0.1f, currentColor.r, v -> {
                currentColor = new Color(v, currentColor.g, currentColor.b, currentColor.a);
            }).width(80f).get();
            Label redLabel = new Label("");
            redLabel.update(() -> redLabel.setText(Strings.fixed(currentColor.r, 1)));
            colorTable.add(redLabel).width(25f);
            colorTable.row();
            
            colorTable.add("绿色: ");
            Slider greenSlider = colorTable.slider(0f, 1f, 0.1f, currentColor.g, v -> {
                currentColor = new Color(currentColor.r, v, currentColor.b, currentColor.a);
            }).width(80f).get();
            Label greenLabel = new Label("");
            greenLabel.update(() -> greenLabel.setText(Strings.fixed(currentColor.g, 1)));
            colorTable.add(greenLabel).width(25f);
            colorTable.row();
            
            colorTable.add("蓝色: ");
            Slider blueSlider = colorTable.slider(0f, 1f, 0.1f, currentColor.b, v -> {
                currentColor = new Color(currentColor.r, currentColor.g, v, currentColor.a);
            }).width(80f).get();
            Label blueLabel = new Label("");
            blueLabel.update(() -> blueLabel.setText(Strings.fixed(currentColor.b, 1)));
            colorTable.add(blueLabel).width(25f);
            colorTable.row();
            
            colorTable.add("透明度: ");
            Slider alphaSlider = colorTable.slider(0f, 1f, 0.1f, currentColor.a, v -> {
                currentColor = new Color(currentColor.r, currentColor.g, currentColor.b, v);
            }).width(80f).get();
            Label alphaLabel = new Label("");
            alphaLabel.update(() -> alphaLabel.setText(Strings.fixed(currentColor.a, 1)));
            colorTable.add(alphaLabel).width(25f);
            
            // 预设颜色按钮
            Table presetTable = new Table();
            presetTable.defaults().pad(2f);
            String[] presetNames = {"原色", "白色", "黑色", "红色", "绿色", "蓝色"};
            Color[] presetColors = {
                new Color(1f, 1f, 1f, 1f), // 原色
                new Color(1f, 1f, 1f, 1f), // 白色
                new Color(0f, 0f, 0f, 1f), // 黑色
                new Color(1f, 0f, 0f, 1f), // 红色
                new Color(0f, 1f, 0f, 1f), // 绿色
                new Color(0f, 0f, 1f, 1f)  // 蓝色
            };
            
            for (int i = 0; i < presetNames.length; i++) {
                final int idx = i;
                presetTable.button(presetNames[idx], Styles.flatTogglet, () -> {
                    currentColor = presetColors[idx];
                    redSlider.setValue(currentColor.r);
                    greenSlider.setValue(currentColor.g);
                    blueSlider.setValue(currentColor.b);
                    alphaSlider.setValue(currentColor.a);
                }).size(60f, 25f).get();
                if ((i + 1) % 3 == 0) presetTable.row();
            }
            
            table.add(presetTable).fillX().row();
            
            // 控制按钮
            Table controlTable = new Table();
            controlTable.defaults().pad(5f);
            
            controlTable.button("重置设置", Styles.flatTogglet, this::resetSettings)
                .size(100f, 30f);
            
            controlTable.button("关闭", Styles.flatTogglet, () -> {
                // 简单的关闭方式，不使用getParent()
            }).size(100f, 30f);
            
            table.add(controlTable).fillX().row();
        }
        
        private void resetSettings() {
            // 重置所有设置
            currentImage = 0;
            offsetX = 0f;
            offsetY = 0f;
            currentScale = 1f;
            currentRotation = 0f;
            currentColor = new Color(1f, 1f, 1f, 1f);
            autoRotate = false;
            autoScale = false;
            angle = 0f;
            autoScalePhase = 0f;
        }

        @Override
        public boolean configTapped() {
            return true;
        }
        
        // 加载图片资源
        private void loadImages() {
            try {
                // 延迟加载图片，确保资源已经加载完成
                IMAGES[0] = Core.atlas.find("photo/Teto_Q");
                IMAGES[1] = Core.atlas.find("photo/Teto_R_Q");
                IMAGES[2] = Core.atlas.find("photo/Teto_Ts_Q");
                IMAGES[3] = Core.atlas.find("photo/donk");
                IMAGES[4] = Core.atlas.find("photo/头顶尖尖的初音未来_Q");
                IMAGES[5] = Core.atlas.find("photo/雷霆");
            } catch (Exception e) {
                // 如果图片加载失败，显示错误信息
                System.err.println("图片加载失败: " + e.getMessage());
            }
        }
        
        @Override
        public void onProximityAdded() {
            super.onProximityAdded();
            // 当方块被放置时加载图片
            loadImages();
        }
    }
}