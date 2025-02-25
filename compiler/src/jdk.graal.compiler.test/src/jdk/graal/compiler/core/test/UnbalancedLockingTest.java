/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationTargetException;

import org.junit.Ignore;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.options.OptionValues;

/**
 * Test class exercising irreducible loop duplication logic in {@link BciBlockMapping} in
 * conjunction with locking and exception handlers.
 */
public class UnbalancedLockingTest extends GraalCompilerTest implements CustomizedBytecodePattern {

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
    public byte[] generateClass(String internalClassName) {
        ClassDesc thisClass = ClassDesc.of(internalClassName);

        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                        .withMethod("test", MD_VOID, ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .withCode(UnbalancedLockingTest::createTestMethod))
                        .withMethod("snippet", MethodTypeDesc.of(CD_void, CD_int, CD_Object, CD_Object), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .withCode(UnbalancedLockingTest::createIllegalLockingMethod))
                        .withMethod("bar", MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_Object, CD_Object), ACC_PUBLIC_STATIC, methodBuilder -> methodBuilder
                                        .withCode(b -> createIrreducibleMethod(thisClass, b))));
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
    private static void createIrreducibleMethod(ClassDesc thisClass, CodeBuilder codeBuilder) {
        Label irreducible = codeBuilder.newLabel();
        Label loopHeader1 = codeBuilder.newLabel();
        Label loopHeader2 = codeBuilder.newLabel();

        codeBuilder
                        .iload(0)
                        .bipush(12)
                        .ifThenElse(Opcode.IF_ICMPGE,
                                        b -> b.aload(2).astore(6),
                                        b -> b.aload(3).astore(6))
                        .iconst_0()
                        .istore(3)
                        .iload(0)
                        .iload(1)
                        .ifThenElse(Opcode.IF_ICMPEQ,
                                        b -> b
                                                        .labelBinding(loopHeader1)
                                                        .iload(3)
                                                        .iload(0)
                                                        .ifThen(Opcode.IF_ICMPLT, b1 -> b1
                                                                        .iinc(3, 1)
                                                                        .trying(
                                                                                        bcb -> bcb
                                                                                                        .aload(6)
                                                                                                        .monitorenter()
                                                                                                        .invokestatic(thisClass, "test", MD_VOID)
                                                                                                        .aload(6)
                                                                                                        .monitorexit(),
                                                                                        cb -> cb.catchingAll(bcb -> bcb
                                                                                                        .aload(6)
                                                                                                        .monitorexit()
                                                                                                        .athrow()))
                                                                        .labelBinding(irreducible)
                                                                        .goto_(loopHeader1)),
                                        b -> b
                                                        .labelBinding(loopHeader2)
                                                        .iload(3)
                                                        .iload(0)
                                                        .ifThen(Opcode.IF_ICMPLT, b1 -> b1
                                                                        .iinc(3, 1)
                                                                        .goto_(loopHeader2))
                                                        .aload(2)
                                                        .ifnonnull(irreducible))
                        .return_();
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
    private static void createIllegalLockingMethod(CodeBuilder codeBuilder) {
        codeBuilder
                        .iload(0)
                        .ifThenElse(
                                        b -> b
                                                        .aload(1)
                                                        .astore(3)
                                                        .aload(1)
                                                        .monitorenter(),
                                        b -> b
                                                        .aload(2)
                                                        .astore(3)
                                                        .aload(2)
                                                        .monitorenter())
                        .aload(3)
                        .monitorexit()
                        .return_();
    }

    /**
     * Create bytecodes resembling the following pseudo code.
     *
     * <pre>
     * public static void test() {
     * }
     * </pre>
     */
    private static void createTestMethod(CodeBuilder codeBuilder) {
        codeBuilder.return_();
    }
}
