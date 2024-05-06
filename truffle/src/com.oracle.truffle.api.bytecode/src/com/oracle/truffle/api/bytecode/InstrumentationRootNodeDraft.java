package com.oracle.truffle.api.bytecode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/*
 * Note: bytecode index in frame can be problematic if the bytecodes change. So we never switch the bytecodes field
 * without updating the frame.
 */
public class InstrumentationRootNodeDraft extends RootNode implements BytecodeRootNode {

    protected InstrumentationRootNodeDraft(TruffleLanguage<?> language) {
        super(language);
    }

    @CompilationFinal private volatile AbstractBytecodes bytecodes;
    @Child private InstrumentationImpl instrumentation;

    @CompilationFinal(dimensions = 1) private volatile int[] sourceInfo;

    // constants
    // numLocals
    // numNodes
    // buildIndex
    // sourcedInfo

    static abstract class AbstractBytecodes extends Node {

        @CompilationFinal(dimensions = 1) final short[] bytecodes;
        @CompilationFinal(dimensions = 1) final int[] handlers;
        final Assumption valid;

        AbstractBytecodes(short[] bc, final int[] handlers, Assumption valid) {
            this.bytecodes = bc;
            this.handlers = handlers;
            this.valid = valid;
        }

        abstract int continueAt(InstrumentationRootNodeDraft root, VirtualFrame frame, VirtualFrame localFrame, int startState);

        InstrumentationRootNodeDraft getRoot() {
            return (InstrumentationRootNodeDraft) getParent();
        }

        final void invalidate() {
            // handle byteocodes invalidation here?
            valid.invalidate();
        }

        final boolean isValid() {
            if (valid != null) {
                return valid.isValid();
            }
            return true;
        }

    }

    @Override
    public Object execute(VirtualFrame frame) {
        return continueAt(frame, frame, 0);
    }

    private Object continueAt(VirtualFrame frame, VirtualFrame localFrame, int startState) {
        int state = startState;
        AbstractBytecodes bc = this.bytecodes;
        while (true) {
            state = bc.continueAt(this, frame, localFrame, state);
            if ((state & 0xffff) == 0xffff) {
                break;
            }
            // bytecodes changed
            CompilerDirectives.transferToInterpreterAndInvalidate();
            AbstractBytecodes old = bc;
            bc = this.bytecodes;
            state = translateBytecodeState(frame, localFrame, old, state, bc);
        }
        return frame.getObject((state >> 16) & 0xffff);
    }

    private int translateBytecodeState(VirtualFrame frame, VirtualFrame localFrame,
                    AbstractBytecodes oldBytecodes, int oldState,
                    AbstractBytecodes newBytecodes) {
        if (oldBytecodes == newBytecodes) {
            // fast-path no transition needed
            return oldState;
        }
        int targetOldBci = oldState & 0xFFFF;
        int stableBci = translateBytecodeIndex(oldBytecodes.bytecodes, targetOldBci);
        int[] oldOnStack = computeOnStackInstrumentation(oldBytecodes, stableBci);
        int[] newOnStack = computeOnStackInstrumentation(newBytecodes, stableBci);
        transitionInstrumentationOnStack(frame, localFrame, oldBytecodes, oldOnStack, newBytecodes, newOnStack);
        int newBci = translateFromStableBCI(newBytecodes, stableBci);
        return (oldState & 0x0000) | (newBci & 0xFFFF);
    }

    private static final short[] EMPTY_ARRAY = new short[0];

    private int[] computeOnStackInstrumentation(AbstractBytecodes bytecodes, int targetStableBci) {
        short[] bc = bytecodes.bytecodes;
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int oldBci = 0;
        int newBci = 0;
        int stableBci = 0;
        while (stableBci != targetStableBci) {
            short operand = bc[oldBci];
            int width = getInstructionWidth(operand);
            if (isInstrumentationBeforeInstruction(operand)) {
                stack.push(stableBci);
                oldBci += width;
            } else if (isInstrumentationAfterInstruction(operand)) {
                int b = stack.pop();
                // validate that its aligned.
                oldBci += width;
            } else {
                stableBci += width;
                oldBci += width;
            }
        }

        if (stableBci != targetStableBci) {
            throw new IllegalArgumentException("Invalid BCI");
        }
        return stack.stream().mapToInt((v) -> v).toArray();
    }

    private static void transitionInstrumentationOnStack(
                    VirtualFrame frame, VirtualFrame localFrame,
                    AbstractBytecodes oldBytecodes, int[] oldOnStackEvents,
                    AbstractBytecodes newBytecodes, int[] newOnStackEvents) {
        if (oldOnStackEvents.length == 0 && newOnStackEvents.length == 0) {
            // nothing to do
            return;
        }

        short[] oldBc = oldBytecodes.bytecodes;
        short[] newBc = newBytecodes.bytecodes;

        if (oldOnStackEvents.length != 0 && newOnStackEvents.length != 0) {
            outer: for (int i = 0; i < oldOnStackEvents.length; i++) {
                int oldOnStack = oldOnStackEvents[i];
                assert oldOnStack != -1;

                for (int j = 0; j < newOnStackEvents.length; j++) {
                    int newOnStack = newOnStackEvents[j];
                    if (newOnStack == -1) {
                        continue;
                    }
                    if (isSameInstrumentationInstruction(oldBc, oldOnStack, newBc, newOnStack)) {
                        // nothing to do both instructions are in same stacks
                        oldOnStackEvents[i] = -1;
                        newOnStackEvents[j] = -1;
                        continue outer;
                    }
                }
            }
        }

        for (int i = 0; i < oldOnStackEvents.length; i++) {
            int oldOnStack = oldOnStackEvents[i];
            if (oldOnStack == -1) {
                continue;
            }

            // leave probe
        }

        for (int i = 0; i < newOnStackEvents.length; i++) {
            int newOnStack = newOnStackEvents[i];
            if (newOnStack == -1) {
                continue;
            }

            // enter new
        }

    }

    static final TreeNode[] EMPTY = new TreeNode[0];

    static void createInstrumentationNodes(short[] bc, int bci, int[] tagsTable, int materializedTags) {
        int sp = 0;

        // compute explicit tags

    }

    static TreeNode createMaterializedTree(TreeNode oldTree, int oldTags, int[] tagsTable, int newTags) {
        int searchTags = newTags & oldTags;
        if (searchTags == 0) {
            // no new tags
            return oldTree;
        }
        boolean found = false;
        // peek the table to find the first occurence before creating any objects
        // TODO we need to double check this peeking is worth it or wether we basically
        // always find new tags in the tags table.
        for (int i = 0; i < tagsTable.length; i = i + 3) {
            if ((tagsTable[i + 1] & searchTags) != 0) {
                found = true;
                break;
            }
        }
        if (!found) {
            // no new tags contained in tree
            return oldTree;
        }

        Deque<TreeNode> nodes = new ArrayDeque<>();
        for (int i = 0; i < tagsTable.length; i = i + 3) {
            final int numChildren = tagsTable[i + 2];
            TreeNode[] children;
            if (numChildren == 0) {
                children = EMPTY;
            } else {
                children = resolveMaterializedChildren(newTags, nodes, numChildren);
            }
            TreeNode current = new TreeNode();
            current.tags = tagsTable[i + 1];
            current.beginBci = (short) (tagsTable[i + 0] >>> 16 & 0xFFFF);
            current.endBci = (short) (tagsTable[i + 0] & 0xFFFF);
            current.children = children;
            nodes.push(current);
        }

        if (nodes.isEmpty()) {
            return null;
        }

        TreeNode[] rootChildren = resolveMaterializedChildren(oldTags, nodes, nodes.size());
        if (rootChildren.length == 0) {
            return null;
        } else if (rootChildren.length == 1) {
            return rootChildren[0];
        } else {
            // create synthetic root in case of filtered children
            // for example if no RootTag, but StatementTag is materialized we might get a list of
            // statements here.
            TreeNode root = new TreeNode();
            root.tags = 0; // no tags for the synthetic root tag
            root.children = rootChildren;
            root.beginBci = 0;
            root.endBci = 0; // not sure about this one
            return root;
        }
    }

    static TreeNode[] resolveMaterializedChildren(int materializedTags, Deque<TreeNode> nodes, final int numChildren) {
        TreeNode[] children;
        List<TreeNode> resolvedChildren = null;
        for (int j = 0; j < numChildren; j++) {
            TreeNode tree = nodes.pop();
            if (tree.containsTags(materializedTags)) {
                if (resolvedChildren == null) {
                    resolvedChildren = new ArrayList<>();
                }
                resolvedChildren.add(tree);
            } else {
                if (tree.children.length > 0) {
                    if (resolvedChildren == null) {
                        resolvedChildren = new ArrayList<>();
                    }
                    resolvedChildren.addAll(List.of(tree.children));
                }
            }
        }
        if (resolvedChildren == null) {
            children = EMPTY;
        } else {
            assert !resolvedChildren.isEmpty();
            children = resolvedChildren.toArray(TreeNode[]::new);
        }
        return children;
    }

    public enum InstrumentationKind {
        NONE,
        BEFORE,
        AFTER,
    }

    @Operation
    static final class SetTraceFun {
        @Specialization
        public static void doDefault() {
            // see example
        }
    }

    private void invalidateBytecodes(AbstractBytecodes bytecodes, int state) {

    }

    private int translateFromStableBCI(AbstractBytecodes bytecodes, int targetBci) {
        return 0;
    }

    private int translateBytecodeIndex(short[] bc, int searchBci) {
        int bci = 0;
        int translatedBci = 0;
        while (bci != searchBci) {
            short operand = bc[translatedBci];
            int width = getInstructionWidth(operand);
            if (isInstrumentationInstruction(operand)) {
                bci += width;
            } else {
                bci += width;
                translatedBci += width;
            }
        }
        return translatedBci;
    }

    record BeforeAfterInstrumentation(short beforeOp, short afterOp, short startBci, short endBci, short argument) {
    }

    record NotifyInstrumentation(short bci, short opCode) {
    }

    protected static boolean isSameInstrumentationInstruction(short[] bc1, int bci1, short[] bc2, int bci2) {
        short op1 = bc1[bci1];
        short op2 = bc2[bci2];
        if (op1 != op2) {
            return false;
        }
        int width = getInstructionWidth(op1);
        return Arrays.equals(bc1, bci1, bci1 + width, bc2, bci2, bci2 + width);
    }

    private static short undoQuickening(short $operand) {
        // generated
        return $operand;
    }

    protected static boolean isSameInstruction(short op1, short op2) {
        return undoQuickening(op1) == undoQuickening(op2);
    }

    protected static boolean isInstrumentationInstruction(short operand) {
        return isInstrumentationBeforeInstruction(operand) || isInstrumentationAfterInstruction(operand) || isInstrumentationNotifyInstruction(operand);
    }

    protected static boolean isInstrumentationBeforeInstruction(short operand) {
        // return false
        return false;
    }

    protected static boolean isInstrumentationNotifyInstruction(short operand) {
        // return false
        return false;
    }

    protected static boolean isInstrumentationAfterInstruction(short operand) {
        // return false
        return false;
    }

    protected static int getInstructionWidth(short operand) {
        // generate
        return 0;
    }

    static final class TreeNode extends Node implements InstrumentableNode, WrapperNode {

        @Children TreeNode[] children;

        private volatile ProbeNode installedProbe;
        @CompilationFinal private short beginBci;
        @CompilationFinal private short endBci;
        @CompilationFinal private int tags;

        public TreeNode() {
        }

        public boolean containsTags(int materializedTags) {
            return (tags & materializedTags) != 0;
        }

        public boolean isInstrumentable() {
            return true;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            RootNode root = getRootNode();
            this.installedProbe = probe;
            return this;
        }

        public ProbeNode getTreeProbeNode() {
            return null;
        }

        @Override
        public SourceSection getSourceSection() {
            return super.getSourceSection();
        }

        public boolean hasTag(Class<? extends Tag> other) {
            return false;
        }

        public Node getDelegateNode() {
            return this;
        }

        public ProbeNode getProbeNode() {
            return installedProbe;
        }

    }

    static class BuilderImpl {

    }

    static final class InstrumentationImpl extends Node {

        @Child TreeNode instrumentationTree;
        ProbeNode[] probeTable;
        int originalBytecodeSize;

        AbstractBytecodes updateBytecodes(InstrumentationImpl prev, AbstractBytecodes bytecodes) {
            short[] oldBc = bytecodes.bytecodes;
            int[] oldHandlers = bytecodes.handlers;

            BuilderImpl builder = new BuilderImpl();
// for (DynamicInstrumentation d : customInstrumentations) {
// d.apply(bytecodes, builder);
// }

            List<TreeNode> nodes = new ArrayList<>();
            collectInstrumentedNode(nodes, instrumentationTree);
// int newBcSize = builder.traceAt.length + (nodes.size() * 4);
            int newBcSize = 0;
            short[] newBc = new short[newBcSize];

            int probeNodeId = 0;
            ProbeNode[] probeNodes = new ProbeNode[nodes.size()];

            final Map<Integer, List<TreeNode>> probeBefore = new HashMap<>();
            final Map<Integer, List<TreeNode>> probeAfter = new HashMap<>();
            final Map<TreeNode, Integer> probeIds = new HashMap<>();
            final Map<Integer, Integer> traceAt = new HashMap<>();

            for (TreeNode node : nodes) {
                probeBefore.computeIfAbsent((int) node.beginBci, (s) -> new ArrayList<>(4)).add(node);
                probeAfter.computeIfAbsent((int) node.endBci, (s) -> new ArrayList<>(4)).add(node);
                probeIds.put(node, probeNodeId);
                probeNodes[probeNodeId++] = node.installedProbe;
            }
//
// for (int traceAtBci : builder.traceAt) {
// traceAt.put(traceAtBci, null);
// }

            // TODO iterate all instructions and prepare tables.
            // TODO all bytecodes must be updated lazy

            int oldBci = 0;
            int newBci = 0;
            int stableBci = 0;
            int bciOffset = 0;
            while (oldBci < oldBc.length) {
                short op = oldBc[oldBci];
                int width = getInstructionWidth(op);

                if (isInstrumentationInstruction(op)) {
                    bciOffset -= width;
                } else {
                    Integer stableBciObject = stableBci;
                    for (TreeNode node : probeBefore.get(stableBciObject)) {
                        int probeId = probeIds.get(node);
                        newBc[newBci++] = INSTRUCTION_PROBE_ENTER; //
                        newBc[newBci++] = (short) probeId;
                        bciOffset += 2;
                    }

                    if (traceAt.containsKey(stableBciObject)) {
                        newBc[newBci++] = INSTRUCTION_TRACE_AT; //
                    }

                    System.arraycopy(oldBc, oldBci, newBc, newBci, width);
                    rewriteOffsets(newBc, newBci, bciOffset);

                    newBci += width;
                    stableBci += width;

                    for (TreeNode node : probeBefore.get(stableBci)) {
                        int probeId = probeIds.get(node);
                        newBc[newBci++] = INSTRUCTION_PROBE_RETURN; //
                        newBc[newBci++] = (short) probeId;
                        bciOffset += 2;
                    }
                }

                oldBci += width;
            }

            return null;
        }

        static final int INSTRUCTION_PROBE_ENTER = 1;
        static final int INSTRUCTION_PROBE_RETURN = 2;
        static final int INSTRUCTION_TRACE_AT = 3;

        private void rewriteOffsets(short[] bc, int bci, int offsets) {

        }

        private void collectInstrumentedNode(List<TreeNode> nodes, TreeNode current) {
            if (current == null) {
                return;
            }
            ProbeNode probe = current.installedProbe;
            if (probe != null) {
                nodes.add(current);
            }
            for (TreeNode child : current.children) {
                collectInstrumentedNode(nodes, child);
            }
        }

    }

}
