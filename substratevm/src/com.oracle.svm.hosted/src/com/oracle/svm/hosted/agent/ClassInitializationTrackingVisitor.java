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

import static com.oracle.svm.hosted.agent.NativeImageBytecodeInstrumentationAgent.getJavaVersion;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ASM5;
import static jdk.internal.org.objectweb.asm.Opcodes.GETSTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.IFEQ;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageClassLoader;

import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

public class ClassInitializationTrackingVisitor extends ClassVisitor {

    private final ClassLoader loader;
    private final String className;
    private String moduleName;
    private boolean hasClinit;
    private boolean ldcClassLiteralSupported;

    public ClassInitializationTrackingVisitor(String moduleName, ClassLoader loader, String className, ClassWriter writer) {
        super(ASM5, writer);
        this.moduleName = moduleName;
        this.hasClinit = false;
        this.loader = loader;
        this.className = className;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        ldcClassLiteralSupported = (version & 0x0000FFFF) >= Opcodes.V1_5;
    }

    @Override
    public void visitEnd() {
        if (!hasClinit && instrumentationSupported() && clinitInstrumentationSupported()) {
            MethodVisitor mv = visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
        }
        super.visitEnd();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        boolean isClinitMethod = "<clinit>".equals(name);
        hasClinit = hasClinit || isClinitMethod;
        if (instrumentationSupported()) {
            if (isClinitMethod && clinitInstrumentationSupported()) {
                return new ClassInitializerMethod(methodVisitor);
            } else if (initInstrumentationSupported(name)) {
                return new ClassConstructorMethod(methodVisitor);
            } else {
                return methodVisitor;
            }
        } else {
            return methodVisitor;
        }

    }

    private boolean clinitInstrumentationSupported() {
        return ldcClassLiteralSupported;
    }

    private static Set<String> trackedJDKClasses = new HashSet<>(Arrays.asList(
                    "java/lang/Thread",
                    "java/util/zip/ZipFile",
                    "java/nio/MappedByteBuffer",
                    "java/io/FileDescriptor"));

    private boolean initInstrumentationSupported(String name) {
        if (!"<init>".equals(name)) {
            return false;
        }

        /*
         * JDK 9+ not supported. Our instrumentation is not visible from the JDK modules.
         */
        if (getJavaVersion() > 8) {
            return false;
        }

        /* We track all user classes and JDK classes that must not end up in the image heap. */
        if (loader instanceof NativeImageClassLoader) {
            return true;
        } else {
            if (trackedJDKClasses.contains(className)) {
                return true;
            } else {
                return className.contains("java/nio") && className.contains("Buffer");
            }
        }
    }

    private boolean instrumentationSupported() {
        if (getJavaVersion() == 8) {
            return loader != null && className != null &&
                            /* The class literal throws a NoClassDefFound error. */
                            !className.startsWith("sun/reflect/Generated");
        } else if (getJavaVersion() > 8) {
            return !(moduleName == null ||
                            moduleName.startsWith("java.") ||
                            moduleName.startsWith("jdk."));
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    private static String toInternalName(String className) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('L');
        int nameLength = className.length();
        for (int i = 0; i < nameLength; ++i) {
            char car = className.charAt(i);
            stringBuilder.append(car == '.' ? '/' : car);
        }
        stringBuilder.append(';');
        return stringBuilder.toString();
    }

    private static void guardedTrackingCall(MethodVisitor mv, String methodName, Runnable pushOperands, String descriptor) {
        String trackingClass = ClassInitializationTracking.class.getName().replace('.', '/');
        mv.visitFieldInsn(GETSTATIC, trackingClass, "IS_IMAGE_BUILD_TIME", "Z");
        Label l1 = new Label();
        mv.visitJumpInsn(IFEQ, l1);
        pushOperands.run();
        mv.visitMethodInsn(INVOKESTATIC, trackingClass, methodName, descriptor, false);
        mv.visitLabel(l1);
    }

    public class ClassInitializerMethod extends MethodVisitor {
        ClassInitializerMethod(MethodVisitor methodVisitor) {
            super(ASM5, methodVisitor);
        }

        @Override
        public void visitCode() {
            guardedTrackingCall(mv, "reportClassInitialized", () -> mv.visitLdcInsn(Type.getType(toInternalName(className))), "(Ljava/lang/Class;)V");
            mv.visitCode();
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack == 0 ? 1 : maxStack, maxLocals);
        }
    }

    public class ClassConstructorMethod extends MethodVisitor {
        ClassConstructorMethod(MethodVisitor methodVisitor) {
            super(ASM5, methodVisitor);
        }

        @Override
        public void visitInsn(int opcode) {
            assert opcode != Opcodes.ARETURN && opcode != Opcodes.IRETURN && opcode != Opcodes.FRETURN && opcode != Opcodes.LRETURN && opcode != Opcodes.DRETURN : "Constructor can only return void";
            if (opcode == Opcodes.RETURN) {
                guardedTrackingCall(mv, "reportObjectInstantiated", () -> mv.visitVarInsn(ALOAD, 0), "(Ljava/lang/Object;)V");
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack == 0 ? 1 : maxStack, maxLocals);
        }
    }

}
