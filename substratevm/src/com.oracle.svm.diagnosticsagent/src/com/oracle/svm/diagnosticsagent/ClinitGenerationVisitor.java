/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.diagnosticsagent;

import com.oracle.svm.jvmtiagentbase.JvmtiAgentBase;

import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public class ClinitGenerationVisitor extends ClassVisitor {
    private boolean hasClinit = false;
    private boolean shouldInstrument = true;

    public ClinitGenerationVisitor(int api, ClassWriter writer) {
        super(api, writer);
    }

    public boolean didGeneration() {
        return shouldInstrument && !hasClinit;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        NativeImageDiagnosticsAgent agent = JvmtiAgentBase.singleton();
        shouldInstrument = agent.advisor.shouldTraceClassInitialization(name.replace("/", "."));
    }

    @Override
    public void visitEnd() {
        if (!this.hasClinit) {
            MethodVisitor mv = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitEnd();
        }

        super.visitEnd();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        boolean isClinitMethod = "<clinit>".equals(name);
        this.hasClinit = this.hasClinit || isClinitMethod;
        return methodVisitor;
    }
}
