/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.nodes.helper;

import static com.oracle.truffle.espresso.nodes.quick.QuickNode.nullCheck;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class AbstractSetFieldNode extends Node {
    final Field field;
    final String fieldName;
    private final boolean isStatic;
    private final int slotCount;
    static final int CACHED_LIBRARY_LIMIT = 3;

    AbstractSetFieldNode(Field field) {
        this.field = field;
        this.fieldName = field.getNameAsString();
        this.slotCount = field.getKind().getSlotCount();
        this.isStatic = field.isStatic();
    }

    public abstract void setField(VirtualFrame frame, BytecodeNode root, int top);

    public static AbstractSetFieldNode create(Field f) {
        // @formatter:off
        switch (f.getKind()) {
            case Boolean: return BooleanSetFieldNodeGen.create(f);
            case Byte:    return ByteSetFieldNodeGen.create(f);
            case Short:   return ShortSetFieldNodeGen.create(f);
            case Char:    return CharSetFieldNodeGen.create(f);
            case Int:     return IntSetFieldNodeGen.create(f);
            case Float:   return FloatSetFieldNodeGen.create(f);
            case Long:    return LongSetFieldNodeGen.create(f);
            case Double:  return DoubleSetFieldNodeGen.create(f);
            case Object:  return ObjectSetFieldNodeGen.create(f);
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

    protected void setForeignField(StaticObject receiver, Object fieldValue,
                    InteropLibrary interopLibrary,
                    EspressoContext context, BranchProfile error) {
        assert field.getDeclaringKlass().isAssignableFrom(receiver.getKlass());
        assert receiver.isForeignObject();
        assert !(fieldValue instanceof StaticObject) || !((StaticObject) fieldValue).isForeignObject();
        try {
            interopLibrary.writeMember(receiver.rawForeignObject(), fieldName, fieldValue);
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            error.enter();
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_NoSuchFieldError, "Foreign object has no writable field " + fieldName);
        } catch (UnsupportedTypeException e) {
            error.enter();
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException,
                            "Could not cast the value to the actual type of the foreign field " + fieldName);
        }
    }
}

abstract class IntSetFieldNode extends AbstractSetFieldNode {
    IntSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Int;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        int value = root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, int value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, int value) {
        receiver.setIntField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, int fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class BooleanSetFieldNode extends AbstractSetFieldNode {
    BooleanSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Boolean;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        boolean value = root.peekInt(frame, top - 1) != 0;
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, boolean value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, boolean value) {
        receiver.setBooleanField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, boolean fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class CharSetFieldNode extends AbstractSetFieldNode {
    CharSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Char;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        char value = (char) root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, char value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, char value) {
        receiver.setCharField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, char fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class ShortSetFieldNode extends AbstractSetFieldNode {
    ShortSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Short;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        short value = (short) root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, short value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, short value) {
        receiver.setShortField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, short fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class ByteSetFieldNode extends AbstractSetFieldNode {
    ByteSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Byte;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        byte value = (byte) root.peekInt(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, byte value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, byte value) {
        receiver.setByteField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, byte fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class LongSetFieldNode extends AbstractSetFieldNode {
    LongSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Long;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        long value = root.peekLong(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, long value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, long value) {
        receiver.setLongField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, long fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class FloatSetFieldNode extends AbstractSetFieldNode {
    FloatSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Float;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        float value = root.peekFloat(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, float value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, float value) {
        receiver.setFloatField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, float fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class DoubleSetFieldNode extends AbstractSetFieldNode {
    DoubleSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Double;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        double value = root.peekDouble(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, double value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, double value) {
        receiver.setDoubleField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, double fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue, interopLibrary, context, error);
    }
}

abstract class ObjectSetFieldNode extends AbstractSetFieldNode {
    ObjectSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Object;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, int top) {
        StaticObject value = root.peekAndReleaseObject(frame, top - 1);
        StaticObject receiver = getReceiver(frame, root, top);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, Object value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, StaticObject value) {
        receiver.setField(field, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, StaticObject fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue.isForeignObject() ? fieldValue.rawForeignObject() : fieldValue, interopLibrary, context, error);
    }
}
