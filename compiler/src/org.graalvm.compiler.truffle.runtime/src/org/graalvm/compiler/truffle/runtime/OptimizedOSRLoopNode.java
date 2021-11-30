/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import jdk.vm.ci.meta.SpeculationLog;

/**
 * Loop node implementation that supports on-stack-replacement with compiled code.
 *
 * @see #create(RepeatingNode)
 */
@SuppressWarnings("deprecation")
public abstract class OptimizedOSRLoopNode extends AbstractOptimizedLoopNode implements ReplaceObserver {

    /**
     * If an OSR compilation is scheduled the corresponding call target is stored here.
     */
    private volatile OptimizedCallTarget compiledOSRLoop;

    /**
     * The speculation log used by the call target. If multiple compilations happen over time (with
     * different call targets), the speculation log remains the same so that failed speculations are
     * correctly propagated between compilations.
     */
    private volatile SpeculationLog speculationLog;

    /**
     * The current base loop count. Reset for each loop invocation in the interpreter.
     */
    private int baseLoopCount;

    private final int osrThreshold;
    private final boolean firstTierBackedgeCounts;
    private volatile boolean compilationDisabled;

    private OptimizedOSRLoopNode(RepeatingNode repeatingNode, int osrThreshold, boolean firstTierBackedgeCounts) {
        super(repeatingNode);
        this.osrThreshold = osrThreshold;
        this.firstTierBackedgeCounts = firstTierBackedgeCounts;
    }

    /**
     * @param rootFrameDescriptor may be {@code null}.
     */
    protected AbstractLoopOSRRootNode createRootNode(FrameDescriptor rootFrameDescriptor, Class<? extends VirtualFrame> clazz) {
        /*
         * Use a new frame descriptor, because the frame that this new root node creates is not
         * used.
         */
        return new LoopOSRRootNode(this, new FrameDescriptor(), clazz);
    }

    @Override
    public final Node copy() {
        OptimizedOSRLoopNode copy = (OptimizedOSRLoopNode) super.copy();
        copy.compiledOSRLoop = null;
        return copy;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        RepeatingNode loopBody = repeatingNode;
        if (CompilerDirectives.inInterpreter()) {
            try {
                Object status = loopBody.initialLoopStatus();
                while (loopBody.shouldContinue(status)) {
                    if (compiledOSRLoop == null) {
                        status = profilingLoop(frame);
                    } else {
                        status = compilingLoop(frame);
                    }
                }
                return status;
            } finally {
                baseLoopCount = 0;
            }
        } else if (CompilerDirectives.hasNextTier()) {
            long iterationsCompleted = 0;
            Object status;
            try {
                while (inject(loopBody.shouldContinue((status = loopBody.executeRepeatingWithValue(frame))))) {
                    iterationsCompleted++;
                    if (CompilerDirectives.inInterpreter()) {
                        // compiled method got invalidated. We might need OSR again.
                        return execute(frame);
                    }
                    TruffleSafepoint.poll(this);
                }
            } finally {
                if (firstTierBackedgeCounts && iterationsCompleted > 1) {
                    LoopNode.reportLoopCount(this, toIntOrMaxInt(iterationsCompleted));
                }
            }
            return status;
        } else {
            Object status;
            while (inject(loopBody.shouldContinue((status = loopBody.executeRepeatingWithValue(frame))))) {
                if (CompilerDirectives.inInterpreter()) {
                    // compiled method got invalidated. We might need OSR again.
                    return execute(frame);
                }
                TruffleSafepoint.poll(this);
            }
            return status;
        }
    }

    static int toIntOrMaxInt(long i) {
        return i > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) i;
    }

    private Object profilingLoop(VirtualFrame frame) {
        RepeatingNode loopBody = repeatingNode;
        long iterations = 0;
        try {
            Object status;
            while (loopBody.shouldContinue(status = loopBody.executeRepeatingWithValue(frame))) {
                // the baseLoopCount might be updated from a child loop during an iteration.
                if (++iterations + baseLoopCount > osrThreshold && !compilationDisabled) {
                    compileLoop(frame);
                    // The status returned here is CONTINUE_LOOP_STATUS.
                    return status;
                }
                TruffleSafepoint.poll(this);
            }
            // The status returned here is different than CONTINUE_LOOP_STATUS.
            return status;
        } finally {
            reportLoopIterations(iterations);
        }
    }

    private void reportLoopIterations(long iterations) {
        baseLoopCount = toIntOrMaxInt(baseLoopCount + iterations);
        profileCounted(iterations);
        LoopNode.reportLoopCount(this, toIntOrMaxInt(iterations));
    }

    final void reportChildLoopCount(int iterations) {
        int newBaseLoopCount = baseLoopCount + iterations;
        if (newBaseLoopCount < 0) { // overflowed
            newBaseLoopCount = Integer.MAX_VALUE;
        }
        baseLoopCount = newBaseLoopCount;
    }

    /**
     * Forces OSR compilation for this loop.
     */
    public final void forceOSR() {
        baseLoopCount = osrThreshold;
        RootNode rootNode = getRootNode();
        VirtualFrame dummyFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], rootNode != null ? rootNode.getFrameDescriptor() : new FrameDescriptor());
        compileLoop(dummyFrame);
    }

    public final OptimizedCallTarget getCompiledOSRLoop() {
        return compiledOSRLoop;
    }

    private Object compilingLoop(VirtualFrame frame) {
        RepeatingNode loopBody = repeatingNode;
        long iterations = 0;
        try {
            Object status;
            do {
                OptimizedCallTarget target = compiledOSRLoop;
                if (target == null) {
                    return loopBody.initialLoopStatus();
                }
                if (!target.isSubmittedForCompilation()) {
                    if (target.isValid()) {
                        return callOSR(target, frame);
                    }
                    invalidateOSRTarget("OSR compilation failed or cancelled");
                    return loopBody.initialLoopStatus();
                }
                iterations++;
                TruffleSafepoint.poll(this);
            } while (loopBody.shouldContinue(status = loopBody.executeRepeatingWithValue(frame)));
            return status;
        } finally {
            reportLoopIterations(iterations);
        }
    }

    private Object callOSR(OptimizedCallTarget target, VirtualFrame frame) {
        Object status = target.callOSR(frame);
        if (!repeatingNode.initialLoopStatus().equals(status)) {
            return status;
        } else {
            if (!target.isValid()) {
                invalidateOSRTarget("OSR compilation got invalidated");
            }
            return status;
        }
    }

    private void compileLoop(VirtualFrame frame) {
        atomic(new Runnable() {
            @Override
            public void run() {
                if (compilationDisabled) {
                    return;
                }
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

    private AbstractLoopOSRRootNode createRootNodeImpl(RootNode root, Class<? extends VirtualFrame> frameClass) {
        return createRootNode(root == null ? null : root.getFrameDescriptor(), frameClass);
    }

    private OptimizedCallTarget compileImpl(VirtualFrame frame) {
        RootNode root = getRootNode();
        if (speculationLog == null) {
            speculationLog = GraalTruffleRuntime.getRuntime().createSpeculationLog();
        }
        OptimizedCallTarget osrTarget = (OptimizedCallTarget) createRootNodeImpl(root, frame.getClass()).getCallTarget();
        if (!osrTarget.acceptForCompilation()) {
            /*
             * Don't retry if the target will not be accepted anyway.
             */
            compilationDisabled = true;
            return null;
        }

        osrTarget.setSpeculationLog(speculationLog);
        osrTarget.compile(true);
        return osrTarget;
    }

    @Override
    public final boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        callNodeReplacedOnOSRTarget(oldNode, newNode, reason);
        return false;
    }

    private void callNodeReplacedOnOSRTarget(Node oldNode, Node newNode, CharSequence reason) {
        atomic(new Runnable() {
            @Override
            public void run() {
                OptimizedCallTarget target = compiledOSRLoop;
                if (target != null) {
                    resetCompiledOSRLoop();
                    target.nodeReplaced(oldNode, newNode, reason);
                }
            }
        });
    }

    private void resetCompiledOSRLoop() {
        OptimizedCallTarget target = this.compiledOSRLoop;
        if (target != null && target.isCompilationFailed()) {
            this.compilationDisabled = true;
        }
        this.compiledOSRLoop = null;
    }

    private void invalidateOSRTarget(CharSequence reason) {
        atomic(new Runnable() {
            @Override
            public void run() {
                OptimizedCallTarget target = compiledOSRLoop;
                if (target != null) {
                    resetCompiledOSRLoop();
                    target.invalidate(reason);
                }
            }
        });
    }

    /**
     * Creates the default loop node implementation with the default configuration. If OSR is
     * disabled {@link OptimizedLoopNode} will be used instead.
     */
    public static LoopNode create(RepeatingNode repeat) {
        // No RootNode accessible here, as repeat is not adopted.
        EngineData engine = GraalTVMCI.getEngineData(null);
        OptionValues engineOptions = engine.engineOptions;

        // using static methods with LoopNode return type ensures
        // that only one loop node implementation gets loaded.
        if (engine.compilation && engineOptions.get(PolyglotCompilerOptions.OSR)) {
            return createDefault(repeat, engineOptions);
        } else {
            return OptimizedLoopNode.create(repeat);
        }
    }

    private static LoopNode createDefault(RepeatingNode repeatableNode, OptionValues options) {
        return new OptimizedDefaultOSRLoopNode(repeatableNode,
                        options.get(PolyglotCompilerOptions.OSRCompilationThreshold),
                        options.get(PolyglotCompilerOptions.FirstTierBackedgeCounts));
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
     * @deprecated without replacement
     */
    @Deprecated
    public static OptimizedOSRLoopNode createOSRLoop(RepeatingNode repeating, int osrThreshold, com.oracle.truffle.api.frame.FrameSlot[] readFrameSlots,
                    com.oracle.truffle.api.frame.FrameSlot[] writtenFrameSlots) {
        if ((readFrameSlots == null) != (writtenFrameSlots == null)) {
            throw new IllegalArgumentException("If either readFrameSlots or writtenFrameSlots is set both must be provided.");
        }
        return new OptimizedVirtualizingOSRLoopNode(repeating, osrThreshold, false, readFrameSlots, writtenFrameSlots);
    }

    /**
     * @deprecated Use OptimizedOSRLoopNode#createOSRLoop(RepeatingNode, int,
     *             com.oracle.truffle.api.frame.FrameSlot[],
     *             com.oracle.truffle.api.frame.FrameSlot[]) instead.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static OptimizedOSRLoopNode createOSRLoop(RepeatingNode repeating, int osrThreshold, int invalidationBackoff, com.oracle.truffle.api.frame.FrameSlot[] readFrameSlots,
                    com.oracle.truffle.api.frame.FrameSlot[] writtenFrameSlots) {
        return createOSRLoop(repeating, osrThreshold, readFrameSlots, writtenFrameSlots);
    }

    /**
     * Used by default in guest languages.
     */
    private static final class OptimizedDefaultOSRLoopNode extends OptimizedOSRLoopNode {

        OptimizedDefaultOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold, boolean firstTierBackedgeCounts) {
            super(repeatableNode, osrThreshold, firstTierBackedgeCounts);
        }

    }

    /**
     * Used in guest languages with Graal runtime access that require more revirtualization of local
     * variables and more configuration options.
     */
    private static final class OptimizedVirtualizingOSRLoopNode extends OptimizedOSRLoopNode {

        @CompilationFinal(dimensions = 1) private final com.oracle.truffle.api.frame.FrameSlot[] readFrameSlots;
        @CompilationFinal(dimensions = 1) private final com.oracle.truffle.api.frame.FrameSlot[] writtenFrameSlots;

        private VirtualizingLoopOSRRootNode previousRoot;

        private OptimizedVirtualizingOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold, boolean firstTierBackedgeCounts, com.oracle.truffle.api.frame.FrameSlot[] readFrameSlots,
                        com.oracle.truffle.api.frame.FrameSlot[] writtenFrameSlots) {
            super(repeatableNode, osrThreshold, firstTierBackedgeCounts);
            this.readFrameSlots = readFrameSlots;
            this.writtenFrameSlots = writtenFrameSlots;
        }

        @Override
        protected AbstractLoopOSRRootNode createRootNode(FrameDescriptor rootFrameDescriptor, Class<? extends VirtualFrame> clazz) {
            if (readFrameSlots == null || writtenFrameSlots == null) {
                return super.createRootNode(rootFrameDescriptor, clazz);
            } else {
                FrameDescriptor frameDescriptor = rootFrameDescriptor == null ? new FrameDescriptor() : rootFrameDescriptor;
                if (previousRoot == null) {
                    previousRoot = new VirtualizingLoopOSRRootNode(this, frameDescriptor, clazz, readFrameSlots, writtenFrameSlots);
                } else {
                    // we want to reuse speculations from a previous compilation so no rewrite loops
                    // occur.
                    previousRoot = new VirtualizingLoopOSRRootNode(previousRoot, this, frameDescriptor, clazz);
                }
                return previousRoot;
            }
        }

    }

    abstract static class AbstractLoopOSRRootNode extends BaseOSRRootNode {

        protected final Class<? extends VirtualFrame> clazz;

        /**
         * Not adopted by the OSRRootNode; belongs to another RootNode. OptimizedCallTarget treats
         * OSRRootNodes specially, skipping adoption of child nodes.
         */
        @Child protected OptimizedOSRLoopNode loopNode;

        AbstractLoopOSRRootNode(OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor, Class<? extends VirtualFrame> clazz) {
            super(null, frameDescriptor);
            this.loopNode = loop;
            this.clazz = clazz;
        }

        @Override
        public SourceSection getSourceSection() {
            return loopNode.getSourceSection();
        }

        @Override
        protected Object executeOSR(VirtualFrame frame) {
            VirtualFrame parentFrame = clazz.cast(frame.getArguments()[0]);
            RepeatingNode loopBody = loopNode.repeatingNode;
            Object status;
            while (loopBody.shouldContinue(status = loopBody.executeRepeatingWithValue(parentFrame))) {
                if (CompilerDirectives.inInterpreter()) {
                    return loopBody.initialLoopStatus();
                }
                TruffleSafepoint.poll(this);
            }
            return status;
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

    static final class LoopOSRRootNode extends AbstractLoopOSRRootNode {
        LoopOSRRootNode(OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor, Class<? extends VirtualFrame> clazz) {
            super(loop, frameDescriptor, clazz);
        }
    }

    private static final class VirtualizingLoopOSRRootNode extends AbstractLoopOSRRootNode {

        @CompilationFinal(dimensions = 1) private final com.oracle.truffle.api.frame.FrameSlot[] readFrameSlots;
        @CompilationFinal(dimensions = 1) private final com.oracle.truffle.api.frame.FrameSlot[] writtenFrameSlots;

        @CompilationFinal(dimensions = 1) private final byte[] readFrameSlotsTags;
        @CompilationFinal(dimensions = 1) private final byte[] writtenFrameSlotsTags;
        private final int maxTagsLength;

        VirtualizingLoopOSRRootNode(VirtualizingLoopOSRRootNode previousRoot, OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor,
                        Class<? extends VirtualFrame> clazz) {
            super(loop, frameDescriptor, clazz);
            this.readFrameSlots = previousRoot.readFrameSlots;
            this.writtenFrameSlots = previousRoot.writtenFrameSlots;
            this.readFrameSlotsTags = previousRoot.readFrameSlotsTags;
            this.writtenFrameSlotsTags = previousRoot.writtenFrameSlotsTags;
            this.maxTagsLength = previousRoot.maxTagsLength;
        }

        VirtualizingLoopOSRRootNode(OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor,
                        Class<? extends VirtualFrame> clazz,
                        com.oracle.truffle.api.frame.FrameSlot[] readFrameSlots, com.oracle.truffle.api.frame.FrameSlot[] writtenFrameSlots) {
            super(loop, frameDescriptor, clazz);
            this.readFrameSlots = readFrameSlots;
            this.writtenFrameSlots = writtenFrameSlots;
            this.readFrameSlotsTags = new byte[readFrameSlots.length];
            this.writtenFrameSlotsTags = new byte[writtenFrameSlots.length];
            int maxIndex = -1;
            maxIndex = initializeFrameSlots(frameDescriptor, readFrameSlots, readFrameSlotsTags, maxIndex);
            maxIndex = initializeFrameSlots(frameDescriptor, writtenFrameSlots, writtenFrameSlotsTags, maxIndex);
            this.maxTagsLength = maxIndex + 1;
        }

        private static int initializeFrameSlots(FrameDescriptor frameDescriptor, com.oracle.truffle.api.frame.FrameSlot[] frameSlots, byte[] tags, int maxIndex) {
            int currentMaxIndex = maxIndex;
            for (int i = 0; i < frameSlots.length; i++) {
                com.oracle.truffle.api.frame.FrameSlot frameSlot = frameSlots[i];
                if (getFrameSlotIndex(frameSlot) > currentMaxIndex) {
                    currentMaxIndex = getFrameSlotIndex(frameSlot);
                }
                tags[i] = frameDescriptor.getFrameSlotKind(frameSlot).tag;
            }
            return currentMaxIndex;
        }

        @SuppressWarnings("deprecation")
        private static int getFrameSlotIndex(com.oracle.truffle.api.frame.FrameSlot slot) {
            return slot.getIndex();
        }

        @Override
        protected Object executeOSR(VirtualFrame originalFrame) {
            FrameWithoutBoxing loopFrame = (FrameWithoutBoxing) (originalFrame);
            FrameWithoutBoxing parentFrame = (FrameWithoutBoxing) (loopFrame.getArguments()[0]);
            executeTransfer(parentFrame, loopFrame, readFrameSlots, readFrameSlotsTags);
            try {
                RepeatingNode loopBody = loopNode.repeatingNode;
                Object status;
                while (loopBody.shouldContinue(status = loopBody.executeRepeatingWithValue(loopFrame))) {
                    if (CompilerDirectives.inInterpreter()) {
                        return loopBody.initialLoopStatus();
                    }
                    TruffleSafepoint.poll(this);
                }
                return status;
            } finally {
                executeTransfer(loopFrame, parentFrame, writtenFrameSlots, writtenFrameSlotsTags);
            }
        }

        @ExplodeLoop
        private void executeTransfer(FrameWithoutBoxing source, FrameWithoutBoxing target, com.oracle.truffle.api.frame.FrameSlot[] frameSlots, byte[] speculatedTags) {
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
                com.oracle.truffle.api.frame.FrameSlot slot = frameSlots[i];
                int index = getFrameSlotIndex(slot);

                byte speculatedTag = speculatedTags[i];
                byte currentSourceTag = currentSourceTags[index];
                if (CompilerDirectives.inInterpreter()) {
                    if (currentSourceTag == 0 && speculatedTag != 0) {
                        if (frameSlots == readFrameSlots) {
                            throw new AssertionError("Frame slot " + slot + " was never written outside the loop but virtualized as read frame slot.");
                        } else {
                            throw new AssertionError("Frame slot " + slot + " was never written in the loop but virtualized as written frame slot.");
                        }
                    }
                }

                while (true) {
                    try {
                        switch (speculatedTag) {
                            case FrameWithoutBoxing.BOOLEAN_TAG:
                                target.setBoolean(slot, source.getBoolean(slot));
                                break;
                            case FrameWithoutBoxing.BYTE_TAG:
                                target.setByte(slot, source.getByte(slot));
                                break;
                            case FrameWithoutBoxing.DOUBLE_TAG:
                                target.setDouble(slot, source.getDouble(slot));
                                break;
                            case FrameWithoutBoxing.FLOAT_TAG:
                                target.setFloat(slot, source.getFloat(slot));
                                break;
                            case FrameWithoutBoxing.INT_TAG:
                                target.setInt(slot, source.getInt(slot));
                                break;
                            case FrameWithoutBoxing.LONG_TAG:
                                target.setLong(slot, source.getLong(slot));
                                break;
                            case FrameWithoutBoxing.OBJECT_TAG:
                                target.setObject(slot, source.getObject(slot));
                                break;
                            default:
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw new AssertionError("Defined frame slot " + slot + " is illegal. Revirtualization failed. Please initialize frame slot with a FrameSlotKind.");
                        }
                    } catch (FrameSlotTypeException e) {
                        // The tag for this slot may have changed; if so, deoptimize and update it.
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        assert speculatedTag != currentSourceTag;
                        speculatedTags[i] = currentSourceTag;
                        speculatedTag = currentSourceTag;
                        continue;
                    }
                    break;
                }
            }
        }

    }

}
