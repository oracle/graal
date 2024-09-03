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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that the parser and compiler can handle bytecode where exception handlers are reachable
 * from normal control flow. Such bytecode can be the result of obfuscation or code shrinking tools.
 * For example:
 *
 * <pre>
 * try{
 *   foo()
 * } catch (Exception e) {
 *   x = baz(x);
 *   return x;
 * }
 * (...)
 * try{
 *   bar()
 * } catch (Exception e) {
 *   doSomething()
 *   x = baz(x);
 *   return x;
 * }
 * </pre>
 *
 * On bytecode level, the second exception handler can re-use the first handler:
 *
 * <pre>
 * try{
 *   bar()
 * } catch (Exception e) {
 *   doSomething()
 *   goto handlerFoo
 * }
 * </pre>
 *
 */
public class ExceptionHandlerReachabilityTest extends CustomizedBytecodePatternTest {

    @Test
    public void test() {
        testParseAndRun(SharedExceptionHandlerClass.class.getName(), "sharedExceptionHandlerMethod", new Class<?>[]{int.class});
    }

    public void testParseAndRun(String clazzName, String methodName, Class<?>[] args) {
        try {
            Class<?> testClass = getClass(clazzName);
            ResolvedJavaMethod method = asResolvedJavaMethod(testClass.getMethod(methodName, args));

            // test successful parsing
            parseEager(method, StructuredGraph.AllowAssumptions.YES, getInitialOptions());

            // test successful compilation + execution
            int actual = (int) test(method, null, 11).returnValue;
            int expected = SharedExceptionHandlerClass.sharedExceptionHandlerMethod(11);
            Assert.assertEquals(expected, actual);
        } catch (PermanentBailoutException e) {
            Assert.fail(e.getMessage());
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw GraalError.shouldNotReachHere(e);
        }

    }

    @Override
    protected byte[] generateClass(String className) {
        try {
            ClassReader classReader = new ClassReader(className);
            final ClassWriter cw = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            classReader.accept(new ClassVisitor(Opcodes.ASM9, cw) {

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                    if (name.equals("sharedExceptionHandlerMethod")) {
                        return new SharedExceptionHandlerReplacer(mv, className.replace('.', '/'));
                    }
                    return mv;
                }

            }, ClassReader.EXPAND_FRAMES);

            return cw.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SharedExceptionHandlerReplacer extends MethodVisitor {
        private final MethodVisitor mv;
        private final String clazzName;

        SharedExceptionHandlerReplacer(MethodVisitor methodVisitor, String clazzName) {
            super(ASM9, null);
            this.mv = methodVisitor;
            this.clazzName = clazzName;
        }

        @Override
        public void visitCode() {
            mv.visitCode();
            Label label0 = new Label();
            Label label1 = new Label();

            Label startEx1 = new Label();
            Label endEx1 = new Label();
            Label handlerEx1 = new Label();
            Label startEx2 = new Label();
            Label endEx2 = new Label();
            Label handlerEx2 = new Label();
            mv.visitVarInsn(ILOAD, 0);
            mv.visitVarInsn(ISTORE, 1);
            mv.visitTryCatchBlock(startEx1, endEx1, handlerEx1, "java/lang/IllegalArgumentException");
            mv.visitLabel(startEx1);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitMethodInsn(INVOKESTATIC, clazzName, "foo", "(I)I", false);
            mv.visitVarInsn(ISTORE, 1);
            mv.visitLabel(endEx1);
            mv.visitJumpInsn(GOTO, label0);
            mv.visitLabel(handlerEx1);
            // --- REMOVE storing exception to make stack frames compatible:
            // mv.visitVarInsn(ASTORE, 2);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitMethodInsn(INVOKESTATIC, clazzName, "baz", "(I)I", false);
            mv.visitVarInsn(ISTORE, 1);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitInsn(IRETURN);
            mv.visitLabel(label0);
            mv.visitTryCatchBlock(startEx2, endEx2, handlerEx2, "java/lang/NumberFormatException");
            mv.visitLabel(startEx2);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitMethodInsn(INVOKESTATIC, clazzName, "bar", "(I)I", false);
            mv.visitVarInsn(ISTORE, 1);
            mv.visitLabel(endEx2);
            mv.visitJumpInsn(GOTO, label1);
            mv.visitLabel(handlerEx2);
            // --- REMOVE storing exception to make stack frames compatible:
            // mv.visitVarInsn(ASTORE, 2);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitMethodInsn(INVOKESTATIC, clazzName, "doSomething", "(I)I", false);
            mv.visitVarInsn(ISTORE, 1);
            // --- ADD jump to first exception handler from within second exception handler:
            mv.visitJumpInsn(GOTO, handlerEx1);
            // --- REMOVE duplicate code from first handler:
            // mv.visitVarInsn(ILOAD, 1);
            // mv.visitMethodInsn(INVOKESTATIC, clazzName, "baz", "(I)I", false);
            // mv.visitVarInsn(ISTORE, 1);
            // mv.visitVarInsn(ILOAD, 1);
            // mv.visitInsn(IRETURN);
            mv.visitLabel(label1);
            mv.visitVarInsn(ILOAD, 1);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(1, 3);
            mv.visitEnd();
        }
    }

    public class SharedExceptionHandlerClass {

        /**
         * The bytecode of this method will be modified by {@link SharedExceptionHandlerReplacer}.
         * The modified bytecode contains a {@code goto} from within the second exception handler to
         * the first exception handler. This reduces the overall bytecode size due to code sharing.
         * The pattern is produced by code obfuscation tools, see [GR-47376].
         */
        public static int sharedExceptionHandlerMethod(int i) {
            int x = i;
            try {
                x = foo(x);
            } catch (IllegalArgumentException e1) {
                x = baz(x);
                return x;
            }
            try {
                x = bar(x);
            } catch (NumberFormatException e2) {
                x = doSomething(x);
                // The following code will be replaced by a goto to the first exception handler:
                x = baz(x);
                return x;
            }
            return x;
        }

        public static int foo(int x) throws IllegalArgumentException {
            if (x < 0) {
                throw new IllegalArgumentException();
            }

            return x * 10;
        }

        public static int bar(int x) throws NumberFormatException {
            if (x > 100) {
                throw new NumberFormatException();
            }

            return x * 1000;
        }

        public static int baz(int x) {
            return x * x;
        }

        public static int doSomething(int x) {
            return x * x;
        }
    }
}
