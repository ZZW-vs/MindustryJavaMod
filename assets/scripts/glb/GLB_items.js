module.exports = {
  load() {
    Log.info("[ZZW] Referencing items from Java...");

    zzw.content.items.Iron = JSBridge.getItem("iron");
    zzw.content.items.Gold = JSBridge.getItem("gold");
    zzw.content.items.Andesite = JSBridge.getItem("andesite");
    zzw.content.items.Andesite_Alloy = JSBridge.getItem("andesite_alloy");
    zzw.content.items.Brass = JSBridge.getItem("brass");
    zzw.content.items.Zinc = JSBridge.getItem("zinc");

    zzw.content.items.Iron_Sheet = JSBridge.getItem("iron_sheet");
    zzw.content.items.Gold_Sheet = JSBridge.getItem("gold_sheet");
    zzw.content.items.Copper_Sheet = JSBridge.getItem("copper_sheet");
    zzw.content.items.Brass_Sheet = JSBridge.getItem("brass_sheet");

    zzw.content.items.Pumpkin_Seeds = JSBridge.getItem("pumpkin_seeds");
    zzw.content.items.Pulp = JSBridge.getItem("pulp");

    zzw.content.items.Text_An = JSBridge.getItem("campfire_fire");

    Log.info("[ZZW] Items referenced from Java");
  },

  getItem(name) {
    const item = zzw.content.items[name];
    if (!item) {
      Log.warn("[ZZW] Item not found: " + name);
    }
    return item;
  }
};
