package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A phase to identify isomorphisms within basic blocks
 * MIT (basis)  http://groups.csail.mit.edu/cag/slp/SLP-PLDI-2000.pdf
 * GCC          https://ols.fedoraproject.org/GCC/Reprints-2007/rosen-reprint.pdf
 * INTEL        https://people.apache.org/~xli/papers/npc10_java_vectorization.pdf
 */
public final class IsomorphicPackingPhase extends BasePhase<PhaseContext> {

    private static class NodePair<T extends Node> {
        private T left;
        private T right;

        public NodePair(T left, T right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodePair<?> nodePair = (NodePair<?>) o;
            return left.equals(nodePair.left) &&
                    right.equals(nodePair.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, right);
        }
    }

    /**
     * Check whether the left and right node of a potential pack are isomorphic.
     * "Isomorphic statements are those that contain the same operations in the same order."
     * @param left Left node of the potential pack
     * @param right Right node of the potential pack
     * @return Boolean indicating whether the left and right node of a potential pack are isomorphic.
     */
    private static boolean isomorphic(FixedNode left, FixedNode right) {
        // Trivial case, isomorphic if the same
        if (left == right || left.equals(right)) return true;

        // Are left & right the same action?
        if (!left.getNodeClass().equals(right.getNodeClass())) return false; // TODO: support subclasses

        // Is the input count the same? (accounts for inputs that are null)
        if (left.inputs().count() != right.inputs().count()) return false;

        // Conservatively bail if we have a FAN and non-FAN
        if (left instanceof FixedAccessNode != right instanceof FixedAccessNode) return false;

        // Ensure that FixedAccessNodes are dominated by the same controlSplit node
        if (left instanceof FixedAccessNode &&
                ((FixedAccessNode) left).getGuard() != null &&
                ((FixedAccessNode) right).getGuard() != null &&
                !((FixedAccessNode) left).getGuard().equals(((FixedAccessNode) right).getGuard())) return false;

        return true;
    }

    private static int findDepth(Block block, FixedNode node) {
        int depth = 0;
        for (FixedNode current : block.getNodes()) {
            if (current.equals(node)) {
                return depth;
            }
            depth++;
        }

        return -1;
    }

    private static boolean hasNoPath(Block block, NodeMap<Block> nodeToBlockMap, Node shallow, Node deep) {
        return hasNoPath(block, nodeToBlockMap, shallow, deep, 0);
    }

    private static boolean hasNoPath(Block block, NodeMap<Block> nodeToBlockMap, Node shallow, Node deep, int iterationDepth) {
        if (iterationDepth >= 1000) return false; // Stop infinite/deep recursion

        // TODO: pre-compute depth information
        // TODO: calcluate depth information to avoid recursing too far, doing unnecessary calculations
        // TODO: verify that at this stage it's even possible to get dependency cycles

        for (Node pred : deep.inputs()) {
            if (nodeToBlockMap.get(pred)!=block) // ensure that the predecessor is in the block
                continue;

            if (shallow == pred)
                return false;

            if (/* pred is below shallow && */ !hasNoPath(block, nodeToBlockMap, shallow, pred, iterationDepth + 1))
                return false;
        }

        return true;
    }

    /**
     * Ensure that there is no data path between left and right.
     * Pre: left and right are isomorphic
     * @param left Left node of the potential pack
     * @param right Right node of the potential pack
     * @return Are the two statements independent? Only independent statements may be packed.
     */
    private static boolean independent(Block block, FixedNode left, FixedNode right) {
        // Calculate depth from how far into block.getNodes() we are. TODO: this is a hack.
        final int leftDepth = findDepth(block, left);
        final int rightDepth = findDepth(block, right);

        if (leftDepth == rightDepth) return !left.equals(right);

        int shallowDepth = Math.min(leftDepth, rightDepth);
        FixedNode deep = leftDepth == shallowDepth ? right : left;
        FixedNode shallow = leftDepth == shallowDepth ? left : right;

        return hasNoPath(block, left.graph().getLastSchedule().getNodeToBlockMap(), shallow, deep);
    }

    private static boolean stmts_can_pack(Block block, FixedNode left, FixedNode right) {
        return isomorphic(left, right) && independent(block, left, right);
    }

    private static void find_adj_refs(Block block, Set<NodePair<FixedNode>> packSet) {
        // Create initial seed set containing memory operations
        Deque<FixedAccessNode> memoryNodes = // Candidate set of memory nodes
                StreamSupport.stream(block.getNodes().spliterator(), false)
                        .filter(NodePredicates.isA(FixedAccessNode.class)::apply)
                        .map(x -> (FixedAccessNode) x)
                        .collect(Collectors.toCollection(ArrayDeque::new));

        while (!memoryNodes.isEmpty()) {
            FixedNode leftCandidate = memoryNodes.pop();

            boolean assigned = false;
            for (FixedNode rightCandidate : memoryNodes) {
                if (stmts_can_pack(block, leftCandidate, rightCandidate)) {
                    packSet.add(new NodePair<>(leftCandidate, rightCandidate));
                    assigned = true;
                    break; // TODO: don't be greedy
                }
            }

            if (!assigned) {
                System.out.println(String.format("Dropped %s", leftCandidate.toString()));
            }
        }

    }

    private static void extend_packlist(Block block, Set<NodePair<FixedNode>> packSet) {
        throw new UnsupportedOperationException("not implemented");
    }

    private static void combine_packs(Set<NodePair<FixedNode>> packSet) {
        throw new UnsupportedOperationException("not implemented");
    }

    /* for each pair of nodes (either packed or unpacked)
       1. isomorphic?
       2. independent?
       3. left not already packed in a left position
       4. right not already packed in a right position
       5. alignment information consistent
       6. parallel execution is faster than scalar version (LATER)
     */
    private static void SLP_extract(Block block) {
        Set<NodePair<FixedNode>> packSet = new HashSet<>();
        find_adj_refs(block, packSet);
//        extend_packlist(block, packSet);
//        combine_packs(packSet);
        // return a new basic block with the new instructions scheduled
    }

    private final SchedulePhase schedulePhase;

    public IsomorphicPackingPhase(SchedulePhase schedulePhase) {
        this.schedulePhase = schedulePhase;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        // Schedule phase is required so that lowered addresses are in the nodeToBlockMap
        schedulePhase.apply(graph);
        ControlFlowGraph cfg = graph.getLastSchedule().getCFG();
        for (Block block : cfg.reversePostOrder()) {
            SLP_extract(block);
        }
    }
}
