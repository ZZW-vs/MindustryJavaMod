module.exports = {
  load() {
    Log.info("[ZZW] Referencing items from Java...");

    zzw.content.items.Iron = Vars.content.item("iron");
    zzw.content.items.Gold = Vars.content.item("gold");
    zzw.content.items.Andesite = Vars.content.item("andesite");
    zzw.content.items.Andesite_Alloy = Vars.content.item("andesite_alloy");
    zzw.content.items.Brass = Vars.content.item("brass");
    zzw.content.items.Zinc = Vars.content.item("zinc");

    zzw.content.items.Iron_Sheet = Vars.content.item("iron_sheet");
    zzw.content.items.Gold_Sheet = Vars.content.item("gold_sheet");
    zzw.content.items.Copper_Sheet = Vars.content.item("copper_sheet");
    zzw.content.items.Brass_Sheet = Vars.content.item("brass_sheet");

    zzw.content.items.Pumpkin_Seeds = Vars.content.item("pumpkin_seeds");
    zzw.content.items.Pulp = Vars.content.item("pulp");

    zzw.content.items.Text_An = Vars.content.item("campfire_fire");

    Log.info("[ZZW] Items referenced from Java");
  },

  getItem(name) {
    return zzw.content.items[name];
  }
};
