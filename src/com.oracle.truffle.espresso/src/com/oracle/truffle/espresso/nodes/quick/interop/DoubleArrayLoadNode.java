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
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class DoubleArrayLoadNode extends QuickNode {
    protected static final int LIMIT = 3;

    protected DoubleArrayLoadNode(int top, int callerBCI) {
        super(top, callerBCI);
    }

    @Override
    public final int execute(VirtualFrame frame) {
        BytecodeNode root = getBytecodesNode();
        StaticObject array = nullCheck(root.peekAndReleaseObject(frame, top - 2));
        int index = root.peekInt(frame, top - 1);
        root.putDouble(frame, top - 2, executeLoad(array, index));
        return Bytecodes.stackEffectOf(Bytecodes.DALOAD);
    }

    abstract double executeLoad(StaticObject array, int index);

    @Specialization(guards = "array.isForeignObject()")
    double doForeign(StaticObject array, int index,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached ToEspressoNode toEspressoNode,
                    @CachedContext(EspressoLanguage.class) EspressoContext context) {
        Object result;
        try {
            result = interop.readArrayElement(array.rawForeignObject(), index);
        } catch (UnsupportedMessageException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_IllegalArgumentException, "The foreign object is not a readable array");
        } catch (InvalidArrayIndexException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }

        try {
            return (double) toEspressoNode.execute(result, context.getMeta()._double);
        } catch (UnsupportedTypeException e) {
            throw Meta.throwExceptionWithMessage(context.getMeta().java_lang_ClassCastException, "Could not cast the foreign array element to double");
        }
    }

    @Specialization(guards = "array.isEspressoObject()")
    double doEspresso(StaticObject array, int index) {
        return getBytecodesNode().getInterpreterToVM().getArrayDouble(index, array);
    }

    @Override
    public boolean producedForeignObject(VirtualFrame frame) {
        return false;
    }
}
