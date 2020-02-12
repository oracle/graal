/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.nodes.helper.AbstractGetFieldNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.object.DebugCounter;

public class InlinedGetterNode extends QuickNode {

    private static final DebugCounter getterNodes = DebugCounter.create("getters: ");
    private static final DebugCounter leafGetterNodes = DebugCounter.create("leaf get: ");

    private static final int INSTANCE_GETTER_BCI = 1;
    private static final int STATIC_GETTER_BCI = 0;

    final Field field;
    final Method inlinedMethod;

    @Child AbstractGetFieldNode getFieldNode;

    InlinedGetterNode(Method inlinedMethod, int top, int callerBCI) {
        super(top, callerBCI);
        this.inlinedMethod = inlinedMethod;
        this.field = getInlinedField(inlinedMethod);
        getFieldNode = AbstractGetFieldNode.create(this.field);
        assert field.isStatic() == inlinedMethod.isStatic();
    }

    public static InlinedGetterNode create(Method inlinedMethod, int top, int opCode, int curBCI) {
        getterNodes.inc();
        if (inlinedMethod.isFinalFlagSet() || inlinedMethod.getDeclaringKlass().isFinalFlagSet()) {
            return new InlinedGetterNode(inlinedMethod, top, curBCI);
        } else {
            leafGetterNodes.inc();
            return new LeafAssumptionGetterNode(inlinedMethod, top, opCode, curBCI);
        }
    }

    @Override
    public int execute(VirtualFrame frame) {
        BytecodeNode root = getBytecodesNode();
        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        : nullCheck(root.peekAndReleaseObject(frame, top - 1));
        int resultAt = inlinedMethod.isStatic() ? top : (top - 1);
        return (resultAt - top) + getFieldNode.getField(frame, root, receiver, resultAt);
    }

    private static Field getInlinedField(Method inlinedMethod) {
        BytecodeStream code = new BytecodeStream(inlinedMethod.getCode());
        if (inlinedMethod.isStatic()) {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(STATIC_GETTER_BCI));
        } else {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(INSTANCE_GETTER_BCI));
        }
    }
}
