/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
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
            speculationLog = OptimizedTruffleRuntime.getRuntime().createSpeculationLog();
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
        EngineData engine = OptimizedTVMCI.getEngineData(null);
        OptionValues engineOptions = engine.engineOptions;

        // using static methods with LoopNode return type ensures
        // that only one loop node implementation gets loaded.
        if (engine.compilation && engineOptions.get(OptimizedRuntimeOptions.OSR)) {
            return createDefault(repeat, engineOptions);
        } else {
            return OptimizedLoopNode.create(repeat);
        }
    }

    private static LoopNode createDefault(RepeatingNode repeatableNode, OptionValues options) {
        return new OptimizedDefaultOSRLoopNode(repeatableNode,
                        options.get(OptimizedRuntimeOptions.OSRCompilationThreshold),
                        options.get(OptimizedRuntimeOptions.FirstTierBackedgeCounts));
    }

    /**
     * Used by default in guest languages.
     */
    private static final class OptimizedDefaultOSRLoopNode extends OptimizedOSRLoopNode {

        OptimizedDefaultOSRLoopNode(RepeatingNode repeatableNode, int osrThreshold, boolean firstTierBackedgeCounts) {
            super(repeatableNode, osrThreshold, firstTierBackedgeCounts);
        }

    }

    abstract static class AbstractLoopOSRRootNode extends BaseOSRRootNode {

        protected final Class<? extends VirtualFrame> clazz;

        AbstractLoopOSRRootNode(OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor, Class<? extends VirtualFrame> clazz) {
            super(null, frameDescriptor, loop);
            this.clazz = clazz;
        }

        @Override
        public SourceSection getSourceSection() {
            return getLoopNode().getSourceSection();
        }

        OptimizedOSRLoopNode getLoopNode() {
            return (OptimizedOSRLoopNode) loopNode;
        }

        @Override
        protected Object executeOSR(VirtualFrame frame) {
            VirtualFrame parentFrame = clazz.cast(frame.getArguments()[0]);
            OptimizedOSRLoopNode loop = getLoopNode();
            RepeatingNode loopBody = loop.repeatingNode;
            Object status;
            long iterationsCompleted = 0;
            try {
                while (loop.inject(loopBody.shouldContinue(status = loopBody.executeRepeatingWithValue(parentFrame)))) {
                    if (CompilerDirectives.hasNextTier()) {
                        iterationsCompleted++;
                    }
                    if (CompilerDirectives.inInterpreter()) {
                        return loopBody.initialLoopStatus();
                    }
                    TruffleSafepoint.poll(loop);
                }
            } finally {
                if (loop.firstTierBackedgeCounts && iterationsCompleted > 1) {
                    LoopNode.reportLoopCount(this, toIntOrMaxInt(iterationsCompleted));
                }
            }
            return status;
        }

        @Override
        public final boolean isCloningAllowed() {
            return false;
        }

        @Override
        public final String toString() {
            return getLoopNode().getRepeatingNode().toString() + "<OSR>";
        }
    }

    static final class LoopOSRRootNode extends AbstractLoopOSRRootNode {
        LoopOSRRootNode(OptimizedOSRLoopNode loop, FrameDescriptor frameDescriptor, Class<? extends VirtualFrame> clazz) {
            super(loop, frameDescriptor, clazz);
        }
    }

}
