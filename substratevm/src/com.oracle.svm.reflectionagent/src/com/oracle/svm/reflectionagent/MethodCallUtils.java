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
package com.oracle.svm.reflectionagent;

import com.oracle.svm.shaded.org.objectweb.asm.Type;
import com.oracle.svm.shaded.org.objectweb.asm.tree.MethodInsnNode;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.Frame;
import com.oracle.svm.shaded.org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.oracle.svm.shaded.org.objectweb.asm.Opcodes.INVOKESTATIC;

public final class MethodCallUtils {

    private MethodCallUtils() {

    }

    public record Signature(String owner, String name, String desc) {

        public Signature(MethodInsnNode methodCall) {
            this(methodCall.owner, methodCall.name, methodCall.desc);
        }

        public Signature(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) {
            this(Type.getInternalName(owner), name, buildDescriptor(returnType, parameterTypes));
        }

        private static String buildDescriptor(Class<?> returnType, Class<?>... parameterTypes) {
            return "(" + Arrays.stream(parameterTypes).map(Type::getDescriptor).collect(Collectors.joining()) + ")" + Type.getDescriptor(returnType);
        }
    }

    public static SourceValue getCallArg(MethodInsnNode call, int argIdx, Frame<SourceValue> frame) {
        int numOfArgs = Type.getArgumentTypes(call.desc).length + (call.getOpcode() == INVOKESTATIC ? 0 : 1);
        int stackPos = frame.getStackSize() - numOfArgs + argIdx;
        return frame.getStack(stackPos);
    }

    public static String getTypeDescOfArg(MethodInsnNode methodCall, int argIdx) {
        if (methodCall.getOpcode() != INVOKESTATIC) {
            return argIdx == 0 ? "L" + methodCall.owner + ";" : Type.getArgumentTypes(methodCall.desc)[argIdx - 1].getDescriptor();
        } else {
            return Type.getArgumentTypes(methodCall.desc)[argIdx].getDescriptor();
        }
    }
}
