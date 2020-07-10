/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
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
 * @see #createOSRLoop(RepeatingNode, int, FrameSlot[], FrameSlot[])
 */
public abstract class OptimizedOSRLoopNode extends LoopNode implements ReplaceObserver {

    @Child private RepeatingNode repeatableNode;

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

    private OptimizedOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold) {
        Objects.requireNonNull(repeatableNode);
        this.repeatableNode = repeatableNode;
        this.osrThreshold = osrThreshold;
    }

    /**
     * @param rootFrameDescriptor may be {@code null}.
     */
    protected OSRRootNode createRootNode(FrameDescriptor rootFrameDescriptor, Class<? extends VirtualFrame> clazz) {
        /*
         * Use a new frame descriptor, because the frame that this new root node creates is not
         * used.
         */
        return new OSRRootNode(this, new FrameDescriptor(), clazz);
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

    @SuppressWarnings("deprecation")
    @Override
    public void executeLoop(VirtualFrame frame) {
        execute(frame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter()) {
            try {
                Object status = repeatableNode.initialLoopStatus();
                while (repeatableNode.shouldContinue(status)) {
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
        } else {
            Object status;
            while (repeatableNode.shouldContinue((status = repeatableNode.executeRepeatingWithValue(frame)))) {
                if (CompilerDirectives.inInterpreter()) {
                    // compiled method got invalidated. We might need OSR again.
                    return execute(frame);
                }
            }
            return status;
        }
    }

    private Object profilingLoop(VirtualFrame frame) {
        int iterations = 0;
        try {
            Object status;
            while (repeatableNode.shouldContinue(status = repeatableNode.executeRepeatingWithValue(frame))) {
                // the baseLoopCount might be updated from a child loop during an iteration.
                if (++iterations + baseLoopCount > osrThreshold) {
                    compileLoop(frame);
                    // The status returned here is CONTINUE_LOOP_STATUS.
                    return status;
                }
            }
            // The status returned here is different than CONTINUE_LOOP_STATUS.
            return status;
        } finally {
            baseLoopCount += iterations;
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
        baseLoopCount += iterations;
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
        int iterations = 0;
        try {
            Object status;
            do {
                OptimizedCallTarget target = compiledOSRLoop;
                if (target == null) {
                    return repeatableNode.initialLoopStatus();
                }
                if (!target.isSubmittedForCompilation()) {
                    if (target.isValid()) {
                        return callOSR(target, frame);
                    }
                    invalidateOSRTarget(this, "OSR compilation failed or cancelled");
                    return repeatableNode.initialLoopStatus();
                }

                iterations++;

            } while (repeatableNode.shouldContinue(status = repeatableNode.executeRepeatingWithValue(frame)));
            return status;
        } finally {
            baseLoopCount += iterations;
            reportParentLoopCount(iterations);
        }
    }

    private Object callOSR(OptimizedCallTarget target, VirtualFrame frame) {
        Object status = target.callOSR(frame);
        if (!repeatableNode.initialLoopStatus().equals(status)) {
            return status;
        } else {
            if (!target.isValid()) {
                invalidateOSRTarget(this, "OSR compilation got invalidated");
            }
            return status;
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

    private OSRRootNode createRootNodeImpl(RootNode root, Class<? extends VirtualFrame> frameClass) {
        return createRootNode(root == null ? null : root.getFrameDescriptor(), frameClass);
    }

    private OptimizedCallTarget compileImpl(VirtualFrame frame) {
        RootNode root = getRootNode();
        if (speculationLog == null) {
            speculationLog = GraalTruffleRuntime.getRuntime().createSpeculationLog();
        }
        OptimizedCallTarget osrTarget = GraalTruffleRuntime.getRuntime().createOSRCallTarget(createRootNodeImpl(root, frame.getClass()));
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
                    compiledOSRLoop = null;
                    target.nodeReplaced(oldNode, newNode, reason);
                }
            }
        });
    }

    private void invalidateOSRTarget(Object source, CharSequence reason) {
        atomic(new Runnable() {
            @Override
            public void run() {
                OptimizedCallTarget target = compiledOSRLoop;
                if (target != null) {
                    compiledOSRLoop = null;
                    target.invalidate(source, reason);
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
        OptionValues engineOptions = GraalTVMCI.getEngineData(null).engineOptions;

        // using static methods with LoopNode return type ensures
        // that only one loop node implementation gets loaded.
        if (TruffleRuntimeOptions.getPolyglotOptionValue(engineOptions, PolyglotCompilerOptions.OSR)) {
            return createDefault(repeat, engineOptions);
        } else {
            return OptimizedLoopNode.create(repeat);
        }
    }

    private static LoopNode createDefault(RepeatingNode repeatableNode, OptionValues options) {
        return new OptimizedDefaultOSRLoopNode(repeatableNode,
                        TruffleRuntimeOptions.getPolyglotOptionValue(options, PolyglotCompilerOptions.OSRCompilationThreshold));
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
     */
    public static OptimizedOSRLoopNode createOSRLoop(RepeatingNode repeating, int osrThreshold, FrameSlot[] readFrameSlots, FrameSlot[] writtenFrameSlots) {
        if ((readFrameSlots == null) != (writtenFrameSlots == null)) {
            throw new IllegalArgumentException("If either readFrameSlots or writtenFrameSlots is set both must be provided.");
        }
        return new OptimizedVirtualizingOSRLoopNode(repeating, osrThreshold, readFrameSlots, writtenFrameSlots);
    }

    /**
     * @deprecated Use
     *             {@link OptimizedOSRLoopNode#createOSRLoop(RepeatingNode, int, FrameSlot[], FrameSlot[])}
     *             instead.
     */
    @SuppressWarnings("unused")
    @Deprecated
    public static OptimizedOSRLoopNode createOSRLoop(RepeatingNode repeating, int osrThreshold, int invalidationBackoff, FrameSlot[] readFrameSlots, FrameSlot[] writtenFrameSlots) {
        return createOSRLoop(repeating, osrThreshold, readFrameSlots, writtenFrameSlots);
    }

    /**
     * Used by default in guest languages.
     */
    private static final class OptimizedDefaultOSRLoopNode extends OptimizedOSRLoopNode {

        OptimizedDefaultOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold) {
            super(repeatableNode, osrThreshold);
        }

    }

    /**
     * Used in guest languages with Graal runtime access that require more revirtualization of local
     * variables and more configuration options.
     */
    private static final class OptimizedVirtualizingOSRLoopNode extends OptimizedOSRLoopNode {

        @CompilationFinal(dimensions = 1) private final FrameSlot[] readFrameSlots;
        @CompilationFinal(dimensions = 1) private final FrameSlot[] writtenFrameSlots;

        private VirtualizingOSRRootNode previousRoot;

        private OptimizedVirtualizingOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold, FrameSlot[] readFrameSlots, FrameSlot[] writtenFrameSlots) {
            super(repeatableNode, osrThreshold);
            this.readFrameSlots = readFrameSlots;
            this.writtenFrameSlots = writtenFrameSlots;
        }

        @Override
        protected OSRRootNode createRootNode(FrameDescriptor rootFrameDescriptor, Class<? extends VirtualFrame> clazz) {
            if (readFrameSlots == null || writtenFrameSlots == null) {
                return super.createRootNode(rootFrameDescriptor, clazz);
            } else {
                FrameDescriptor frameDescriptor = rootFrameDescriptor == null ? new FrameDescriptor() : rootFrameDescriptor;
                if (previousRoot == null) {
                    previousRoot = new VirtualizingOSRRootNode(this, frameDescriptor, clazz, readFrameSlots, writtenFrameSlots);
                } else {
                    // we want to reuse speculations from a previous compilation so no rewrite loops
                    // occur.
                    previousRoot = new VirtualizingOSRRootNode(previousRoot, this, frameDescriptor, clazz);
                }
                return previousRoot;
            }
        }

    }

    public static class OSRRootNode extends RootNode {

        protected final Class<? extends VirtualFrame> clazz;

        /** Not adopted by the OSRRootNode; belongs to another RootNode. */
        @Child protected OptimizedOSRLoopNode loopNode;

        OSRRootNode(OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor, Class<? extends VirtualFrame> clazz) {
            super(null, frameDescriptor);
            this.loopNode = loop;
            this.clazz = clazz;
        }

        @Override
        public SourceSection getSourceSection() {
            return loopNode.getSourceSection();
        }

        public static Object callProxy(OSRRootNode target, VirtualFrame frame) {
            return target.executeImpl(frame);
        }

        protected Object executeImpl(VirtualFrame frame) {
            VirtualFrame parentFrame = clazz.cast(frame.getArguments()[0]);
            Object status;
            while (loopNode.repeatableNode.shouldContinue(status = loopNode.getRepeatingNode().executeRepeatingWithValue(parentFrame))) {
                if (CompilerDirectives.inInterpreter()) {
                    return loopNode.repeatableNode.initialLoopStatus();
                }
            }
            return status;
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

        @CompilationFinal(dimensions = 1) private final FrameSlot[] readFrameSlots;
        @CompilationFinal(dimensions = 1) private final FrameSlot[] writtenFrameSlots;

        @CompilationFinal(dimensions = 1) private final byte[] readFrameSlotsTags;
        @CompilationFinal(dimensions = 1) private final byte[] writtenFrameSlotsTags;
        private final int maxTagsLength;

        VirtualizingOSRRootNode(VirtualizingOSRRootNode previousRoot, OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor,
                        Class<? extends VirtualFrame> clazz) {
            super(loop, frameDescriptor, clazz);
            this.readFrameSlots = previousRoot.readFrameSlots;
            this.writtenFrameSlots = previousRoot.writtenFrameSlots;
            this.readFrameSlotsTags = previousRoot.readFrameSlotsTags;
            this.writtenFrameSlotsTags = previousRoot.writtenFrameSlotsTags;
            this.maxTagsLength = previousRoot.maxTagsLength;
        }

        VirtualizingOSRRootNode(OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor,
                        Class<? extends VirtualFrame> clazz,
                        FrameSlot[] readFrameSlots, FrameSlot[] writtenFrameSlots) {
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

        private static int initializeFrameSlots(FrameDescriptor frameDescriptor, FrameSlot[] frameSlots, byte[] tags, int maxIndex) {
            int currentMaxIndex = maxIndex;
            for (int i = 0; i < frameSlots.length; i++) {
                FrameSlot frameSlot = frameSlots[i];
                if (getFrameSlotIndex(frameSlot) > currentMaxIndex) {
                    currentMaxIndex = getFrameSlotIndex(frameSlot);
                }
                tags[i] = frameDescriptor.getFrameSlotKind(frameSlot).tag;
            }
            return currentMaxIndex;
        }

        @SuppressWarnings("deprecation")
        private static int getFrameSlotIndex(FrameSlot slot) {
            return slot.getIndex();
        }

        @Override
        protected Object executeImpl(VirtualFrame originalFrame) {
            FrameWithoutBoxing loopFrame = (FrameWithoutBoxing) (originalFrame);
            FrameWithoutBoxing parentFrame = (FrameWithoutBoxing) (loopFrame.getArguments()[0]);
            executeTransfer(parentFrame, loopFrame, readFrameSlots, readFrameSlotsTags);
            try {
                Object status;
                while (loopNode.repeatableNode.shouldContinue(status = loopNode.getRepeatingNode().executeRepeatingWithValue(loopFrame))) {
                    if (CompilerDirectives.inInterpreter()) {
                        return loopNode.repeatableNode.initialLoopStatus();
                    }
                }
                return status;
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
