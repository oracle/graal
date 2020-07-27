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

import com.oracle.truffle.api.dsl.Cached;
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
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class GetFieldNode extends QuickNode {
    protected final Field field;

    public GetFieldNode(int top, int callerBCI, Field field) {
        super(top, callerBCI);
        assert !field.isStatic();
        this.field = field;
    }

    @Override
    public final int execute(final VirtualFrame frame) {
        // TODO: instrumentation
        BytecodeNode root = getBytecodesNode();
        StaticObject receiver = nullCheck(root.peekAndReleaseObject(frame, top - 1));
        Object result = executeGetField(receiver);
        return root.putKind(frame, top - 1, result, field.getKind());
    }

    @Override
    public boolean producedForeignObject(VirtualFrame frame) {
        return field.getKind().isObject() && getBytecodesNode().peekObject(frame, top).isForeignObject();
    }

    protected abstract Object executeGetField(StaticObject receiver);

    @Specialization(guards = "receiver.isForeignObject()", limit = "1")
    protected Object doForeign(StaticObject receiver, @CachedLibrary("receiver.rawForeignObject()") InteropLibrary interopLibrary,
                    @Cached ToEspressoNode toEspressoNode, @CachedContext(EspressoLanguage.class) EspressoContext context) {
        String fieldName = field.getNameAsString();
        Object value;
        try {
            value = interopLibrary.readMember(receiver.rawForeignObject(), fieldName);
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_NoSuchFieldError, "Foreign object has no readable field " + fieldName);
        }

        try {
            return toEspressoNode.execute(value, field.resolveTypeKlass());
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Field " + fieldName + " cannot be cast to type " + field.getTypeAsString());
        }
    }

    @Specialization(guards = "receiver.isEspressoObject()")
    protected Object doEspresso(StaticObject receiver) {
        return field.get(receiver);
    }
}
