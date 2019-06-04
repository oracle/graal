package org.graalvm.compiler.phases.common;

import jdk.vm.ci.meta.JavaKind;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.*;
import org.graalvm.compiler.nodes.VectorSupport.*;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.VectorAddNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.memory.*;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.LowTierContext;
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
public final class IsomorphicPackingPhase extends BasePhase<LowTierContext> {

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

        public List<T> getElements() {
            return elements;
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

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            if (isPair()) {
                sb.append('P');
            }

            sb.append('<');
            sb.append(elements.toString());
            sb.append('>');
            return sb.toString();
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

    // Alignments, enum with associated values
    static class Alignment {
        private enum Type {
            TOP,    // these are alignments that have not yet been determined
            BOTTOM, // these are invalid/nonexistent alignments
            VALUE   // these are valid alignments, with an associated value
        }

        private final Type type;
        private final int value;

        private Alignment(Type type, int value) {
            this.type = type;
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isValue() {
            return type == Type.VALUE;
        }

        public boolean isBottom() {
            return type == Type.BOTTOM;
        }

        public boolean isTop() {
            return type == Type.TOP;
        }

        public Alignment addValue(int delta) {
            if (isValue()) {
                return Alignment.value(value + delta);
            }

            return bottom();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Alignment alignment = (Alignment) o;
            return value == alignment.value &&
                    type == alignment.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, value);
        }

        private final static Alignment top = new Alignment(Type.TOP, -1);
        private final static Alignment bottom = new Alignment(Type.BOTTOM, -666);
        private final static HashMap<Integer, Alignment> alignments = new HashMap<>();

        static Alignment top() {
            return top;
        }

        static Alignment bottom() {
            return bottom;
        }

        static Alignment value(int value) {
            if (value == -1) return top;
            if (value < 0) return bottom;

            return alignments.computeIfAbsent(value, v -> new Alignment(Type.VALUE, v));
        }
    }

    // Class to encapsulate state used by functions in the algorithm
    private static class Instance {
        private final NodeMap<Block> nodeToBlockMap;
        private final BlockMap<List<Node>> blockToNodesMap;
        private final Block currentBlock;
        private final Map<Node, Alignment> alignmentMap = new HashMap<>();

        private Instance(NodeMap<Block> nodeToBlockMap, BlockMap<List<Node>> blockToNodesMap, Block currentBlock) {
            this.nodeToBlockMap = nodeToBlockMap;
            this.blockToNodesMap = blockToNodesMap;
            this.currentBlock = currentBlock;
        }

        // Utilities

        /**
         * Check whether the node is not in the current basic block
         * @param node Node to check the block membership of
         * @return True if the provided node is not in the current basic block
         */
        private boolean notInBlock(Node node) {
            return nodeToBlockMap.get(node) != currentBlock;
        }

        /**
         * Get the width of a vector, measured in the number of underlying elements.
         * Returns either the loop stride or max vector size (for particular node type).
         * Loop stride depends on unroll factor
         * TODO: don't hardcode vector width
         */
        private int vectorWidth(Node node) {
            return 4;
        }

        private int data_size(Node node) {
            if (node instanceof LIRLowerableAccess)
                return data_size((LIRLowerableAccess) node);

            if (node instanceof ValueNode)
                return data_size((ValueNode) node);

            return -1;
        }

        private int data_size(ValueNode node) {
            return node.stamp(NodeView.DEFAULT).getStackKind().getStackKind().getByteCount();
        }

        private int data_size(LIRLowerableAccess node) {
            return node.getAccessStamp().getStackKind().getByteCount();
        }

        /**
         * Get the alignment of a vector memory reference
         */
        private <T extends FixedAccessNode & LIRLowerableAccess> Alignment memoryAlignment(T access, int iv_adjust) {
            final int byteCount = access.getAccessStamp().getStackKind().getByteCount();
            // TODO: velt may be different to type at address
            final int vectorElementWidthInBytes = byteCount * vectorWidth(access);

            // If each vector element is less than 2 bytes, no need to vectorize
            if (vectorElementWidthInBytes < 2) {
                return Alignment.bottom();
            }

            final int offset = (int) access.getAddress().getMaxConstantDisplacement() + iv_adjust * byteCount;
            final int offset_remainder = offset % vectorElementWidthInBytes;

            return Alignment.value(offset_remainder >= 0 ? offset_remainder : offset_remainder + vectorElementWidthInBytes);
        }

        private void setAlignment(Node s1, Node s2, Alignment align) {
            setAlignment(s1, align);
            if (align.isTop() || align.isBottom()) {
                setAlignment(s2, align);
            } else {
                setAlignment(s2, align.addValue(data_size(s1)));
            }
        }

        private void setAlignment(Node node, Alignment alignment) {
            alignmentMap.put(node, alignment);
        }

        private Alignment getAlignment(Node node) {
            return alignmentMap.getOrDefault(node, Alignment.top());
        }

        /**
         * Check whether the left and right node of a potential pack are isomorphic.
         * "Isomorphic statements are those that contain the same operations in the same order."
         * @param left Left node of the potential pack
         * @param right Right node of the potential pack
         * @return Boolean indicating whether the left and right node of a potential pack are isomorphic.
         */
        private boolean isomorphic(Node left, Node right) {
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

        private int findDepth(Node node) {
            int depth = 0;
            for (Node current : currentBlock.getNodes()) {
                if (current.equals(node)) {
                    return depth;
                }
                depth++;
            }

            return -1;
        }

        private boolean hasNoPath(Node shallow, Node deep) {
            return hasNoPath(shallow, deep, 0);
        }

        private boolean hasNoPath(Node shallow, Node deep, int iterationDepth) {
            if (iterationDepth >= 1000) return false; // Stop infinite/deep recursion

            // TODO: pre-compute depth information
            // TODO: calcluate depth information to avoid recursing too far, doing unnecessary calculations
            // TODO: verify that at this stage it's even possible to get dependency cycles

            for (Node pred : deep.inputs()) {
                if (notInBlock(pred)) // ensure that the predecessor is in the block
                    continue;

                if (shallow == pred)
                    return false;

                if (/* pred is below shallow && */ !hasNoPath(shallow, pred, iterationDepth + 1))
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
        private boolean independent(Node left, Node right) {
            // Calculate depth from how far into block.getNodes() we are. TODO: this is a hack.
            final int leftDepth = findDepth(left);
            final int rightDepth = findDepth(right);

            if (leftDepth == rightDepth) return !left.equals(right);

            int shallowDepth = Math.min(leftDepth, rightDepth);
            Node deep = leftDepth == shallowDepth ? right : left;
            Node shallow = leftDepth == shallowDepth ? left : right;

            return hasNoPath(shallow, deep);
        }

        /**
         * Ensure that there is no data path between left and right.
         * This version operates on Nodes, avoiding the need to check for FAN at the callsite.
         * Pre: left and right are isomorphic
         * @param left Left node of the potential pack
         * @param right Right node of the potential pack
         * @return Are the two statements independent? Only independent statements may be packed.
         */
        private boolean adjacent(Node left, Node right) {
            return left instanceof FixedAccessNode &&
                   right instanceof FixedAccessNode &&
                   adjacent((FixedAccessNode) left, (FixedAccessNode) right);
        }

        /**
         * Check whether s1 is immediately before s2 in memory, if both are primitive.
         * @param s1 First FixedAccessNode to check
         * @param s2 Second FixedAccessNode to check
         * @return Boolean indicating whether s1 is immediately before s2 in memory
         */
        private boolean adjacent(FixedAccessNode s1, FixedAccessNode s2) {
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
        private boolean adjacent(FixedAccessNode s1, FixedAccessNode s2, JavaKind s1k, JavaKind s2k) {
            final AddressNode s1a = s1.getAddress(), s2a = s2.getAddress();

            // Only use superword on primitives
            if (!s1k.isPrimitive() || !s2k.isPrimitive()) return false;

            // Only use superword on nodes in the current block
            if (notInBlock(s1) || notInBlock(s2)) return false;

            // Only use superword on types that are comparable
            // TODO: Evaluate whether graph guarantees that pointers for same collection have same base
            if (!s1a.getBase().equals(s2a.getBase())) return false;

            return s2a.getMaxConstantDisplacement() - s1a.getMaxConstantDisplacement() == s1k.getByteCount();
        }

        private boolean stmts_can_pack(Set<Pack<Node>> packSet, Node s1, Node s2, Alignment align) {
            // Also make sure that the platform supports vectors of the primitive type of this candidate pack
            if (isomorphic(s1, s2) && independent(s1, s2) && packSet.stream().noneMatch(p -> p.match(s1, s2))) {
                final Alignment align_s1 = getAlignment(s1);
                final Alignment align_s2 = getAlignment(s2);

                final int s2_data_size = data_size(s2);
                boolean offset = false;
                if (s2_data_size >= 0) {
                    offset = align_s2.equals(align.addValue(s2_data_size));
                }

                return (align_s1.isTop() || align_s1.equals(align)) && (align_s2.isTop() || offset);
            }

            return false;
        }

        /**
         * Extend the packset by visiting operand definitions of nodes inside the provided pack
         * @param packSet Pack set
         * @param pack The pack to use for operand definitions
         */
        private boolean follow_use_defs(Set<Pack<Node>> packSet, Pack<Node> pack) {
            assert pack.isPair(); // the pack should be a pair

            final Node left = pack.getLeft();
            final Node right = pack.getRight();
            final Alignment align = getAlignment(left);

            // TODO: bail if left is load (why?)

            boolean changed = false;

            outer: // labelled outer loop so that hasNext check is performed for left
            for (Iterator<Node> leftInputIt = left.inputs().iterator(); leftInputIt.hasNext();) {
                for (Iterator<Node> rightInputIt = right.inputs().iterator(); rightInputIt.hasNext();) {
                    // Incrementing both iterators at the same time, so looking at the same input always
                    final Node leftInput = leftInputIt.next();
                    final Node rightInput = rightInputIt.next();

                    // Check block membership, bail if nodes not in block (prevent analysis beyond block)
                    if (notInBlock(leftInput) || notInBlock(rightInput)) continue outer;

                    // If the statements cannot be packed, bail
                    if (!stmts_can_pack(packSet, leftInput, rightInput, align)) continue outer;

                    // If there are no savings to be gained, bail
                    // NB: here this is basically useless, as <s>our</s> Oracle's formula does not allow for negative savings
                    if (est_savings(packSet, leftInput, rightInput) < 0) continue outer;

                    changed |= packSet.add(Pack.pair(leftInput, rightInput));
                    setAlignment(left, right, align);
                }
            }

            return changed;
        }

        /**
         * Extend the packset by visiting uses of nodes in the provided pack
         * @param packSet Pack set
         * @param pack The pack to use for nodes to find usages of
         */
        private boolean follow_def_uses(Set<Pack<Node>> packSet, Pack<Node> pack) {
            final Node left = pack.getLeft();
            final Node right = pack.getRight();
            final Alignment align = getAlignment(left);

            int savings = -1;
            Pack<Node> bestPack = null;

            // TODO: bail if left is store (why?)

            for (Node leftUsage : left.usages()) {
                if (notInBlock(left)) continue;

                for (Node rightUsage : right.usages()) {
                    if (leftUsage == rightUsage || notInBlock(right)) continue;

                    // TODO: Rather than adding the first, add the best
                    if (stmts_can_pack(packSet, leftUsage, rightUsage, align)) {
                        final int currentSavings = est_savings(packSet, leftUsage, rightUsage);
                        if (currentSavings > savings) {
                            savings = currentSavings;
                            bestPack = Pack.pair(leftUsage, rightUsage);
                        }
                    }
                }
            }

            if (savings >= 0) {
                setAlignment(bestPack.getLeft(), bestPack.getRight(), align);
                return packSet.add(bestPack);
            }

            return false;
        }

        /**
         * Estimate the savings of executing the pack rather than two separate instructions.
         * @param s1 Candidate left element of Pack
         * @param s2 Candidate right element of Pack
         * @param packSet PackSet, for membership checks
         * @return Savings in an arbitrary unit and can be negative.
         */
        private int est_savings(Set<Pack<Node>> packSet, Node s1, Node s2) {
            // Savings originating from inputs
            int saveIn = 1; // Save 1 instruction as executing 2 in parallel.

            outer: // labelled outer loop so that hasNext check is performed for left
            for (Iterator<Node> leftInputIt = s1.inputs().iterator(); leftInputIt.hasNext();) {
                for (Iterator<Node> rightInputIt = s2.inputs().iterator(); rightInputIt.hasNext();) {
                    final Node leftInput = leftInputIt.next();
                    final Node rightInput = rightInputIt.next();

                    if (leftInput == rightInput) continue outer;

                    if (adjacent(leftInput, rightInput)) {
                        // Inputs are adjacent in memory, this is good.
                        saveIn += 2; // Not necessarily packed, but good because packing is easy.
                    } else if (packSet.contains(Pack.pair(leftInput, rightInput))) {
                        saveIn += 2; // Inputs already packed, so we don't need to pack these.
                    } else {
                        saveIn -= 2; // Not adjacent, not packed. Inputs need to be packed in a vector for candidate.
                    }
                }
            }

            // Savings originating from result
            int ct = 0; // the number of usages that are packed
            int saveUse = 0;
            for (Node s1Usage : s1.usages()) {
                for (Pack<Node> pack : packSet) {
                    if (pack.getLeft()!=s1Usage) continue;

                    for (Node s2Usage : s2.usages()) {
                        if (pack.getRight()!=s2Usage) continue;

                        ct++;

                        if (adjacent(s1Usage, s2Usage)) {
                            saveUse += 2;
                        }
                    }
                }
            }

            // idk, c2 does this though
            if (ct < s1.getUsageCount()) saveUse += 1;
            if (ct < s2.getUsageCount()) saveUse += 1;

            // TODO: investigate this formula - can't have negative savings
            return Math.max(saveIn, saveUse);
        }

        // Core

        /**
         * Create the initial seed packSet of operations that are adjacent
         * TODO: CHECK THAT VECTOR ELEMENT TYPE IS THE SAME
         * @param packSet PackSet to populate
         */
        private void find_adj_refs(Set<Pack<Node>> packSet) {
            // Create initial seed set containing memory operations
            List<FixedAccessNode> memoryNodes = // Candidate list of memory nodes
                    StreamSupport.stream(currentBlock.getNodes().spliterator(), false)
                            .filter(x -> x instanceof FixedAccessNode && x instanceof LIRLowerableAccess)
                            .map(x -> (FixedAccessNode & LIRLowerableAccess) x)
                            .filter(x -> x.getAccessStamp().getStackKind().isPrimitive() && !memoryAlignment(x, 0).isBottom())
                            .collect(Collectors.toList());

            // TODO: Align relative to best reference rather than setting alignment for all
            for (FixedAccessNode node : memoryNodes) {
                setAlignment(node, memoryAlignment((FixedAccessNode & LIRLowerableAccess) node, 0));
            }

            for (FixedAccessNode s1 : memoryNodes) {
                for (FixedAccessNode s2 : memoryNodes) {
                    if (adjacent(s1, s2) && stmts_can_pack(packSet, s1, s2, getAlignment(s1))) {
                        packSet.add(Pack.pair(s1, s2));
                    }
                }
            }
        }

        /**
         * Extend the packset by following use->def and def->use links from pack members until the set does not change
         * @param packSet PackSet to populate
         */
        private void extend_packlist(Set<Pack<Node>> packSet) {
            Set<Pack<Node>> iterationPackSet;

            boolean changed;
            do {
                changed = false;
                iterationPackSet = new HashSet<>(packSet);

                for (Pack<Node> pack : iterationPackSet) {
                    changed |= follow_use_defs(packSet, pack);
                    changed |= follow_def_uses(packSet, pack);
                }
            } while (changed);
        }

        // Combine packs where right = left
        private void combine_packs(Set<Pack<Node>> packSet) {
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

        /**
         * Have all of the dependencies of node been scheduled?
         * Filters inputs, removing blocks that are not in the current block as well as Begin/End/Return.
         */
        private boolean deps_scheduled(Node node, List<Node> scheduled, boolean considerControlFlow) {
            return StreamSupport.stream(node.inputs().spliterator(), false)
                    .filter(n -> nodeToBlockMap.get(n) == currentBlock)
                    .allMatch(scheduled::contains) &&
                    // AND have all the control flow dependencies been scheduled? (only if considering CF)
                    (!considerControlFlow || StreamSupport.stream(node.cfgPredecessors().spliterator(), false)
                            .filter(n -> nodeToBlockMap.get(n) == currentBlock)
                            .allMatch(scheduled::contains)
                    );
        }

        private Pack<Node> earliest_unscheduled(List<Node> unscheduled, Map<Node, Pack<Node>> nodeToPackMap) {
            for (Node node : unscheduled) {
                if (nodeToPackMap.containsKey(node)) {
                    return nodeToPackMap.get(node);
                }
            }

            return null;
        }

        private void schedule(List<Node> unscheduled, Set<Pack<Node>> packSet) {
            final List<Node> scheduled = new ArrayList<>();
            final Deque<FixedNode> lastFixed = new ArrayDeque<>();

            // Populate a nodeToPackMap
            final Map<Node, Pack<Node>> nodeToPackMap = packSet.stream()
                    .flatMap(pack -> pack.getElements().stream().map(node -> Pair.create(node, pack)))
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

            final List<Runnable> deferred = new ArrayList<>();

            // While unscheduled isn't empty
            outer:
            while (!unscheduled.isEmpty()) {
                for (Node node : unscheduled) {
                    // Node is in some pack p
                    if (nodeToPackMap.containsKey(node)) {
                        final Pack<Node> pack = nodeToPackMap.get(node);
                        // Have the dependencies of statements in the pack been scheduled?
                        if (pack.getElements().stream().allMatch(n -> deps_scheduled(n, scheduled, n == pack.getLeft()))) {
                            // Remove statements from unscheduled
                            scheduled.addAll(pack.getElements());
                            unscheduled.removeAll(pack.getElements());
                            deferred.add(() -> schedulePack(pack, lastFixed));
                            continue outer;
                        }
                    } else if (deps_scheduled(node, scheduled, true)) {
                        // Remove statement from unscheduled and schedule
                        scheduled.add(node);
                        unscheduled.remove(node);
                        deferred.add(() -> scheduleStmt(node, lastFixed));
                        continue outer;
                    }
                }

                // We only reach here if there is a grouped statement dependency violation.
                // These are broken by removing one of the packs.
                Pack<Node> packToRemove = earliest_unscheduled(unscheduled, nodeToPackMap);
                assert packToRemove != null : "there no are unscheduled statements in packs";
                for (Node packNode : packToRemove.getElements()) {
                    nodeToPackMap.remove(packNode); // Remove all references for these statements to pack
                }
            }

            for (Runnable runnable : deferred) {
                runnable.run();
            }
        }

        private void schedulePack(Pack<Node> pack, Deque<FixedNode> lastFixed) {
            final Node first = pack.getLeft();

            if (first instanceof ReadNode) {
                final List<ReadNode> nodes = pack.getElements().stream().map(x -> (ReadNode) x).collect(Collectors.toList());
                final VectorReadNode vectorRead = VectorReadNode.fromPackElements(nodes);

                final VectorUnpackNode vts = new VectorUnpackNode(vectorRead.stamp(NodeView.DEFAULT).unrestricted(), vectorRead);
                for (ReadNode node : nodes) {
                    node.replaceAtUsagesAndDelete(vts);
                }

                first.graph().add(vectorRead);
                first.graph().addOrUnique(vts);

                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext(vectorRead);
                }

                lastFixed.add(vectorRead);
            }

            if (first instanceof WriteNode) {
                final List<WriteNode> nodes = pack.getElements().stream().map(x -> (WriteNode) x).collect(Collectors.toList());

                final VectorPackNode stv = new VectorPackNode(nodes.get(0).getAccessStamp().unrestricted(), nodes.stream().map(AbstractWriteNode::value).collect(Collectors.toList()));
                final VectorWriteNode vectorWrite = VectorWriteNode.fromPackElements(nodes, stv);

                for (WriteNode node : nodes) {
                    node.safeDelete();
                }

                first.graph().addOrUnique(stv);
                first.graph().add(vectorWrite);

                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext(vectorWrite);
                }

                lastFixed.add(vectorWrite);
            }

            if (first instanceof AddNode) {
                final List<AddNode> nodes = pack.getElements().stream().map(x -> (AddNode) x).collect(Collectors.toList());

                // Input to vector, output to scalar
                final VectorPackNode stvX = new VectorPackNode(nodes.get(0).getX().stamp(NodeView.DEFAULT).unrestricted(), nodes.stream().map(BinaryNode::getX).collect(Collectors.toList()));
                final VectorPackNode stvY = new VectorPackNode(nodes.get(0).getY().stamp(NodeView.DEFAULT).unrestricted(), nodes.stream().map(BinaryNode::getY).collect(Collectors.toList()));
                final VectorAddNode vector = new VectorAddNode(stvX, stvY);
                final VectorUnpackNode vts = new VectorUnpackNode(nodes.get(0).stamp(NodeView.DEFAULT), vector);
                for (AddNode node : nodes) {
                    node.replaceAtUsagesAndDelete(vts);
                }

                first.graph().addOrUnique(stvX);
                first.graph().addOrUnique(stvY);
                first.graph().add(vector);
                first.graph().addOrUnique(vts);
            }
        }

        private void scheduleStmt(Node node, Deque<FixedNode> lastFixed) {
            if (node instanceof FixedNode) {
                if (!lastFixed.isEmpty() && lastFixed.element() instanceof FixedWithNextNode) {
                    ((FixedWithNextNode) lastFixed.poll()).setNext((FixedNode) node);
                }

                lastFixed.add((FixedNode) node);
            }
        }

        // Main
        void SLP_extract() {
            Set<Pack<Node>> packSet = new HashSet<>();
            find_adj_refs(packSet);
            extend_packlist(packSet);
            combine_packs(packSet);
            schedule(new ArrayList<>(blockToNodesMap.get(currentBlock)), packSet);
        }

    }

    private final SchedulePhase schedulePhase;

    public IsomorphicPackingPhase(SchedulePhase schedulePhase) {
        this.schedulePhase = schedulePhase;
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        // Schedule phase is required so that lowered addresses are in the nodeToBlockMap
        schedulePhase.apply(graph);
        ControlFlowGraph cfg = graph.getLastSchedule().getCFG();
        for (Block block : cfg.reversePostOrder()) {
            new Instance(graph.getLastSchedule().getNodeToBlockMap(), graph.getLastSchedule().getBlockToNodesMap(), block).SLP_extract();
        }
    }
}
