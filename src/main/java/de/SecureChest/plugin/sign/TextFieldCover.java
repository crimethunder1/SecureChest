package de.SecureChest.plugin.sign;

import java.util.Collection;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.World;

import de.SecureChest.plugin.lock.ChunkIndex;
import de.SecureChest.plugin.lock.LockKey;

final class TextFieldCover {

    private final Predicate<Material> solidMaterial;

    TextFieldCover(Predicate<Material> solidMaterial) {
        this.solidMaterial = solidMaterial;
    }

    boolean isLoaded(World world, LockKey textField) {
        return world != null && world.isChunkLoaded(
                ChunkIndex.chunkCoordinate(textField.x()), ChunkIndex.chunkCoordinate(textField.z()));
    }

    boolean isCovered(World world, LockKey textField) {
        if (!isLoaded(world, textField)) return false;
        return solidMaterial.test(world.getBlockAt(textField.x(), textField.y(), textField.z()).getType());
    }

    boolean isCovered(World world, Collection<LockKey> textFields) {
        for (LockKey textField : textFields) {
            if (isCovered(world, textField)) return true;
        }
        return false;
    }
}
