Core.settings.put("console", true);

Log.info("[ZZW] Loaded ZZW mod version: " + Vars.mods.locateMod("zzw").meta.version);

(function() {
  let findGlbScr = mod => {
    let dir = mod.root.child("scripts");
    if (!dir.exists()) return null;
    let fiSeq = dir.findAll(fi => fi.name() === "globalScript.js");
    return fiSeq.size === 0 ? null : fiSeq.get(0);
  };
  let runGlbScr = mod => {
    let fi = findGlbScr(mod);
    if (fi == null) return;
    try {
      Vars.mods.scripts.context.evaluateString(Vars.mods.scripts.scope, fi.readString(), fi.name(), 0);
    } catch (err) {
      Log.err("[ZZW] Error loading global script:\n" + err);
    }
  };

  runGlbScr(Vars.mods.locateMod("zzw"));
})();

const MDL_util = require("zzw/mdl/MDL_util");
const MDL_event = require("zzw/mdl/MDL_event");
const GLB_items = require("zzw/glb/GLB_items");

MDL_event._c_onInit(() => {
  Log.info("[ZZW] Mod initialized via JS");
}, 10001);

MDL_event._c_onLoad(() => {
  Log.info("[ZZW] Mod content loaded via JS!");
  
  Time.run(10, () => {
    loadContent();
  });
}, 12345678);

function loadContent() {
  Log.info("[ZZW] Referencing content via JS...");
  
  zzw.content.items = {};
  zzw.content.blocks = {};
  
  GLB_items.load();
  
  Log.info("[ZZW] Content referencing complete");
}
