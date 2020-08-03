/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class PutFieldNode extends QuickNode {
    protected final Field field;
    protected final String fieldName;

    protected PutFieldNode(int top, int callerBCI, Field field) {
        super(top, callerBCI);
        assert !field.isStatic();
        this.field = field;
        this.fieldName = field.getNameAsString();
    }

    @Override
    public final int execute(VirtualFrame frame) {
        // TODO: instrumentation
        BytecodeNode root = getBytecodesNode();
        StaticObject receiver = nullCheck(root.peekAndReleaseObject(frame, top - field.getKind().getSlotCount() - 1));
        Object fieldValue = readValue(frame);
        executePutField(receiver, fieldValue);
        return -field.getKind().getSlotCount();
    }

    @Override
    public final boolean producedForeignObject(VirtualFrame frame) {
        return false;
    }

    public abstract void executePutField(Object receiver, Object fieldValue);

    @Specialization(guards = {"receiver.isForeignObject()", "!isForeignObject(fieldValue)"}, limit = "1")
    protected void doForeign(StaticObject receiver, Object fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context) {
        assert field.getDeclaringKlass().isAssignableFrom(receiver.getKlass());
        try {
            interopLibrary.writeMember(receiver.rawForeignObject(), fieldName, fieldValue);
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_NoSuchFieldError, "Foreign object has no writable field " + fieldName);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException,
                            "Could not cast the value to the actual type of the foreign field " + fieldName);
        }
    }

    @Specialization(guards = {"receiver.isForeignObject()", "fieldValue.isForeignObject()"}, limit = "1")
    protected void doForeignWithForeignField(StaticObject receiver, StaticObject fieldValue,
                    @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @CachedContext(EspressoLanguage.class) EspressoContext context) {
        assert field.getDeclaringKlass().isAssignableFrom(receiver.getKlass());
        try {
            interopLibrary.writeMember(receiver.rawForeignObject(), fieldName, fieldValue.rawForeignObject());
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_NoSuchFieldError, "Foreign object has no writable field " + fieldName);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException,
                            "Could not cast the value to the actual type of the foreign field " + fieldName);
        }
    }

    @Specialization(guards = "receiver.isEspressoObject()")
    protected void doEspresso(StaticObject receiver, Object fieldValue) {
        field.set(receiver, fieldValue);
    }

    protected Object readValue(VirtualFrame frame) {
        BytecodeNode root = getBytecodesNode();
        switch (field.getKind()) {
            case Boolean:
                return root.peekInt(frame, top - 1) != 0;
            case Byte:
                return (byte) root.peekInt(frame, top - 1);
            case Char:
                return (char) root.peekInt(frame, top - 1);
            case Short:
                return (short) root.peekInt(frame, top - 1);
            case Int:
                return root.peekInt(frame, top - 1);
            case Double:
                return root.peekDouble(frame, top - 1);
            case Float:
                return root.peekFloat(frame, top - 1);
            case Long:
                return root.peekLong(frame, top - 1);
            case Object:
                return root.peekAndReleaseObject(frame, top - 1);
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("unexpected kind: " + field.getKind().toString());
        }
    }

    protected static boolean isForeignObject(Object value) {
        return value instanceof StaticObject && ((StaticObject) value).isForeignObject();
    }
}
