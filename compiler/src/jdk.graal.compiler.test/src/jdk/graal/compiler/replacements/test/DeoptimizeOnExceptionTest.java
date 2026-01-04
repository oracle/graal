/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.JAVA_5_VERSION;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.DiscontinuedInstruction.JsrInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction.RetInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.AbstractInliningPhase;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that deoptimization upon exception handling works.
 */
public class DeoptimizeOnExceptionTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    @SuppressWarnings("this-escape")
    public DeoptimizeOnExceptionTest() {
        createSuites(getInitialOptions()).getHighTier().findPhase(AbstractInliningPhase.class).remove();
    }

    private static void raiseException(String m1, String m2, String m3, String m4, String m5) {
        throw new RuntimeException(m1 + m2 + m3 + m4 + m5);
    }

    @Test
    public void test1() {
        test("test1Snippet", "m1", "m2", "m3", "m4", "m5");
    }

    // no local exception handler - will deopt
    public static String test1Snippet(String m1, String m2, String m3, String m4, String m5) {
        if (m1 != null) {
            raiseException(m1, m2, m3, m4, m5);
        }
        return m1 + m2 + m3 + m4 + m5;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test2() {
        try {
            Class<Runnable> testClass = (Class<Runnable>) getClass(DeoptimizeOnExceptionTest.class.getName() + "$" + "TestJSR");
            Runnable r = testClass.getDeclaredConstructor().newInstance();
            ct = Long.MAX_VALUE;
            // warmup
            for (int i = 0; i < 100; i++) {
                r.run();
            }
            ct = 0;
            InstalledCode compiledMethod = getCode(getResolvedJavaMethod(testClass, "run"));
            compiledMethod.executeVarargs(r);
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            Assert.fail();
        }
    }

    public static void methodB() {
        // Application code to be compiled. No need to use
        // GraalCompilerTest.getRandomInstance()
        Random r = new Random(System.currentTimeMillis());
        while (r.nextFloat() > .03f) {
            // Empty
        }

        return;
    }

    public static void methodA() {
        // Application code to be compiled. No need to use
        // GraalCompilerTest.getRandomInstance()
        Random r = new Random(System.currentTimeMillis());
        while (r.nextDouble() > .05) {
            // Empty
        }
        return;
    }

    private static Object m = new Object();
    static long ct = Long.MAX_VALUE;

    public static Object getM() {
        if (ct-- > 0) {
            return m;
        } else {
            return null;
        }
    }

    @Override
    public byte[] generateClass(String className) {
        ClassDesc outerClass = cd(DeoptimizeOnExceptionTest.class);

        // @formatter:off
        return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                        .withVersion(JAVA_5_VERSION, 0)
                        .withInterfaceSymbols(cd(Runnable.class))
                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, b -> b
                                        .aload(0)
                                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                        .return_())
                        .withMethodBody("run", MTD_void, ACC_PUBLIC, b -> {
                            Label l1 = b.newLabel();
                            b
                                            .invokestatic(outerClass, "getM", MethodTypeDesc.of(CD_Object))
                                            .with(JsrInstruction.of(l1))
                                            .return_()
                                            .labelBinding(l1)
                                            .astore(1)
                                            .invokestatic(cd(System.class), "currentTimeMillis", MethodTypeDesc.of(CD_long))
                                            .pop2()
                                            .invokestatic(outerClass, "getM", MethodTypeDesc.of(CD_Object))
                                            .dup()
                                            .ifThenElse(Opcode.IFNONNULL,
                                                            thenBlock -> thenBlock.invokestatic(outerClass, "methodA", MTD_void),
                                                            elseBlock -> elseBlock.invokestatic(outerClass, "methodB", MTD_void))
                                            .with(RetInstruction.of(1));
                        }));
        // @formatter:on
    }

    @Test
    public void test3() {
        ResolvedJavaMethod method = getResolvedJavaMethod("test3Snippet");

        for (int i = 0; i < 2; i++) {
            Result actual;
            boolean expectedCompiledCode = (method.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.NotCompiledExceptionHandler) != 0);
            InstalledCode code = getCode(method, null, false, true, new OptionValues(getInitialOptions(), HighTier.Options.Inline, false));
            assertTrue(code.isValid());

            try {
                actual = new Result(code.executeVarargs(false), null);
            } catch (Exception e) {
                actual = new Result(null, e);
            }

            assertTrue(i > 0 == expectedCompiledCode, "expect compiled code to stay around after the first iteration");
            assertEquals(new Result(expectedCompiledCode, null), actual);
            assertTrue(expectedCompiledCode == code.isValid());
        }
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getName().equals("throwException")) {
            if (b.getMethod().getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.NotCompiledExceptionHandler) != 0) {
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else {
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }

    private static void throwException() throws Exception {
        throw new Exception();
    }

    static int footprint;

    public static boolean test3Snippet(boolean rethrowException) throws Exception {
        try {
            footprint = 1;
            throwException();
        } catch (Exception e) {
            footprint = 2;
            if (rethrowException) {
                throw e;
            }
        }

        return GraalDirectives.inCompiledCode();
    }
}
