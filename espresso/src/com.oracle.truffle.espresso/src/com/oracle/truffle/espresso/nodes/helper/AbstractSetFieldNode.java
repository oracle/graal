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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class AbstractSetFieldNode extends Node implements ContextAccess {
    final Field field;
    final String fieldName;
    static final int CACHED_LIBRARY_LIMIT = 3;

    AbstractSetFieldNode(Field field) {
        this.field = field;
        this.fieldName = field.getNameAsString();
    }

    @Override
    public EspressoContext getContext() {
        return EspressoContext.get(this);
    }

    public abstract void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex);

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
            Meta meta = context.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchFieldError, "Foreign object has no writable field %s", fieldName);
        } catch (UnsupportedTypeException e) {
            error.enter();
            Meta meta = context.getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                            "Could not cast the value to the actual type of the foreign field %s", fieldName);
        }
    }
}

abstract class IntSetFieldNode extends AbstractSetFieldNode {
    IntSetFieldNode(Field f) {
        super(f);
        assert f.getKind() == JavaKind.Int;
    }

    @Override
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        int value = BytecodeNode.popInt(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, int value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, int value) {
        field.setInt(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, int fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        boolean value = BytecodeNode.popInt(frame, top - 1) != 0;
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, boolean value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, boolean value) {
        field.setBoolean(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, boolean fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        char value = (char) BytecodeNode.popInt(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, char value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, char value) {
        field.setChar(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, char fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        short value = (short) BytecodeNode.popInt(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, short value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, short value) {
        field.setShort(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, short fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        byte value = (byte) BytecodeNode.popInt(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, byte value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, byte value) {
        field.setByte(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, byte fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        long value = BytecodeNode.popLong(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, long value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, long value) {
        field.setLong(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, long fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        float value = BytecodeNode.popFloat(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, float value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, float value) {
        field.setFloat(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, float fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        double value = BytecodeNode.popDouble(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, double value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, double value) {
        field.setDouble(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, double fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
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
    public void setField(VirtualFrame frame, BytecodeNode root, StaticObject receiver, int top, int statementIndex) {
        StaticObject value = BytecodeNode.popObject(frame, top - 1);
        root.notifyFieldModification(frame, statementIndex, field, receiver, value);
        executeSetField(receiver, value);
    }

    abstract void executeSetField(StaticObject receiver, Object value);

    @Specialization(guards = "receiver.isEspressoObject()")
    void doEspresso(StaticObject receiver, StaticObject value) {
        field.setObject(receiver, value);
    }

    @Specialization(guards = {"receiver.isForeignObject()"}, limit = "CACHED_LIBRARY_LIMIT")
    void doForeign(StaticObject receiver, StaticObject fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Bind("getContext()") EspressoContext context,
                    @Cached BranchProfile error) {
        setForeignField(receiver, fieldValue.isForeignObject() ? fieldValue.rawForeignObject() : fieldValue, interopLibrary, context, error);
    }
}
