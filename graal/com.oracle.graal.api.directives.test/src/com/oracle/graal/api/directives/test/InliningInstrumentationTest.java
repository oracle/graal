/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.api.directives.test;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.api.directives.GraalDirectives;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Instrumentation will be invalidated when the target is removed. Hence, we use instrumentation to
 * guard the inlining decision.
 */
public class InliningInstrumentationTest extends GraalCompilerTest {

    public InliningInstrumentationTest() {
        HotSpotResolvedJavaMethod notInlinedMethod = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(Simple.class, "notInlinedMethod");
        notInlinedMethod.setNotInlineable();
    }

    static boolean notInlineFlag;

    public void resetFlag() {
        notInlineFlag = false;
    }

    public static void invokeSimpleOpSnippet(int v) {
        Simple obj = new Simple();

        obj.op(v);
        GraalDirectives.instrumentationBegin(-2);
        notInlineFlag = true;
        GraalDirectives.instrumentationEnd();
    }

    @Test
    public void testSimpleOp() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("invokeSimpleOpSnippet");
            executeExpected(method, null, 0); // ensure the method is fully resolved
            resetFlag();
            // trivial method whose node count is less than TrivialInliningSize(10) will be inlined.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs(0);
                Assert.assertFalse("Simple.op(I) shoud be inlined (trivial, nodeCount < 10)", notInlineFlag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void invokeComplexOpSnippet(int v) {
        Complex obj = new Complex();

        obj.op(v);
        GraalDirectives.instrumentationBegin(-2);
        notInlineFlag = true;
        GraalDirectives.instrumentationEnd();
    }

    @Test
    public void testComplexOp() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("invokeComplexOpSnippet");
            executeExpected(method, null, 0); // ensure the method is fully resolved
            resetFlag();
            // complex method will be inlined if the node count does not exceed
            // MaximumInliningSize(300)
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs(0);
                Assert.assertFalse("Complex.op(I) should be inlined (relevance-based, nodeCount=22 < 300)",
                                notInlineFlag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void invokeComplexOpInSlowPathSnippet(int v, boolean condition) {
        Complex obj = new Complex();

        if (GraalDirectives.injectBranchProbability(0.01, condition)) {
            obj.op(v);
            GraalDirectives.instrumentationBegin(-2);
            notInlineFlag = true;
            GraalDirectives.instrumentationEnd();
        }
    }

    @Test
    public void testComplexOpInSlowPath() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("invokeComplexOpInSlowPathSnippet");
            executeExpected(method, null, 0, true); // ensure the method is fully resolved
            resetFlag();
            // The invocation to the complex method is located in a slow path. Such invocation will
            // not be inlined if the node conut exceeds MaximumInliningSize(300) times the branch
            // probability.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs(0, true);
                Assert.assertTrue("Complex.op(I) should not be inlined (relevance-based, nodeCount=22 > 300 *0.01)", notInlineFlag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void invokeManyInvokesCalleeSnippet() {
        Simple obj = new Simple();

        obj.methodContainManyInvokes();
        GraalDirectives.instrumentationBegin(-1);
        notInlineFlag = true;
        GraalDirectives.instrumentationEnd();
    }

    @Test
    public void testManyInvokesCallee() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("invokeManyInvokesCalleeSnippet");
            executeExpected(method, null); // ensure the method is fully resolved
            resetFlag();
            // If the callee contains more than LimitInlinedInvokes(5) invocations, then the
            // invocation will not be inlined.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs();
                Assert.assertTrue("Simple.manyInvokes() should not be inlined (callee invoke probability is too high, invokeP=6 > 5)", notInlineFlag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void invokePolyOpSnippet(SuperClass obj, int v) {
        obj.op(v);
        GraalDirectives.instrumentationBegin(-2);
        notInlineFlag = true;
        GraalDirectives.instrumentationEnd();
    }

    @Test
    public void testPolyOp() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("invokePolyOpSnippet");
            // ensure the method is fully resolved. Build a receiver type profile for the observed
            // invocation.
            for (int i = 0; i < 5000; i++) {
                executeExpected(method, null, Factory.nextSimpleOrComplex(0.8), 0);
            }
            resetFlag();
            // A polymorphic invocation will be inlined if the sum of the node counts of all the
            // potential callees does not exceed MaximumInliningSize(300).
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs(Factory.nextSimpleOrComplex(0.8), 0);
                Assert.assertFalse("SuperClass.op(I) should be inlined (relevance-based, nodeCount=26 < 300)",
                                notInlineFlag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void invokePolyOpInSlowPathSnippet(SuperClass obj, int v, boolean condition) {
        if (condition) { // injectBranchProbability does not override the true branch
                         // probability (which occurs after training many times) and leads to a
                         // DeoptimizingGuard
            obj.op(v);
            GraalDirectives.instrumentationBegin(-2);
            notInlineFlag = true;
            GraalDirectives.instrumentationEnd();
        }
    }

    @Test
    public void testPolyOpInSlowPath() {
        // we cannot reuse invokePolyOpSnippet because of the existing type profile
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("invokePolyOpInSlowPathSnippet");
            // ensure the method is fully resolved. Build a receiver type profile for the observed
            // invocation.
            for (int i = 0; i < 5000; i++) {
                executeExpected(method, null, Factory.nextSimpleOrComplex(0.8), 0, Factory.nextBoolean(0.01));
            }
            resetFlag();
            // Similar to inlining the complex callee, the polymorphic invovation in the slow path
            // will not be inlined if the sum of the node counts of all the potential callees
            // exceeds MaximumInliningSize(300) times the branch probability.
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs(Factory.nextSimpleOrComplex(0.8), 0, true);
                Assert.assertTrue("SuperClass.op(I) should not be inlined (relevance-based, nodeCount=26 > 300 * 0.01)",
                                notInlineFlag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    public static void invokePolyOpWithManyReceiverTypes(SuperClass obj, int v) {
        obj.op(v);
        GraalDirectives.instrumentationBegin(-2);
        notInlineFlag = true;
        GraalDirectives.instrumentationEnd();
    }

    @Test
    public void testPolyOpWithManyReceiverTypes() {
        // we cannot reuse invokePolyOpSnippet because of the existing type profile
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("invokePolyOpWithManyReceiverTypes");
            // ensure the method is fully resolved. Build a receiver type profile for the observed
            // invocation.
            for (int i = 0; i < 5000; i++) {
                executeExpected(method, null, Factory.nextSimple(0.1), 0);
            }
            resetFlag();
            // When the invocation is of more than TypeProfileWidth(8) receiver types and hence the
            // receiver type profile cannot cover all cases, Graal will attempt to inline the mega
            // callee whose portion is greater than MegamorphicInliningMinMethodProbability(0.33D).
            InstalledCode code = getCode(method);
            try {
                code.executeVarargs(Factory.nextSimple(0.1), 0);
                Assert.assertTrue("SuperClass.op(I) should not be inlined (no methods remaining after filtering less frequent methods)",
                                notInlineFlag);
            } catch (Throwable e) {
                Assert.fail("Unexpected exception: " + e);
            }
        }
    }

    private static class Factory {

        static final Random rand = new Random();

        static final Simple simple = new Simple();
        static final Complex complex = new Complex();

        static final Simple1 simple1 = new Simple1();
        static final Simple2 simple2 = new Simple2();
        static final Simple3 simple3 = new Simple3();
        static final Simple4 simple4 = new Simple4();
        static final Simple5 simple5 = new Simple5();
        static final Simple6 simple6 = new Simple6();
        static final Simple7 simple7 = new Simple7();
        static final Simple8 simple8 = new Simple8();
        static final Simple9 simple9 = new Simple9();

        static SuperClass nextSimpleOrComplex(double probability) {
            if (nextBoolean(probability)) {
                return simple;
            } else {
                return complex;
            }
        }

        static boolean nextBoolean(double probability) {
            return rand.nextDouble() < probability;
        }

        static SuperClass nextSimple(double probability) {
            double next = rand.nextDouble();
            if (next < probability) {
                return simple1;
            } else if (next < 2 * probability) {
                return simple2;
            } else if (next < 3 * probability) {
                return simple3;
            } else if (next < 4 * probability) {
                return simple4;
            } else if (next < 5 * probability) {
                return simple5;
            } else if (next < 6 * probability) {
                return simple6;
            } else if (next < 7 * probability) {
                return simple7;
            } else if (next < 8 * probability) {
                return simple8;
            } else if (next < 9 * probability) {
                return simple9;
            } else {
                return simple;
            }
        }

    }

    private abstract static class SuperClass {
        abstract int op(int v);
    }

    private static class Simple extends SuperClass {

        @Override
        int op(int v) {
            return v;
        }

        void notInlinedMethod() {
        }

        void methodContainManyInvokes() {
            notInlinedMethod();
            notInlinedMethod();
            notInlinedMethod();
            notInlinedMethod();
            notInlinedMethod();
            notInlinedMethod();
        }

    }

    private static class Complex extends SuperClass {

        @Override
        int op(int v) {
            return ((((((v + 1) * 2) - 3) / 4) ^ 5) % 6) & 7;
        }
    }

    private static class Simple1 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple2 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple3 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple4 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple5 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple6 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple7 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple8 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

    private static class Simple9 extends SuperClass {
        @Override
        int op(int v) {
            return v;
        }
    }

}
