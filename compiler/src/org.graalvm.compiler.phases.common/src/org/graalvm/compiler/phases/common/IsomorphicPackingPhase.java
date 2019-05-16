package org.graalvm.compiler.phases.common;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.iterators.NodePredicate;
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

        /**
         * Returns true if left matches the left side of the pair or right matches the right side of the pair.
         * @param left Node on the left side of the candidate pair
         * @param right Node on the right side of the candidate pair
         */
        public boolean match(T left, T right) {
            return getLeft().equals(left) || getRight().equals(right);
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

        /**
         * Appends the right pack to the left pack, omitting the first element of the right pack.
         * Pre: last element of left = first element of right
         */
        public static <T extends Node> Pack<T> combine(Pack<T> left, Pack<T> right) {
            T rightLeft = right.getLeft();
            return Pack.list(Stream.concat(
                    left.elements.stream().filter(x -> !x.equals(rightLeft)),
                    right.elements.stream()).collect(Collectors.toList()));
        }
    }

    private static boolean inBlock(NodeMap<Block> nodeToBlockMap, Block block, Node node) {
        return nodeToBlockMap.get(node) == block;
    }

    /**
     * Check whether the left and right node of a potential pack are isomorphic.
     * "Isomorphic statements are those that contain the same operations in the same order."
     * @param left Left node of the potential pack
     * @param right Right node of the potential pack
     * @return Boolean indicating whether the left and right node of a potential pack are isomorphic.
     */
    private static boolean isomorphic(Node left, Node right) {
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

    private static int findDepth(Block block, Node node) {
        int depth = 0;
        for (Node current : block.getNodes()) {
            if (current.equals(node)) {
                return depth;
            }
            depth++;
        }

        return -1;
    }

    private static boolean hasNoPath(NodeMap<Block> nodeToBlockMap, Block block, Node shallow, Node deep) {
        return hasNoPath(nodeToBlockMap, block, shallow, deep, 0);
    }

    private static boolean hasNoPath(NodeMap<Block> nodeToBlockMap, Block block, Node shallow, Node deep, int iterationDepth) {
        if (iterationDepth >= 1000) return false; // Stop infinite/deep recursion

        // TODO: pre-compute depth information
        // TODO: calcluate depth information to avoid recursing too far, doing unnecessary calculations
        // TODO: verify that at this stage it's even possible to get dependency cycles

        for (Node pred : deep.inputs()) {
            if (!inBlock(nodeToBlockMap, block, pred)) // ensure that the predecessor is in the block
                continue;

            if (shallow == pred)
                return false;

            if (/* pred is below shallow && */ !hasNoPath(nodeToBlockMap, block, shallow, pred, iterationDepth + 1))
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
    private static boolean independent(NodeMap<Block> nodeToBlockMap, Block block, Node left, Node right) {
        // Calculate depth from how far into block.getNodes() we are. TODO: this is a hack.
        final int leftDepth = findDepth(block, left);
        final int rightDepth = findDepth(block, right);

        if (leftDepth == rightDepth) return !left.equals(right);

        int shallowDepth = Math.min(leftDepth, rightDepth);
        Node deep = leftDepth == shallowDepth ? right : left;
        Node shallow = leftDepth == shallowDepth ? left : right;

        return hasNoPath(nodeToBlockMap, block, shallow, deep);
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

    private static boolean stmts_can_pack(NodeMap<Block> nodeToBlockMap, Block block, Set<Pack<Node>> packSet, Node left, Node right) {
        return isomorphic(left, right) &&
                independent(nodeToBlockMap, block, left, right) &&
                packSet.stream().noneMatch(p -> p.match(left, right));
    }

    /**
     * Create the initial seed packSet of operations that are adjacent
     * TODO: PERFORM ADJACENCY TEST
     * TODO: CHECK THAT VECTOR ELEMENT TYPE IS THE SAME
     * @param block Basic block
     * @param packSet PackSet to populate
     */
    private static void find_adj_refs(NodeMap<Block> nodeToBlockMap, Block block, Set<Pack<Node>> packSet) {
        // Create initial seed set containing memory operations
        List<FixedAccessNode> memoryNodes = // Candidate list of memory nodes
                StreamSupport.stream(block.getNodes().spliterator(), false)
                        .filter(NodePredicates.isA(FixedAccessNode.class)::apply)
                        .map(x -> (FixedAccessNode) x)
                        .collect(Collectors.toList());

        // TODO: do better than this, specifically because of stmts_can_back being inefficient
        for (FixedAccessNode s1 : memoryNodes) {
            for (FixedAccessNode s2 : memoryNodes) {
                if (s1 == s2) continue;

                if (adjacent(s1, s2) && stmts_can_pack(nodeToBlockMap, block, packSet, s1, s2)) {
                    packSet.add(Pack.pair(s1, s2));
                }
            }
        }

    }

    /**
     * Extend the packset by visiting operand definitions of nodes inside the provided pack
     * @param block Basic block currently being optimized
     * @param packSet Pack set
     * @param pack The pack to use for operand definitions
     */
    private static boolean follow_use_defs(NodeMap<Block> nodeToBlockMap, Block block, Set<Pack<Node>> packSet, Pack<Node> pack) {
        assert pack.isPair(); // the pack should be a pair

        final Node left = pack.getLeft();
        final Node right = pack.getRight();

        // TODO: bail if left is load (why?)

        boolean changed = false;

        for (Iterator<Node> leftInputIt = left.inputs().iterator(); leftInputIt.hasNext();) {
            for (Iterator<Node> rightInputIt = right.inputs().iterator(); rightInputIt.hasNext();) {
                // Incrementing both iterators at the same time, so looking at the same input always
                final Node leftInput = leftInputIt.next();
                final Node rightInput = rightInputIt.next();

                // Check block membership, bail if nodes not in block (prevent analysis beyond block)
                if (!inBlock(nodeToBlockMap, block, leftInput) || !inBlock(nodeToBlockMap, block, rightInput)) continue;

                if (!stmts_can_pack(nodeToBlockMap, block, packSet, leftInput, rightInput)) continue;

                changed |= packSet.add(Pack.pair(leftInput, rightInput));
            }
        }

        return changed;
    }

    /**
     * Extend the packset by visiting uses of nodes in the provided pack
     * @param block Basic block currently being optimized
     * @param packSet Pack set
     * @param pack The pack to use for nodes to find usages of
     */
    private static boolean follow_def_uses(NodeMap<Block> nodeToBlockMap, Block block, Set<Pack<Node>> packSet, Pack<Node> pack) {
        final Node left = pack.getLeft();
        final Node right = pack.getRight();

        // TODO: bail if left is store (why?)

        for (Node leftUsage : left.usages()) {
            if (!inBlock(nodeToBlockMap, block, left)) continue;

            for (Node rightUsage : right.usages()) {
                if (leftUsage == rightUsage || !inBlock(nodeToBlockMap, block, right)) continue;

                // TODO: Rather than adding the first, add the best
                if (stmts_can_pack(nodeToBlockMap, block, packSet, leftUsage, rightUsage)) {
                    return packSet.add(Pack.pair(leftUsage, rightUsage));
                }
            }
        }

        return false;
    }

    /**
     * Extend the packset by following use->def and def->use links from pack members until the set does not change
     * @param block Basic block
     * @param packSet PackSet to populate
     */
    private static void extend_packlist(NodeMap<Block> nodeToBlockMap, Block block, Set<Pack<Node>> packSet) {
        Set<Pack<Node>> iterationPackSet;

        boolean changed;
        do {
            changed = false;
            iterationPackSet = new HashSet<>(packSet);

            for (Pack<Node> pack : iterationPackSet) {
                changed |= follow_use_defs(nodeToBlockMap, block, packSet, pack);
                changed |= follow_def_uses(nodeToBlockMap, block, packSet, pack);
            }
        } while (changed);
    }

    // Combine packs where right = left
    private static void combine_packs(Set<Pack<Node>> packSet) {
        boolean changed;
        do {
            changed = false;

            Deque<Pack<Node>> remove = new ArrayDeque<>();
            Deque<Pack<Node>> add = new ArrayDeque<>();

            for (Pack<Node> leftPack : packSet) {
                if (remove.contains(leftPack)) continue;

                for (Pack<Node> rightPack : packSet) {
                    if (remove.contains(leftPack) || remove.contains(rightPack)) continue;

                    if (leftPack != rightPack && leftPack.getRight().equals(rightPack.getLeft())) {
                        remove.push(leftPack);
                        remove.push(rightPack);

                        add.push(Pack.combine(leftPack, rightPack));

                        changed = true;
                    }
                }
            }

            packSet.removeAll(remove);
            packSet.addAll(add);
        } while (changed);
    }

    private static void SLP_extract(NodeMap<Block> nodeToBlockMap, Block block) {
        Set<Pack<Node>> packSet = new HashSet<>();
        find_adj_refs(nodeToBlockMap, block, packSet);
        extend_packlist(nodeToBlockMap, block, packSet);
        combine_packs(packSet);
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
            SLP_extract(graph.getLastSchedule().getNodeToBlockMap(), block);
        }
    }
}
