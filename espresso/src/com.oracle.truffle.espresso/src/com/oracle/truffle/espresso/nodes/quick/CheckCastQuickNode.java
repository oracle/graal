/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class CheckCastQuickNode extends QuickNode {

    static final int stackEffectOf_CHECKCAST = Bytecodes.stackEffectOf(Bytecodes.CHECKCAST);

    final Klass typeToCheck;
    @Child InstanceOf instanceOf;

    public CheckCastQuickNode(Klass typeToCheck, int top, int callerBCI) {
        super(top, callerBCI);
        assert !typeToCheck.isPrimitive();
        this.typeToCheck = typeToCheck;
        this.instanceOf = InstanceOf.create(typeToCheck, true);
    }

    @Override
    public int execute(VirtualFrame frame) {
        BytecodeNode root = getBytecodeNode();
        StaticObject receiver = EspressoFrame.peekObject(frame, top - 1);
        if (StaticObject.isNull(receiver) || instanceOf.execute(receiver.getKlass())) {
            return stackEffectOf_CHECKCAST;
        }
        root.enterImplicitExceptionProfile();
        EspressoFrame.popObject(frame, top - 1);
        Meta meta = typeToCheck.getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException,
                        getExceptionMessage(root, receiver));
    }

    @TruffleBoundary
    private String getExceptionMessage(BytecodeNode root, StaticObject receiver) {
        return receiver.getKlass().getType() + " cannot be cast to: " + typeToCheck.getType() + " in context " + root.getMethod().toString();
    }
}
