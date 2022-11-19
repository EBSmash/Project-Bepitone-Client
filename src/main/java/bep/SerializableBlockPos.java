package bep;


import net.minecraft.util.math.BlockPos;

import java.io.Serializable;
import java.util.ArrayList;
//https://github.com/FencingF/FencingFPlusTwo/blob/2.5.0/src/main/java/org/fenci/fencingfplus2/util/world/SerializableBlockPos.java
public final class SerializableBlockPos implements Serializable {
    private final int x;
    private final int y;
    private final int z;

    public SerializableBlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SerializableBlockPos(BlockPos pos) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerializableBlockPos that = (SerializableBlockPos) o;
        return x == that.x &&
            y == that.y &&
            z == that.z;
    }

    public static boolean isBlockPosInList(ArrayList<SerializableBlockPos> list, SerializableBlockPos pos) {
        for (SerializableBlockPos serializableBlockPos : list) {
            if (serializableBlockPos.getX() == pos.getX() && serializableBlockPos.getY() == pos.getY() && serializableBlockPos.getZ() == pos.getZ()) {
                return true;
            }
        }
        return false;
    }
}