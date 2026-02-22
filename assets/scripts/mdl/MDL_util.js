module.exports = {
  lerp(a, b, t) {
    return a + (b - a) * t;
  },

  clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  },

  rgb(r, g, b) {
    return new Color(r, g, b, 1);
  },

  rgba(r, g, b, a) {
    return new Color(r, g, b, a);
  },

  localizeModMeta(modName) {
    let mod = Vars.mods.locateMod(modName);
    if (mod == null) return;
    mod.meta.displayName = Core.bundle.get("mod." + modName + ".name", mod.meta.displayName);
    mod.meta.description = Core.bundle.get("mod." + modName + ".description", mod.meta.description);
  }
};
