/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.agent.jdk8.lambda;

import static jdk.internal.org.objectweb.asm.Opcodes.ASM5;

import com.oracle.svm.hosted.NativeImageClassLoader;
import com.oracle.svm.hosted.agent.NativeImageBytecodeInstrumentationAgent;

import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;

public class LambdaMetaFactoryRewriteVisitor extends ClassVisitor {

    private final ClassLoader loader;
    private final String className;

    public LambdaMetaFactoryRewriteVisitor(ClassLoader loader, String className, ClassWriter writer) {
        super(ASM5, writer);
        this.loader = loader;
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (instrumentationSupported()) {
            return new LambdaMetaFactoryMethodVisitor(methodVisitor);
        } else {
            return methodVisitor;
        }
    }

    private boolean instrumentationSupported() {
        if (NativeImageBytecodeInstrumentationAgent.getJavaVersion() == 8) {
            return className != null && loader instanceof NativeImageClassLoader;
        } else {
            return false;
        }
    }

    public class LambdaMetaFactoryMethodVisitor extends MethodVisitor {
        LambdaMetaFactoryMethodVisitor(MethodVisitor methodVisitor) {
            super(ASM5, methodVisitor);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (isMetaFactoryCall(bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName())) {
                Handle handle = new Handle(bootstrapMethodHandle.getTag(), "com/oracle/svm/hosted/agent/jdk8/lambda/LambdaMetafactory", bootstrapMethodHandle.getName(),
                                bootstrapMethodHandle.getDesc());
                super.visitInvokeDynamicInsn(name, descriptor, handle, bootstrapMethodArguments);
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }

        private boolean isMetaFactoryCall(String owner, String name) {
            return owner.equals("java/lang/invoke/LambdaMetafactory") &&
                            (name.equals("metafactory") || name.equals("altMetafactory"));
        }
    }

}
