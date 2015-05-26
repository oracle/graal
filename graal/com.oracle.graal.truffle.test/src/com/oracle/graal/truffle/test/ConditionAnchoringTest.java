/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import com.oracle.jvmci.meta.ConstantReflectionProvider;
import com.oracle.jvmci.meta.ResolvedJavaMethod;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.MetaAccessProvider;
import com.oracle.jvmci.meta.JavaType;
import com.oracle.jvmci.meta.ResolvedJavaField;
import static com.oracle.graal.graph.test.matchers.NodeIterableCount.*;
import static com.oracle.graal.graph.test.matchers.NodeIterableIsEmpty.*;
import static org.hamcrest.core.IsInstanceOf.*;
import static org.junit.Assert.*;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.LoweringTool.StandardLoweringStage;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.graal.truffle.substitutions.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.unsafe.*;

public class ConditionAnchoringTest extends GraalCompilerTest {
    private static final UnsafeAccess access;
    private static final long offset;
    private static final Object location = new Object();

    static {
        Unsafe unsafe = com.oracle.jvmci.common.UnsafeAccess.unsafe;
        access = Truffle.getRuntime().getCapability(UnsafeAccessFactory.class).createUnsafeAccess(unsafe);
        long fieldOffset = 0;
        try {
            fieldOffset = unsafe.objectFieldOffset(CheckedObject.class.getDeclaredField("field"));
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
            return access.getInt(o, offset, o.id == 42, location);
        }
        return -1;
    }

    // test with a different kind of condition (not a comparison against a constant)
    public int checkedAccess2(CheckedObject o) {
        if (o.id == o.iid) {
            return access.getInt(o, offset, o.id == o.iid, location);
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

        NodeIterable<UnsafeLoadNode> unsafeNodes = graph.getNodes().filter(UnsafeLoadNode.class);
        assertThat(unsafeNodes, hasCount(1));

        // lower unsafe load
        PhaseContext context = new PhaseContext(getProviders());
        LoweringPhase lowering = new LoweringPhase(new CanonicalizerPhase(), StandardLoweringStage.HIGH_TIER);
        lowering.apply(graph, context);

        unsafeNodes = graph.getNodes().filter(UnsafeLoadNode.class);
        NodeIterable<ConditionAnchorNode> conditionAnchors = graph.getNodes().filter(ConditionAnchorNode.class);
        NodeIterable<ReadNode> reads = graph.getNodes().filter(ReadNode.class);
        assertThat(unsafeNodes, isEmpty());
        assertThat(conditionAnchors, hasCount(1));
        assertThat(reads, hasCount(2 * ids + 1)); // 2 * ids id reads, 1 'field' access

        // float reads and canonicalize to give a chance to conditions to GVN
        FloatingReadPhase floatingReadPhase = new FloatingReadPhase();
        floatingReadPhase.apply(graph);
        CanonicalizerPhase canonicalizerPhase = new CanonicalizerPhase();
        canonicalizerPhase.apply(graph, context);

        NodeIterable<FloatingReadNode> floatingReads = graph.getNodes().filter(FloatingReadNode.class);
        assertThat(floatingReads, hasCount(ids + 1)); // 1 id read, 1 'field' access

        // apply DominatorConditionalEliminationPhase
        DominatorConditionalEliminationPhase conditionalElimination = new DominatorConditionalEliminationPhase(false);
        conditionalElimination.apply(graph);

        floatingReads = graph.getNodes().filter(FloatingReadNode.class).filter(n -> ((FloatingReadNode) n).location().getLocationIdentity() instanceof ObjectLocationIdentity);
        conditionAnchors = graph.getNodes().filter(ConditionAnchorNode.class);
        assertThat(floatingReads, hasCount(1));
        assertThat(conditionAnchors, isEmpty());
        FloatingReadNode readNode = floatingReads.first();
        assertThat(readNode.getGuard(), instanceOf(BeginNode.class));
        assertThat(readNode.getGuard().asNode().predecessor(), instanceOf(IfNode.class));
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        // get UnsafeAccessImpl.unsafeGetInt intrinsified
        TruffleGraphBuilderPlugins.registerUnsafeAccessImplPlugins(conf.getPlugins().getInvocationPlugins(), false);
        // get UnsafeAccess.getInt inlined
        conf.getPlugins().setInlineInvokePlugin(new InlineEverythingPlugin());
        conf.getPlugins().setLoadFieldPlugin(new FoldLoadsPlugins(getMetaAccess(), getConstantReflection()));
        return super.editGraphBuilderConfiguration(conf);
    }

    private static final class InlineEverythingPlugin implements InlineInvokePlugin {
        public InlineInfo getInlineInfo(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
            assert method.hasBytecodes();
            return new InlineInfo(method, false);
        }
    }

    private static final class FoldLoadsPlugins implements LoadFieldPlugin {
        private final MetaAccessProvider metaAccess;
        private final ConstantReflectionProvider constantReflection;

        public FoldLoadsPlugins(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
            this.metaAccess = metaAccess;
            this.constantReflection = constantReflection;
        }

        public boolean apply(GraphBuilderContext graphBuilderContext, ValueNode receiver, ResolvedJavaField field) {
            if (receiver.isConstant()) {
                JavaConstant asJavaConstant = receiver.asJavaConstant();
                return tryConstantFold(graphBuilderContext, metaAccess, constantReflection, field, asJavaConstant);
            }
            return false;
        }

        public boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaField staticField) {
            return tryConstantFold(graphBuilderContext, metaAccess, constantReflection, staticField, null);
        }
    }
}
