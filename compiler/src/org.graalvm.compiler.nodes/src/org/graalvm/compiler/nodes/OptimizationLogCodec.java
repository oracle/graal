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
package org.graalvm.compiler.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapUtil;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.util.CollectionsUtil;

/**
 * Encodes and decodes the {@link OptimizationLog}.
 *
 * @see CompanionObjectCodec
 */
public class OptimizationLogCodec extends CompanionObjectCodec<OptimizationLog, OptimizationLogCodec.EncodedOptimizationLog> {
    /**
     * An encoded optimization or optimization phase.
     */
    private interface OptimizationTreeNode {

    }

    /**
     * An encoded representation of {@link OptimizationLogImpl.OptimizationPhaseNode}.
     */
    private static final class OptimizationPhase implements OptimizationTreeNode {
        private final CharSequence phaseName;

        private List<OptimizationTreeNode> children;

        private OptimizationPhase(CharSequence phaseName) {
            this.phaseName = phaseName;
            this.children = null;
        }

        private void addChild(OptimizationTreeNode child) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(child);
        }
    }

    /**
     * An encoded representation of {@link OptimizationLogImpl.OptimizationNode}.
     */
    private static final class Optimization implements OptimizationTreeNode {
        private final EconomicMap<String, Object> properties;

        private final NodeSourcePosition position;

        private final String optimizationName;

        private final String eventName;

        private Optimization(EconomicMap<String, Object> properties, NodeSourcePosition position, String optimizationName, String eventName) {
            this.properties = properties;
            this.position = position;
            this.optimizationName = optimizationName;
            this.eventName = eventName;
        }
    }

    /**
     * An encoded representation of the {@link OptimizationLog}.
     */
    protected static final class EncodedOptimizationLog implements EncodedObject {
        private OptimizationPhase root;
    }

    private static final class OptimizationLogEncoder implements Encoder<OptimizationLog, EncodedOptimizationLog> {
        @Override
        public boolean shouldBeEncoded(OptimizationLog optimizationLog) {
            return optimizationLog.isOptimizationLogEnabled();
        }

        @Override
        public EncodedOptimizationLog prepare(OptimizationLog optimizationLog) {
            assert shouldBeEncoded(optimizationLog) && optimizationLog instanceof OptimizationLogImpl;
            return new EncodedOptimizationLog();
        }

        @Override
        public void encode(EncodedOptimizationLog encodedObject, OptimizationLog optimizationLog, Function<Node, Integer> mapper) {
            assert encodedObject.root == null && shouldBeEncoded(optimizationLog) && optimizationLog instanceof OptimizationLogImpl;
            encodedObject.root = (OptimizationPhase) encodeSubtree(((OptimizationLogImpl) optimizationLog).findRootPhase());
        }

        private static OptimizationTreeNode encodeSubtree(OptimizationLog.OptimizationTreeNode node) {
            if (node instanceof OptimizationLogImpl.OptimizationPhaseNode) {
                OptimizationLogImpl.OptimizationPhaseNode phaseNode = (OptimizationLogImpl.OptimizationPhaseNode) node;
                OptimizationPhase encodedPhase = new OptimizationPhase(phaseNode.getPhaseName());
                if (phaseNode.getChildren() != null) {
                    phaseNode.getChildren().forEach((child) -> encodedPhase.addChild(encodeSubtree(child)));
                }
                return encodedPhase;
            }
            assert node instanceof OptimizationLogImpl.OptimizationNode;
            OptimizationLogImpl.OptimizationNode optimizationEntry = (OptimizationLogImpl.OptimizationNode) node;
            return new Optimization(optimizationEntry.getProperties(), optimizationEntry.getPosition(),
                            optimizationEntry.getOptimizationName(), optimizationEntry.getEventName());
        }
    }

    private static final class OptimizationLogDecoder implements Decoder<OptimizationLog> {
        @Override
        public OptimizationLog decode(StructuredGraph graph, Object encodedObject) {
            if (!graph.getOptimizationLog().isOptimizationLogEnabled()) {
                return graph.getOptimizationLog();
            }
            OptimizationLogImpl optimizationLog = new OptimizationLogImpl(graph);
            if (encodedObject != null) {
                EncodedOptimizationLog instance = (EncodedOptimizationLog) encodedObject;
                assert instance.root != null;
                if (instance.root.children != null) {
                    for (OptimizationTreeNode child : instance.root.children) {
                        decodeSubtreeInto(child, optimizationLog);
                    }
                }
            }
            return optimizationLog;
        }

        @Override
        public void registerNode(OptimizationLog optimizationLog, Node node, int orderId) {

        }

        @SuppressWarnings("try")
        private static void decodeSubtreeInto(OptimizationTreeNode node, OptimizationLogImpl optimizationLog) {
            if (node instanceof OptimizationPhase) {
                OptimizationPhase optimizationPhase = (OptimizationPhase) node;
                try (DebugCloseable c = optimizationLog.enterPhase(optimizationPhase.phaseName)) {
                    if (optimizationPhase.children != null) {
                        for (OptimizationTreeNode child : optimizationPhase.children) {
                            decodeSubtreeInto(child, optimizationLog);
                        }
                    }
                }
            } else {
                assert node instanceof Optimization;
                Optimization optimization = (Optimization) node;
                optimizationLog.getCurrentPhase().addChild(new OptimizationLogImpl.OptimizationNode(optimization.properties,
                                optimization.position, optimization.optimizationName, optimization.eventName));
            }
        }
    }

    public OptimizationLogCodec() {
        super(StructuredGraph::getOptimizationLog, new OptimizationLogEncoder());
    }

    @Override
    public Decoder<OptimizationLog> singleObjectDecoder() {
        return new OptimizationLogDecoder();
    }

    @Override
    public boolean verify(StructuredGraph originalGraph, StructuredGraph decodedGraph) {
        OptimizationLog original = originalGraph.getOptimizationLog();
        OptimizationLog decoded = decodedGraph.getOptimizationLog();
        if (!original.isOptimizationLogEnabled() || !decoded.isOptimizationLogEnabled()) {
            return true;
        }
        assert original instanceof OptimizationLogImpl;
        assert decoded instanceof OptimizationLogImpl;
        return subtreesEqual(((OptimizationLogImpl) original).findRootPhase(), ((OptimizationLogImpl) decoded).findRootPhase());
    }

    private static boolean subtreesEqual(OptimizationLog.OptimizationTreeNode treeNode1, OptimizationLog.OptimizationTreeNode treeNode2) {
        if (treeNode1 instanceof OptimizationLogImpl.OptimizationNode && treeNode2 instanceof OptimizationLogImpl.OptimizationNode) {
            OptimizationLogImpl.OptimizationNode entry1 = (OptimizationLogImpl.OptimizationNode) treeNode1;
            OptimizationLogImpl.OptimizationNode entry2 = (OptimizationLogImpl.OptimizationNode) treeNode2;
            return entry1.getOptimizationName().equals(entry2.getOptimizationName()) && entry1.getEventName().equals(entry2.getEventName()) &&
                            EconomicMapUtil.equals(entry1.getProperties(), entry2.getProperties());
        } else if (treeNode1 instanceof OptimizationLogImpl.OptimizationPhaseNode && treeNode2 instanceof OptimizationLogImpl.OptimizationPhaseNode) {
            OptimizationLogImpl.OptimizationPhaseNode phase1 = (OptimizationLogImpl.OptimizationPhaseNode) treeNode1;
            OptimizationLogImpl.OptimizationPhaseNode phase2 = (OptimizationLogImpl.OptimizationPhaseNode) treeNode2;
            if (!phase1.getPhaseName().equals(phase2.getPhaseName())) {
                return false;
            }
            if (phase1.getChildren() == null && phase2.getChildren() == null) {
                return true;
            }
            if (phase1.getChildren() == null || phase2.getChildren() == null) {
                return false;
            }
            Iterable<OptimizationLog.OptimizationTreeNode> children1 = () -> phase1.getChildren().stream().iterator();
            Iterable<OptimizationLog.OptimizationTreeNode> children2 = () -> phase2.getChildren().stream().iterator();
            return CollectionsUtil.allMatch(CollectionsUtil.zipLongest(children1, children2), (pair) -> subtreesEqual(pair.getLeft(), pair.getRight()));
        }
        return false;
    }
}
