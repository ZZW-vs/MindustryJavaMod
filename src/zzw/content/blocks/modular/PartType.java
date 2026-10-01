package zzw.content.blocks.modular;

import arc.graphics.g2d.TextureRegion;

/**
 * 模块化方块零件类别 (PU132 younggamExperimental.PartType 移植)。
 *
 * <p>用于蓝图编辑 UI 里对零件分类显示, 每种类别带一张分类图标贴图。</p>
 */
public enum PartType{
    none,
    blade,
    saw,
    base,
    breach,
    ammo,
    misc;

    /** 分类图标贴图 (load 时由方块写入) */
    public TextureRegion region;
}