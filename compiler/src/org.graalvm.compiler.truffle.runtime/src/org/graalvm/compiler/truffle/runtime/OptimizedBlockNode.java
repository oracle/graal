/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.VoidElement;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ValueProfile;

public final class OptimizedBlockNode<T extends Node & VoidElement> extends BlockNode<T> implements ReplaceObserver {

    private static class NullElementExceptionHandler implements ElementExceptionHandler {
        @Override
        public void onBlockElementException(VirtualFrame frame, Throwable e, int elementIndex) {
        }
    }

    /*
     * We use a dedicated instance to represent null exception handler at the call boundary as the
     * call target profile does not (and should not) support null values.
     */
    private static final NullElementExceptionHandler NULL_ELEMENT_EXCEPTION_HANDLER = new NullElementExceptionHandler();

    @CompilationFinal private volatile PartialBlocks partialBlocks;
    @CompilationFinal private volatile Assumption startAlwaysZero;
    @CompilationFinal Class<? extends ElementExceptionHandler> exceptionHandlerClass = ElementExceptionHandler.class;

    OptimizedBlockNode(T[] elements) {
        super(elements);
    }

    private static void handleElementException(VirtualFrame frame, ElementExceptionHandler exceptionHandler, int index, Throwable ex) {
        if (exceptionHandler != null) {
            exceptionHandler.onBlockElementException(frame, ex, index);
        }
    }

    @Override
    public Object execute(VirtualFrame frame, int start, ElementExceptionHandler eh) {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.execute(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((GenericElement) e[last]).execute(frame);
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.execute(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((GenericElement) e[last]).execute(frame);
    }

    @Override
    public void executeVoid(VirtualFrame frame, int start, ElementExceptionHandler eh) {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                g.execute(frame, start, eh);
                return;
            }
        }
        T[] e = getElements();
        executeBlockComplex(frame, e, start, e.length, eh);
    }

    @Override
    @ExplodeLoop
    public void executeVoid(VirtualFrame frame) {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                g.execute(frame, 0, null);
                return;
            }
        }
        T[] e = getElements();
        for (int i = 0; i < e.length; i++) {
            e[i].executeVoid(frame);
        }
    }

    @Override
    public byte executeByte(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeByte(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeByte(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public byte executeByte(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeByte(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeByte(frame);
    }

    @Override
    public short executeShort(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeShort(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeShort(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public short executeShort(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeShort(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeShort(frame);
    }

    @Override
    public int executeInt(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeInt(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeInt(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeInt(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeInt(frame);
    }

    @Override
    public char executeChar(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeChar(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeChar(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public char executeChar(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeChar(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeChar(frame);
    }

    @Override
    public long executeLong(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeLong(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeLong(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeLong(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeLong(frame);
    }

    @Override
    public float executeFloat(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeFloat(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeFloat(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public float executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeFloat(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeFloat(frame);
    }

    @Override
    public double executeDouble(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeDouble(frame, start, eh);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeDouble(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeDouble(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeDouble(frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame, int start, ElementExceptionHandler eh) throws UnexpectedResultException {
        handleStart(start, eh);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeBoolean(frame, start, eh);
            }
        }

        T[] e = getElements();
        int last = e.length - 1;
        executeBlockComplex(frame, e, start, last, eh);
        try {
            return ((TypedElement) e[last]).executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, eh, last, ex);
            throw ex;
        }
    }

    @Override
    @ExplodeLoop
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        assert handleStart(0, null);
        if (CompilerDirectives.inCompiledCode()) {
            PartialBlocks g = this.partialBlocks;
            if (g != null) {
                return g.executeBoolean(frame, 0, null);
            }
        }
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; i++) {
            e[i].executeVoid(frame);
        }
        return ((TypedElement) e[last]).executeBoolean(frame);
    }

    @SuppressWarnings("static-method")
    @ExplodeLoop
    private T[] executeBlockComplex(VirtualFrame frame, T[] e, int start, int end, ElementExceptionHandler eh) {
        /*
         * The interpreter code can be simpler with an explicit start index.
         */
        if (CompilerDirectives.inCompiledCode()) {
            CompilerAsserts.partialEvaluationConstant(eh);
            for (int i = 0; i < end; i++) {
                if (i >= start) {
                    try {
                        e[i].executeVoid(frame);
                    } catch (Throwable ex) {
                        handleElementException(frame, eh, i, ex);
                        throw ex;
                    }
                }
            }
        } else {
            for (int i = start; i < end; i++) {
                try {
                    e[i].executeVoid(frame);
                } catch (Throwable ex) {
                    handleElementException(frame, eh, i, ex);
                    throw ex;
                }
            }
        }
        return e;
    }

    Assumption getStartAlwaysZero() {
        Assumption local = this.startAlwaysZero;
        if (local == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.startAlwaysZero = local = Truffle.getRuntime().createAssumption();
        }
        return local;
    }

    private boolean handleStart(int start, ElementExceptionHandler eh) {
        CompilerAsserts.partialEvaluationConstant(eh);
        if (start < 0 || start >= getElements().length) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Invalid startIndex " + start + " for block with " + getElements().length + " elements.");
        }
        if (start != 0 && getStartAlwaysZero().isValid()) {
            CompilerDirectives.transferToInterpreter();
            getStartAlwaysZero().invalidate("Observed non-zero start.");
        }
        /*
         * We only run the profiling in interpreter because we don't want to deoptimize if we
         * compile a block that was never executed.
         */
        if (CompilerDirectives.inInterpreter()) {
            Class<? extends ElementExceptionHandler> cachedEhClass = this.exceptionHandlerClass;
            Class<? extends ElementExceptionHandler> ehClass = eh == null ? NULL_ELEMENT_EXCEPTION_HANDLER.getClass() : eh.getClass();
            if (cachedEhClass == ElementExceptionHandler.class) {
                exceptionHandlerClass = cachedEhClass = ehClass;
            }
            if (cachedEhClass != ehClass) {
                throw new IllegalArgumentException(String.format("Block node must be invoked with a compilation final exception handler type. " +
                                "Got type %s but was expecting type %s from a previous execution.", ehClass, cachedEhClass));
            }
        }
        return true;
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

    private static PartialBlocks computePartialBlocks(OptimizedCallTarget rootCompilation, OptimizedBlockNode<?> currentBlock, BlockVisitor visitor, Object[] array, int maxBlockSize) {
        int currentBlockSize = 0;
        int currentBlockIndex = 0;
        int totalCount = 0;
        int[] blockRanges = null;
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
                    } else if (currentBlockIndex >= blockRanges.length) {
                        blockRanges = Arrays.copyOf(blockRanges, blockRanges.length * 2);
                    }
                    blockRanges[currentBlockIndex++] = i;
                    currentBlockSize = childCount;
                }
            }
        }

        if (blockRanges != null) {
            /*
             * parent blocks should not count partial child blocks. they can hardly do much better.
             */
            visitor.count -= totalCount;

            // trim block ranges
            blockRanges = blockRanges.length != currentBlockIndex ? Arrays.copyOf(blockRanges, currentBlockIndex) : blockRanges;
            return new PartialBlocks(rootCompilation, currentBlock, blockRanges, visitor.blockIndex++);
        }
        return null;
    }

    public PartialBlocks getPartialBlocks() {
        return partialBlocks;
    }

    @Override
    public boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        PartialBlocks blocks = this.partialBlocks;
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
                OptimizedBlockNode<?> blockNode = ((OptimizedBlockNode<?>) node);
                Object[] children = blockNode.getElements();
                PartialBlocks oldBlocks = blockNode.getPartialBlocks();
                PartialBlocks newBlocks;
                if (oldBlocks == null) {
                    newBlocks = computePartialBlocks(rootCompilation, blockNode, this, children, maxBlockSize);
                } else {
                    newBlocks = oldBlocks;
                }
                if (oldBlocks == null) {
                    blockNode.atomic(new Runnable() {
                        @Override
                        public void run() {
                            PartialBlocks otherOldBlocks = blockNode.getPartialBlocks();
                            if (otherOldBlocks == null) {
                                blockNode.partialBlocks = newBlocks;
                            }
                        }
                    });
                }
                if (newBlocks != null) {
                    blockTargets.addAll(Arrays.asList(newBlocks.blockTargets));
                }
            } else {
                NodeUtil.forEachChild(node, this);
            }
            return true;
        }
    }

    static final class PartialBlockRootNode extends RootNode {

        private final OptimizedBlockNode<?> block;
        private final int startIndex;
        private final int endIndex;
        private final int blockIndex;
        @CompilationFinal private ValueProfile ehClassProfile;

        PartialBlockRootNode(FrameDescriptor descriptor, OptimizedBlockNode<?> block, int startIndex, int endIndex, int blockIndex) {
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
        public String getQualifiedName() {
            return computeName(block.getRootNode().getQualifiedName());
        }

        private String computeName(String name) {
            StringBuilder blockIndices = new StringBuilder();
            Node parent = block.getParent();
            while (parent != null) {
                if (parent instanceof OptimizedBlockNode<?>) {
                    PartialBlocks blocks = ((OptimizedBlockNode<?>) parent).getPartialBlocks();
                    if (blocks != null) {
                        blockIndices.append(((PartialBlockRootNode) blocks.getBlockTargets()[0].getRootNode()).blockIndex);
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
            int start = readAndProfileStart(arguments);
            ElementExceptionHandler eh = readElementExceptionHandler(arguments);
            VoidElement[] elements = block.getElements();
            int lastIndex = endIndex - 1;
            for (int i = startIndex; i < lastIndex; i++) {
                if (i >= start) {
                    try {
                        elements[i].executeVoid(outerFrame);
                    } catch (Throwable ex) {
                        handleElementException(frame, eh, i, ex);
                        throw ex;
                    }
                }
            }
            try {
                if (lastIndex == elements.length - 1 && elements[lastIndex] instanceof GenericElement) {
                    return ((GenericElement) elements[lastIndex]).execute(outerFrame);
                } else {
                    elements[lastIndex].executeVoid(outerFrame);
                    return null;
                }
            } catch (Throwable ex) {
                handleElementException(frame, eh, lastIndex, ex);
                throw ex;
            }
        }

        private ElementExceptionHandler readElementExceptionHandler(Object[] arguments) {
            ElementExceptionHandler eh = (ElementExceptionHandler) arguments[2];
            if (eh == null) {
                return null;
            }
            return block.exceptionHandlerClass.cast(eh);
        }

        private int readAndProfileStart(Object[] arguments) {
            assert block.startAlwaysZero != null;
            if (block.startAlwaysZero.isValid()) {
                return 0;
            } else {
                return (int) arguments[1];
            }
        }

        @Override
        public String toString() {
            return getQualifiedName();
        }
    }

    public static final class PartialBlocks {

        final OptimizedBlockNode<?> block;
        /*
         * We do not use direct call nodes to avoid inlining.
         */
        @CompilationFinal(dimensions = 1) final OptimizedCallTarget[] blockTargets;
        @CompilationFinal(dimensions = 1) final int[] blockRanges;

        PartialBlocks(OptimizedCallTarget rootCompilation, OptimizedBlockNode<?> block, int[] blockRanges, int blockIndex) {
            assert blockRanges.length > 0;
            this.block = block;
            this.blockRanges = blockRanges;
            block.getStartAlwaysZero(); // make sure assumption is initialized

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
                targets[i] = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new PartialBlockRootNode(new FrameDescriptor(), block, startIndex, endIndex, blockIndex));
                // we know the parameter types for block compilations. No need to check, lets cast
                // them unsafely.
                targets[i].getCompilationProfile().initializeArgumentTypes(new Class<?>[]{materializedFrameClass, Integer.class, block.exceptionHandlerClass});
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

        int executeInt(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Integer) {
                return (int) result;
            }
            throw new UnexpectedResultException(result);
        }

        byte executeByte(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Byte) {
                return (byte) result;
            }
            throw new UnexpectedResultException(result);
        }

        short executeShort(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Short) {
                return (short) result;
            }
            throw new UnexpectedResultException(result);
        }

        long executeLong(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Long) {
                return (long) result;
            }
            throw new UnexpectedResultException(result);
        }

        char executeChar(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Character) {
                return (char) result;
            }
            throw new UnexpectedResultException(result);
        }

        float executeFloat(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Float) {
                return (float) result;
            }
            throw new UnexpectedResultException(result);
        }

        double executeDouble(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Double) {
                return (double) result;
            }
            throw new UnexpectedResultException(result);
        }

        boolean executeBoolean(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
            Object result = execute(frame, startElement, exceptionHandler);
            if (result instanceof Boolean) {
                return (boolean) result;
            }
            throw new UnexpectedResultException(result);
        }

        @ExplodeLoop
        Object execute(VirtualFrame frame, int startElement, ElementExceptionHandler exceptionHandler) {
            int start = startElement;
            Object[] arguments = new Object[]{frame.materialize(), start, exceptionHandler == null ? NULL_ELEMENT_EXCEPTION_HANDLER : exceptionHandler};
            int[] ranges = blockRanges;
            OptimizedCallTarget[] targets = this.blockTargets;
            for (int i = 0; i < ranges.length; i++) {
                if (start < ranges[i]) {
                    targets[i].doInvoke(arguments);
                }
            }
            if (start < block.getElements().length) {
                return targets[ranges.length].doInvoke(arguments);
            }
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("should not happen, verified by the block node.");
        }
    }

}
