/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.reflectionagent.analyzers;

import com.oracle.svm.shaded.org.objectweb.asm.tree.AbstractInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.MethodInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.VarInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.Frame;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Arrays;
import java.util.Set;

import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ALOAD;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.ASTORE;

public abstract class ConstantValueAnalyzer {

    private final AbstractInsnNode[] instructions;
    private final Frame<SourceValue>[] frames;
    private final Set<MethodInsnNode> constantCalls;

    public ConstantValueAnalyzer(AbstractInsnNode[] instructions, Frame<SourceValue>[] frames, Set<MethodInsnNode> constantCalls) {
        this.instructions = instructions;
        this.frames = frames;
        this.constantCalls = constantCalls;
    }

    public boolean isConstant(SourceValue value) {
        if (value.insns.size() != 1) {
            return false;
        }

        AbstractInsnNode sourceInstruction = value.insns.iterator().next();
        int sourceInstructionIndex = Arrays.asList(instructions).indexOf(sourceInstruction);
        Frame<SourceValue> sourceInstructionFrame = frames[sourceInstructionIndex];

        if (sourceInstruction.getOpcode() == ALOAD) {
            SourceValue sourceValue = sourceInstructionFrame.getLocal(((VarInsnNode) sourceInstruction).var);
            return isConstant(sourceValue);
        } else if (sourceInstruction.getOpcode() == ASTORE) {
            SourceValue sourceValue = sourceInstructionFrame.getStack(sourceInstructionFrame.getStackSize() - 1);
            return isConstant(sourceValue);
        } else if (sourceInstruction instanceof MethodInsnNode methodCall) {
            return constantCalls.contains(methodCall);
        }

        return isConstant(value, sourceInstruction, sourceInstructionFrame);
    }

    protected abstract boolean isConstant(SourceValue value, AbstractInsnNode sourceInstruction, Frame<SourceValue> sourceInstructionFrame);

    protected abstract String typeDescriptor();
}
