/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.phases.TruffleHostInliningPhase;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Please keep this test in sync with SubstrateTruffleHostInliningTest.
 */
public class HostInliningTest extends GraalCompilerTest {

    static final int NODE_COST_LIMIT = 500;

    @Test
    public void test() {
        runTest("testBasicInlining");
        runTest("testDominatedDeopt");
        runTest("testTruffleBoundary");
        runTest("testPropagateDeopt");
        runTest("testPropagateDeoptTwoLevels");
        runTest("testRecursive");
        runTest("testNotExplorable");
        runTest("testBecomesDirectAfterInline");
        runTest("testVirtualCall");
        runTest("testInInterpreter1");
        runTest("testInInterpreter2");
        runTest("testInInterpreter3");
        runTest("testInInterpreter4");
        runTest("testInInterpreter5");
        runTest("testInInterpreter6");
        runTest("testInInterpreter7");
        runTest("testInInterpreter8");
        runTest("testInInterpreter9");
        runTest("testInInterpreter10");
        runTest("testInInterpreter11");
        runTest("testInInterpreter12");
    }

    @SuppressWarnings("try")
    void runTest(String methodName) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        OptionValues options = createHostInliningOptions(NODE_COST_LIMIT);
        StructuredGraph graph = parseForCompile(method, options);

        try {
            // call it so all method are initialized
            getMethod(methodName).invoke(null, 5);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new AssertionError(e);
        }

        try (DebugContext.Scope ds = graph.getDebug().scope("Testing", method, graph)) {
            new ConvertDeoptimizeToGuardPhase().apply(graph, getDefaultHighTierContext());
            new TruffleHostInliningPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());

            ExpectNotInlined notInlined = method.getAnnotation(ExpectNotInlined.class);
            Set<String> found = new HashSet<>();
            for (InvokeNode invoke : graph.getNodes().filter(InvokeNode.class)) {
                ResolvedJavaMethod invokedMethod = invoke.getTargetMethod();
                if (notInlined == null) {
                    Assert.fail("Unexpected node type found in the graph: " + invoke);
                } else {
                    for (String expectedMethodName : notInlined.value()) {
                        if (expectedMethodName.equals(invokedMethod.getName())) {
                            if (found.contains(invokedMethod.getName())) {
                                Assert.fail("Found multiple calls to " + invokedMethod.getName() + " but expected one.");
                            }
                            found.add(invokedMethod.getName());
                        }
                    }
                    if (!found.contains(invokedMethod.getName())) {
                        Assert.fail("Unexpected invoke found " + invoke + ". Expected one of " + Arrays.toString(notInlined.value()));
                    }
                }
            }
            if (notInlined != null) {
                for (String expectedMethodName : notInlined.value()) {
                    if (!found.contains(expectedMethodName)) {
                        Assert.fail("Expected not inlinined method with name " + expectedMethodName + " but not found.");
                    }
                }
            }

        } catch (Throwable e) {
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "error graph");
            throw new AssertionError("Error validating graph " + graph, e);
        }

    }

    static OptionValues createHostInliningOptions(int bytecodeInterpreterLimit) {
        EconomicMap<OptionKey<?>, Object> values = EconomicMap.create();
        values.put(TruffleHostInliningPhase.Options.TruffleHostInlining, true);
        values.put(HighTier.Options.Inline, false);
        values.put(TruffleHostInliningPhase.Options.TruffleHostInliningByteCodeInterpreterBudget, bytecodeInterpreterLimit);
        OptionValues options = new OptionValues(getInitialOptions(), values);
        return options;
    }

    @BytecodeInterpreterSwitch
    private static int testBasicInlining(int value) {
        nonTrivialMethod(); // inlined
        for (int i = 0; i < value; i++) {
            nonTrivialMethod(); // inlined
        }
        try {
            nonTrivialMethod(); // inlined
        } finally {
            nonTrivialMethod(); // inlined
        }
        try {
            nonTrivialMethod(); // inlined
        } catch (Throwable t) {
            nonTrivialMethod(); // inlined
            throw t;
        }
        nonTrivialMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined({"nonTrivialMethod", "traceTransferToInterpreter"})
    private static int testDominatedDeopt(int value) {
        if (value == 1) {
            CompilerDirectives.transferToInterpreterAndInvalidate(); // inlined
            nonTrivialMethod(); // cutoff
        }
        return value;
    }

    @BytecodeParserNeverInline
    static void nonTrivialMethod() {
    }

    @BytecodeParserNeverInline
    static void otherNonTrivalMethod() {
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("truffleBoundary")
    private static int testTruffleBoundary(int value) {
        if (value == 1) {
            truffleBoundary(); // cutoff
        }
        return value;
    }

    @TruffleBoundary
    @BytecodeParserNeverInline
    static void truffleBoundary() {
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined({"nonTrivialMethod", "traceTransferToInterpreter"})
    private static int testPropagateDeopt(int value) {
        if (value == 1) {
            propagateDeopt(); // inlined
            nonTrivialMethod(); // cutoff
        }
        return value;
    }

    static void propagateDeopt() {
        CompilerDirectives.transferToInterpreterAndInvalidate(); // inlined
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined({"nonTrivialMethod", "traceTransferToInterpreter"})
    private static int testPropagateDeoptTwoLevels(int value) {
        if (value == 1) {
            propagateDeoptLevelTwo(); // inlined
            nonTrivialMethod(); // cutoff
        }
        return value;
    }

    static void propagateDeoptLevelTwo() {
        propagateDeopt(); // inlined
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("recursive")
    private static int testRecursive(int value) {
        recursive(value); // inlined
        return value;
    }

    static void recursive(int i) {
        if (i == 0) {
            return;
        }
        recursive(i - 1); // cutoff -> recursive
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("notExplorable")
    private static int testNotExplorable(int value) {
        notExplorable(value); // cutoff -> charAt not explorable
        return value;
    }

    @BytecodeParserNeverInline
    static int notExplorable(int value) {
        /*
         *
         */
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        new HashMap<>().put(value, value);
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testBecomesDirectAfterInline(int value) {
        becomesDirectAfterInline(new B_extends_A());
        becomesDirectAfterInline(new C_extends_A());
        return value;
    }

    @BytecodeParserNeverInline
    static void becomesDirectAfterInline(A a) {
        a.foo(); // inlined
    }

    interface A {
        void foo();
    }

    static final class B_extends_A implements A {
        @Override
        @BytecodeParserNeverInline
        public void foo() {
        }
    }

    static final class C_extends_A implements A {
        @Override
        @BytecodeParserNeverInline
        public void foo() {
        }
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("foo")
    private static int testVirtualCall(int value) {
        A a = value == 42 ? new B_extends_A() : new C_extends_A();
        a.foo(); // virtual -> not inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("nonTrivialMethod")
    private static int testInInterpreter1(int value) {
        otherNonTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter()) {
            nonTrivialMethod(); // cutoff
        }
        otherNonTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("nonTrivialMethod")
    private static int testInInterpreter2(int value) {
        otherNonTrivalMethod(); // inlined
        if (value == 24) {
            otherNonTrivalMethod(); // inlined
            if (CompilerDirectives.inInterpreter()) {
                if (value == 24) {
                    nonTrivialMethod(); // cutoff
                }
            }
            otherNonTrivalMethod(); // inlined
        }
        otherNonTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("nonTrivialMethod")
    private static int testInInterpreter3(int value) {
        otherNonTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter() && value == 24) {
            nonTrivialMethod(); // cutoff
        }
        otherNonTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testInInterpreter4(int value) {
        otherNonTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter()) {
            if (CompilerDirectives.inCompiledCode()) {
                nonTrivialMethod(); // dead
            }
        }
        otherNonTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testInInterpreter5(int value) {
        otherNonTrivalMethod(); // inlined
        if (!CompilerDirectives.inInterpreter()) {
            nonTrivialMethod(); // dead
        }
        otherNonTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("nonTrivialMethod")
    private static int testInInterpreter6(int value) {
        otherNonTrivalMethod(); // inlined
        boolean condition = CompilerDirectives.inInterpreter();
        if (condition) {
            nonTrivialMethod(); // cutoff
        }
        otherNonTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("foo")
    private static int testInInterpreter7(int value) {
        testInInterpreter7Impl(new B_extends_A());
        return value;
    }

    @BytecodeParserNeverInline
    static void testInInterpreter7Impl(A a) {
        if (CompilerDirectives.inInterpreter()) {
            a.foo(); // not inlined even if it becomes direct
        }
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("foo")
    static int testInInterpreter8(int value) {
        boolean b = constant();
        A type = b ? new B_extends_A() : new C_extends_A();
        if (CompilerDirectives.inInterpreter()) {
            type.foo();
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    static int testInInterpreter9(int value) {
        boolean b = constant();
        A type = b ? new B_extends_A() : new C_extends_A();
        if (!CompilerDirectives.inInterpreter()) {
            type.foo();
        }
        return value;
    }

    @BytecodeParserNeverInline
    static boolean constant() {
        return true;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("nonTrivialMethod")
    static int testInInterpreter10(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }
        nonTrivialMethod();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined("nonTrivialMethod")
    static int testInInterpreter11(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }

        if (value == 5) {
            nonTrivialMethod();
            return 42;
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    static int testInInterpreter12(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            if (value == 5) {
                nonTrivialMethod();
            }
            return 42;
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return -1;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ExpectNotInlined {
        String[] value();
    }

    interface RuntimeCompilable {

        Object call(int argument);

    }

    static class MakeRuntimeCompileReachable extends RootNode {

        final RuntimeCompilable compilable;

        MakeRuntimeCompileReachable(RuntimeCompilable compilable) {
            super(null);
            this.compilable = compilable;
        }

        @Override

        public Object execute(VirtualFrame frame) {
            return compilable.call((int) frame.getArguments()[0]);
        }

    }

}
