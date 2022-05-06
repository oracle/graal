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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
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
@SuppressWarnings("deprecation")
public final class BytecodeOSRMetadata {
    // Marker object to indicate that OSR is disabled.
    public static final BytecodeOSRMetadata DISABLED = new BytecodeOSRMetadata(null, Integer.MAX_VALUE);
    // Must be a power of 2 (polling uses bit masks). OSRCompilationThreshold is a multiple of this
    // interval.
    public static final int OSR_POLL_INTERVAL = 1024;

    private final BytecodeOSRNode osrNode;

    // Lazily initialized state. Most nodes with back-edges will not trigger compilation, so we
    // defer initialization of some fields until they're actually used.
    static final class LazyState //
                    // Support for deprecated frame transfer: GR-38296
                    extends FinalCompilationListMap {

        private final Map<Integer, OptimizedCallTarget> compilationMap;
        @CompilationFinal private FrameDescriptor frameDescriptor;
        @CompilationFinal private Assumption frameVersion;
        @CompilationFinal(dimensions = 1) private com.oracle.truffle.api.frame.FrameSlot[] frameSlots;

        LazyState() {
            this.compilationMap = new ConcurrentHashMap<>();
            // We set these fields in updateFrameSlots using a concrete frame just before
            // compilation, when the frame is (hopefully) stable.
            this.frameDescriptor = null;
            this.frameVersion = null;
            this.frameSlots = null;
        }

        private void push(int target, OptimizedCallTarget callTarget, OsrEntryDescription entry) {
            compilationMap.put(target, callTarget);
            // Support for deprecated frame transfer: GR-38296
            put(target, entry);
        }

        private void doClear() {
            compilationMap.clear();
            // Support for deprecated frame transfer: GR-38296
            clear();
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
            if (!Assumption.isValidAssumption(state.frameVersion)) {
                FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
                state.frameDescriptor = frameDescriptor;
                // If we get the frame slots before the assumption, the slots may be updated in
                // between, and we might obtain the new (valid) assumption, despite our slots
                // actually being stale. Get the assumption first to avoid this race.
                state.frameVersion = frameDescriptor.getVersion();
                state.frameSlots = frameDescriptor.getSlots().toArray(new com.oracle.truffle.api.frame.FrameSlot[0]);
            }
            if (osrEntry != null) {
                // The concrete frame can have different tags from the descriptor (e.g., when a slot
                // is uninitialized), so we use the frame's tags to avoid deoptimizing during
                // transfer.
                byte[] tags = frame.getTags();
                // The tags array lazily grows when new slots are initialized, so it could be
                // smaller than the number of slots. Copy it into an array with the correct size.
                osrEntry.frameTags = Arrays.copyOf(tags, state.frameSlots.length);
                osrEntry.indexedFrameTags = new byte[state.frameDescriptor.getNumberOfSlots()];
                for (int i = 0; i < osrEntry.indexedFrameTags.length; i++) {
                    osrEntry.indexedFrameTags[i] = frame.getTag(i);
                }
            }
        });
    }

    private final int osrThreshold;
    private int backEdgeCount;

    BytecodeOSRMetadata(BytecodeOSRNode osrNode, int osrThreshold) {
        this.osrNode = osrNode;
        this.osrThreshold = osrThreshold;
        this.backEdgeCount = 0;
    }

    Object tryOSR(int target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame) {
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
            if (beforeTransfer != null) {
                beforeTransfer.run();
            }
            // Note: We pass the parent frame as a parameter, so the original arguments are not
            // preserved. In the interface, we call the OSR frame arguments undefined.
            return callTarget.callOSR(parentFrame);
        }
        // Case 3: code is invalid; either give up or reschedule compilation
        if (callTarget.isCompilationFailed()) {
            markCompilationFailed();
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
     * Creates an OSR call target at the given dispatch target and requests compilation. The node's
     * AST lock should be held when this is invoked.
     */
    private OptimizedCallTarget createOSRTarget(int target, Object interpreterState, FrameDescriptor frameDescriptor, Object frameEntryState) {
        TruffleLanguage<?> language = GraalRuntimeAccessor.NODES.getLanguage(((Node) osrNode).getRootNode());
        return (OptimizedCallTarget) new BytecodeOSRRootNode(language, frameDescriptor, osrNode, target, interpreterState, frameEntryState).getCallTarget();

    }

    private void requestOSRCompilation(int target, OptimizedCallTarget callTarget, FrameWithoutBoxing frame) {
        osrNode.prepareOSR(target);
        updateFrameSlots(frame, getEntryCacheFromCallTarget(callTarget));
        callTarget.compile(true);
        if (callTarget.isCompilationFailed()) {
            markCompilationFailed();
        }
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

        // The frame version could have changed; if so, deoptimize and update the slots+tags.
        if (!state.frameVersion.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateFrameSlots(source, description);
        }

        // Transfer legacy frame slots
        transferLoop(state.frameSlots.length, source, target, description.frameTags, state.frameSlots, FrameSlotTransfer.legacySlotTransfer);
        // Transfer indexed frame slots
        transferLoop(description.indexedFrameTags.length, source, target, description.indexedFrameTags, null, FrameSlotTransfer.indexedTransfer);
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

        // The frame version could have changed; if so, deoptimize.
        if (!state.frameVersion.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateFrameSlots(source, null);
        }

        // transfer legacy frame slots
        int length = state.frameSlots.length;
        if (length != source.getTags().length) {
            // A legacy slot has been added while we were OSR-executing. Invalidate so next
            // compilation may have constant for loop explosion.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            length = source.getTags().length;
        }

        transferLoop(length, source, target, null, state.frameSlots, FrameSlotTransfer.legacySlotTransfer);
        // transfer indexed frame slots
        transferLoop(state.frameDescriptor.getNumberOfSlots(), source, target, null, null, FrameSlotTransfer.indexedTransfer);
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
     * @param frameSlotArray An array of legacy frame slots, if applicable, or null.
     * @param transfer Either {@link FrameSlotTransfer#legacySlotTransfer} or
     *            {@link FrameSlotTransfer#indexedTransfer}
     */
    @ExplodeLoop
    private static void transferLoop(
                    int length,
                    FrameWithoutBoxing source, FrameWithoutBoxing target,
                    byte[] expectedTags,
                    FrameSlot[] frameSlotArray, FrameSlotTransfer transfer) {
        int i = 0;
        while (i < length) {
            byte actualTag = transfer.getTag(source, i, frameSlotArray);
            byte expectedTag = expectedTags == null ? actualTag : expectedTags[i];

            boolean incompatibleTags = expectedTag != actualTag;
            if (incompatibleTags) {
                // The tag for this slot may have changed; if so, deoptimize and update it.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedTags[i] = actualTag;
                continue; // try again with updated tags.
            }

            transfer.transfer(source, target, i, expectedTag, frameSlotArray);
            i++;
        }
    }

    @ExplodeLoop
    private static void transferAuxiliarySlots(FrameWithoutBoxing source, FrameWithoutBoxing target, LazyState state) {
        for (int auxSlot = 0; auxSlot < state.frameDescriptor.getNumberOfAuxiliarySlots(); auxSlot++) {
            target.setAuxiliarySlot(auxSlot, source.getAuxiliarySlot(auxSlot));
        }
    }

    private abstract static class FrameSlotTransfer {
        private static final FrameSlotTransfer indexedTransfer = new FrameSlotTransfer() {
            @Override
            public void transfer(FrameWithoutBoxing source, FrameWithoutBoxing target, int slot, byte expectedTag, com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray) {
                transferIndexedFrameSlot(source, target, slot, expectedTag);
            }

            @Override
            public byte getTag(FrameWithoutBoxing frame, int slot, FrameSlot[] frameSlotArray) {
                return frame.getTag(slot);
            }
        };

        private static final FrameSlotTransfer legacySlotTransfer = new FrameSlotTransfer() {
            @Override
            public void transfer(FrameWithoutBoxing source, FrameWithoutBoxing target, int slot, byte expectedTag, com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray) {
                com.oracle.truffle.api.frame.FrameSlot frameSlot = frameSlotArray[slot];
                transferFrameSlot(source, target, frameSlot, expectedTag);
            }

            @Override
            public byte getTag(FrameWithoutBoxing frame, int slot, FrameSlot[] frameSlotArray) {
                com.oracle.truffle.api.frame.FrameSlot frameSlot = frameSlotArray[slot];
                return frame.getTag(frameSlot);
            }
        };

        public abstract void transfer(FrameWithoutBoxing source, FrameWithoutBoxing target, int slot, byte expectedTag, com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray);

        public abstract byte getTag(FrameWithoutBoxing frame, int slot, com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray);
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

    private static void transferFrameSlot(FrameWithoutBoxing source, FrameWithoutBoxing target, com.oracle.truffle.api.frame.FrameSlot slot, byte expectedTag) {
        assert expectedTag != FrameWithoutBoxing.STATIC_TAG;
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
                        markCompilationFailed();
                    }
                    callTarget.nodeReplaced(oldNode, newNode, reason);
                }
            });
        }
    }

    private void markCompilationFailed() {
        ((Node) osrNode).atomic(() -> {
            osrNode.setOSRMetadata(DISABLED);
            LazyState state = lazyState;
            if (state != null) {
                state.doClear();
            }
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
     * Describes the observed state of the Frame on an OSR entry point.
     */
    static final class OsrEntryDescription {
        @CompilationFinal(dimensions = 1) private byte[] frameTags;
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
