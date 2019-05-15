package org.graalvm.compiler.phases.common;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A phase to identify isomorphisms within basic blocks
 * MIT (basis)  http://groups.csail.mit.edu/cag/slp/SLP-PLDI-2000.pdf
 * GCC          https://ols.fedoraproject.org/GCC/Reprints-2007/rosen-reprint.pdf
 * INTEL        https://people.apache.org/~xli/papers/npc10_java_vectorization.pdf
 */
public final class IsomorphicPackingPhase extends BasePhase<PhaseContext> {

    // TODO: extract to separate file
    private static class Pack<T extends Node> implements Iterable<T> {
        private List<T> elements;

        private Pack(T left, T right) {
            this.elements = Arrays.asList(left, right);
        }

        private Pack(List<T> elements) {
            this.elements = elements;
        }

        // Accessors
        public T getLeft() {
            return elements.get(0);
        }

        public T getRight() {
            return elements.get(elements.size() - 1);
        }

        public boolean isPair() {
            return elements.size() == 2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pack<?> pack = (Pack<?>) o;
            return elements.equals(pack.elements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elements);
        }

        // Iterable<T>
        @Override
        public Iterator<T> iterator() {
            return elements.iterator();
        }

        @Override
        public Spliterator<T> spliterator() {
            return elements.spliterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            elements.forEach(action);
        }

        // Builders
        public static <T extends Node> Pack<T> pair(T left, T right) {
            return new Pack<>(left, right);
        }

        public static <T extends Node> Pack<T> list(List<T> elements) {
            if (elements.size() < 2) throw new IllegalArgumentException("cannot construct pack consisting of single element");

            return new Pack<>(elements);
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

    /**
     * Check whether s1 is immediately before s2 in memory, if both are primitive.
     * @param s1 First FixedAccessNode to check
     * @param s2 Second FixedAccessNode to check
     * @return Boolean indicating whether s1 is immediately before s2 in memory
     */
    private static boolean adjacent(FixedAccessNode s1, FixedAccessNode s2) {
        final JavaKind s1k = s1 instanceof WriteNode ? ((WriteNode) s1).value().getStackKind() : s1.getStackKind();
        final JavaKind s2k = s2 instanceof WriteNode ? ((WriteNode) s2).value().getStackKind() : s2.getStackKind();

        return adjacent(s1, s2, s1k, s2k);
    }

    /**
     * Check whether s1 is immediately before s2 in memory, if both are primitive.
     * This function exists as not all nodes carry the right kind information.
     * TODO: Find a better way to deal with this
     * @param s1 First FixedAccessNode to check
     * @param s2 Second FixedAccessNode to check
     * @param s1k JavaKind of the first FixedAccessNode
     * @param s2k JavaKind of the second FixedAccessNode
     * @return Boolean indicating whether s1 is immediately before s2 in memory
     */
    private static boolean adjacent(FixedAccessNode s1, FixedAccessNode s2, JavaKind s1k, JavaKind s2k) {
        final AddressNode s1a = s1.getAddress(), s2a = s2.getAddress();

        // Only use superword on primitives
        if (!s1k.isPrimitive() || !s2k.isPrimitive()) return false;

        // Only use superword on types that are comparable
        // TODO: Evaluate whether graph guarantees that pointers for same collection have same base
        if (!s1a.getBase().equals(s2a.getBase())) return false;

        return s2a.getMaxConstantDisplacement() - s1a.getMaxConstantDisplacement() == s1k.getByteCount();
    }

    private static boolean stmts_can_pack(Block block, FixedNode left, FixedNode right) {
        // TODO: ensure that the left candidate isn't already present in a left position
        // TODO: ensure that the right candidate isn't already present in a right position
        return isomorphic(left, right) && independent(block, left, right);
    }

    /**
     * Create the initial seed packSet of operations that are adjacent
     * TODO: PERFORM ADJACENCY TEST
     * TODO: CHECK THAT VECTOR ELEMENT TYPE IS THE SAME
     * @param block Basic block
     * @param packSet PackSet to populate
     */
    private static void find_adj_refs(Block block, Set<Pack<FixedNode>> packSet) {
        // Create initial seed set containing memory operations
        Deque<FixedAccessNode> memoryNodes = // Candidate set of memory nodes
                StreamSupport.stream(block.getNodes().spliterator(), false)
                        .filter(NodePredicates.isA(FixedAccessNode.class)::apply)
                        .map(x -> (FixedAccessNode) x)
                        .collect(Collectors.toCollection(ArrayDeque::new));

        while (!memoryNodes.isEmpty()) {
            FixedAccessNode leftCandidate = memoryNodes.pop();

            boolean assigned = false;
            for (FixedAccessNode rightCandidate : memoryNodes) {
                if (adjacent(leftCandidate, rightCandidate) && stmts_can_pack(block, leftCandidate, rightCandidate)) {
                    packSet.add(Pack.pair(leftCandidate, rightCandidate));
                    assigned = true;
                    break; // TODO: don't be greedy
                }
            }

            if (!assigned) { // TODO: handle dropped values & do this check correctly
                System.out.println(String.format("Dropped %s", leftCandidate.toString()));
            }
        }

    }

    /**
     * Extend the packset by visiting operand definitions of nodes inside the provided pack
     * @param block Basic block currently being optimized
     * \@param packSet Pack set
     * @param pack The pack to use for operand definitions
     */
    private static void follow_use_defs(Block block, /*Set<Pack<FixedNode>> packSet, */Pack<FixedNode> pack) {
        assert pack.isPair(); // the pack should be a pair

        for (FixedNode node : pack) {
            node.inputs();
        }
    }

    /**
     * Extend the packset by visiting uses of jnodes of nodes in the provided pack
     * @param block Basic block currently being optimized
     * \@param packSet Pack set
     * @param pack The pack to use for nodes to find usages of
     */
    private static void follow_def_uses(Block block, /*Set<Pack<FixedNode>> packSet, */Pack<FixedNode> pack) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Extend the packset by following use->def and def->use links from pack members until the set does not change
     * @param block Basic block
     * @param packSet PackSet to populate
     */
    private static void extend_packlist(Block block, Set<Pack<FixedNode>> packSet) {
        boolean change = false;

        do {
            for (Pack<FixedNode> pack : packSet) {
                follow_use_defs(block, /* packSet, */pack);
                follow_def_uses(block, /* packSet, */pack);
            }
        } while (change);
    }

    private static void combine_packs(Set<Pack<FixedNode>> packSet) {
        throw new UnsupportedOperationException("not implemented");
    }

    private static void SLP_extract(Block block) {
        Set<Pack<FixedNode>> packSet = new HashSet<>();
        find_adj_refs(block, packSet);
        extend_packlist(block, packSet);
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
