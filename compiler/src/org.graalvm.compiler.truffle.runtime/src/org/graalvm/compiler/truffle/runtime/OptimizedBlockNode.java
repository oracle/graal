/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.NeverValidAssumption;

public final class OptimizedBlockNode<T extends Node> extends BlockNode<T> implements ReplaceObserver {

    @CompilationFinal private volatile PartialBlocks<T> partialBlocks;
    private final ElementExecutor<T> executor;
    @CompilationFinal private volatile Assumption alwaysNoArgument;

    OptimizedBlockNode(T[] elements, ElementExecutor<T> executor) {
        super(elements);
        this.executor = executor;
    }

    @Override
    @ExplodeLoop
    public Object executeGeneric(VirtualFrame frame, int argument) {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.execute(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeGeneric(frame, e[last], last, arg);
    }

    private int profileArg(int arg) {
        Assumption a = alwaysNoArgument;
        if (a == null) {
            if (CompilerDirectives.inInterpreter()) {
                // no need to deoptimize if the block was never executed
                if (arg == NO_ARGUMENT) {
                    alwaysNoArgument = Truffle.getRuntime().createAssumption("Always zero block node argument.");
                } else {
                    alwaysNoArgument = NeverValidAssumption.INSTANCE;
                }
            }
        } else if (a.isValid()) {
            if (arg == NO_ARGUMENT) {
                return NO_ARGUMENT;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                a.invalidate();
            }
        }
        return arg;
    }

    @Override
    @ExplodeLoop
    public void executeVoid(VirtualFrame frame, int argument) {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                g.execute(frame, arg);
                return;
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        for (int i = 0; i < e.length; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
    }

    @Override
    @ExplodeLoop
    public byte executeByte(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeByte(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeByte(frame, e[last], last, arg);
    }

    @Override
    @ExplodeLoop
    public short executeShort(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeShort(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeShort(frame, e[last], last, arg);
    }

    @Override
    @ExplodeLoop
    public char executeChar(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeChar(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeChar(frame, e[last], last, arg);
    }

    @Override
    @ExplodeLoop
    public int executeInt(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeInt(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeInt(frame, e[last], last, arg);
    }

    @Override
    @ExplodeLoop
    public long executeLong(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeLong(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeLong(frame, e[last], last, arg);
    }

    @Override
    @ExplodeLoop
    public float executeFloat(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeFloat(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeFloat(frame, e[last], last, arg);
    }

    @Override
    @ExplodeLoop
    public double executeDouble(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeDouble(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeDouble(frame, e[last], last, arg);
    }

    @Override
    @ExplodeLoop
    public boolean executeBoolean(VirtualFrame frame, int argument) throws UnexpectedResultException {
        int arg = profileArg(argument);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks<T> g = this.partialBlocks;
            if (g != null) {
                return g.executeBoolean(frame, arg);
            }
        }
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeBoolean(frame, e[last], last, arg);
    }

    static List<OptimizedCallTarget> preparePartialBlockCompilations(OptimizedCallTarget rootCompilation) {
        if (rootCompilation.getOptionValue(PolyglotCompilerOptions.PartialBlockCompilation)) {
            int nonTrivialNodeCount = rootCompilation.getNonTrivialNodeCount();
            int maxBlockSize = rootCompilation.getOptionValue(PolyglotCompilerOptions.PartialBlockCompilationSize);
            if (nonTrivialNodeCount > maxBlockSize) {
                BlockVisitor visitor = new BlockVisitor(rootCompilation, maxBlockSize);
                NodeUtil.forEachChild(rootCompilation.getRootNode(), visitor);
                return visitor.blockTargets;
            }
        }
        return Collections.emptyList();
    }

    /*
     * The partial blocks are computed depth first. The deepest block is computed first and if that
     * block computes partial blocks then these nodes will be subtracted from the parent block
     * computation. A block might be split if it has at least two elements. A set of block elements
     * is split if it reaches the maxBlockSize limit. If the total number of child nodes of a block
     * is smaller than the maxBlockSize limit, then the block will not be split. A parent block
     * might still split in that case.
     */
    private static <T extends Node> PartialBlocks<T> computePartialBlocks(OptimizedCallTarget rootCompilation, OptimizedBlockNode<T> currentBlock, BlockVisitor visitor, Object[] array,
                    int maxBlockSize) {
        int currentBlockSize = 0;
        int currentBlockIndex = 0;
        int totalCount = 0;
        int[] blockRanges = null;
        int[] blockSizes = null;
        for (int i = 0; i < array.length; i++) {
            Object child = array[i];
            if (child != null) {
                Node childNode = (Node) child;
                int nodeCountBefore = visitor.count;
                visitor.visit(childNode);
                int childCount = visitor.count - nodeCountBefore;
                totalCount += childCount;
                int newBlockSize = currentBlockSize + childCount;
                if (newBlockSize <= maxBlockSize) {
                    currentBlockSize = newBlockSize;
                } else {
                    if (blockRanges == null) {
                        blockRanges = new int[8];
                        blockSizes = new int[8];
                    } else if (currentBlockIndex >= blockRanges.length) {
                        blockRanges = Arrays.copyOf(blockRanges, blockRanges.length * 2);
                        blockSizes = Arrays.copyOf(blockSizes, blockSizes.length * 2);
                    }
                    blockSizes[currentBlockIndex] = currentBlockSize;
                    blockRanges[currentBlockIndex++] = i;
                    currentBlockSize = childCount;
                }
            }
        }

        if (blockRanges != null) {
            blockSizes[currentBlockIndex] = currentBlockSize;
            /*
             * parent blocks should not count partial child blocks. they can hardly do much better.
             */
            visitor.count -= totalCount;

            // trim block ranges
            blockRanges = blockRanges.length != currentBlockIndex ? Arrays.copyOf(blockRanges, currentBlockIndex) : blockRanges;
            return new PartialBlocks<>(rootCompilation, currentBlock, blockRanges, blockSizes, visitor.blockIndex++);
        }
        return null;
    }

    public PartialBlocks<T> getPartialBlocks() {
        return partialBlocks;
    }

    @Override
    public boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        PartialBlocks<T> blocks = this.partialBlocks;
        if (blocks == null) {
            /* No partial compilation units compiled -> common case */
            return false;
        }

        /*
         * Find block element for which the child node was replaced. Maybe be the replaced node
         * itself. No replaces may happen during this event as the nodeReplaced event has acquired
         * an AST lock.
         */
        Node element = newNode;
        Node elementParent = element.getParent();
        while (elementParent != this && elementParent != null) {
            element = elementParent;
            elementParent = elementParent.getParent();
        }
        if (elementParent != this) {
            // could not find element, should not happen
            assert false;
            return false;
        }

        /*
         * Search the element using a slow iterative search. This could probably be improved with a
         * second child array that allows to binary search using the hashCode. At this point replace
         * events like this seem rare enough to not justify the cost.
         */
        int elementIndex = -1;
        T[] elements = getElements();
        for (int i = 0; i < elements.length; i++) {
            if (element == elements[i]) {
                elementIndex = i;
                break;
            }
        }
        if (elementIndex == -1) {
            // could not find element, should not happen
            assert false;
            return false;
        }
        assert elementIndex < getElements().length;
        assert elementIndex >= 0;
        /*
         * Block ranges are always ascending so we can use a binary search to find the block index.
         */
        int foundBlockIndex = Arrays.binarySearch(blocks.blockRanges, elementIndex);
        int callTargetIndex;
        if (foundBlockIndex < 0) {
            // insertion match
            callTargetIndex = -foundBlockIndex - 1;
        } else {
            // direct match
            callTargetIndex = foundBlockIndex + 1;
        }
        blocks.blockTargets[callTargetIndex].nodeReplaced(oldNode, newNode, reason);
        // Also report the replace to parent block compilations and root call targets.
        return false;
    }

    static final class BlockVisitor implements NodeVisitor {

        final List<OptimizedCallTarget> blockTargets = new ArrayList<>();
        final OptimizedCallTarget rootCompilation;
        final int maxBlockSize;
        int blockIndex;
        int count;

        BlockVisitor(OptimizedCallTarget rootCompilation, int maxBlockSize) {
            this.rootCompilation = rootCompilation;
            this.maxBlockSize = maxBlockSize;
        }

        @Override
        public boolean visit(Node node) {
            if (!node.getCost().isTrivial()) {
                count++;
            }
            if (node instanceof BlockNode<?>) {
                computeBlock((OptimizedBlockNode<?>) node);
            } else {
                NodeUtil.forEachChild(node, this);
            }
            return true;
        }

        private <T extends Node> void computeBlock(OptimizedBlockNode<T> blockNode) {
            Object[] children = blockNode.getElements();
            PartialBlocks<T> oldBlocks = blockNode.getPartialBlocks();
            PartialBlocks<T> newBlocks;
            if (oldBlocks == null) {
                newBlocks = computePartialBlocks(rootCompilation, blockNode, this, children, maxBlockSize);
            } else {
                newBlocks = oldBlocks;
            }
            if (oldBlocks == null) {
                blockNode.atomic(new Runnable() {
                    @Override
                    public void run() {
                        PartialBlocks<T> otherOldBlocks = blockNode.getPartialBlocks();
                        if (otherOldBlocks == null) {
                            blockNode.partialBlocks = newBlocks;
                        }
                    }
                });
            }
            if (newBlocks != null) {
                blockTargets.addAll(Arrays.asList(newBlocks.blockTargets));
            }
        }
    }

    static final class PartialBlockRootNode<T extends Node> extends RootNode {

        private final OptimizedBlockNode<T> block;
        private final int startIndex;
        private final int endIndex;
        private final int blockIndex;
        private SourceSection cachedSourceSection;

        PartialBlockRootNode(FrameDescriptor descriptor, OptimizedBlockNode<T> block, int startIndex, int endIndex, int blockIndex) {
            super(null, descriptor);
            this.block = block;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.blockIndex = blockIndex;
            assert startIndex != endIndex : "no empty blocks allowed";
        }

        @Override
        public boolean isCloningAllowed() {
            return false;
        }

        @Override
        public String getName() {
            return computeName(block.getRootNode().getName());
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        @TruffleBoundary
        public SourceSection getSourceSection() {
            SourceSection section = this.cachedSourceSection;
            if (section == null) {
                T[] elements = block.getElements();
                SourceSection startSection = elements[startIndex].getSourceSection();
                SourceSection endSection = elements[endIndex - 1].getSourceSection();
                if (startSection != null && endSection != null && startSection.getSource().equals(endSection.getSource())) {
                    section = startSection.getSource().createSection(startSection.getStartLine(), startSection.getStartColumn(), endSection.getEndLine(), endSection.getEndColumn());
                } else if (startSection != null) {
                    section = startSection;
                } else {
                    section = endSection;
                }
                this.cachedSourceSection = section;
            }
            return section;
        }

        @Override
        public String getQualifiedName() {
            return computeName(block.getRootNode().getQualifiedName());
        }

        private String computeName(String name) {
            StringBuilder blockIndices = new StringBuilder();
            Node parent = block.getParent();
            while (parent != null) {
                if (parent instanceof OptimizedBlockNode<?>) {
                    PartialBlocks<?> blocks = ((OptimizedBlockNode<?>) parent).getPartialBlocks();
                    if (blocks != null) {
                        blockIndices.append(((PartialBlockRootNode<?>) blocks.getBlockTargets()[0].getRootNode()).blockIndex);
                        blockIndices.append(":");
                    }
                }
                parent = parent.getParent();
            }
            blockIndices.append(blockIndex);

            String suffix = "<Partial-" + blockIndices + "-range:" + startIndex + ":" + endIndex + ">";
            if (name == null) {
                return suffix;
            } else {
                return name + suffix;
            }
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            MaterializedFrame outerFrame = (MaterializedFrame) arguments[0];
            int arg = readAndProfileArg(arguments);
            ElementExecutor<T> ex = block.executor;
            T[] e = block.getElements();
            int last = endIndex - 1;
            for (int i = startIndex; i < last; ++i) {
                ex.executeVoid(outerFrame, e[i], i, arg);
            }
            if (last == block.getElements().length - 1) {
                // last element of the block -> return a value
                return ex.executeGeneric(outerFrame, e[last], last, arg);
            } else {
                ex.executeVoid(outerFrame, e[last], last, arg);
                return null;
            }
        }

        private int readAndProfileArg(Object[] arguments) {
            Assumption alwaysNoArgument = block.alwaysNoArgument;
            if (alwaysNoArgument != null && alwaysNoArgument.isValid()) {
                return NO_ARGUMENT;
            } else {
                return (int) arguments[1];
            }
        }

        @Override
        public String toString() {
            return getQualifiedName();
        }
    }

    public static final class PartialBlocks<T extends Node> {

        final OptimizedBlockNode<?> block;
        /*
         * We do not use direct call nodes to avoid inlining.
         */
        @CompilationFinal(dimensions = 1) final OptimizedCallTarget[] blockTargets;
        @CompilationFinal(dimensions = 1) final int[] blockRanges;

        PartialBlocks(OptimizedCallTarget rootCompilation, OptimizedBlockNode<T> block, int[] blockRanges, int[] blockSizes, int blockIndex) {
            assert blockRanges.length > 0;
            this.block = block;
            this.blockRanges = blockRanges;

            RootNode rootNode = rootCompilation.getRootNode();
            assert rootNode == block.getRootNode();
            GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
            Class<?> materializedFrameClass = runtime.createMaterializedFrame(new Object[0]).getClass();
            FrameDescriptor descriptor = rootNode.getFrameDescriptor();
            runtime.markFrameMaterializeCalled(descriptor);
            int startIndex = 0;
            OptimizedCallTarget[] targets = new OptimizedCallTarget[blockRanges.length + 1];
            for (int i = 0; i < targets.length; i++) {
                int endIndex;
                if (i < blockRanges.length) {
                    endIndex = blockRanges[i];
                } else {
                    endIndex = block.getElements().length;
                }

                PartialBlockRootNode<T> partialRootNode = new PartialBlockRootNode<>(new FrameDescriptor(), block, startIndex, endIndex, blockIndex);
                GraalRuntimeAccessor.NODES.applyPolyglotEngine(rootNode, partialRootNode);

                targets[i] = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(partialRootNode);
                targets[i].setNonTrivialNodeCount(blockSizes[i]);
                // we know the parameter types for block compilations. No need to check, lets cast
                // them unsafely.
                targets[i].initializeArgumentTypes(new Class<?>[]{materializedFrameClass, Integer.class});
                // All block compilations share the speculation log of the root compilation.
                targets[i].setSpeculationLog(rootCompilation.getSpeculationLog());
                startIndex = endIndex;
            }
            this.blockTargets = targets;
        }

        public OptimizedCallTarget[] getBlockTargets() {
            return blockTargets;
        }

        public int[] getBlockRanges() {
            return blockRanges;
        }

        int executeInt(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Integer) {
                return (int) result;
            }
            throw new UnexpectedResultException(result);
        }

        byte executeByte(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Byte) {
                return (byte) result;
            }
            throw new UnexpectedResultException(result);
        }

        short executeShort(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Short) {
                return (short) result;
            }
            throw new UnexpectedResultException(result);
        }

        long executeLong(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Long) {
                return (long) result;
            }
            throw new UnexpectedResultException(result);
        }

        char executeChar(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Character) {
                return (char) result;
            }
            throw new UnexpectedResultException(result);
        }

        float executeFloat(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Float) {
                return (float) result;
            }
            throw new UnexpectedResultException(result);
        }

        double executeDouble(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Double) {
                return (double) result;
            }
            throw new UnexpectedResultException(result);
        }

        boolean executeBoolean(VirtualFrame frame, int arg) throws UnexpectedResultException {
            Object result = execute(frame, arg);
            if (result instanceof Boolean) {
                return (boolean) result;
            }
            throw new UnexpectedResultException(result);
        }

        @ExplodeLoop
        Object execute(VirtualFrame frame, int arg) {
            Object[] arguments = new Object[]{frame.materialize(), arg};
            int[] ranges = blockRanges;
            OptimizedCallTarget[] targets = this.blockTargets;
            for (int i = 0; i < ranges.length; i++) {
                targets[i].doInvoke(arguments);
            }
            return targets[ranges.length].doInvoke(arguments);
        }
    }

}
