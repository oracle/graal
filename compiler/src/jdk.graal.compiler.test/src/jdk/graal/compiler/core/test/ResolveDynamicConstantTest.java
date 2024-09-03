/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ResolveDynamicConstantTest extends CustomizedBytecodePatternTest {

    private static final int PUBLIC_STATIC = ACC_PUBLIC | ACC_STATIC;

    @Test
    public void test00601m001() throws Throwable {
        runTest("test.resolveDynamicConstant00601m001");
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000_000; i++) {
            Instant expected = Instant.now(Clock.systemUTC());
            Instant test = Instant.now();
            long diff = Math.abs(test.toEpochMilli() - expected.toEpochMilli());
            if (diff >= 100) {
                System.out.printf("%d: %d%n", i, diff);
            }
        }
    }

    @Test
    public void test00602m008() throws Throwable {
        runTest("test.resolveDynamicConstant00602m008");
    }

    static void resolveDynamicConstant00601m001Gen(String internalClassName, ClassWriter cw) {
        FieldVisitor fv = cw.visitField(PUBLIC_STATIC, "bsmInvocationCount", "I", null, null);
        fv.visitEnd();

        String sig;
        Handle handle;

        sig = "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)I";
        handle = new Handle(H_INVOKESTATIC, internalClassName, "getConstant", sig, false);
        ConstantDynamic iconst = new ConstantDynamic("constantdynamic", "I", handle);

        MethodVisitor run = cw.visitMethod(PUBLIC_STATIC, "run", "()Z", null, null);
        run.visitFieldInsn(GETSTATIC, internalClassName, "bsmInvocationCount", "I");
        Label labelFalse = new Label();
        run.visitJumpInsn(IFNE, labelFalse);
        run.visitLdcInsn(iconst);
        run.visitInsn(POP);
        run.visitFieldInsn(GETSTATIC, internalClassName, "bsmInvocationCount", "I");
        run.visitJumpInsn(IFEQ, labelFalse);
        run.visitInsn(ICONST_1);
        run.visitInsn(IRETURN);
        run.visitLabel(labelFalse);
        run.visitInsn(ICONST_0);
        run.visitInsn(IRETURN);
        run.visitMaxs(0, 0);
        run.visitEnd();

        MethodVisitor getConstant = cw.visitMethod(PUBLIC_STATIC | ACC_VARARGS, "getConstant", "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)I", null, null);
        getConstant.visitFieldInsn(GETSTATIC, internalClassName, "bsmInvocationCount", "I");
        getConstant.visitInsn(ICONST_1);
        getConstant.visitInsn(IADD);
        getConstant.visitFieldInsn(PUTSTATIC, internalClassName, "bsmInvocationCount", "I");
        getConstant.visitInsn(ICONST_1);
        getConstant.visitInsn(IRETURN);
        getConstant.visitMaxs(0, 0);
        getConstant.visitEnd();
    }

    static void resolveDynamicConstant00602m008Gen(String internalClassName, ClassWriter cw) {
        FieldVisitor fv = cw.visitField(PUBLIC_STATIC, "staticBSMInvocationCount", "I", null, null);
        fv.visitEnd();

        String sig;
        Handle handle;

        sig = "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)D";
        handle = new Handle(H_INVOKESTATIC, internalClassName, "getStaticConstant", sig, false);
        ConstantDynamic dconst = new ConstantDynamic("constantdynamic", "D", handle);

        sig = "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)I";
        handle = new Handle(H_INVOKESTATIC, internalClassName, "getConstant", sig, false);
        ConstantDynamic iconst = new ConstantDynamic("constantdynamic", "I", handle, dconst);

        MethodVisitor run = cw.visitMethod(PUBLIC_STATIC, "run", "()Z", null, null);
        run.visitFieldInsn(GETSTATIC, internalClassName, "staticBSMInvocationCount", "I");
        Label labelFalse = new Label();
        run.visitJumpInsn(IFNE, labelFalse);
        run.visitLdcInsn(iconst);
        run.visitInsn(POP);
        run.visitFieldInsn(GETSTATIC, internalClassName, "staticBSMInvocationCount", "I");
        run.visitLdcInsn(Integer.valueOf(1));
        run.visitJumpInsn(IF_ICMPNE, labelFalse);
        run.visitInsn(ICONST_1);
        run.visitInsn(IRETURN);
        run.visitLabel(labelFalse);
        run.visitInsn(ICONST_0);
        run.visitInsn(IRETURN);
        run.visitMaxs(0, 0);
        run.visitEnd();

        MethodVisitor getConstant = cw.visitMethod(PUBLIC_STATIC | ACC_VARARGS, "getConstant", "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)I", null, null);
        getConstant.visitInsn(ICONST_1);
        getConstant.visitInsn(IRETURN);
        getConstant.visitMaxs(0, 0);
        getConstant.visitEnd();

        MethodVisitor getStaticConstant = cw.visitMethod(PUBLIC_STATIC | ACC_VARARGS, "getStaticConstant", "(Ljava/lang/invoke/MethodHandles$Lookup;[Ljava/lang/Object;)D", null, null);
        getStaticConstant.visitFieldInsn(GETSTATIC, internalClassName, "staticBSMInvocationCount", "I");
        getStaticConstant.visitInsn(ICONST_1);
        getStaticConstant.visitInsn(IADD);
        getStaticConstant.visitFieldInsn(PUTSTATIC, internalClassName, "staticBSMInvocationCount", "I");
        getStaticConstant.visitInsn(DCONST_1);
        getStaticConstant.visitInsn(DRETURN);
        getStaticConstant.visitMaxs(0, 0);
        getStaticConstant.visitEnd();
    }

    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(55, ACC_SUPER | ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        try {
            String simpleName = internalClassName.substring("test/".length());
            Method method = getClass().getDeclaredMethod(simpleName + "Gen", String.class, ClassWriter.class);
            method.invoke(this, internalClassName, cw);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        MethodVisitor main = cw.visitMethod(PUBLIC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        main.visitMethodInsn(INVOKESTATIC, internalClassName, "run", "()Z", false);
        main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Z)V", false);
        main.visitInsn(RETURN);
        main.visitMaxs(0, 0);
        main.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void runTest(String internalClassName) throws Throwable {
        Class<?> testClass = getClass(internalClassName);
        ResolvedJavaMethod run = getResolvedJavaMethod(testClass, "run");
        Result actual = executeActual(run, null);
        if (actual.exception != null) {
            throw new AssertionError(actual.exception);
        }
        Assert.assertTrue((Boolean) actual.returnValue);
    }
}
