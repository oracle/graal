/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapUtil;
import org.graalvm.collections.UnmodifiableEconomicMap;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * Encodes and decodes the {@link OptimizationLog}.
 *
 * @see CompanionObjectEncoder
 */
public class OptimizationLogCodec extends CompanionObjectEncoder<OptimizationLog, OptimizationLogCodec.EncodedOptimizationLog> {
    /**
     * An encoded representation of the {@link OptimizationLog}.
     */
    protected static final class EncodedOptimizationLog implements CompanionObjectEncoder.EncodedObject {
        /**
         * A copy of the original optimization tree.
         */
        private Graph optimizationTree;

        /**
         * The root phase in the copied optimization tree.
         */
        private OptimizationLogImpl.OptimizationPhaseNode root;
    }

    @Override
    protected OptimizationLog getCompanionObject(StructuredGraph graph) {
        return graph.getOptimizationLog();
    }

    @Override
    protected boolean shouldBeEncoded(OptimizationLog optimizationLog) {
        return optimizationLog.isStructuredOptimizationLogEnabled();
    }

    @Override
    protected EncodedOptimizationLog createInstance(OptimizationLog optimizationLog) {
        assert shouldBeEncoded(optimizationLog) && optimizationLog instanceof OptimizationLogImpl : "prepare should be called iff there is anything to encode";
        return new EncodedOptimizationLog();
    }

    @Override
    protected void encodeIntoInstance(EncodedOptimizationLog encodedObject, OptimizationLog optimizationLog, Function<Node, Integer> mapper) {
        assert encodedObject.optimizationTree == null && encodedObject.root == null && shouldBeEncoded(optimizationLog) &&
                        optimizationLog instanceof OptimizationLogImpl : "encode should be once iff there is anything to encode";
        OptimizationLogImpl optimizationLogImpl = (OptimizationLogImpl) optimizationLog;
        Graph original = optimizationLogImpl.getOptimizationTree();
        encodedObject.optimizationTree = new Graph(original.name, original.getOptions(), original.getDebug(), original.trackNodeSourcePosition());
        UnmodifiableEconomicMap<Node, Node> duplicates = encodedObject.optimizationTree.addDuplicates(original.getNodes(), original, original.getNodeCount(), (EconomicMap<Node, Node>) null);
        encodedObject.root = (OptimizationLogImpl.OptimizationPhaseNode) duplicates.get(optimizationLogImpl.findRootPhase());
    }

    /**
     * Decodes an encoded optimization log for a given graph if the graph expects a non-null
     * optimization log. Returns {@code null} otherwise.
     *
     * @param graph the graph being decoded
     * @param encodedObject the encoded optimization log to decode
     * @return an encoded optimization log or {@code null}
     */
    public static OptimizationLog maybeDecode(StructuredGraph graph, Object encodedObject) {
        if (!graph.getOptimizationLog().isStructuredOptimizationLogEnabled()) {
            return null;
        }
        OptimizationLogImpl optimizationLog = new OptimizationLogImpl(graph);
        if (encodedObject != null) {
            EncodedOptimizationLog instance = (EncodedOptimizationLog) encodedObject;
            assert instance.optimizationTree != null && instance.root != null : "an empty optimization tree should be encoded as null";
            OptimizationLogImpl.OptimizationPhaseNode rootPhase = optimizationLog.findRootPhase();
            UnmodifiableEconomicMap<Node, Node> duplicates = optimizationLog.getOptimizationTree().addDuplicates(instance.optimizationTree.getNodes(), instance.optimizationTree,
                            instance.optimizationTree.getNodeCount(),
                            (EconomicMap<Node, Node>) null);
            duplicates.get(instance.root).safeDelete();
            for (OptimizationLog.OptimizationTreeNode child : instance.root.getChildren()) {
                rootPhase.getChildren().add(duplicates.get(child));
            }
        }
        return optimizationLog;
    }

    @Override
    public boolean verify(StructuredGraph originalGraph, StructuredGraph decodedGraph) {
        OptimizationLog original = originalGraph.getOptimizationLog();
        OptimizationLog decoded = decodedGraph.getOptimizationLog();
        if (!original.isStructuredOptimizationLogEnabled() || !decoded.isStructuredOptimizationLogEnabled()) {
            return true;
        }
        assert original instanceof OptimizationLogImpl && decoded instanceof OptimizationLogImpl : "enabled optimization log implies instanceof OptimizationLogImpl";
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
            Iterable<OptimizationLog.OptimizationTreeNode> children1 = () -> phase1.getChildren().stream().iterator();
            Iterable<OptimizationLog.OptimizationTreeNode> children2 = () -> phase2.getChildren().stream().iterator();
            return CollectionsUtil.allMatch(CollectionsUtil.zipLongest(children1, children2), (pair) -> subtreesEqual(pair.getLeft(), pair.getRight()));
        }
        return false;
    }
}
