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
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.bytecodes.ReferenceArrayStore;
import com.oracle.truffle.espresso.nodes.bytecodes.ReferenceArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * @see ReferenceArrayStore
 */
public final class ReferenceArrayStoreQuickNode extends QuickNode {

    static final int stackEffectOf_AASTORE = Bytecodes.stackEffectOf(Bytecodes.AASTORE);

    @Child ReferenceArrayStore.WithoutNullCheck objectArrayStore;

    public ReferenceArrayStoreQuickNode(int top, int callerBCI) {
        super(top, callerBCI);
        this.objectArrayStore = ReferenceArrayStoreNodeGen.WithoutNullCheckNodeGen.create();
    }

    @Override
    public int execute(VirtualFrame frame) {
        StaticObject value = EspressoFrame.popObject(frame, top - 1);
        int index = EspressoFrame.popInt(frame, top - 2);
        StaticObject array = nullCheck(EspressoFrame.popObject(frame, top - 3));
        objectArrayStore.execute(array, index, value);
        return stackEffectOf_AASTORE;
    }
}
