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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class InstanceOfQuickNode extends QuickNode {

    @Child InstanceOf instanceOf;

    static final int stackEffectOf_INSTANCEOF = Bytecodes.stackEffectOf(Bytecodes.INSTANCEOF);

    public InstanceOfQuickNode(Klass typeToCheck, int top, int curBCI) {
        super(top, curBCI);
        assert !typeToCheck.isPrimitive();
        this.instanceOf = InstanceOf.create(typeToCheck, true);
    }

    @Override
    public int execute(VirtualFrame frame) {
        StaticObject receiver = EspressoFrame.popObject(frame, top - 1);
        boolean result = StaticObject.notNull(receiver) && instanceOf.execute(receiver.getKlass());
        EspressoFrame.putInt(frame, top - 1, result ? 1 : 0);
        return stackEffectOf_INSTANCEOF;
    }
}
