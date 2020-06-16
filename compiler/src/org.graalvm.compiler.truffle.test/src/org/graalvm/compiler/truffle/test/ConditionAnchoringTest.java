/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.graph.test.matchers.NodeIterableCount.hasCount;
import static org.graalvm.compiler.graph.test.matchers.NodeIterableIsEmpty.isEmpty;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConditionAnchorNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LoweringTool.StandardLoweringStage;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.truffle.compiler.nodes.ObjectLocationIdentity;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class ConditionAnchoringTest extends GraalCompilerTest {
    private static final long offset;
    private static final Object location = new Object();

    static {
        long fieldOffset = 0;
        try {
            fieldOffset = UNSAFE.objectFieldOffset(CheckedObject.class.getDeclaredField("field"));
        } catch (NoSuchFieldException | SecurityException e) {
            e.printStackTrace();
        }
        offset = fieldOffset;
    }

    private static class CheckedObject {
        int id;
        int iid;
        @SuppressWarnings("unused") int field;
    }

    public int checkedAccess(CheckedObject o) {
        if (o.id == 42) {
            return MyUnsafeAccess.unsafeGetInt(o, offset, o.id == 42, location);
        }
        return -1;
    }

    // test with a different kind of condition (not a comparison against a constant)
    public int checkedAccess2(CheckedObject o) {
        if (o.id == o.iid) {
            return MyUnsafeAccess.unsafeGetInt(o, offset, o.id == o.iid, location);
        }
        return -1;
    }

    @Test
    public void test() {
        test("checkedAccess", 1);
    }

    @Test
    public void test2() {
        test("checkedAccess2", 2);
    }

    public void test(String name, int ids) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.YES);

        NodeIterable<RawLoadNode> unsafeNodes = graph.getNodes().filter(RawLoadNode.class);
        assertThat(unsafeNodes, hasCount(1));

        // lower unsafe load
        CoreProviders context = getProviders();
        LoweringPhase lowering = new LoweringPhase(createCanonicalizerPhase(), StandardLoweringStage.HIGH_TIER);
        lowering.apply(graph, context);

        unsafeNodes = graph.getNodes().filter(RawLoadNode.class);
        NodeIterable<ConditionAnchorNode> conditionAnchors = graph.getNodes().filter(ConditionAnchorNode.class);
        NodeIterable<ReadNode> reads = graph.getNodes().filter(ReadNode.class);
        assertThat(unsafeNodes, isEmpty());
        assertThat(conditionAnchors, hasCount(1));
        assertThat(reads, hasCount(2 * ids + 1)); // 2 * ids id reads, 1 'field' access

        // float reads and canonicalize to give a chance to conditions to GVN
        FloatingReadPhase floatingReadPhase = new FloatingReadPhase();
        floatingReadPhase.apply(graph);
        CanonicalizerPhase canonicalizerPhase = createCanonicalizerPhase();
        canonicalizerPhase.apply(graph, context);

        NodeIterable<FloatingReadNode> floatingReads = graph.getNodes().filter(FloatingReadNode.class);
        assertThat(floatingReads, hasCount(ids + 1)); // 1 id read, 1 'field' access

        new ConditionalEliminationPhase(false).apply(graph, context);

        floatingReads = graph.getNodes().filter(FloatingReadNode.class).filter(n -> ((FloatingReadNode) n).getLocationIdentity() instanceof ObjectLocationIdentity);
        conditionAnchors = graph.getNodes().filter(ConditionAnchorNode.class);
        assertThat(floatingReads, hasCount(1));
        assertThat(conditionAnchors, isEmpty());
        FloatingReadNode readNode = floatingReads.first();
        assertThat(readNode.getGuard(), instanceOf(BeginNode.class));
        assertThat(readNode.getGuard().asNode().predecessor(), instanceOf(IfNode.class));
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        // get UnsafeAccessImpl.unsafeGetInt intrinsified
        Registration r = new Registration(invocationPlugins, MyUnsafeAccess.class);
        TruffleGraphBuilderPlugins.registerUnsafeLoadStorePlugins(r, false, null, JavaKind.Int);
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        // get UnsafeAccess.getInt inlined
        conf.getPlugins().appendInlineInvokePlugin(new InlineEverythingPlugin());
        return super.editGraphBuilderConfiguration(conf);
    }

    private static final class InlineEverythingPlugin implements InlineInvokePlugin {
        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            assert method.hasBytecodes();
            return createStandardInlineInfo(method);
        }
    }

    @SuppressWarnings({"unused", "hiding"})
    private static final class MyUnsafeAccess {
        private static final Unsafe MY_UNSAFE = UNSAFE;

        static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
            return MY_UNSAFE.getInt(receiver, offset);
        }

        static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
            MY_UNSAFE.putInt(receiver, offset, value);
        }
    }
}
