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
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.helper.TypeCheckNode;
import com.oracle.truffle.espresso.nodes.helper.TypeCheckNodeGen;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class InstanceOfNode extends QuickNode {

    final Klass typeToCheck;
    @Child TypeCheckNode typeCheckNode;

    public InstanceOfNode(Klass typeToCheck, int top, int curBCI) {
        super(top, curBCI);
        assert !typeToCheck.isPrimitive();
        this.typeToCheck = typeToCheck;
        this.typeCheckNode = TypeCheckNodeGen.create(typeToCheck.getContext());
    }

    @Override
    public int execute(VirtualFrame frame, long[] primitives, Object[] refs) {
        StaticObject receiver = BytecodeNode.popObject(refs, top - 1);
        boolean result = StaticObject.notNull(receiver) && typeCheckNode.executeTypeCheck(typeToCheck, receiver.getKlass());
        BytecodeNode.putKind(primitives, refs, top - 1, result, JavaKind.Boolean);
        return 0; // stack effect -> pop receiver, push boolean
    }
}
