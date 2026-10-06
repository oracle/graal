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
package jdk.graal.compiler.core.test.ea;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.FieldAliasNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.virtual.phases.ea.FieldLoadRefreshPhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FieldAliasReadEliminationTest extends GraalCompilerTest {

    static final class State {
        final Object[] references;

        State(Object[] references) {
            this.references = references;
        }
    }

    private static native void memoryKill();

    private static native void genericMemoryKill();

    private static native void foreignMemoryKill();

    private static native void foreignNoMemoryKill();

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration registration = new Registration(invocationPlugins, FieldAliasReadEliminationTest.class);
        registration.register(new InvocationPlugin("foreignNoMemoryKill") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ForeignCallDescriptor descriptor = new ForeignCallDescriptor("fieldAliasNoMemoryKillTest", void.class, new Class<?>[0], CallSideEffect.HAS_SIDE_EFFECT,
                                new LocationIdentity[0], false, false);
                b.add(new ForeignCallNode(descriptor));
                return true;
            }
        });
        registration.register(new InvocationPlugin("foreignMemoryKill") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ForeignCallDescriptor descriptor = new ForeignCallDescriptor("fieldAliasTest", void.class, new Class<?>[0], CallSideEffect.HAS_SIDE_EFFECT,
                                new LocationIdentity[]{LocationIdentity.any()}, false, false);
                b.add(new ForeignCallNode(descriptor));
                return true;
            }
        });
        registration.register(new InvocationPlugin("genericMemoryKill") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new MembarNode(MembarNode.FenceKind.FULL));
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static Object[] readAfterCall(State state, Object[] alias) {
        memoryKill();
        return state.references;
    }

    public static boolean ordinaryReadsAcrossForeignCall(State state) {
        Object[] before = state.references;
        foreignNoMemoryKill();
        return before == state.references;
    }

    public static int unboxAcrossGenericKill(Integer value) {
        int before = value.intValue();
        genericMemoryKill();
        return before + value.intValue();
    }

    @Test
    public void testOrdinaryReadsRetainForeignCallMemoryEffects() {
        for (boolean partialEscape : new boolean[]{false, true}) {
            StructuredGraph graph = parseEager("ordinaryReadsAcrossForeignCall", AllowAssumptions.NO);
            eliminate(graph, partialEscape);
            Assert.assertEquals(1, graph.getNodes().filter(LoadFieldNode.class).count());
            Assert.assertEquals(1, graph.getNodes(ReturnNode.TYPE).first().result().asJavaConstant().asInt());
        }
    }

    @Test
    public void testGenericKillStillInvalidatesUnboxingCache() {
        StructuredGraph graph = parseEager("unboxAcrossGenericKill", AllowAssumptions.NO);
        Assert.assertEquals(2, graph.getNodes().filter(UnboxNode.class).count());
        new PartialEscapePhase(false, true, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, getDefaultHighTierContext());
        Assert.assertEquals(2, graph.getNodes().filter(UnboxNode.class).count());
    }

    public static Object[] readAfterConditionalCall(State state, Object[] alias, boolean call) {
        if (call) {
            memoryKill();
        }
        return state.references;
    }

    public static Object[] readAfterConditionalCallWithRead(State state, Object[] alias, boolean call) {
        if (call) {
            memoryKill();
            // Keep the branch-local read alive independently of the final read.
            GraalDirectives.blackhole(state.references);
        }
        return state.references;
    }

    @Test
    public void testReadAndFieldAliasMergeInReadElimination() throws NoSuchFieldException {
        checkReadAndFieldAliasMerge(false);
    }

    @Test
    public void testReadAndFieldAliasMergeInPartialEscapeAnalysis() throws NoSuchFieldException {
        checkReadAndFieldAliasMerge(true);
    }

    private void checkReadAndFieldAliasMerge(boolean partialEscape) throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias("readAfterConditionalCallWithRead", true);
        Assert.assertEquals(2, graph.getNodes().filter(LoadFieldNode.class).count());
        new DominatorBasedGlobalValueNumberingPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        eliminate(graph, partialEscape);
        ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertTrue("The final field reload must be replaced by a phi of the branch read and incoming alias", ret.result() instanceof ValuePhiNode);
        ValuePhiNode result = (ValuePhiNode) ret.result();
        Assert.assertEquals(2, result.valueCount());
        Assert.assertTrue(result.values().contains(graph.getParameter(1)));
        Assert.assertEquals(1, graph.getNodes().filter(LoadFieldNode.class).count());
        LoadFieldNode reload = graph.getNodes().filter(LoadFieldNode.class).first();
        Assert.assertTrue(result.values().contains(reload));
        Assert.assertTrue("Only the call path may reload the field", reload.predecessor().predecessor() instanceof Invoke);
    }

    public static Object[] readAfterGenericKill(State state, Object[] alias) {
        genericMemoryKill();
        return state.references;
    }

    public static Object[] readAfterForeignCall(State state, Object[] alias) {
        foreignMemoryKill();
        return state.references;
    }

    public static Object[] readAfterForeignCallWithoutMemoryKill(State state, Object[] alias) {
        foreignNoMemoryKill();
        return state.references;
    }

    public static Object[] readAfterTwoCalls(State state, Object[] alias) {
        memoryKill();
        Object[] first = state.references;
        memoryKill();
        return first == state.references ? first : state.references;
    }

    public static Object[] readAfterCallAndGenericKill(State state, Object[] alias) {
        memoryKill();
        genericMemoryKill();
        return state.references;
    }

    static boolean alternate;

    @BytecodeParserNeverInline
    public static void callToInline() {
        if (alternate) {
            memoryKill();
        }
        memoryKill();
    }

    public static Object[] readAfterInlineableCall(State state, Object[] alias) {
        try {
            callToInline();
        } catch (Throwable t) {
            return state.references;
        }
        return state.references;
    }

    @Test
    public void testRefreshSurvivesInlining() throws NoSuchFieldException {
        for (boolean partialEscape : new boolean[]{false, true}) {
            StructuredGraph graph = graphWithFieldAlias("readAfterInlineableCall", true);
            eliminate(graph, partialEscape);
            ResolvedJavaMethod callee = getResolvedJavaMethod("callToInline");
            Invoke invoke = null;
            for (Invoke candidate : graph.getInvokes()) {
                if (candidate != null && candidate.callTarget().targetMethod().equals(callee)) {
                    invoke = candidate;
                    break;
                }
            }
            Assert.assertNotNull(invoke);
            StructuredGraph inlineGraph = parseEager(callee, AllowAssumptions.NO, graph.getOptions());
            InliningUtil.inline(invoke, inlineGraph, false, callee);
            createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
            new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
            eliminate(graph, partialEscape);
            for (ReturnNode ret : graph.getNodes(ReturnNode.TYPE)) {
                Assert.assertNotSame(graph.getParameter(1), ret.result());
            }
            int loads = graph.getNodes().filter(LoadFieldNode.class).count();
            eliminate(graph, partialEscape);
            Assert.assertEquals(loads, graph.getNodes().filter(LoadFieldNode.class).count());
        }
    }

    public static Object[] readAfterCaughtCall(State state, Object[] alias) {
        try {
            memoryKill();
        } catch (Throwable t) {
            return state.references;
        }
        return state.references;
    }

    public static Object[] readAfterLoopCall(State state, Object[] alias, int count) {
        for (int i = 0; i < count; i++) {
            memoryKill();
        }
        return state.references;
    }

    @Test
    public void testFieldAliasReloadsInsertedAfterGVN() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias("readAfterTwoCalls", true);
        new DominatorBasedGlobalValueNumberingPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        Assert.assertEquals(2, graph.getNodes().filter(LoadFieldNode.class).count());
    }

    @Test
    public void testRefreshPhaseInsertsOnlyOnNormalContinuation() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias("readAfterCaughtCall", true);
        InvokeWithExceptionNode call = graph.getNodes(InvokeWithExceptionNode.TYPE).first();
        new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        Assert.assertTrue(call.next().next() instanceof LoadFieldNode);
        Assert.assertEquals(2, graph.getNodes().filter(LoadFieldNode.class).count());
        checkBothReturnsReload(graph);
        Assert.assertEquals(1, graph.getNodes().filter(FieldAliasNode.class).count());
    }

    @Test
    public void testRefreshPhaseSkipsCallsBeforeAlias() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias("readAfterCall", true);
        FieldAliasNode alias = graph.getNodes().filter(FieldAliasNode.class).first();
        LoadFieldNode load = graph.getNodes().filter(LoadFieldNode.class).first();
        GraphUtil.unlinkFixedNode(alias);
        graph.addBeforeFixed(load, alias);
        new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        Assert.assertEquals(0, graph.getNodes().filter(LoadFieldNode.class).count());
        Assert.assertSame(graph.getParameter(1), graph.getNodes(ReturnNode.TYPE).first().result());
    }

    @Test
    public void testCallRefreshInReadElimination() throws NoSuchFieldException {
        checkCallRefresh(false);
    }

    @Test
    public void testCallRefreshInPartialEscapeAnalysis() throws NoSuchFieldException {
        checkCallRefresh(true);
    }

    private void eliminate(StructuredGraph graph, boolean partialEscape) {
        if (partialEscape) {
            new PartialEscapePhase(false, true, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, getDefaultHighTierContext());
        } else {
            new ReadEliminationPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        }
    }

    private void checkCallRefresh(boolean partialEscape) throws NoSuchFieldException {
        for (String snippet : new String[]{"readAfterCall", "readAfterConditionalCall", "readAfterCaughtCall", "readAfterLoopCall", "readAfterGenericKill", "readAfterForeignCall", "readAfterTwoCalls",
                        "readAfterCallAndGenericKill", "readAfterForeignCallWithoutMemoryKill"}) {
            StructuredGraph graph = graphWithFieldAlias(snippet, true);
            new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
            eliminate(graph, partialEscape);
            int loads = graph.getNodes().filter(LoadFieldNode.class).count();
            if (snippet.equals("readAfterGenericKill") || snippet.equals("readAfterForeignCall") || snippet.equals("readAfterForeignCallWithoutMemoryKill")) {
                Assert.assertEquals(0, loads);
                Assert.assertSame(graph.getParameter(1), graph.getNodes(ReturnNode.TYPE).first().result());
            } else {
                Assert.assertTrue(snippet, loads > 0);
                if (snippet.equals("readAfterCall")) {
                    Assert.assertEquals(1, loads);
                    Assert.assertSame(graph.getNodes().filter(LoadFieldNode.class).first(), graph.getNodes(ReturnNode.TYPE).first().result());
                }
                if (snippet.equals("readAfterCallAndGenericKill")) {
                    Assert.assertEquals(1, loads);
                    LoadFieldNode load = graph.getNodes().filter(LoadFieldNode.class).first();
                    FixedNode predecessor = graph.getNodes().filter(MembarNode.class).first();
                    while (predecessor != null && predecessor != load) {
                        predecessor = predecessor.predecessor() instanceof FixedNode fixed ? fixed : null;
                    }
                    Assert.assertSame("The cached alias reload survives a generic kill", load, predecessor);
                }
                for (LoadFieldNode load : graph.getNodes().filter(LoadFieldNode.class)) {
                    if (!snippet.equals("readAfterCaughtCall")) {
                        Assert.assertTrue(load.isImmutableFieldLoad());
                    }
                    Assert.assertEquals(graph.getParameter(1).stamp(NodeView.DEFAULT), load.stamp(NodeView.DEFAULT));
                }
                if (snippet.equals("readAfterCaughtCall")) {
                    Assert.assertEquals(2, loads);
                    checkBothReturnsReload(graph);
                } else {
                    for (ReturnNode ret : graph.getNodes(ReturnNode.TYPE)) {
                        Assert.assertNotSame(snippet, graph.getParameter(1), ret.result());
                    }
                }
            }
            eliminate(graph, partialEscape);
            Assert.assertEquals("Repeated read elimination must not duplicate refreshes: " + snippet, loads, graph.getNodes().filter(LoadFieldNode.class).count());
            Assert.assertEquals(1, graph.getNodes().filter(FieldAliasNode.class).count());
        }
    }

    private static void checkBothReturnsReload(StructuredGraph graph) {
        Assert.assertEquals(2, graph.getNodes(ReturnNode.TYPE).count());
        Assert.assertEquals(0, graph.getNodes(ReturnNode.TYPE).filter(ret -> ((ReturnNode) ret).result() == graph.getParameter(1)).count());
        Assert.assertEquals(2, graph.getNodes(ReturnNode.TYPE).filter(ret -> ((ReturnNode) ret).result() instanceof LoadFieldNode).count());
    }

    public static Object[] reloadAfterCall(State state, Object[] alias) {
        memoryKill();
        return state.references;
    }

    @Test
    public void testReloadStaysAfterCallThroughLowering() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias("reloadAfterCall", true);
        new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        getCode(graph.method(), graph, true);
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        if (!graph.method().equals(getResolvedJavaMethod("reloadAfterCall"))) {
            return;
        }
        ResolvedJavaField field = getMetaAccess().lookupJavaType(State.class).getInstanceFields(false)[0];
        ReadNode reload = null;
        for (ReadNode read : graph.getNodes().filter(ReadNode.class)) {
            if (read.getLocationIdentity().equals(new FieldLocationIdentity(field, true))) {
                Assert.assertNull(reload);
                reload = read;
            }
        }
        Assert.assertNotNull(reload);
        FixedNode predecessor = reload;
        while (predecessor != null && !(predecessor instanceof Invoke)) {
            predecessor = predecessor.predecessor() instanceof FixedNode fixed ? fixed : null;
        }
        Assert.assertNotNull("The fixed reload must remain after the call", predecessor);
    }

    @Test
    public void testImmutableAliasSurvivesGenericKillInReadElimination() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias("readAfterGenericKill", true);
        HighTierContext context = getDefaultHighTierContext();
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(graph, context);
        Assert.assertSame(graph.getParameter(1), graph.getNodes().filter(ReturnNode.class).first().result());
    }

    @Test
    public void testImmutableAliasSurvivesGenericKillInPartialEscapeAnalysis() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias("readAfterGenericKill", true);
        HighTierContext context = getDefaultHighTierContext();
        new PartialEscapePhase(false, true, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        Assert.assertSame(graph.getParameter(1), graph.getNodes().filter(ReturnNode.class).first().result());
    }

    @Test
    public void testMutableAliasIsKilledByCall() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias(false);
        HighTierContext context = getDefaultHighTierContext();
        new PartialEscapePhase(false, true, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        Assert.assertTrue(graph.getNodes().filter(ReturnNode.class).first().result() instanceof LoadFieldNode);
    }

    @Test
    public void testStoreInvalidatesImmutableAliasInReadElimination() throws NoSuchFieldException {
        checkStoreInvalidatesImmutableAlias(false);
    }

    @Test
    public void testStoreInvalidatesImmutableAliasInPartialEscapeAnalysis() throws NoSuchFieldException {
        checkStoreInvalidatesImmutableAlias(true);
    }

    private void checkStoreInvalidatesImmutableAlias(boolean partialEscape) throws NoSuchFieldException {
        for (boolean sameReceiver : new boolean[]{false, true}) {
            for (boolean killAfterStore : new boolean[]{false, true}) {
                StructuredGraph graph = graphWithFieldAlias("readAfterGenericKill", true);
                LoadFieldNode load = graph.getNodes().filter(LoadFieldNode.class).first();
                ParameterNode storedValue = graph.addWithoutUnique(new ParameterNode(2, StampPair.createSingle(load.stamp(NodeView.DEFAULT))));
                ParameterNode receiver = sameReceiver ? graph.getParameter(0)
                                : graph.addWithoutUnique(new ParameterNode(3, StampPair.createSingle(graph.getParameter(0).stamp(NodeView.DEFAULT))));
                StoreFieldNode store = graph.add(new StoreFieldNode(receiver, load.field(), storedValue));
                graph.addBeforeFixed(killAfterStore ? graph.getNodes().filter(MembarNode.class).first() : load, store);
                eliminate(graph, partialEscape);
                ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
                Assert.assertNotSame("A store must invalidate the immutable field alias", graph.getParameter(1), ret.result());
                if (sameReceiver && !killAfterStore) {
                    Assert.assertSame("The store establishes the ordinary field cache", storedValue, ret.result());
                } else {
                    Assert.assertTrue("The field must be read after losing the cached value", ret.result() instanceof LoadFieldNode);
                }
                new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
                Assert.assertNotSame(graph.getParameter(1), ret.result());
            }
        }
    }

    @Test
    public void testWriteNodeInvalidatesImmutableAlias() throws NoSuchFieldException {
        for (boolean partialEscape : new boolean[]{false, true}) {
            StructuredGraph graph = graphWithFieldAlias("readAfterGenericKill", true);
            LoadFieldNode load = graph.getNodes().filter(LoadFieldNode.class).first();
            ParameterNode value = graph.addWithoutUnique(new ParameterNode(2, StampPair.createSingle(load.stamp(NodeView.DEFAULT))));
            OffsetAddressNode address = graph.addOrUnique(new OffsetAddressNode(graph.getParameter(0), ConstantNode.forLong(load.field().getOffset(), graph)));
            WriteNode store = graph.add(new WriteNode(address, new FieldLocationIdentity(load.field()), value, BarrierType.NONE, MemoryOrderMode.PLAIN));
            store.setStateAfter(graph.start().stateAfter());
            graph.addBeforeFixed(load, store);
            eliminate(graph, partialEscape);
            Assert.assertSame("A low-level field write must invalidate the immutable alias", load, graph.getNodes(ReturnNode.TYPE).first().result());
        }
    }

    public static Object[] readAfterConstructingOther(State state, Object[] alias) {
        State other = new State(new Object[10]);
        GraalDirectives.blackhole(other);
        return state.references;
    }

    @Test
    public void testConstructorStoreDoesNotRejectRefresh() throws ReflectiveOperationException {
        StructuredGraph graph = graphWithFieldAlias("readAfterConstructingOther", true);
        ResolvedJavaMethod constructor = getMetaAccess().lookupJavaMethod(State.class.getDeclaredConstructor(Object[].class));
        for (Invoke invoke : graph.getInvokes()) {
            if (invoke.callTarget().targetMethod().equals(constructor)) {
                InliningUtil.inline(invoke, parseEager(constructor, AllowAssumptions.NO, graph.getOptions()), false, constructor);
                break;
            }
        }
        Assert.assertEquals(1, graph.getNodes().filter(StoreFieldNode.class).count());
        new FieldLoadRefreshPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        Assert.assertTrue("Initializing another instance conservatively invalidates the alias", graph.getNodes(ReturnNode.TYPE).first().result() instanceof LoadFieldNode);
    }

    private StructuredGraph graphWithFieldAlias(boolean immutable) throws NoSuchFieldException {
        return graphWithFieldAlias("readAfterCall", immutable);
    }

    private StructuredGraph graphWithFieldAlias(String snippet, boolean immutable) throws NoSuchFieldException {
        StructuredGraph graph = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.NO, new OptionValues(getInitialOptions(), GraalOptions.UseExceptionProbability, false,
                        GraalOptions.OptFloatingReads, false));
        ResolvedJavaField referencesField = getMetaAccess().lookupJavaField(State.class.getDeclaredField("references"));
        LoadFieldNode load = graph.getNodes().filter(LoadFieldNode.class).first();
        ParameterNode aliasParameter = graph.addWithoutUnique(new ParameterNode(1, StampPair.createSingle(load.stamp(NodeView.DEFAULT))));
        FieldAliasNode alias = graph.add(new FieldAliasNode(graph.getParameter(0), referencesField, aliasParameter, immutable));
        graph.addAfterFixed(graph.start(), alias);
        return graph;
    }

}
