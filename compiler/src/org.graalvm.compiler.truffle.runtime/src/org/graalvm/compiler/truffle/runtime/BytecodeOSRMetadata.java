/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FrameWithoutBoxing;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

/**
 * Represents the metadata required to perform OSR compilation on Graal. An instance of this class
 * is stored in the metadata field of a {@link BytecodeOSRNode}.
 *
 * <p>
 * Performance note: We do not require the metadata field to be {@code volatile}. As long as the
 * field is initialized under double-checked locking (as is done in
 * {@link GraalRuntimeSupport#pollBytecodeOSRBackEdge}, all threads will observe the same metadata
 * instance. The JMM guarantees that the instance's final fields will be safely initialized before
 * it is published; the non-final + non-volatile fields (e.g., the back edge counter) may not be,
 * but we tolerate this inaccuracy in order to avoid volatile accesses in the hot path.
 */
public final class BytecodeOSRMetadata {
    // Marker object to indicate that OSR is disabled.
    public static final BytecodeOSRMetadata DISABLED = new BytecodeOSRMetadata(null, Integer.MAX_VALUE, 0);
    // Must be a power of 2 (polling uses bit masks). OSRCompilationThreshold is a multiple of this
    // interval.
    public static final int OSR_POLL_INTERVAL = 1024;

    private final BytecodeOSRNode osrNode;

    /*
     * When set in #currentlyCompiling, indicates a callTarget is in the process of being submitted
     * for compilation.
     */
    private static final Object PLACEHOLDER = new Object();
    /*
     * When set in #currentlyCompiling, prevents any further submission of compilations.
     */
    private static final Object DISABLE = new Object();

    /*
     * Stores the latest call target submitted for compilation, or #DISABLE if compilation is
     * disabled for this method. All writes should be made through compareAndSet, the exception
     * being setting the #DISABLE value.
     */
    private final AtomicReference<Object> currentlyCompiling = new AtomicReference<>();
    private final ReAttemptsCounter compilationReAttempts = new ReAttemptsCounter();

    private OptimizedCallTarget getCurrentlyCompiling() {
        Object value = currentlyCompiling.get();
        if (value instanceof OptimizedCallTarget) {
            return (OptimizedCallTarget) value;
        }
        return null;
    }

    // Lazily initialized state. Most nodes with back-edges will not trigger compilation, so we
    // defer initialization of some fields until they're actually used.
    static final class LazyState //
                    // Support for deprecated frame transfer: GR-38296
                    extends FinalCompilationListMap {

        private final Map<Integer, OptimizedCallTarget> compilationMap;
        @CompilationFinal private FrameDescriptor frameDescriptor;

        LazyState() {
            this.compilationMap = new ConcurrentHashMap<>();
            // We set these fields in updateFrameSlots using a concrete frame just before
            // compilation, when the frame is (hopefully) stable.
            this.frameDescriptor = null;
        }

        private void push(int target, OptimizedCallTarget callTarget, OsrEntryDescription entry) {
            compilationMap.put(target, callTarget);
            // Support for deprecated frame transfer: GR-38296
            put(target, entry);
        }

        private void doClear() {
            compilationMap.clear();
            // We might be disabling OSR while doing an OSR call. Keep around the data necessary to
            // transfer from and restore the parent frame.
        }
    }

    @CompilationFinal private volatile LazyState lazyState;

    LazyState getLazyState() {
        LazyState currentLazyState = lazyState;
        if (currentLazyState == null) {
            return getLazyStateBoundary();
        }
        return currentLazyState;
    }

    @TruffleBoundary
    private LazyState getLazyStateBoundary() {
        return ((Node) osrNode).atomic(() -> {
            LazyState lockedLazyState = lazyState;
            if (lockedLazyState == null) {
                lockedLazyState = lazyState = new LazyState();
            }
            return lockedLazyState;
        });
    }

    private void updateFrameSlots(FrameWithoutBoxing frame, OsrEntryDescription osrEntry) {
        CompilerAsserts.neverPartOfCompilation();
        LazyState state = getLazyState();
        ((Node) osrNode).atomic(() -> {
            if (state.frameDescriptor == null) {
                FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
                state.frameDescriptor = frameDescriptor;
            }
            if (osrEntry != null) {
                // The concrete frame can have different tags from the descriptor (e.g., when a slot
                // is uninitialized), so we use the frame's tags to avoid deoptimizing during
                // transfer.
                // The tags array lazily grows when new slots are initialized, so it could be
                // smaller than the number of slots. Copy it into an array with the correct size.
                osrEntry.indexedFrameTags = new byte[state.frameDescriptor.getNumberOfSlots()];
                for (int i = 0; i < osrEntry.indexedFrameTags.length; i++) {
                    osrEntry.indexedFrameTags[i] = frame.getTag(i);
                }
            }
        });
    }

    private final int osrThreshold;
    private final int maxCompilationReAttempts;
    private int backEdgeCount;
    private boolean isDisabled = false;

    BytecodeOSRMetadata(BytecodeOSRNode osrNode, int osrThreshold, int maxCompilationReAttempts) {
        this.osrNode = osrNode;
        this.osrThreshold = osrThreshold;
        this.maxCompilationReAttempts = maxCompilationReAttempts;
        this.backEdgeCount = 0;
        if (osrNode == null) {
            isDisabled = true;
        }
    }

    Object tryOSR(int target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame) {
        if (isDisabled()) {
            return null;
        }
        LazyState state = getLazyState();
        assert state.frameDescriptor == null || state.frameDescriptor == parentFrame.getFrameDescriptor();
        OptimizedCallTarget callTarget = state.compilationMap.get(target);
        if (callTarget == null) {
            callTarget = ((Node) osrNode).atomic(() -> {
                OptimizedCallTarget lockedTarget = state.compilationMap.get(target);
                if (lockedTarget == null) {
                    OsrEntryDescription entryDescription = new OsrEntryDescription();
                    lockedTarget = createOSRTarget(target, interpreterState, parentFrame.getFrameDescriptor(), entryDescription);
                    state.push(target, lockedTarget, entryDescription);
                    requestOSRCompilation(target, lockedTarget, (FrameWithoutBoxing) parentFrame);
                }
                return lockedTarget;
            });
        }

        // Case 1: code is still being compiled
        if (callTarget.isCompiling()) {
            return null;
        }
        // Case 2: code is compiled and valid
        if (callTarget.isValid()) {
            /*
             * Note: though unlikely, it is still possible to call into an OSR compiled method at
             * this point, even if disabled. This is OK, as we leave enough information around to
             * work with.
             */
            if (beforeTransfer != null) {
                beforeTransfer.run();
            }
            return callTarget.callOSR(osrNode.storeParentFrameInArguments(parentFrame));
        }
        // Case 3: code is invalid; either give up or reschedule compilation
        if (callTarget.isCompilationFailed()) {
            markOSRDisabled();
        } else {
            requestOSRCompilation(target, callTarget, (FrameWithoutBoxing) parentFrame);
        }
        return null;
    }

    /**
     * Increment back edge count and return whether compilation should be polled.
     * <p>
     * When the OSR threshold is reached, this method will return true after every OSR_POLL_INTERVAL
     * back-edges. This method is thread-safe, but could under-count.
     */
    public boolean incrementAndPoll() {
        int newBackEdgeCount = ++backEdgeCount; // Omit overflow check; OSR should trigger long
                                                // before overflow happens
        return (newBackEdgeCount >= osrThreshold && (newBackEdgeCount & (OSR_POLL_INTERVAL - 1)) == 0);
    }

    /**
     * Returns whether OSR compilation is disabled for the current unit.
     */
    public boolean isDisabled() {
        return isDisabled;
    }

    /**
     * Creates an OSR call target at the given dispatch target and requests compilation. The node's
     * AST lock should be held when this is invoked.
     */
    private OptimizedCallTarget createOSRTarget(int target, Object interpreterState, FrameDescriptor frameDescriptor, Object frameEntryState) {
        TruffleLanguage<?> language = GraalRuntimeAccessor.NODES.getLanguage(((Node) osrNode).getRootNode());
        return (OptimizedCallTarget) new BytecodeOSRRootNode(language, frameDescriptor, osrNode, target, interpreterState, frameEntryState).getCallTarget();

    }

    private void requestOSRCompilation(int target, OptimizedCallTarget callTarget, FrameWithoutBoxing frame) {
        OptimizedCallTarget previousCompilation = getCurrentlyCompiling();
        if (previousCompilation != null && previousCompilation.isSubmittedForCompilation()) {
            // Completed compilation of the previously scheduled compilation. Clear the reference.
            currentlyCompiling.compareAndSet(previousCompilation, null);
        }
        if (!currentlyCompiling.compareAndSet(null, PLACEHOLDER)) {
            // Prevent multiple OSR compilations of the same method at different entry points.
            return;
        }
        compilationReAttempts.inc(target);
        if (compilationReAttempts.total() >= maxCompilationReAttempts) {
            /*
             * Methods that gets OSR re-compiled too often bailout of OSR compilation. This has two
             * main advantages:
             * 
             * - Deopt loops do not clog the compiler queue indefinitely
             * 
             * - Mitigates possibilities of Stack Overflows arising from to deopt loops in OSR.
             */
            markOSRDisabled();
            if (callTarget.getOptionValue(PolyglotCompilerOptions.ThrowOnMaxOSRCompilationReAttemptsReached)) {
                throw new AssertionError("Max OSR compilation re-attempts reached for " + osrNode);
            }
            return;
        }
        try {
            osrNode.prepareOSR(target);
            updateFrameSlots(frame, getEntryCacheFromCallTarget(callTarget));
            callTarget.compile(true);
        } catch (Throwable e) {
            markOSRDisabled();
            throw e;
        }
        if (callTarget.isCompilationFailed()) {
            markOSRDisabled();
            return;
        }
        boolean submitted = currentlyCompiling.compareAndSet(PLACEHOLDER, previousCompilation);
        assert submitted || currentlyCompiling.get() == DISABLE;
    }

    private static OsrEntryDescription getEntryCacheFromCallTarget(OptimizedCallTarget callTarget) {
        assert callTarget.getRootNode() instanceof BytecodeOSRRootNode;
        return (OsrEntryDescription) ((BytecodeOSRRootNode) callTarget.getRootNode()).getEntryTagsCache();
    }

    /**
     * Transfer state from {@code source} to {@code target}. Can be used to transfer state into an
     * OSR frame.
     */
    public void transferFrame(FrameWithoutBoxing source, FrameWithoutBoxing target, int bytecodeTarget, Object targetMetadata) {
        LazyState state = getLazyState();
        CompilerAsserts.partialEvaluationConstant(state);
        // The frames should use the same descriptor.
        validateDescriptors(source, target, state);

        if (targetMetadata == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Transferring frame for OSR from an uninitialized bytecode target.");
        }
        if (!(targetMetadata instanceof OsrEntryDescription)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Wrong usage of targetMetadata during OSR frame transfer.");
        }
        assert targetMetadata == state.get(bytecodeTarget); // GR-38296

        OsrEntryDescription description = (OsrEntryDescription) targetMetadata;
        CompilerAsserts.partialEvaluationConstant(description);

        // Transfer indexed frame slots
        transferLoop(description.indexedFrameTags.length, source, target, description.indexedFrameTags);
        // transfer auxiliary slots
        transferAuxiliarySlots(source, target, state);
    }

    /**
     * Transfer state from {@code source} to {@code target}. Can be used to transfer state from an
     * OSR frame to a parent frame. Overall less efficient than its
     * {@link #transferFrame(FrameWithoutBoxing, FrameWithoutBoxing, int, Object) counterpart},
     * mainly due to not being able to speculate on the source tags: While entering bytecode OSR is
     * done through specific entry points (likely back edges), returning could be done from anywhere
     * within a method body (through regular returns, or exception thrown).
     *
     * While we could theoretically have the same mechanism as on entries (caching encountered
     * return state), we could not efficiently be able to retrieve from the cache (as we do not get
     * the equivalent of the {@code int osrBytecodeTarget} for returns), even ignoring the potential
     * memory cost of such a cache.
     *
     * Therefore, we are doing a best-effort of copying over source to target, reading from the
     * actual frames rather than a cache: If the tag cannot be constant-folded at this point, we get
     * a switch in compiled code. Since we are at a boundary back to interpreted code, this cost
     * should not be too high.
     */
    public void restoreFrame(FrameWithoutBoxing source, FrameWithoutBoxing target) {
        LazyState state = getLazyState();
        CompilerAsserts.partialEvaluationConstant(state);
        // The frames should use the same descriptor.
        validateDescriptors(source, target, state);

        // We can't reasonably have constant expected tags for parent frame restoration.

        // transfer indexed frame slots
        transferLoop(state.frameDescriptor.getNumberOfSlots(), source, target, null);
        // transfer auxiliary slots
        transferAuxiliarySlots(source, target, state);
    }

    private static void validateDescriptors(FrameWithoutBoxing source, FrameWithoutBoxing target, LazyState state) {
        if (source.getFrameDescriptor() != state.frameDescriptor) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Source frame descriptor is different from the descriptor used for compilation.");
        } else if (target.getFrameDescriptor() != state.frameDescriptor) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Target frame descriptor is different from the descriptor used for compilation.");
        }
    }

    /**
     * Common transfer loop for copying over legacy frame slot or indexed slots from a source frame
     * to a target frame.
     *
     * @param length Number of slots to transfer. Must be
     *            {@link CompilerDirectives#isCompilationConstant(Object) compilation constant}
     * @param source The frame to copy from
     * @param target The frame to copy to
     * @param expectedTags The array of tags the source is expected to have, or null if no previous
     *            knowledge of tags was collected. If compilation constant, frame slot accesses may
     *            be simplified.
     */
    @ExplodeLoop
    private static void transferLoop(
                    int length,
                    FrameWithoutBoxing source, FrameWithoutBoxing target,
                    byte[] expectedTags) {
        int i = 0;
        while (i < length) {
            byte actualTag = source.getTag(i);
            byte expectedTag = expectedTags == null ? actualTag : expectedTags[i];

            boolean incompatibleTags = expectedTag != actualTag;
            if (incompatibleTags) {
                // The tag for this slot may have changed; if so, deoptimize and update it.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedTags[i] = actualTag;
                continue; // try again with updated tags.
            }

            transferIndexedFrameSlot(source, target, i, expectedTag);
            i++;
        }
    }

    @ExplodeLoop
    private static void transferAuxiliarySlots(FrameWithoutBoxing source, FrameWithoutBoxing target, LazyState state) {
        for (int auxSlot = 0; auxSlot < state.frameDescriptor.getNumberOfAuxiliarySlots(); auxSlot++) {
            target.setAuxiliarySlot(auxSlot, source.getAuxiliarySlot(auxSlot));
        }
    }

    private static void transferIndexedFrameSlot(FrameWithoutBoxing source, FrameWithoutBoxing target, int slot, byte expectedTag) {
        try {
            switch (expectedTag) {
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
                case FrameWithoutBoxing.STATIC_TAG:
                    // Since we do not know the actual value of the slot at this point, we
                    // copy both.
                    target.setObjectStatic(slot, source.getObjectStatic(slot));
                    target.setLongStatic(slot, source.getLongStatic(slot));
                    break;
                case FrameWithoutBoxing.ILLEGAL_TAG:
                    target.clear(slot);
                    break;
            }
        } catch (FrameSlotTypeException e) {
            // Should be impossible
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AssertionError("Cannot transfer source frame.");
        }
    }

    void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        LazyState state = lazyState;
        if (state != null) {
            ((Node) osrNode).atomic(() -> {
                for (OptimizedCallTarget callTarget : state.compilationMap.values()) {
                    if (callTarget.isCompilationFailed()) {
                        markOSRDisabled();
                    }
                    callTarget.nodeReplaced(oldNode, newNode, reason);
                }
            });
        }
    }

    private void markOSRDisabled() {
        ((Node) osrNode).atomic(() -> {
            // Prevent new compilations from scheduling ASAP
            currentlyCompiling.set(DISABLE);

            isDisabled = true;
            LazyState state = lazyState;
            if (state != null) {
                state.doClear();
            }
            compilationReAttempts.clear();
        });
    }

    // for testing
    public Map<Integer, OptimizedCallTarget> getOSRCompilations() {
        return getLazyState().compilationMap;
    }

    public int getBackEdgeCount() {
        return backEdgeCount;
    }

    /**
     * Counts re-attempts at compilations. Represents an approximation of the number of
     * deoptimizations in OSR a given method goes through.
     */
    private static final class ReAttemptsCounter {
        private final Set<Integer> knownTargets = new HashSet<>(1);
        private int total = 0;

        public void inc(int target) {
            if (!knownTargets.add(target)) {
                total++;
            }
        }

        public int total() {
            return total;
        }

        public void clear() {
            knownTargets.clear();
        }
    }

    /**
     * Describes the observed state of the Frame on an OSR entry point.
     */
    static final class OsrEntryDescription {
        @CompilationFinal(dimensions = 1) private byte[] indexedFrameTags;
    }

    // Support for deprecated frame transfer: GR-38296
    private abstract static class FinalCompilationListMap {
        private static final class Cell {
            final Cell next;
            final int target;
            final OsrEntryDescription entry;

            Cell(int target, OsrEntryDescription entry, Cell next) {
                this.next = next;
                this.target = target;
                this.entry = entry;
            }
        }

        @CompilationFinal //
        volatile Cell head = null;

        @ExplodeLoop
        public final OsrEntryDescription get(int target) {
            Cell cur = head;
            while (cur != null) {
                if (cur.target == target) {
                    return cur.entry;
                }
                cur = cur.next;
            }
            return null;
        }

        public final void put(int target, OsrEntryDescription value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                assert get(target) == null;
                head = new Cell(target, value, head);
            }
        }

        public final void clear() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                head = null;
            }
        }
    }
}
