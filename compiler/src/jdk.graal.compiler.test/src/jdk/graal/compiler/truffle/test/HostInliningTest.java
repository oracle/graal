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
package jdk.graal.compiler.truffle.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.host.HostInliningPhase;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Please keep this test in sync with SubstrateTruffleHostInliningTest.
 */
@RunWith(Parameterized.class)
public class HostInliningTest extends TruffleCompilerImplTest {

    static final int NODE_COST_LIMIT = 1000;

    public enum TestRun {
        WITH_CONVERT_TO_GUARD,
        DEFAULT,
    }

    @Parameter // first data value (0) is default
    public /* NOT private */ TestRun run;

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.DEFAULT);
    }

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
        runTest("testExplorationDepth0");
        runTest("testExplorationDepth1");
        runTest("testExplorationDepth2");
        runTest("testExplorationDepth0Fail");
        runTest("testExplorationDepth1Fail");
        runTest("testExplorationDepth2Fail");
        runTest("testBytecodeSwitchtoBytecodeSwitch");
        runTest("testInliningCutoff");
        runTest("testNonDirectCalls");
        runTest("testConstantFolding");
        runTest("testDirectIntrinsics");
        runTest("testIndirectIntrinsics");
        runTest("testCountingConditionProfile");
        runTest("testInterpreterCaller");
        runTest("testIndirectThrow");
        runTest("testThrow");
        runTest("testRangeCheck");
        runTest("testImplicitCast");
    }

    @SuppressWarnings("try")
    void runTest(String methodName) {
        // initialize the Truffle runtime to ensure that all intrinsics are applied
        Truffle.getRuntime();

        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        ExplorationDepth depth = method.getAnnotation(ExplorationDepth.class);
        int explorationDepth = -1;
        if (depth != null) {
            explorationDepth = depth.value();
        }

        NodeCostLimit nodeCostLimit = method.getAnnotation(NodeCostLimit.class);
        OptionValues options = createHostInliningOptions(nodeCostLimit != null ? nodeCostLimit.value() : NODE_COST_LIMIT, explorationDepth);
        StructuredGraph graph = parseForCompile(method, options);
        try {
            // call it so all method are initialized
            getMethod(methodName).invoke(null, 5);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof ExpectedException) {
                // ignore
            } else {
                throw new AssertionError(e);
            }
        }

        try (DebugContext.Scope ds = graph.getDebug().scope("Testing", method, graph)) {
            HighTierContext context = getEagerHighTierContext();
            CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
            if (run == TestRun.WITH_CONVERT_TO_GUARD) {
                new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, context);
            }
            new HostInliningPhase(canonicalizer).apply(graph, context);

            ExpectNotInlined notInlined = method.getAnnotation(ExpectNotInlined.class);
            ExpectSameGraph sameGraph = method.getAnnotation(ExpectSameGraph.class);

            if (sameGraph != null) {
                ResolvedJavaMethod compareMethod = getResolvedJavaMethod(sameGraph.value());
                StructuredGraph compareGraph = parseForCompile(compareMethod, options);
                assertEquals(compareGraph, graph);
            }

            assertInvokesFound(graph, notInlined != null ? notInlined.name() : null, notInlined != null ? notInlined.count() : null);

        } catch (Throwable e) {
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "error graph");
            throw new AssertionError("Error validating graph " + graph, e);
        }

    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        super.registerInvocationPlugins(invocationPlugins);
        invocationPlugins.register(HostInliningTest.class, new InvocationPlugin("intrinsic") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Int, b.add(ConstantNode.forInt(42)));
                return true;
            }
        });

        invocationPlugins.register(B_extends_A.class, new InvocationPlugin("intrinsic", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Int, b.add(ConstantNode.forInt(20)));
                return true;
            }
        });

        invocationPlugins.register(C_extends_A.class, new InvocationPlugin("intrinsic", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Int, b.add(ConstantNode.forInt(22)));
                return true;
            }
        });
    }

    public static void assertInvokesFound(StructuredGraph graph, String[] notInlined, int[] counts) {
        Map<String, Integer> found = new HashMap<>();
        List<Invoke> invokes = new ArrayList<>();
        invokes.addAll(graph.getNodes().filter(InvokeNode.class).snapshot());
        invokes.addAll(graph.getNodes().filter(InvokeWithExceptionNode.class).snapshot());

        invoke: for (Invoke invoke : invokes) {
            ResolvedJavaMethod invokedMethod = invoke.getTargetMethod();
            if (notInlined == null) {
                Assert.fail("Unexpected node type found in the graph: " + invoke);
            } else {
                for (int i = 0; i < notInlined.length; i++) {
                    String expectedMethodName = notInlined[i];
                    if (expectedMethodName.equals(invokedMethod.getName())) {
                        int expectedCount = counts[i];
                        int currentCount = found.getOrDefault(invokedMethod.getName(), 0);
                        if (expectedCount >= 0) {
                            currentCount++;

                            if (currentCount > expectedCount) {
                                Assert.fail("Expected " + expectedCount + " calls to " + invokedMethod.getName() + " but got " + currentCount + ".");
                            }
                            found.put(invokedMethod.getName(), currentCount);
                        }
                        continue invoke;
                    }
                }
                Assert.fail("Unexpected invoke found " + invoke + ". Expected one of " + Arrays.toString(notInlined));
            }
        }
        if (notInlined != null) {
            for (int i = 0; i < notInlined.length; i++) {
                String expectedMethodName = notInlined[i];
                int expectedCount = counts[i];
                int currentCount = found.getOrDefault(expectedMethodName, 0);
                if (expectedCount >= 0 && currentCount < expectedCount) {
                    Assert.fail("Expected " + expectedCount + " calls to " + expectedMethodName + " but got " + currentCount + ".");
                }
            }
        }
    }

    @Override
    protected InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getDeclaringClass().toJavaName().equals(getClass().getName())) {
            return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
        }
        return null;
    }

    static OptionValues createHostInliningOptions(int bytecodeInterpreterLimit, int explorationDepth) {
        EconomicMap<OptionKey<?>, Object> values = EconomicMap.create();
        values.put(HostInliningPhase.Options.TruffleHostInlining, true);
        values.put(HighTier.Options.Inline, false);
        values.put(HostInliningPhase.Options.TruffleHostInliningByteCodeInterpreterBudget, bytecodeInterpreterLimit);
        if (explorationDepth != -1) {
            values.put(HostInliningPhase.Options.TruffleHostInliningMaxExplorationDepth, explorationDepth);
        }
        OptionValues options = new OptionValues(getInitialOptions(), values);
        return options;
    }

    @BytecodeInterpreterSwitch
    private static int testBasicInlining(int value) {
        trivialMethod(); // inlined
        for (int i = 0; i < value; i++) {
            trivialMethod(); // inlined
        }
        try {
            trivialMethod(); // inlined
        } finally {
            trivialMethod(); // inlined
        }
        try {
            trivialMethod(); // inlined
        } catch (Throwable t) {
            trivialMethod(); // inlined
            throw t;
        }
        trivialMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = {"trivialMethod", "traceTransferToInterpreter"}, count = {1, 1})
    private static int testDominatedDeopt(int value) {
        if (value == 1) {
            CompilerDirectives.transferToInterpreterAndInvalidate(); // inlined
            trivialMethod(); // cutoff
        }
        return value;
    }

    static void trivialMethod() {
    }

    static void otherTrivalMethod() {
    }

    /*
     * Non trivial methods must have a cost >= 30.
     */
    static int nonTrivialMethod(int v) {
        int sum = 0;
        sum += (v / 2 + v / 3 + v + v / 4 + v / 5);
        sum += (v / 6 + v / 7 + v + v / 8 + v / 9);
        return sum;
    }

    @InliningCutoff
    static int inliningCutoff(int v) {
        return v;
    }

    static void trivalWithBoundary() {
        truffleBoundary();
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "truffleBoundary", count = 1)
    private static int testTruffleBoundary(int value) {
        if (value == 1) {
            truffleBoundary(); // cutoff
        }
        return value;
    }

    @TruffleBoundary
    static void truffleBoundary() {
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = {"propagateDeopt", "trivialMethod"}, count = {1, 1})
    private static int testPropagateDeopt(int value) {
        if (value == 1) {
            propagateDeopt(); // inlined
            trivialMethod(); // cutoff
        }
        return value;
    }

    static void propagateDeopt() {
        CompilerDirectives.transferToInterpreterAndInvalidate(); // inlined
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = {"trivialMethod", "propagateDeoptLevelTwo"}, count = {1, 1})
    private static int testPropagateDeoptTwoLevels(int value) {
        if (value == 1) {
            propagateDeoptLevelTwo(); // inlined
            trivialMethod(); // cutoff
        }
        return value;
    }

    static void propagateDeoptLevelTwo() {
        propagateDeopt(); // inlined
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "recursive", count = 1)
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
    @ExpectNotInlined(name = "notExplorable", count = 1)
    private static int testNotExplorable(int value) {
        notExplorable(value); // cutoff -> charAt not explorable
        return value;
    }

    static int notExplorable(int value) {
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

    static void becomesDirectAfterInline(A a) {
        a.foo(); // inlined
    }

    interface A {
        int foo();

        int intrinsic();
    }

    static final class B_extends_A implements A {
        @Override
        public int foo() {
            return 1;
        }

        @Override
        public int intrinsic() {
            // will return 20 through intrinsic
            return 19;
        }
    }

    static final class C_extends_A implements A {
        @Override
        public int foo() {
            return 2;
        }

        @Override
        public int intrinsic() {
            // will return 22 through intrinsic
            return 18;
        }
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "foo", count = 1)
    private static int testVirtualCall(int value) {
        A a = value == 42 ? new B_extends_A() : new C_extends_A();
        a.foo(); // virtual -> not inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "trivialMethod", count = 1)
    private static int testInInterpreter1(int value) {
        otherTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter()) {
            trivialMethod(); // cutoff
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "trivialMethod", count = 1)
    private static int testInInterpreter2(int value) {
        otherTrivalMethod(); // inlined
        if (value == 24) {
            otherTrivalMethod(); // inlined
            if (CompilerDirectives.inInterpreter()) {
                if (value == 24) {
                    trivialMethod(); // cutoff
                }
            }
            otherTrivalMethod(); // inlined
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "trivialMethod", count = 1)
    private static int testInInterpreter3(int value) {
        otherTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter() && value == 24) {
            trivialMethod(); // cutoff
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testInInterpreter4(int value) {
        otherTrivalMethod(); // inlined
        if (CompilerDirectives.inInterpreter()) {
            if (CompilerDirectives.inCompiledCode()) {
                trivialMethod(); // dead
            }
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    private static int testInInterpreter5(int value) {
        otherTrivalMethod(); // inlined
        if (!CompilerDirectives.inInterpreter()) {
            trivialMethod(); // dead
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "trivialMethod", count = 1)
    private static int testInInterpreter6(int value) {
        otherTrivalMethod(); // inlined
        boolean condition = CompilerDirectives.inInterpreter();
        if (condition) {
            trivialMethod(); // cutoff
        }
        otherTrivalMethod(); // inlined
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "foo", count = 1)
    private static int testInInterpreter7(int value) {
        testInInterpreter7Impl(new B_extends_A());
        return value;
    }

    static void testInInterpreter7Impl(A a) {
        if (CompilerDirectives.inInterpreter()) {
            a.foo(); // not inlined even if it becomes direct
        }
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "foo", count = 1)
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

    static boolean constant() {
        return true;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "trivialMethod", count = 1)
    static int testInInterpreter10(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }
        trivialMethod();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "trivialMethod", count = 1)
    static int testInInterpreter11(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }

        if (value == 5) {
            trivialMethod();
            return 42;
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    static int testInInterpreter12(int value) {
        if (!CompilerDirectives.inInterpreter()) {
            if (value == 5) {
                trivialMethod();
            }
            return 42;
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return -1;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "explorationDepth0", count = 1)
    @ExplorationDepth(0)
    static int testExplorationDepth0Fail(int value) {
        explorationDepth0();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExplorationDepth(1)
    static int testExplorationDepth0(int value) {
        explorationDepth0();
        return value;
    }

    static void explorationDepth0() {
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "explorationDepth1", count = 1)
    @ExplorationDepth(1)
    static int testExplorationDepth1Fail(int value) {
        explorationDepth1();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExplorationDepth(2)
    static int testExplorationDepth1(int value) {
        explorationDepth1();
        return value;
    }

    static void explorationDepth1() {
        explorationDepth0();
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "explorationDepth2", count = 1)
    @ExplorationDepth(2)
    static int testExplorationDepth2Fail(int value) {
        explorationDepth2();
        return value;
    }

    static void explorationDepth2() {
        explorationDepth1();
    }

    @BytecodeInterpreterSwitch
    @ExplorationDepth(3)
    static int testExplorationDepth2(int value) {
        explorationDepth0();
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "truffleBoundary", count = 1)
    static int testBytecodeSwitchtoBytecodeSwitch(int value) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            switch (i) {
                case 0:
                    result = 4;
                    break;
                case 1:
                    result = 3;
                    break;
                case 2:
                    result = 6;
                    break;
                default:
                    result = bytecodeSwitchtoBytecodeSwitch1(value);
                    break;
            }
        }
        return result;
    }

    // methods with bytecode switch are always inlined into bytecode switches
    @BytecodeInterpreterSwitch
    static int bytecodeSwitchtoBytecodeSwitch1(int value) {
        switch (value) {
            case 3:
                return 4;
            case 4:
                return 1;
            case 5:
                return 7;
            default:
                return bytecodeSwitchtoBytecodeSwitch3(value);
        }
    }

    // methods with bytecode switch are always inlined into bytecode switches,
    // also transitively.
    @BytecodeInterpreterSwitch
    static int bytecodeSwitchtoBytecodeSwitch3(int value) {
        switch (value) {
            case 6:
                return 4;
            case 7:
                trivalWithBoundary();
                return 3;
            case 8:
                return nonTrivialMethod(value);
            default:
                trivialMethod();
                return -1;
        }
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "inliningCutoff", count = 1)
    static int testInliningCutoff(int value) {
        return inliningCutoff(value);
    }

    @BytecodeInterpreterSwitch
    static int testNonDirectCalls(int value) {
        return nonDirectCalls(value, new B_extends_A()) + nonDirectCalls(value, new C_extends_A());
    }

    private static int nonDirectCalls(int value, A a) {
        // more than 10 indirect calls
        int sum = value;
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        sum += a.foo();
        return sum;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "truffleBoundary", count = 1)
    static int testConstantFolding(int value) {
        return constantFolding(1) + constantFolding(12) + value;
    }

    private static int constantFolding(int value) {
        // more than 10 indirect calls
        switch (value) {
            case 0:
                truffleBoundary();
                break;
            case 1:
                truffleBoundary();
                break;
            case 2:
                truffleBoundary();
                break;
            case 3:
                truffleBoundary();
                break;
            case 4:
                truffleBoundary();
                break;
            case 5:
                truffleBoundary();
                break;
            case 6:
                truffleBoundary();
                break;
            case 7:
                truffleBoundary();
                break;
            case 8:
                truffleBoundary();
                break;
            case 9:
                truffleBoundary();
                break;
            case 10:
                truffleBoundary();
                break;
            case 11:
                truffleBoundary();
                break;
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectSameGraph("constant42")
    @SuppressWarnings("unused")
    static int testDirectIntrinsics(int value) {
        return intrinsic();
    }

    static int constant42(@SuppressWarnings("unused") int value) {
        return 42;
    }

    static int intrinsic() {
        // will return 42 through intrinsic
        return 41;
    }

    static final A value0 = new B_extends_A();
    static final A value1 = new C_extends_A();

    @BytecodeInterpreterSwitch
    @ExpectSameGraph("constant42")
    static int testIndirectIntrinsics(@SuppressWarnings("unused") int value) {
        int ret = testIndirectIntrinsicsImpl(value0);
        ret += testIndirectIntrinsicsImpl(value1);
        return ret;
    }

    @SuppressWarnings("truffle-inlining")
    abstract static class IfNode extends Node {

        abstract int execute(boolean condition);

        @Specialization
        int doDefault(boolean condition, @Cached InlinedCountingConditionProfile profile) {
            if (profile.profile(this, condition)) {
                return 20;
            } else {
                return 21;
            }
        }

    }

    static final IfNode IF_NODE = HostInliningTestFactory.IfNodeGen.create();

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "traceTransferToInterpreter", count = -1 /* any number of calls ok */)
    static int testCountingConditionProfile(@SuppressWarnings("unused") int value) {
        return IF_NODE.execute(true) + IF_NODE.execute(false);
    }

    static final Context c = Context.create();
    static final OptimizedCallTarget TARGET;
    static final OptimizedDirectCallNode CALL;

    @BeforeClass
    public static void enterContext() {
        c.enter();
    }

    public static void closeContext() {
        c.close();
    }

    static {
        c.enter();
        TARGET = (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
        CALL = (OptimizedDirectCallNode) DirectCallNode.create(TARGET);
        c.leave();
    }

    /*
     * This test might fail and needs to be updated if something in the call code changes.
     */
    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = {"traceTransferToInterpreter", "callBoundary", "profileExceptionType", "handleException", "addStackFrameInfo",
                    "profileArgumentsSlow", "<init>", "beforeCall"}, count = {-1, 1, -1, -1, -1, 1, -1, 1})
    static int testInterpreterCaller(@SuppressWarnings("unused") int value) {
        return (int) CALL.call();
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "<init>", count = 1)
    static int testThrow(int value) {
        if (value == 5) {
            throw new ExpectedException();
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = {"indirectThrow", "trivialMethod"}, count = {1, 1})
    static int testIndirectThrow(int value) {
        if (value == 5) {
            indirectThrow();
            trivialMethod();
        }
        return value;
    }

    @BytecodeInterpreterSwitch
    @ExpectNotInlined(name = "<init>", count = 1)
    static int testRangeCheck(int value) {
        if (value == 6) {
            rangeCheck(10, 10, 11);
        }
        return value;
    }

    static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new ExpectedException();
        }
        if (fromIndex < 0) {
            throw new ExpectedException();
        }
        if (toIndex > arrayLength) {
            throw new ExpectedException();
        }
    }

    private static void indirectThrow() {
        throw new ExpectedException();
    }

    @TypeSystem
    static class MyTypes {
        @ImplicitCast
        public static double intToDouble(int value) {
            return value;
        }
    }

    @BytecodeInterpreterSwitch
    static int testImplicitCast(int value) {
        return (int) MyTypesGen.asImplicitDouble(0, value);
    }

    static int testIndirectIntrinsicsImpl(A a) {
        return a.intrinsic(); // inlined and intrinsic
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ExpectSameGraph {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ExpectNotInlined {
        String[] name();

        int[] count();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ExplorationDepth {
        int value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface NodeCostLimit {
        int value();
    }

    interface RuntimeCompilable {

        Object call(int argument);

    }

    @SuppressWarnings("serial")
    static class ExpectedException extends RuntimeException {

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
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
