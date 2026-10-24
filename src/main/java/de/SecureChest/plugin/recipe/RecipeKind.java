package de.SecureChest.plugin.recipe;

public enum RecipeKind {

    CHEST_KEY("chest_key", "Chest Key");

    private final String configKey;
    private final String displayName;

    RecipeKind(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public static RecipeKind fromConfigKey(String configKey) {
        for (RecipeKind kind : values()) {
            if (kind.configKey.equals(configKey)) return kind;
        }
        return null;
    }
}
