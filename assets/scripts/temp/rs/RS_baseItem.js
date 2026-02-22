module.exports = {
  createBasicItem(name, colorHex) {
    return new Item(name, Color.valueOf(colorHex)){{
      hardness = 1;
      cost = 0.5;
      alwaysUnlocked = false;
    }};
  }
};
