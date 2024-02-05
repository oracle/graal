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
package jdk.graal.compiler.truffle.test;

import static jdk.graal.compiler.graph.test.matchers.NodeIterableCount.hasCount;
import static jdk.graal.compiler.graph.test.matchers.NodeIterableIsEmpty.isEmpty;
import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Field;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConditionAnchorNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LoweringPhase;
import jdk.graal.compiler.truffle.nodes.ObjectLocationIdentity;
import jdk.graal.compiler.truffle.substitutions.TruffleGraphBuilderPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class ConditionAnchoringTest extends GraalCompilerTest {
    private static final long offset;
    private static final Object location = new Object();

    static {
        long fieldOffset = 0;
        try {
            Field field = CheckedObject.class.getDeclaredField("field");
            fieldOffset = getObjectFieldOffset(field);
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
        LoweringPhase lowering = new HighTierLoweringPhase(createCanonicalizerPhase());
        lowering.apply(graph, context);

        unsafeNodes = graph.getNodes().filter(RawLoadNode.class);
        NodeIterable<ConditionAnchorNode> conditionAnchors = graph.getNodes().filter(ConditionAnchorNode.class);
        NodeIterable<ReadNode> reads = graph.getNodes().filter(ReadNode.class);
        assertThat(unsafeNodes, isEmpty());
        assertThat(conditionAnchors, hasCount(1));
        assertThat(reads, hasCount(2 * ids + 1)); // 2 * ids id reads, 1 'field' access

        // float reads and canonicalize to give a chance to conditions to GVN
        CanonicalizerPhase canonicalizerPhase = createCanonicalizerPhase();
        FloatingReadPhase floatingReadPhase = new FloatingReadPhase(canonicalizerPhase);
        floatingReadPhase.apply(graph, context);

        NodeIterable<FloatingReadNode> floatingReads = graph.getNodes().filter(FloatingReadNode.class);
        assertThat(floatingReads, hasCount(ids + 1)); // 1 id read, 1 'field' access

        new ConditionalEliminationPhase(canonicalizerPhase, false).apply(graph, context);

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
