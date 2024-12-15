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

import com.oracle.svm.shaded.org.objectweb.asm.Type;
import com.oracle.svm.shaded.org.objectweb.asm.tree.AbstractInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.FieldInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.LdcInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.MethodInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.Frame;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Set;

import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.GETSTATIC;
import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.LDC;

public class ConstantClassAnalyzer extends ConstantValueAnalyzer {

    public ConstantClassAnalyzer(AbstractInsnNode[] instructions, Frame<SourceValue>[] frames, Set<MethodInsnNode> constantCalls) {
        super(instructions, frames, constantCalls);
    }

    @Override
    protected boolean isConstant(SourceValue value, AbstractInsnNode sourceInstruction, Frame<SourceValue> sourceInstructionFrame) {
        return switch (sourceInstruction.getOpcode()) {
            case LDC -> {
                LdcInsnNode ldc = (LdcInsnNode) sourceInstruction;
                yield ldc.cst instanceof Type type && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY);
            }
            case GETSTATIC -> {
                FieldInsnNode field = (FieldInsnNode) sourceInstruction;
                yield isPrimitiveType(field);
            }
            default -> false;
        };
    }

    private static boolean isPrimitiveType(FieldInsnNode field) {
        if (!field.name.equals("TYPE")) {
            return false;
        }

        return switch (field.owner) {
            case "java/lang/Byte", "java/lang/Char", "java/lang/Short", "java/lang/Integer", "java/lang/Long", "java/lang/Float", "java/lang/Double", "java/lang/Void" -> true;
            default -> false;
        };
    }

    @Override
    protected String typeDescriptor() {
        return "Ljava/lang/Class;";
    }
}
