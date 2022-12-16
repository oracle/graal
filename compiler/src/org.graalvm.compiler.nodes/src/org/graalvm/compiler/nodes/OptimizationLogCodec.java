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
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.util.CollectionsUtil;

public class OptimizationLogCodec extends CompanionObjectCodec<OptimizationLog, OptimizationLogCodec.EncodedOptimizationLog> {
    private interface OptimizationTreeNode {

    }

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

    private static final class Optimization implements OptimizationTreeNode {
        private final EconomicMap<String, Object> properties;

        private final NodeSourcePosition position;

        private final String optimizationName;

        private final String event;

        private Optimization(EconomicMap<String, Object> properties, NodeSourcePosition position, String optimizationName, String event) {
            this.properties = properties;
            this.position = position;
            this.optimizationName = optimizationName;
            this.event = event;
        }
    }

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
            if (node instanceof OptimizationLogImpl.OptimizationPhaseScopeImpl) {
                OptimizationLogImpl.OptimizationPhaseScopeImpl phaseNode = (OptimizationLogImpl.OptimizationPhaseScopeImpl) node;
                OptimizationPhase encodedPhase = new OptimizationPhase(phaseNode.getPhaseName());
                if (phaseNode.getChildren() != null) {
                    phaseNode.getChildren().forEach((child) -> encodedPhase.addChild(encodeSubtree(child)));
                }
                return encodedPhase;
            }
            assert node instanceof OptimizationLogImpl.OptimizationEntryImpl;
            OptimizationLogImpl.OptimizationEntryImpl optimizationEntry = (OptimizationLogImpl.OptimizationEntryImpl) node;
            return new Optimization(optimizationEntry.getProperties(), optimizationEntry.getPosition(),
                            optimizationEntry.getOptimizationName(), optimizationEntry.getEventName());
        }
    }

    private static final class OptimizationLogDecoder implements Decoder<OptimizationLog> {
        @Override
        public void decode(OptimizationLog optimizationLog, Object encodedObject, Function<Integer, Node> mapper) {
            assert encodedObject instanceof EncodedOptimizationLog;
            EncodedOptimizationLog instance = (EncodedOptimizationLog) encodedObject;
            assert instance.root != null && optimizationLog instanceof OptimizationLogImpl;
            OptimizationLogImpl optimizationLogImpl = (OptimizationLogImpl) optimizationLog;
            OptimizationLogImpl.OptimizationPhaseScopeImpl rootPhase = optimizationLogImpl.findRootPhase();
            if (instance.root.children != null) {
                for (OptimizationTreeNode child : instance.root.children) {
                    decodeSubtreeInto(rootPhase, child, optimizationLogImpl, 1);
                }
            }
        }

        private static void decodeSubtreeInto(OptimizationLogImpl.OptimizationPhaseScopeImpl parent, OptimizationTreeNode node,
                        OptimizationLogImpl optimizationLog, int nesting) {
            if (node instanceof OptimizationPhase) {
                OptimizationPhase optimizationPhase = (OptimizationPhase) node;
                try (OptimizationLogImpl.OptimizationPhaseScopeImpl decodedPhase = optimizationLog.enterPhase(optimizationPhase.phaseName, nesting)) {
                    if (optimizationPhase.children != null) {
                        for (OptimizationTreeNode child : optimizationPhase.children) {
                            decodeSubtreeInto(decodedPhase, child, optimizationLog, nesting + 1);
                        }
                    }
                }
            } else {
                assert node instanceof Optimization && parent != null;
                Optimization optimization = (Optimization) node;
                parent.addChild(new OptimizationLogImpl.OptimizationEntryImpl(optimizationLog, optimization.properties,
                                optimization.position, optimization.optimizationName, optimization.event));
            }
        }

        @Override
        public boolean verify(OptimizationLog original, OptimizationLog decoded) {
            assert original instanceof OptimizationLogImpl;
            assert decoded instanceof OptimizationLogImpl;
            return subtreesEqual(((OptimizationLogImpl) original).findRootPhase(), ((OptimizationLogImpl) decoded).findRootPhase());
        }

        private static boolean subtreesEqual(OptimizationLog.OptimizationTreeNode treeNode1, OptimizationLog.OptimizationTreeNode treeNode2) {
            if (treeNode1 instanceof OptimizationLog.OptimizationEntry && treeNode2 instanceof OptimizationLog.OptimizationEntry) {
                OptimizationLogImpl.OptimizationEntryImpl entry1 = (OptimizationLogImpl.OptimizationEntryImpl) treeNode1;
                OptimizationLogImpl.OptimizationEntryImpl entry2 = (OptimizationLogImpl.OptimizationEntryImpl) treeNode2;
                return entry1.getOptimizationName().equals(entry2.getOptimizationName()) && entry1.getEventName().equals(entry2.getEventName()) &&
                                EconomicMapUtil.equals(entry1.getProperties(), entry2.getProperties());
            } else if (treeNode1 instanceof OptimizationLog.OptimizationPhaseScope && treeNode2 instanceof OptimizationLog.OptimizationPhaseScope) {
                OptimizationLogImpl.OptimizationPhaseScopeImpl phase1 = (OptimizationLogImpl.OptimizationPhaseScopeImpl) treeNode1;
                OptimizationLogImpl.OptimizationPhaseScopeImpl phase2 = (OptimizationLogImpl.OptimizationPhaseScopeImpl) treeNode2;
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

    public OptimizationLogCodec() {
        super(StructuredGraph::getOptimizationLog, new OptimizationLogEncoder(), new OptimizationLogDecoder());
    }
}
