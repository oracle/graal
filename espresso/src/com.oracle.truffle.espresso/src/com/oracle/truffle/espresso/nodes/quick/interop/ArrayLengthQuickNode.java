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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.bytecodes.ArrayLength;
import com.oracle.truffle.espresso.nodes.bytecodes.ArrayLengthFactory;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * @see ArrayLength
 */
public final class ArrayLengthQuickNode extends QuickNode {

    static final int stackEffectOf_ARRAYLENGTH = Bytecodes.stackEffectOf(Bytecodes.ARRAYLENGTH);

    @Child ArrayLength.WithoutNullCheck arrayLength;

    public ArrayLengthQuickNode(int top, int callerBCI) {
        super(top, callerBCI);
        this.arrayLength = ArrayLengthFactory.WithoutNullCheckNodeGen.create();
    }

    @Override
    public int execute(VirtualFrame frame) {
        StaticObject array = nullCheck(BytecodeNode.popObject(frame, top - 1));
        BytecodeNode.putInt(frame, top - 1, arrayLength.execute(array));
        return stackEffectOf_ARRAYLENGTH;
    }
}
