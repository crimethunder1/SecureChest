package de.SecureChest.plugin.area;

public sealed interface AreaChange {

    record Enable() implements AreaChange {}

    record Disable() implements AreaChange {}

    record Create(String name, String worldName, int firstX, int firstZ, int secondX, int secondZ) implements AreaChange {}

    record Resize(String name, int firstX, int firstZ, int secondX, int secondZ) implements AreaChange {}

    record Delete(String name) implements AreaChange {}
}
