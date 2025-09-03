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
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.bytecodes.DoubleArrayLoad;
import com.oracle.truffle.espresso.nodes.bytecodes.DoubleArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * @see DoubleArrayLoad
 */
public final class DoubleArrayLoadQuickNode extends QuickNode {

    static final int stackEffectOf_DALOAD = Bytecodes.stackEffectOf(Bytecodes.DALOAD);

    @Child DoubleArrayLoad.WithoutNullCheck doubleArrayLoad;

    public DoubleArrayLoadQuickNode(int top, int callerBCI) {
        super(top, callerBCI);
        this.doubleArrayLoad = DoubleArrayLoadNodeGen.WithoutNullCheckNodeGen.create();
    }

    @Override
    public int execute(VirtualFrame frame, boolean isContinuationResume) {
        int index = EspressoFrame.popInt(frame, top - 1);
        StaticObject array = nullCheck(EspressoFrame.popObject(frame, top - 2));
        EspressoFrame.putDouble(frame, top - 2, doubleArrayLoad.execute(array, index));
        return stackEffectOf_DALOAD;
    }
}
