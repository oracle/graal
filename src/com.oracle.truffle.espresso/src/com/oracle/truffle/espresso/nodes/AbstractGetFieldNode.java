package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class AbstractGetFieldNode extends Node {
    final Field field;
    final int slotCount;

    AbstractGetFieldNode(Field field) {
        this.field = field;
        this.slotCount = field.getKind().getSlotCount();
    }

    public abstract int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at);

    public static AbstractGetFieldNode create(Field f) {
        // @formatter:off
        switch (f.getKind()) {
            case Boolean: return new BooleanGetFieldNode(f);
            case Byte:    return new ByteGetFieldNode(f);
            case Short:   return new ShortGetFieldNode(f);
            case Char:    return new CharGetFieldNode(f);
            case Int:     return new IntGetFieldNode(f);
            case Float:   return new FloatGetFieldNode(f);
            case Long:    return new LongGetFieldNode(f);
            case Double:  return new DoubleGetFieldNode(f);
            case Object:  return new ObjectGetFieldNode(f);
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }
}

final class IntGetFieldNode extends AbstractGetFieldNode {
    IntGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Int;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putInt(frame, at, receiver.getIntField(field));
        return slotCount;
    }
}

final class BooleanGetFieldNode extends AbstractGetFieldNode {
    BooleanGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Boolean;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putInt(frame, at, receiver.getBooleanField(field) ? 1 : 0);
        return slotCount;
    }
}

final class CharGetFieldNode extends AbstractGetFieldNode {
    CharGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Char;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putInt(frame, at, receiver.getCharField(field));
        return slotCount;
    }
}

final class ShortGetFieldNode extends AbstractGetFieldNode {
    ShortGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Short;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putInt(frame, at, receiver.getShortField(field));
        return slotCount;
    }
}

final class ByteGetFieldNode extends AbstractGetFieldNode {
    ByteGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Byte;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putInt(frame, at, receiver.getByteField(field));
        return slotCount;
    }
}

final class LongGetFieldNode extends AbstractGetFieldNode {
    LongGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Long;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putLong(frame, at, receiver.getLongField(field));
        return slotCount;
    }
}

final class FloatGetFieldNode extends AbstractGetFieldNode {
    FloatGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Float;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putFloat(frame, at, receiver.getFloatField(field));
        return slotCount;
    }
}

final class DoubleGetFieldNode extends AbstractGetFieldNode {
    DoubleGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Double;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putDouble(frame, at, receiver.getDoubleField(field));
        return slotCount;
    }
}

final class ObjectGetFieldNode extends AbstractGetFieldNode {
    ObjectGetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Object;
    }

    @Override
    public int getField(VirtualFrame frame, BytecodesNode root, StaticObject receiver, int at) {
        root.putObject(frame, at, receiver.getField(field));
        return slotCount;
    }
}
