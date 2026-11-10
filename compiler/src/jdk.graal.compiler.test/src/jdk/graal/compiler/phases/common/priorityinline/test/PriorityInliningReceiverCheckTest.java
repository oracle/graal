/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.test;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.HotSpotBytecodeParser;
import jdk.graal.compiler.hotspot.HotSpotGraphBuilderInstance;
import jdk.graal.compiler.hotspot.HotSpotGraphBuilderPhase;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

/// Checks interface guard placement and receiver errors under priority inlining.
public class PriorityInliningReceiverCheckTest extends GraalCompilerTest implements CustomizedBytecodePattern {

    /// Records the observable store in the generated snippets, which use a separate class loader.
    public static Object storedReceiver;

    public interface Parent {
        int value();
    }

    public interface Child extends Parent {
        /// Exposes a parser-checked receiver to later stores or multiple interface invokes.
        default Child checkedReceiver() {
            return this;
        }
    }

    public static final class First implements Child {
        @Override
        public int value() {
            return 1;
        }
    }

    public static final class Second implements Child {
        @Override
        public int value() {
            return 2;
        }
    }

    public static final class Third implements Child {
        @Override
        public int value() {
            return 3;
        }
    }

    public static final class Unseen implements Child {
        @Override
        public int value() {
            return 4;
        }
    }

    public static final class ParentOnly implements Parent {
        @Override
        public int value() {
            return 5;
        }
    }

    public static final class Coincidental {
        public int value() {
            return 6;
        }
    }

    /// Generates calls with an `Object` receiver and no preceding cast. Interface receiver types
    /// are not enforced by the verifier, so HotSpot can execute these snippets as the reference for
    /// ICCE and null failures. Intrinsifying the identity method `checkedReceiver` retains its
    /// parser-generated membership check while exposing the receiver to the store and shared uses.
    @Override
    public byte[] generateClass(String className) {
        MethodTypeDesc snippetType = MethodTypeDesc.of(CD_int, CD_Object);
        MethodTypeDesc valueType = MethodTypeDesc.of(CD_int);
        MethodTypeDesc receiverType = MethodTypeDesc.of(cd(Child.class));
        // @formatter:off
        return ClassFile.of().build(ClassDesc.of(className), builder -> builder
                        .withMethodBody("completeProfile", snippetType, ACC_PUBLIC_STATIC, b -> b
                                        .aload(0).invokeinterface(cd(Child.class), "value", valueType).ireturn())
                        .withMethodBody("incompleteProfile", snippetType, ACC_PUBLIC_STATIC, b -> b
                                        .aload(0).invokeinterface(cd(Child.class), "value", valueType).ireturn())
                        .withMethodBody("storeBeforeCall", snippetType, ACC_PUBLIC_STATIC, b -> b
                                        .aload(0).invokeinterface(cd(Child.class), "checkedReceiver", receiverType).astore(1)
                                        .aload(1).putstatic(cd(PriorityInliningReceiverCheckTest.class), "storedReceiver", CD_Object)
                                        .aload(1).invokeinterface(cd(Child.class), "value", valueType).ireturn())
                        .withMethodBody("sharedReceiver", snippetType, ACC_PUBLIC_STATIC, b -> b
                                        .aload(0).invokeinterface(cd(Child.class), "checkedReceiver", receiverType).astore(1)
                                        .aload(1).invokeinterface(cd(Child.class), "value", valueType)
                                        .aload(1).invokeinterface(cd(Child.class), "value", valueType).iadd().ireturn()));
        // @formatter:on
    }

    /// Checks complete receiver profiles, whose unmatched path deoptimizes.
    @Test
    public void testCompleteProfile() {
        checkDispatch(false, false);
    }

    /// Checks incomplete receiver profiles, whose unmatched path retains an interface invoke.
    @Test
    public void testIncompleteProfile() {
        checkDispatch(true, false);
    }

    /// Checks dispatch when the membership guard no longer has a receiver refinement as a user.
    @Test
    public void testWithoutPi() {
        checkDispatch(false, true);
    }

    /// Checks that receiver validation stays before an intervening store.
    @Test
    public void testCheckBeforeSideEffect() {
        PreparedGraph input = prepareGraph("storeBeforeCall", false, false);
        FixedNode predecessor = (FixedNode) input.guard().predecessor();
        assertTrue(input.guard().next() != input.invoke().asFixedNode());
        inline(input.graph());
        Assert.assertSame(predecessor, input.guard().predecessor());
        checkCompiledReceivers(input.graph(), "storeBeforeCall");
    }

    /// Checks that other uses of the checked receiver retain their membership proof.
    @Test
    public void testSharedReceiver() {
        PreparedGraph input = prepareGraph("sharedReceiver", false, false);
        assertFalse(input.receiver().hasExactlyOneUsage());
        FixedNode predecessor = (FixedNode) input.guard().predecessor();
        inline(input.graph());
        Assert.assertSame(predecessor, input.guard().predecessor());
        checkCompiledReceivers(input.graph(), "sharedReceiver");
    }

    /// Checks the actual dispatch branches and the preserved unmatched path before code generation.
    private void checkDispatch(boolean incomplete, boolean withoutPi) {
        String snippet = incomplete ? "incompleteProfile" : "completeProfile";
        PreparedGraph input = prepareGraph(snippet, incomplete, withoutPi);
        Assert.assertSame(input.invoke().asFixedNode(), input.guard().next());
        assertTrue(withoutPi ? input.guard().hasNoUsages() : input.receiver().hasExactlyOneUsage());
        inline(input.graph());
        FixedNode miss = input.graph().start().next();
        for (int i = 0; i < 2; i++) {
            assertTrue("Exact receiver tests must precede the membership check", miss instanceof IfNode);
            IfNode dispatch = (IfNode) miss;
            assertTrue(dispatch.condition() instanceof InstanceOfNode typeTest && typeTest.getCheckedStamp().isExactType());
            miss = dispatch.falseSuccessor().next();
        }
        assertTrue("The membership check must occur only on the unmatched path", !input.guard().isAlive() || miss == input.guard());
        if (incomplete) {
            Assert.assertSame(input.guard(), miss);
            assertTrue("The generic invoke must retain the receiver check", input.guard().next() instanceof Invoke);
        } else {
            assertTrue("An unmatched complete profile must deoptimize", (input.guard().isAlive() ? input.guard().next() : miss) instanceof DeoptimizeNode);
        }
        checkCompiledReceivers(input.graph(), snippet);
    }

    /// Keeps receiver failures on deoptimization paths, as in image-runtime JIT compilation.
    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        conf.getPlugins().appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.equals(getResolvedJavaMethod(Child.class, "checkedReceiver"))) {
                    // The parser has already checked the receiver before invoking this plugin.
                    b.push(JavaKind.Object, args[0]);
                    return true;
                }
                return false;
            }
        });
        return super.editGraphBuilderConfiguration(conf).withBytecodeExceptionMode(BytecodeExceptionMode.OmitAll);
    }

    /// Enables the production parser's symbolic-interface check for these HotSpot compiler tests.
    @Override
    protected PhaseSuite<HighTierContext> getCustomGraphBuilderSuite(GraphBuilderConfiguration conf) {
        PhaseSuite<HighTierContext> suite = super.getCustomGraphBuilderSuite(conf);
        var iterator = suite.findPhase(GraphBuilderPhase.class);
        GraphBuilderPhase phase = (GraphBuilderPhase) iterator.previous();
        iterator.set(new ReceiverCheckGraphBuilderPhase(phase.getGraphBuilderConfig()));
        return suite;
    }

    /// Uses the platform parser with the receiver-check policy of image-runtime compilation.
    private static class ReceiverCheckGraphBuilderPhase extends HotSpotGraphBuilderPhase {
        ReceiverCheckGraphBuilderPhase(GraphBuilderConfiguration conf) {
            super(conf);
        }

        @Override
        public GraphBuilderPhase copyWithConfig(GraphBuilderConfiguration conf) {
            return new ReceiverCheckGraphBuilderPhase(conf);
        }

        @Override
        protected Instance createInstance(CoreProviders providers, GraphBuilderConfiguration conf, OptimisticOptimizations optimisticOpts, IntrinsicContext intrinsicContext) {
            return new HotSpotGraphBuilderInstance(providers, conf, optimisticOpts, intrinsicContext) {
                @Override
                protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext context) {
                    return new HotSpotBytecodeParser(this, graph, parent, method, entryBCI, context) {
                        @Override
                        protected boolean needsIncompatibleClassChangeErrorCheck() {
                            return true;
                        }
                    };
                }
            };
        }
    }

    /// Parses the membership check and supplies deterministic profiles for exact receiver dispatch.
    private PreparedGraph prepareGraph(String snippet, boolean incomplete, boolean withoutPi) {
        // Load several implementations before parsing to avoid a single-implementor assumption.
        new First();
        new Second();
        new Third();
        new Unseen();
        ResolvedJavaMethod method;
        try {
            method = getResolvedJavaMethod(getClass(getClass().getName() + "$Snippets"), snippet);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        FixedGuardNode guard = null;
        for (FixedGuardNode candidate : graph.getNodes(FixedGuardNode.TYPE)) {
            if (candidate.getCondition() instanceof InstanceOfNode typeTest && typeTest.getCheckedStamp().type().equals(getMetaAccess().lookupJavaType(Child.class))) {
                Assert.assertNull("Parsing must retain exactly one membership check", guard);
                guard = candidate;
            }
        }
        Assert.assertNotNull(guard);
        Assert.assertEquals(DeoptimizationReason.ClassCastException, guard.getReason());
        Assert.assertEquals(DeoptimizationAction.None, guard.getAction());
        PiNode checkedReceiver = guard.usages().filter(PiNode.class).first();
        Assert.assertNotNull(checkedReceiver);
        ValueNode receiver = ((InstanceOfNode) guard.getCondition()).getValue();
        double probability = incomplete ? 0.375 : 0.5;
        JavaTypeProfile profile = new JavaTypeProfile(TriState.FALSE, incomplete ? 0.25 : 0.0,
                        new ProfiledType[]{new ProfiledType(getMetaAccess().lookupJavaType(First.class), probability),
                                        new ProfiledType(getMetaAccess().lookupJavaType(Second.class), probability)});
        Invoke invoke = null;
        for (MethodCallTargetNode target : graph.getNodes(MethodCallTargetNode.TYPE)) {
            assertTrue(target.invokeKind().isInterface());
            Assert.assertEquals(getMetaAccess().lookupJavaType(Child.class), target.referencedType());
            target.setJavaTypeProfile(profile);
            if (invoke == null) {
                invoke = target.invoke();
            }
        }
        Assert.assertNotNull(invoke);
        Assert.assertSame(checkedReceiver, invoke.callTarget().arguments().get(0));
        if (withoutPi) {
            invoke.callTarget().arguments().set(0, receiver);
            GraphUtil.tryKillUnused(checkedReceiver);
        }
        assertTrue(graph.verify());
        return new PreparedGraph(graph, guard, checkedReceiver, invoke);
    }

    /// Applies priority inlining to the prepared graph.
    private void inline(StructuredGraph graph) {
        new PriorityInliningPhase(createCanonicalizerPhase(), graph.getOptions()).apply(graph, getDefaultHighTierContext());
        assertTrue("The injected profiles must produce receiver dispatch", graph.getNodes(IfNode.TYPE).isNotEmpty());
        assertTrue(graph.verify());
    }

    /// Enters freshly installed code for each receiver, including valid misses and ICCE failures.
    private void checkCompiledReceivers(StructuredGraph graph, String snippet) {
        ResolvedJavaMethod method = graph.method();
        Object[] challenges = {new First(), new Second(), new Third(), new Unseen(), new ParentOnly(), new Coincidental(), null};
        for (Object receiver : challenges) {
            InstalledCode code = getCode(method, (StructuredGraph) graph.copy(getDebugContext()), true);
            Object initialStoredReceiver = new Object();
            storedReceiver = initialStoredReceiver;
            Result expected = executeExpected(method, null, receiver);
            Object expectedStoredReceiver = storedReceiver;
            if (receiver == null) {
                assertTrue(expected.exception instanceof NullPointerException);
            } else if (!(receiver instanceof Child)) {
                assertTrue(expected.exception instanceof IncompatibleClassChangeError);
            }
            // Restore the initial value so reference execution cannot conceal a missing store.
            storedReceiver = initialStoredReceiver;
            assertTrue("Each challenge must enter freshly installed compiled code", code.isValid());
            Result actual;
            try {
                actual = new Result(code.executeVarargs(receiver), null);
            } catch (Throwable error) {
                actual = new Result(null, error);
            }
            assertEquals(expected, actual);
            if (snippet.equals("storeBeforeCall")) {
                Assert.assertSame("Receiver errors must preserve the order of the store", expectedStoredReceiver, storedReceiver);
            }
            if (receiver instanceof First || receiver instanceof Second) {
                assertTrue("Profiled receivers must stay in compiled code", code.isValid());
            }
        }
    }

    /// Retains the input graph and membership proof while inlining rewires control flow.
    private record PreparedGraph(StructuredGraph graph, FixedGuardNode guard, PiNode receiver, Invoke invoke) {
    }
}
