/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Loop node implementation that supports on-stack-replacement with compiled code.
 *
 * @see #create(RepeatingNode)
 * @see #createOSRLoop(RepeatingNode, int, int, FrameSlot[], FrameSlot[])
 */
public abstract class OptimizedOSRLoopNode extends LoopNode implements ReplaceObserver {

    @Child private RepeatingNode repeatableNode;

    /**
     * We disable profiling if its very unlikely that this loop is ever going to get OSR compiled.
     * For example if the parent call target or a parent loop is already hot or compiling.
     */
    private volatile boolean profilingEnabled = true;

    /**
     * If an OSR compilation is scheduled the corresponding call target is stored here.
     */
    private volatile OptimizedCallTarget compiledOSRLoop;

    /**
     * The current loop count. Not for condition probabilities use as it might stop profiling.
     */
    private int loopCount;
    /**
     * The current loop threshold. Might get increased with each invalidation.
     */
    private int osrThreshold;

    private OptimizedOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold) {
        Objects.requireNonNull(repeatableNode);
        if (osrThreshold < 0) {
            throw new IllegalArgumentException("Invalid OSR threshold.");
        }
        this.repeatableNode = repeatableNode;
        this.osrThreshold = osrThreshold;
    }

    protected abstract int getInvalidationBackoff();

    protected OSRRootNode createRootNode(@SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> truffleLanguage, FrameDescriptor frameDescriptor,
                    Class<? extends VirtualFrame> clazz) {
        return new OSRRootNode(this, truffleLanguage, frameDescriptor, clazz);
    }

    @Override
    public final Node copy() {
        OptimizedOSRLoopNode copy = (OptimizedOSRLoopNode) super.copy();
        copy.compiledOSRLoop = null;
        return copy;
    }

    @Override
    public final RepeatingNode getRepeatingNode() {
        return repeatableNode;
    }

    @Override
    public void executeLoop(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter()) {
            boolean done = false;
            while (!done) {
                if (!profilingEnabled) {
                    while (repeatableNode.executeRepeating(frame)) {
                        // no OSR compilation can happen for this loop anymore.
                    }
                    break;
                } else if (compiledOSRLoop == null) {
                    done = profilingLoop(frame);
                } else {
                    done = compilingLoop(frame);
                }
            }
        } else {
            while (repeatableNode.executeRepeating(frame)) {
                if (CompilerDirectives.inInterpreter()) {
                    // compiled method got invalidated. We might need OSR again.
                    executeLoop(frame);
                    return;
                }
            }
        }
    }

    private boolean profilingLoop(VirtualFrame frame) {
        int iterations = 0;
        try {
            while (repeatableNode.executeRepeating(frame)) {
                iterations++;
                int totalLoopCount = ++loopCount;
                if (totalLoopCount > osrThreshold) {
                    compileLoop(frame);
                    return false;
                }
            }
            return true;
        } finally {
            reportParentLoopCount(iterations);
        }
    }

    private void reportParentLoopCount(int iterations) {
        Node parent = getParent();
        if (parent != null) {
            LoopNode.reportLoopCount(parent, iterations);
        }
    }

    final void reportChildLoopCount(int iterations) {
        loopCount += iterations;
    }

    /**
     * Forces OSR compilation for this loop.
     */
    public final void forceOSR() {
        osrThreshold = loopCount;
        RootNode rootNode = getRootNode();
        VirtualFrame dummyFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], rootNode != null ? rootNode.getFrameDescriptor() : new FrameDescriptor());
        compileLoop(dummyFrame);
    }

    public final OptimizedCallTarget getCompiledOSRLoop() {
        return compiledOSRLoop;
    }

    private boolean compilingLoop(VirtualFrame frame) {
        int iterations = 0;
        try {
            do {
                OptimizedCallTarget target = compiledOSRLoop;
                if (target == null) {
                    return false;
                } else if (target.isValid()) {
                    Object result = target.callDirect(frame);
                    if (result == Boolean.TRUE) {
                        // loop is done. No further repetitions necessary.
                        return true;
                    } else {
                        invalidate(this, "OSR compilation got invalidated");
                        return false;
                    }
                } else if (!target.isCompiling()) {
                    invalidate(this, "OSR compilation failed or cancelled");
                    return false;
                } else {
                    iterations++;
                    loopCount++;
                }
            } while (repeatableNode.executeRepeating(frame));
            return true;
        } finally {
            reportParentLoopCount(iterations);
        }
    }

    private void compileLoop(VirtualFrame frame) {
        atomic(new Runnable() {
            @Override
            public void run() {
                /*
                 * Compilations need to run atomically as they may be scheduled by multiple threads
                 * at the same time. This strategy lets the first thread win. Later threads will not
                 * issue compiles.
                 */
                if (compiledOSRLoop == null) {
                    compiledOSRLoop = compileImpl(frame);
                }
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private OSRRootNode createRootNodeImpl(RootNode root, Class<? extends VirtualFrame> frameClass) {
        Class truffleLanguage = TruffleLanguage.class;
        FrameDescriptor frameDescriptor;
        if (root != null) {
            frameDescriptor = root.getFrameDescriptor();
        } else {
            frameDescriptor = new FrameDescriptor();
        }
        return createRootNode(truffleLanguage, frameDescriptor, frameClass);
    }

    private OptimizedCallTarget compileImpl(VirtualFrame frame) {
        RootNode root = getRootNode();
        if (shouldCompile(root)) {
            Node parent = getParent();
            OptimizedCallTarget osrTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(createRootNodeImpl(root, frame.getClass()));
            // to avoid a deopt on first call we provide some profiling information
            osrTarget.profileReturnType(Boolean.TRUE);
            osrTarget.profileReturnType(Boolean.FALSE);
            osrTarget.profileArguments(new Object[]{frame});
            // let the old parent re-adopt the children
            parent.adoptChildren();
            osrTarget.compile();
            return osrTarget;
        } else {
            return null;
        }
    }

    private boolean shouldCompile(RootNode root) {
        OptimizedCallTarget parentTarget = root != null ? (OptimizedCallTarget) root.getCallTarget() : null;
        if (parentTarget != null) {
            if (parentTarget.isCompiling() ||
                            parentTarget.isValid()) {
                // no profiling anymore parent is already compiling.
                profilingEnabled = false;
                return false;
            }

            if (parentTarget.getCompilationProfile().getInterpreterCallCount() > TruffleCompilerOptions.TruffleMinInvokeThreshold.getValue()) {
                // do not yet disable profiling
                return false;
            }
        }

        Node node = getParent();
        while (node != null) {
            if (node instanceof OptimizedOSRLoopNode) {
                OptimizedCallTarget target = ((OptimizedOSRLoopNode) node).getCompiledOSRLoop();
                if (target != null) {
                    // parent loop node is already compiling
                    return false;
                }
            }
            node = node.getParent();
        }
        return true;
    }

    @Override
    public final boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        invalidate(newNode, reason);
        return false;
    }

    private void invalidate(Object source, CharSequence reason) {
        OptimizedCallTarget target = this.compiledOSRLoop;
        if (target != null) {
            int invalidationBackoff = getInvalidationBackoff();
            if (invalidationBackoff < 0) {
                throw new IllegalArgumentException("Invalid OSR invalidation backoff.");
            }
            osrThreshold = loopCount + invalidationBackoff;
            compiledOSRLoop = null;
            target.invalidate(source, reason);
        }
    }

    /**
     * Creates the default loop node implementation with the default configuration. If OSR is
     * disabled {@link OptimizedLoopNode} will be used instead.
     */
    public static LoopNode create(RepeatingNode repeat) {
        // using static methods with LoopNode return type ensures
        // that only one loop node implementation gets loaded.
        if (TruffleCompilerOptions.TruffleOSR.getValue()) {
            return createDefault(repeat);
        } else {
            return OptimizedLoopNode.create(repeat);
        }
    }

    private static LoopNode createDefault(RepeatingNode repeatableNode) {
        return new OptimizedDefaultOSRLoopNode(repeatableNode, TruffleCompilerOptions.TruffleOSRCompilationThreshold.getValue());
    }

    /**
     * <p>
     * Creates a configurable instance of the OSR loop node. If readFrameSlots and writtenFrameSlots
     * are set then the involved frame must never escape, ie {@link VirtualFrame#materialize()} is
     * never invoked.
     * </p>
     *
     * <p>
     * <b>Important note:</b> All readFrameSlots that are given must be initialized before entering
     * the loop. Also all writtenFrameSlots must be initialized inside of the loop if they were not
     * initialized outside the loop.
     * </p>
     *
     * @param repeating the repeating node to use for this loop.
     * @param osrThreshold the threshold after how many loop iterations an OSR compilation is
     *            triggered. If the repeating node uses child loops or
     *            {@link LoopNode#reportLoopCount(Node, int)} then these iterations also contribute
     *            to this loop's iterations.
     * @param invalidationBackoff how many iterations the loop should get reprofiled until the next
     *            compile is scheduled.
     * @param readFrameSlots a set of all frame slots which are read inside the loop.
     *            <code>null</code> for unknown. All given frame slots must not have the
     *            {@link FrameSlotKind#Illegal illegal frame slot kind} set. If readFrameSlot is
     *            kept <code>null</code> writtenFrameSlots must be <code>null</code> as well.
     * @param writtenFrameSlots a set of all frame slots which are written inside the loop.
     *            <code>null</code> for unknown. All given frame slots must not have the
     *            {@link FrameSlotKind#Illegal illegal frame slot kind} set. If readFrameSlot is
     *            kept <code>null</code> writtenFRameSlots must be <code>null</code> as well.
     *
     * @see LoopNode LoopNode on how to use loop nodes.
     */
    public static OptimizedOSRLoopNode createOSRLoop(RepeatingNode repeating, int osrThreshold, int invalidationBackoff, FrameSlot[] readFrameSlots, FrameSlot[] writtenFrameSlots) {
        if ((readFrameSlots == null) != (writtenFrameSlots == null)) {
            throw new IllegalArgumentException("If either readFrameSlots or writtenFrameSlots is set both must be provided.");
        }
        return new OptimizedVirtualizingOSRLoopNode(repeating, osrThreshold, invalidationBackoff, readFrameSlots, writtenFrameSlots);
    }

    /**
     * Used by default in guest languages.
     */
    private static final class OptimizedDefaultOSRLoopNode extends OptimizedOSRLoopNode {

        OptimizedDefaultOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold) {
            super(repeatableNode, osrThreshold);
        }

        @Override
        protected int getInvalidationBackoff() {
            return TruffleCompilerOptions.TruffleInvalidationReprofileCount.getValue();
        }

    }

    /**
     * Used in guest languages with Graal runtime access that require more revirtualization of local
     * variables and more configuration options.
     */
    private static final class OptimizedVirtualizingOSRLoopNode extends OptimizedOSRLoopNode {

        private final int invalidationBackoff;
        @CompilationFinal private final FrameSlot[] readFrameSlots;
        @CompilationFinal private final FrameSlot[] writtenFrameSlots;

        private VirtualizingOSRRootNode previousRoot;

        private OptimizedVirtualizingOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold, int invalidationBackoff, FrameSlot[] readFrameSlots, FrameSlot[] writtenFrameSlots) {
            super(repeatableNode, osrThreshold);
            this.invalidationBackoff = invalidationBackoff;
            this.readFrameSlots = readFrameSlots;
            this.writtenFrameSlots = writtenFrameSlots;
        }

        @Override
        public int getInvalidationBackoff() {
            return invalidationBackoff;
        }

        @Override
        protected OSRRootNode createRootNode(@SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> truffleLanguage, FrameDescriptor frameDescriptor, Class<? extends VirtualFrame> clazz) {
            if (readFrameSlots == null || writtenFrameSlots == null) {
                return super.createRootNode(truffleLanguage, frameDescriptor, clazz);
            } else {
                if (previousRoot == null) {
                    previousRoot = new VirtualizingOSRRootNode(this, truffleLanguage, frameDescriptor, clazz, readFrameSlots, writtenFrameSlots);
                } else {
                    // we want to reuse speculations from a previous compilation so no rewrite loops
                    // occur.
                    previousRoot = new VirtualizingOSRRootNode(previousRoot, this, truffleLanguage, frameDescriptor, clazz);
                }
                return previousRoot;
            }
        }

    }

    public static class OSRRootNode extends RootNode {

        protected final Class<? extends VirtualFrame> clazz;

        @Child protected OptimizedOSRLoopNode loopNode;

        OSRRootNode(OptimizedOSRLoopNode loop, @SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> truffleLanguage, FrameDescriptor frameDescriptor, Class<? extends VirtualFrame> clazz) {
            super(truffleLanguage, loop.getSourceSection(), frameDescriptor);
            this.loopNode = loop;
            this.clazz = clazz;
        }

        public static Object callProxy(OSRRootNode target, VirtualFrame frame) {
            return target.executeImpl(frame);
        }

        protected Object executeImpl(VirtualFrame frame) {
            VirtualFrame parentFrame = clazz.cast(frame.getArguments()[0]);
            while (loopNode.getRepeatingNode().executeRepeating(parentFrame)) {
                if (CompilerDirectives.inInterpreter()) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            return callProxy(this, frame);
        }

        @Override
        public final boolean isCloningAllowed() {
            return false;
        }

        @Override
        public final String toString() {
            return loopNode.getRepeatingNode().toString() + "<OSR>";
        }
    }

    private static final class VirtualizingOSRRootNode extends OSRRootNode {

        @CompilationFinal private final FrameSlot[] readFrameSlots;
        @CompilationFinal private final FrameSlot[] writtenFrameSlots;

        @CompilationFinal private final byte[] readFrameSlotsTags;
        @CompilationFinal private final byte[] writtenFrameSlotsTags;
        private final int maxTagsLength;

        VirtualizingOSRRootNode(VirtualizingOSRRootNode previousRoot, OptimizedOSRLoopNode loop, @SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> truffleLanguage,
                        FrameDescriptor frameDescriptor,
                        Class<? extends VirtualFrame> clazz) {
            super(loop, truffleLanguage, frameDescriptor, clazz);
            this.readFrameSlots = previousRoot.readFrameSlots;
            this.writtenFrameSlots = previousRoot.writtenFrameSlots;
            this.readFrameSlotsTags = previousRoot.readFrameSlotsTags;
            this.writtenFrameSlotsTags = previousRoot.writtenFrameSlotsTags;
            this.maxTagsLength = previousRoot.maxTagsLength;
        }

        VirtualizingOSRRootNode(OptimizedOSRLoopNode loop, @SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> truffleLanguage, FrameDescriptor frameDescriptor,
                        Class<? extends VirtualFrame> clazz,
                        FrameSlot[] readFrameSlots, FrameSlot[] writtenFrameSlots) {
            super(loop, truffleLanguage, frameDescriptor, clazz);
            this.readFrameSlots = readFrameSlots;
            this.writtenFrameSlots = writtenFrameSlots;
            this.readFrameSlotsTags = new byte[readFrameSlots.length];
            this.writtenFrameSlotsTags = new byte[writtenFrameSlots.length];
            int maxIndex = -1;
            maxIndex = initializeFrameSlots(readFrameSlots, readFrameSlotsTags, maxIndex);
            maxIndex = initializeFrameSlots(writtenFrameSlots, writtenFrameSlotsTags, maxIndex);
            this.maxTagsLength = maxIndex + 1;
        }

        private static int initializeFrameSlots(FrameSlot[] frameSlots, byte[] tags, int maxIndex) {
            int currentMaxIndex = maxIndex;
            for (int i = 0; i < frameSlots.length; i++) {
                FrameSlot frameSlot = frameSlots[i];
                if (frameSlot.getIndex() > currentMaxIndex) {
                    currentMaxIndex = frameSlot.getIndex();
                }
                tags[i] = frameSlot.getKind().tag;
            }
            return currentMaxIndex;
        }

        @Override
        protected Object executeImpl(VirtualFrame originalFrame) {
            FrameWithoutBoxing loopFrame = (FrameWithoutBoxing) (originalFrame);
            FrameWithoutBoxing parentFrame = (FrameWithoutBoxing) (loopFrame.getArguments()[0]);
            executeTransfer(parentFrame, loopFrame, readFrameSlots, readFrameSlotsTags);
            try {
                while (loopNode.getRepeatingNode().executeRepeating(loopFrame)) {
                    if (CompilerDirectives.inInterpreter()) {
                        return Boolean.FALSE;
                    }
                }
                return Boolean.TRUE;
            } finally {
                executeTransfer(loopFrame, parentFrame, writtenFrameSlots, writtenFrameSlotsTags);
            }
        }

        @ExplodeLoop
        private void executeTransfer(FrameWithoutBoxing source, FrameWithoutBoxing target, FrameSlot[] frameSlots, byte[] speculatedTags) {
            if (frameSlots == null) {
                return;
            }
            byte[] currentSourceTags = source.getTags();
            byte[] currentTargetTags = target.getTags();

            /*
             * We check max tags so length of the tags array is not checked inside the loop each
             * time.
             */
            if (currentSourceTags.length < maxTagsLength || currentTargetTags.length < maxTagsLength) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError("Frames should never shrink.");
            }

            for (int i = 0; i < frameSlots.length; i++) {
                FrameSlot slot = frameSlots[i];
                int index = slot.getIndex();

                byte speculatedTag = speculatedTags[i];
                byte currentSourceTag = currentSourceTags[index];
                if (CompilerDirectives.inInterpreter()) {
                    if (currentSourceTag == 0 && speculatedTag != 0) {
                        if (frameSlots == readFrameSlots) {
                            throw new AssertionError("Frame slot " + slot + " was never writte outside the loop but virtualized as read frame slot.");
                        } else {
                            throw new AssertionError("Frame slot " + slot + " was never written in the loop but virtualized as written frame slot.");
                        }
                    }
                }

                boolean tagsCondition = speculatedTag == currentSourceTag;
                if (!tagsCondition) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    speculatedTags[i] = currentSourceTag;
                    speculatedTag = currentSourceTag;
                }

                switch (speculatedTag) {
                    case FrameWithoutBoxing.BOOLEAN_TAG:
                        target.setBoolean(slot, source.getBooleanUnsafe(index, slot, tagsCondition));
                        break;
                    case FrameWithoutBoxing.BYTE_TAG:
                        target.setByte(slot, source.getByteUnsafe(index, slot, tagsCondition));
                        break;
                    case FrameWithoutBoxing.DOUBLE_TAG:
                        target.setDouble(slot, source.getDoubleUnsafe(index, slot, tagsCondition));
                        break;
                    case FrameWithoutBoxing.FLOAT_TAG:
                        target.setFloat(slot, source.getFloatUnsafe(index, slot, tagsCondition));
                        break;
                    case FrameWithoutBoxing.INT_TAG:
                        target.setInt(slot, source.getIntUnsafe(index, slot, tagsCondition));
                        break;
                    case FrameWithoutBoxing.LONG_TAG:
                        target.setLong(slot, source.getLongUnsafe(index, slot, tagsCondition));
                        break;
                    case FrameWithoutBoxing.OBJECT_TAG:
                        target.setObject(slot, source.getObjectUnsafe(index, slot, tagsCondition));
                        break;
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new AssertionError("Defined frame slot " + slot + " is illegal. Revirtualization failed. Please initialize frame slot with a FrameSlotKind.");
                }
            }
        }

    }

}
