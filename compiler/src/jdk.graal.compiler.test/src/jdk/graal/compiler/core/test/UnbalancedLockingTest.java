/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Ignore;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Test class exercising irreducible loop duplication logic in {@link BciBlockMapping} in
 * conjunction with locking and exception handlers.
 */
public class UnbalancedLockingTest extends CustomizedBytecodePatternTest {

    /**
     * Run many calls to a test method to force a c1/c2 compile, check with -XX:+PrintCompilation.
     */
    private static boolean CheckC1C2 = false;

    @Test
    @Ignore("Bytecode merging two different locked objects to a single unlock (not parsable with hs compilers)")
    public void testPhiMonitorObject() throws ClassNotFoundException {
        Class<?> testClass = getClass(UnbalancedLockingTest.class.getName() + "$" + "ABC");
        if (CheckC1C2) {
            // call often to ensure compilation with c1/c2
            for (int i = 0; i < 50000; i++) {
                try {
                    testClass.getMethod("snippet", int.class, Object.class, Object.class).invoke(null, 1, new Object(), new Object());
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                }
            }
        }
        test(getResolvedJavaMethod(testClass, "snippet"), null, 1, new Object(), new Object());
    }

    @Test
    public void testIrreducibleLoop() throws ClassNotFoundException {
        Class<?> testClass = getClass(UnbalancedLockingTest.class.getName() + "$" + "ABC");
        OptionValues opt = new OptionValues(getInitialOptions(), BytecodeParserOptions.InlineDuringParsing, false, GraalOptions.StressInvokeWithExceptionNode, true);
        test(opt, getResolvedJavaMethod(testClass, "bar"), null, 1, 42, new Object(), new Object());
    }

    /**
     * Create a class containing illegal/irrecudible locking patterns that penetrate
     * {@linkp BciBlockMapping.Options#DuplicateIrreducibleLoops}.
     */
    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(52, ACC_SUPER | ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);
        createTestMethod(cw);
        createIllegalLockingMethod(cw);
        createIrreducibleMethod(cw);
        cw.visitEnd();
        // Checkstyle: resume
        return cw.toByteArray();
    }

    /**
     * Create bytecodes resembling the following pseudo code.
     *
     * <pre>
     * public static void bar(int bound, int check, Object o, Object o2) {
     *     Object lock;
     *     if (bound > 12) {
     *         lock = o;
     *     } else {
     *         lock = o2;
     *     }
     *
     *     int i = 0;
     *     if (bound == check) {
     *         while (i < bound) {
     *             i++;
     *             synchronized (lock) {
     *                 test(); // with exc handler
     *             }
     *             label:
     *         }
     *     } else {
     *         while (i < bound) {
     *             i++;
     *         }
     *         if (o != null) {
     *              goto label:
     *         }
     *     }
     *     excpHandler:
     *        unlock(lock)
     * }
     * </pre>
     */
    private static void createIrreducibleMethod(ClassWriter cw) {
        // Checkstyle: stop
        {

            MethodVisitor snippet = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "bar", "(IILjava/lang/Object;Ljava/lang/Object;)V", null, null);
            Label assignmentEnd = new Label();
            Label assignmentElse = new Label();

            snippet.visitVarInsn(ILOAD, 0);
            snippet.visitIntInsn(BIPUSH, 12);
            snippet.visitJumpInsn(IF_ICMPLE, assignmentElse);
            {
                snippet.visitVarInsn(ALOAD, 2);
                snippet.visitVarInsn(ASTORE, 6);
                snippet.visitJumpInsn(GOTO, assignmentEnd);
            }
            snippet.visitLabel(assignmentElse);
            {

                snippet.visitVarInsn(ALOAD, 3);
                snippet.visitVarInsn(ASTORE, 6);
            }

            snippet.visitLabel(assignmentEnd);

            Label exceptionHandler = new Label();
            Label irreducible = new Label();
            Label loopHeader1 = new Label();
            Label end1 = new Label();
            Label loopHeader2 = new Label();
            Label end2 = new Label();

            snippet.visitCode();

            snippet.visitInsn(ICONST_0);
            snippet.visitVarInsn(ISTORE, 3);
            snippet.visitVarInsn(ILOAD, 0);
            snippet.visitVarInsn(ILOAD, 1);
            Label elseBranch = new Label();
            snippet.visitJumpInsn(IF_ICMPNE, elseBranch);
            {
                snippet.visitLabel(loopHeader1);
                {
                    snippet.visitVarInsn(ILOAD, 3);
                    snippet.visitVarInsn(ILOAD, 0);
                    snippet.visitJumpInsn(IF_ICMPGE, end1);
                    snippet.visitIincInsn(3, 1);

                    Label exceptionStart = new Label();
                    Label exceptionEnd = new Label();

                    snippet.visitTryCatchBlock(exceptionStart, exceptionEnd, exceptionHandler, null);

                    snippet.visitLabel(exceptionStart);

                    snippet.visitVarInsn(ALOAD, 6);
                    snippet.visitInsn(MONITORENTER);

                    snippet.visitMethodInsn(INVOKESTATIC, Type.getInternalName(UnbalancedLockingTest.class) + "$ABC", "test", "()V", false);

                    snippet.visitVarInsn(ALOAD, 6);
                    snippet.visitInsn(MONITOREXIT);
                    snippet.visitLabel(irreducible);

                    snippet.visitLabel(exceptionEnd);

                    snippet.visitJumpInsn(GOTO, loopHeader1);
                }
                snippet.visitLabel(end1);
                snippet.visitInsn(RETURN);
            }
            snippet.visitLabel(elseBranch);
            {
                Label secondEnd = new Label();
                snippet.visitLabel(loopHeader2);
                {
                    snippet.visitVarInsn(ILOAD, 3);
                    snippet.visitVarInsn(ILOAD, 0);
                    snippet.visitJumpInsn(IF_ICMPGE, end2);
                    snippet.visitIincInsn(3, 1);
                    snippet.visitJumpInsn(GOTO, loopHeader2);
                }
                snippet.visitLabel(end2);
                snippet.visitVarInsn(ALOAD, 2);
                snippet.visitJumpInsn(IFNULL, secondEnd);
                {
                    // irreducible jump into other loops body
                    snippet.visitJumpInsn(GOTO, irreducible);
                }
                snippet.visitLabel(secondEnd);
                snippet.visitInsn(RETURN);
            }

            snippet.visitLabel(exceptionHandler);
            {
                snippet.visitVarInsn(ALOAD, 6);
                snippet.visitInsn(MONITOREXIT);
                snippet.visitInsn(ATHROW);
            }

            snippet.visitMaxs(1, 4);
            snippet.visitEnd();
        }
        // Checkstyle: resume
    }

    /**
     * Create bytecodes resembling the following pseudo code.
     *
     * <pre>
     * public static void snippet(int i, Object o1, Object o2) {
     *     Object phi;
     *     if (i == 0) {
     *         // lock o1
     *         phi = o1;
     *         monitorEnter(o1)
     *     } else {
     *         phi = o2;
     *         monitorEnter(o2)
     *     }
     *     monitorexit(phi)
     * }
     * </pre>
     */
    private static void createIllegalLockingMethod(ClassWriter cw) {
        // Checkstyle: stop
        {
            MethodVisitor snippet = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "snippet", "(ILjava/lang/Object;Ljava/lang/Object;)V", null, null);
            snippet.visitCode();
            snippet.visitVarInsn(ILOAD, 0);

            Label falseBranch = new Label();
            Label merge = new Label();

            snippet.visitJumpInsn(IFNE, falseBranch);
            {// true branch
                snippet.visitVarInsn(ALOAD, 1);
                snippet.visitVarInsn(ASTORE, 3);

                snippet.visitVarInsn(ALOAD, 1);
                snippet.visitInsn(MONITORENTER);

                snippet.visitJumpInsn(GOTO, merge);
            }
            {// false branch
                snippet.visitLabel(falseBranch);
                snippet.visitVarInsn(ALOAD, 2);
                snippet.visitVarInsn(ASTORE, 3);

                snippet.visitVarInsn(ALOAD, 2);
                snippet.visitInsn(MONITORENTER);
            }
            snippet.visitLabel(merge);

            snippet.visitVarInsn(ALOAD, 3);
            snippet.visitInsn(MONITOREXIT);

            snippet.visitInsn(RETURN);
            snippet.visitMaxs(1, 4);
            snippet.visitEnd();
        }
        // Checkstyle: resume
    }

    /**
     * Create bytecodes resembling the following pseudo code.
     *
     * <pre>
     * public static void test() {
     * }
     * </pre>
     */
    private static void createTestMethod(ClassWriter cw) {
        // Checkstyle: stop
        {
            MethodVisitor snippet = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", "()V", null, null);
            snippet.visitCode();
            snippet.visitInsn(RETURN);
            snippet.visitMaxs(1, 4);
            snippet.visitEnd();
        }
        // Checkstyle: resume
    }

}
