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
package com.oracle.svm.hosted.agent;

import java.util.function.Consumer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/*
 * Note: no LambdaMetaFactory (e.g., Java lambdas) in this file.
 */
public class InnerClassLambdaMetaFactoryRewriter extends ClassVisitor {

    private int numberOfRewrites = 0;
    private Consumer<Boolean> completeConsumer;

    public InnerClassLambdaMetaFactoryRewriter(ClassWriter writer, Consumer<Boolean> consumer) {
        super(Opcodes.ASM7, writer);
        this.completeConsumer = consumer;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals("spinInnerClass")) {
            return new SpinInnerClassVisitor(methodVisitor);
        } else if (name.equals("buildCallSite")) {
            return new BuildCallSiteVisitor(methodVisitor);
        } else {
            return methodVisitor;
        }
    }

    public class SpinInnerClassVisitor extends MethodVisitor {
        SpinInnerClassVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM7, methodVisitor);
        }

        private boolean reachedGenerateConstructor = false;

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (opcode == Opcodes.IFEQ && reachedGenerateConstructor) {
                reachedGenerateConstructor = false;
                super.visitInsn(Opcodes.POP);
                super.visitInsn(Opcodes.ICONST_1);
                super.visitJumpInsn(opcode, label);
                rewriteCompleted();
            } else {
                super.visitJumpInsn(opcode, label);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals("generateConstructor")) {
                reachedGenerateConstructor = true;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    public class BuildCallSiteVisitor extends MethodVisitor {

        BuildCallSiteVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM7, methodVisitor);
        }

        private boolean firstNE = true;

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (opcode == Opcodes.IFNE && firstNE) {
                firstNE = false;
                super.visitInsn(Opcodes.POP);
                super.visitInsn(Opcodes.ICONST_1);
                super.visitJumpInsn(opcode, label);
                rewriteCompleted();
            } else {
                super.visitJumpInsn(opcode, label);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals("ensureClassInitialized")) {
                Label label = new Label();
                super.visitInsn(Opcodes.POP);
                super.visitInsn(Opcodes.POP);
                super.visitJumpInsn(Opcodes.GOTO, label);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                super.visitLabel(label);
                rewriteCompleted();
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        completeConsumer.accept(numberOfRewrites == 3);
    }

    private void rewriteCompleted() {
        numberOfRewrites += 1;
    }
}
