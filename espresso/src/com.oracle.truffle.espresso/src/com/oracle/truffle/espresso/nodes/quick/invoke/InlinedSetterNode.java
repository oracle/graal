/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.helper.AbstractSetFieldNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.perf.DebugCounter;

public class InlinedSetterNode extends QuickNode {

    private static final DebugCounter setterNodes = DebugCounter.create("setters: ");
    private static final DebugCounter leafSetterNodes = DebugCounter.create("leaf set: ");

    private static final int INSTANCE_SETTER_BCI = 2;
    private static final int STATIC_SETTER_BCI = 1;

    final Field field;
    final Method inlinedMethod;
    protected final int stackEffect;
    final int slotCount;
    protected final int statementIndex;

    @Child AbstractSetFieldNode setFieldNode;

    InlinedSetterNode(Method inlinedMethod, int top, int opcode, int callerBCI, int statementIndex) {
        super(top, callerBCI);
        this.inlinedMethod = inlinedMethod;
        this.field = getInlinedField(inlinedMethod);
        this.slotCount = field.getKind().getSlotCount();
        this.stackEffect = Bytecodes.stackEffectOf(opcode);
        this.statementIndex = statementIndex;
        setFieldNode = AbstractSetFieldNode.create(this.field);
        assert field.isStatic() == inlinedMethod.isStatic();
    }

    public static InlinedSetterNode create(Method inlinedMethod, int top, int opCode, int curBCI, int statementIndex) {
        setterNodes.inc();
        if (inlinedMethod.isFinalFlagSet() || inlinedMethod.getDeclaringKlass().isFinalFlagSet()) {
            return new InlinedSetterNode(inlinedMethod, top, opCode, curBCI, statementIndex);
        } else {
            leafSetterNodes.inc();
            return new LeafAssumptionSetterNode(inlinedMethod, top, opCode, curBCI, statementIndex);
        }
    }

    @Override
    public int execute(VirtualFrame frame) {
        BytecodeNode root = getBytecodeNode();
        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        : nullCheck(BytecodeNode.popObject(frame, top - 1 - slotCount));
        setFieldNode.setField(frame, root, receiver, top, statementIndex);
        return -slotCount + stackEffect;
    }

    private static Field getInlinedField(Method inlinedMethod) {
        BytecodeStream code = new BytecodeStream(inlinedMethod.getOriginalCode());
        if (inlinedMethod.isStatic()) {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(STATIC_SETTER_BCI));
        } else {
            return inlinedMethod.getRuntimeConstantPool().resolvedFieldAt(inlinedMethod.getDeclaringKlass(), code.readCPI(INSTANCE_SETTER_BCI));
        }
    }
}
