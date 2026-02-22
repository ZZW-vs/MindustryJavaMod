const events = [];

module.exports = {
  _c_onInit(func, id) {
    Events.on(EventType.ClientLoadEvent, e => {
      try {
        func();
      } catch (err) {
        Log.err("[ZZW] Error in onInit event: " + err);
      }
    });
  },

  _c_onLoad(func, id) {
    Events.on(EventType.ContentInitEvent, e => {
      try {
        func();
      } catch (err) {
        Log.err("[ZZW] Error in onLoad event: " + err);
      }
    });
  },

  _c_onWorldLoad(func, id) {
    Events.on(EventType.WorldLoadEvent, e => {
      try {
        func();
      } catch (err) {
        Log.err("[ZZW] Error in onWorldLoad event: " + err);
      }
    });
  }
};
