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
package jdk.graal.compiler.truffle.test;

import static jdk.graal.compiler.debug.GraalError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExplosionKeyNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.SimplifyingGraphDecoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Decoder-level coverage for differing references in the mapping of a single virtual object.
 * Publishing objects in a Truffle root can instead produce distinct, invariant mappings and a
 * reference phi in a local. Install the precise mapping shape here and observe decoder-time copies.
 */
public class MergeExplodeMaterializedStateTest extends GraalCompilerTest {
    // Bytecode local slots in dispatchSnippet, after its five parameters.
    private static final int KEY_LOCAL = 5;
    private static final int OBJECT_LOCAL = 6;

    public static Object dispatchSnippet(Object zero, Object one, Object two, Object three, int[] control) {
        int key = 0;
        Object value = zero;
        while (true) {
            if (!GraalDirectives.inCompiledCode()) {
                return value;
            }
            if (--control[1] == 0) {
                return null;
            }
            switch (key) {
                case 0:
                    if (control[0] == 0) {
                        key = 1;
                        value = one;
                    } else {
                        key = 2;
                        value = two;
                    }
                    break;
                case 1:
                    if (control[2] == 0) {
                        key = 2;
                        value = two;
                    } else {
                        key = 3;
                        value = three;
                    }
                    break;
                case 2:
                    if (control[2] == 0) {
                        key = 3;
                        value = three;
                    } else {
                        key = 1;
                        value = one;
                    }
                    break;
                case 3:
                    if (control[0] == 0) {
                        key = 1;
                        value = one;
                    } else {
                        key = 2;
                        value = two;
                    }
                    break;
                default:
                    throw shouldNotReachHere("Unexpected dispatch key");
            }
        }
    }

    @Test
    public void markedMaterializedObjectState() {
        assertMaterializedObjectState(true);
    }

    @Test
    public void unmarkedMaterializedObjectState() {
        assertMaterializedObjectState(false);
    }

    @SuppressWarnings("try")
    private void assertMaterializedObjectState(boolean markKey) {
        ResolvedJavaMethod method = getResolvedJavaMethod("dispatchSnippet");
        StructuredGraph original = parseEager(method, AllowAssumptions.YES);
        EncodedGraph encoded = GraphEncoder.encodeSingleGraph(original, getTarget().arch);
        StructuredGraph graph = new StructuredGraph.Builder(original.getOptions(), original.getDebug(), AllowAssumptions.YES).method(method).build();
        MaterializedStateDecoder decoder = new MaterializedStateDecoder(graph, markKey);
        try (Graph.NodeEventScope scope = graph.trackNodeEvents(new Graph.NodeEventListener() {
            @Override
            public void nodeAdded(Node node) {
                decoder.trackStateCopy(node);
            }
        })) {
            decoder.decodeExploded(encoded);
        }
        Assert.assertTrue("Expected multiple entry states", decoder.entryMappings.size() > 2);
        for (MaterializedObjectState mapping : decoder.entryMappings) {
            Assert.assertTrue("Entry states must retain their specialized references", mapping.materializedValue() instanceof ParameterNode);
        }
        // One copy introduces the materialized-reference phi; one materializes the LoopBegin.
        // Further entries must extend the phi without copying the dispatcher state again.
        Assert.assertEquals(2, decoder.stateCopies);
        IntegerSwitchNode dispatcher = null;
        for (IntegerSwitchNode candidate : graph.getNodes().filter(IntegerSwitchNode.class)) {
            if (candidate.value() instanceof ValuePhiNode phi && phi.merge() instanceof LoopBeginNode) {
                Assert.assertNull("Expected one reconstructed dispatcher", dispatcher);
                dispatcher = candidate;
            }
        }
        Assert.assertNotNull(dispatcher);
        ValuePhiNode keyPhi = (ValuePhiNode) dispatcher.value();
        FrameState state = keyPhi.merge().stateAfter();
        MaterializedObjectState mapping = (MaterializedObjectState) state.virtualObjectMappingAt(0);
        Assert.assertTrue(mapping.materializedValue() instanceof ValuePhiNode);
        ValuePhiNode objectPhi = (ValuePhiNode) mapping.materializedValue();
        Assert.assertSame(keyPhi.merge(), objectPhi.merge());
        Assert.assertEquals(keyPhi.valueCount(), objectPhi.valueCount());
        for (int i = 0; i < keyPhi.valueCount(); i++) {
            Assert.assertSame("Materialized references must follow predecessor ordering", graph.getParameter(keyPhi.valueAt(i).asJavaConstant().asInt()), objectPhi.valueAt(i));
        }
    }

    private final class MaterializedStateDecoder extends SimplifyingGraphDecoder {
        private final List<MaterializedObjectState> entryMappings = new ArrayList<>();
        private final boolean markKey;
        private VirtualInstanceNode object;
        private int stateCopies;

        MaterializedStateDecoder(StructuredGraph graph, boolean markKey) {
            super(getTarget().arch, graph, getProviders(), false);
            this.markKey = markKey;
        }

        void decodeExploded(EncodedGraph encodedGraph) {
            recordGraphElements(encodedGraph);
            MethodScope methodScope = new MethodScope(null, graph, encodedGraph, LoopExplosionKind.MERGE_EXPLODE) {
            };
            methodScope.currentLoopScope = createInitialLoopScope(methodScope, null);
            doDecode(methodScope);
            cleanupGraph(methodScope);
            Assert.assertTrue(graph.verify());
        }

        @Override
        protected void handleLoopExplosionBegin(MethodScope methodScope, LoopScope loopScope, LoopBeginNode loopBegin) {
            // Model the marker produced by the Truffle invocation plugin at the first header.
            if (markKey && loopScope.trigger == LoopScopeTrigger.START) {
                FrameState state = loopBegin.stateAfter();
                state.values().set(KEY_LOCAL, graph.addWithoutUnique(new LoopExplosionKeyNode(state.values().get(KEY_LOCAL))));
            }
            super.handleLoopExplosionBegin(methodScope, loopScope, loopBegin);
        }

        @Override
        protected void checkLoopExplosionIteration(MethodScope methodScope, LoopScope loopScope) {
            Assert.assertTrue("Loop explosion must converge", loopScope.loopIteration < 20);
        }

        @Override
        protected void afterMethodScope(MethodScope methodScope) {
            /*
             * Represent the live object local using a materialized-object mapping, as escape
             * analysis can do. Keep the same virtual identity in every entry, but map it to the
             * entry's specialized object reference. Inject this representation before loop
             * reconstruction so the test does not depend on escape-analysis heuristics.
             */
            Assert.assertEquals(markKey, methodScope.loopExplosionHeadKeyFilter != null);
            object = graph.addWithoutUnique(new VirtualInstanceNode(getMetaAccess().lookupJavaType(Object.class), true));
            int bci = methodScope.loopExplosionHead.stateAfter().bci;
            for (Node node : methodScope.loopExplosionMerges) {
                MergeNode merge = (MergeNode) node;
                FrameState state = merge.stateAfter();
                if (state != null && state.bci == bci) {
                    ValueNode value = state.values().get(OBJECT_LOCAL);
                    Assert.assertTrue(value instanceof ParameterNode);
                    MaterializedObjectState mapping = graph.addWithoutUnique(new MaterializedObjectState(object, value));
                    FrameState copy = state.duplicate();
                    copy.values().set(OBJECT_LOCAL, object);
                    copy.addVirtualObjectMapping(mapping);
                    merge.setStateAfter(copy);
                    entryMappings.add(mapping);
                }
            }
        }

        void trackStateCopy(Node node) {
            if (node instanceof FrameState state && state.values().size() > OBJECT_LOCAL && state.values().get(OBJECT_LOCAL) == object &&
                            state.values().get(KEY_LOCAL) instanceof ValuePhiNode phi && phi.merge() instanceof MergeNode merge && merge.stateAfter() != null &&
                            merge.stateAfter().values().contains(phi)) {
                stateCopies++;
            }
        }
    }
}
