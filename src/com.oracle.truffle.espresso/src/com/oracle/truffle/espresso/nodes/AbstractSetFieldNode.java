package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;

import static com.oracle.truffle.espresso.nodes.QuickNode.nullCheck;

public abstract class AbstractSetFieldNode extends Node {
    final Field field;
    private final boolean isStatic;
    private final int slotCount;

    AbstractSetFieldNode(Field field) {
        this.field = field;
        this.slotCount = field.getKind().getSlotCount();
        this.isStatic = field.isStatic();
    }

    public abstract void setField(VirtualFrame frame, BytecodeNode root, int top);

    public static AbstractSetFieldNode create(Field f) {
        // @formatter:off
        switch (f.getKind()) {
            case Boolean: return new BooleanSetFieldNode(f);
            case Byte:    return new ByteSetFieldNode(f);
            case Short:   return new ShortSetFieldNode(f);
            case Char:    return new CharSetFieldNode(f);
            case Int:     return new IntSetFieldNode(f);
            case Float:   return new FloatSetFieldNode(f);
            case Long:    return new LongSetFieldNode(f);
            case Double:  return new DoubleSetFieldNode(f);
            case Object:  return new ObjectSetFieldNode(f);
            default:
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    StaticObject getReceiver(VirtualFrame frame, BytecodeNode root, int top) {
        return isStatic
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        : nullCheck(root.peekObject(frame, top - 1 - slotCount));
    }
}

final class IntSetFieldNode extends AbstractSetFieldNode {
    IntSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Int;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        int value = root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setIntField(field, value);
    }
}

final class BooleanSetFieldNode extends AbstractSetFieldNode {
    BooleanSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Boolean;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        boolean value = root.peekInt(frame, top - 1) != 0;
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setBooleanField(field, value);
    }
}

final class CharSetFieldNode extends AbstractSetFieldNode {
    CharSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Char;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        char value = (char) root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setCharField(field, value);
    }
}

final class ShortSetFieldNode extends AbstractSetFieldNode {
    ShortSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Short;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        short value = (short) root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setShortField(field, value);
    }
}

final class ByteSetFieldNode extends AbstractSetFieldNode {
    ByteSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Byte;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        byte value = (byte) root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setByteField(field, value);
    }
}

final class LongSetFieldNode extends AbstractSetFieldNode {
    LongSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Long;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        long value = root.peekLong(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setLongField(field, value);
    }
}

final class FloatSetFieldNode extends AbstractSetFieldNode {
    FloatSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Float;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        float value = root.peekFloat(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setFloatField(field, value);
    }
}

final class DoubleSetFieldNode extends AbstractSetFieldNode {
    DoubleSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Double;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        double value = root.peekDouble(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setDoubleField(field, value);
    }
}

final class ObjectSetFieldNode extends AbstractSetFieldNode {
    ObjectSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Object;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        StaticObject value = root.peekAndReleaseObject(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        receiver.setField(field, value);
    }
}
