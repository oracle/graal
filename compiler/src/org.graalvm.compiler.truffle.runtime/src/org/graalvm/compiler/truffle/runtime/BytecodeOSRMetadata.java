/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.Assumption;
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
    static final class LazyState {
        // Maps bytecode targets to their respective call targets and actual frame descriptions on
        // entry
        private final FinalCompilationListMap compilationMap;
        @CompilationFinal private FrameDescriptor frameDescriptor;
        @CompilationFinal private Assumption frameVersion;
        @CompilationFinal(dimensions = 1) private com.oracle.truffle.api.frame.FrameSlot[] frameSlots;

        LazyState(FinalCompilationListMap compilationMap) {
            this.compilationMap = compilationMap;
            // We set these fields in updateFrameSlots using a concrete frame just before
            // compilation, when the frame is (hopefully) stable.
            this.frameDescriptor = null;
            this.frameVersion = null;
            this.frameSlots = null;
        }
    }

    /**
     * Describes the observed state of the Frame on an OSR entry point.
     */
    static final class OsrEntryDescription {
        final OptimizedCallTarget compilationTarget;
        @CompilationFinal(dimensions = 1) private byte[] frameTags;
        @CompilationFinal(dimensions = 1) private byte[] indexedFrameTags;

        public OsrEntryDescription(OptimizedCallTarget compilationTarget) {
            this.compilationTarget = compilationTarget;
        }
    }

    static final class FinalCompilationListMap {
        private static final int[] EMPTY_TARGETS = new int[0];
        private static final OsrEntryDescription[] EMPTY_ENTRIES = new OsrEntryDescription[0];

        @CompilationFinal(dimensions = 1) //
        private int[] targets;
        @CompilationFinal(dimensions = 1) //
        private OsrEntryDescription[] entries;

        public FinalCompilationListMap() {
            this.targets = EMPTY_TARGETS;
            this.entries = EMPTY_ENTRIES;
        }

        public int size() {
            return targets.length;
        }

        @ExplodeLoop
        public OsrEntryDescription get(int key) {
            int[] targetsSnapshot = targets;
            OsrEntryDescription[] entriesSnapshot = entries;
            if (targetsSnapshot.length != entriesSnapshot.length) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                synchronized (this) {
                    targetsSnapshot = targets;
                    entriesSnapshot = entries;
                }
            }
            int len = targetsSnapshot.length;
            for (int i = 0; i < len; i++) {
                if (targetsSnapshot[i] == key) {
                    return entriesSnapshot[i];
                }
            }
            return null;
        }

        public void put(int key, OsrEntryDescription value) {
            // must hold osr node atomic lock
            CompilerDirectives.transferToInterpreterAndInvalidate();
            assert get(key) == null;
            int oldLen = size();
            int[] newTargets = Arrays.copyOf(targets, oldLen + 1);
            OsrEntryDescription[] newEntries = Arrays.copyOf(entries, oldLen + 1);
            newTargets[oldLen] = key;
            newEntries[oldLen] = value;
            this.targets = newTargets;
            this.entries = newEntries;
        }

        public void clear() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                targets = EMPTY_TARGETS;
                entries = EMPTY_ENTRIES;
            }
        }

        public Map<Integer, OptimizedCallTarget> asCallTargetMap() {
            // must hold osr node atomic lock
            Map<Integer, OptimizedCallTarget> map = new HashMap<>();
            for (int i = 0; i < size(); i++) {
                map.put(targets[i], entries[i].compilationTarget);
            }
            return Collections.unmodifiableMap(map);
        }
    }

    @CompilationFinal private volatile LazyState lazyState;

    private LazyState getLazyState() {
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
                lockedLazyState = lazyState = new LazyState(new FinalCompilationListMap());
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
            // The concrete frame can have different tags from the descriptor (e.g., when a slot is
            // uninitialized), so we use the frame's tags to avoid deoptimizing during transfer.
            byte[] tags = frame.getTags();
            // The tags array lazily grows when new slots are initialized, so it could be smaller
            // than the number of slots. Copy it into an array with the correct size.
            osrEntry.frameTags = Arrays.copyOf(tags, state.frameSlots.length);
            osrEntry.indexedFrameTags = new byte[state.frameDescriptor.getNumberOfSlots()];
            for (int i = 0; i < osrEntry.indexedFrameTags.length; i++) {
                osrEntry.indexedFrameTags[i] = frame.getTag(i);
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
        OsrEntryDescription osrEntry = state.compilationMap.get(target);
        if (osrEntry == null) {
            osrEntry = ((Node) osrNode).atomic(() -> {
                OsrEntryDescription lockedTarget = state.compilationMap.get(target);
                if (lockedTarget == null) {
                    OptimizedCallTarget callTarget = createOSRTarget(target, interpreterState, parentFrame.getFrameDescriptor());
                    lockedTarget = new OsrEntryDescription(callTarget);
                    requestOSRCompilation(target, lockedTarget, (FrameWithoutBoxing) parentFrame);
                    state.compilationMap.put(target, lockedTarget);
                }
                return lockedTarget;
            });
        }
        OptimizedCallTarget callTarget = osrEntry.compilationTarget;

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
            requestOSRCompilation(target, osrEntry, (FrameWithoutBoxing) parentFrame);
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
    private OptimizedCallTarget createOSRTarget(int target, Object interpreterState, FrameDescriptor frameDescriptor) {
        TruffleLanguage<?> language = GraalRuntimeAccessor.NODES.getLanguage(((Node) osrNode).getRootNode());
        return (OptimizedCallTarget) new BytecodeOSRRootNode(language, frameDescriptor, osrNode, target, interpreterState).getCallTarget();

    }

    private void requestOSRCompilation(int target, OsrEntryDescription entry, FrameWithoutBoxing frame) {
        osrNode.prepareOSR(target);
        updateFrameSlots(frame, entry);
        entry.compilationTarget.compile(true);
        if (entry.compilationTarget.isCompilationFailed()) {
            markCompilationFailed();
        }
    }

    public void transferFrame(FrameWithoutBoxing source, FrameWithoutBoxing target, int bytecodeTarget) {
        transferFrame(source, target, bytecodeTarget, false);
    }

    /**
     * Transfer state from {@code source} to {@code target}. Can be used to transfer state into (or
     * out of) an OSR frame.
     */
    private void transferFrame(FrameWithoutBoxing source, FrameWithoutBoxing target, int bytecodeTarget, boolean restore) {
        LazyState state = getLazyState();
        CompilerAsserts.partialEvaluationConstant(state);
        // The frames should use the same descriptor.
        validateDescriptors(source, target, state);

        OsrEntryDescription description = state.compilationMap.get(bytecodeTarget);
        if (description == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Transferring frame for OSR from an uninitialized bytecode target.");
        }
        CompilerAsserts.partialEvaluationConstant(description);

        // The frame version could have changed; if so, deoptimize and update the slots+tags.
        if (!state.frameVersion.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateFrameSlots(source, description);
        }

        // Transfer legacy frame slots
        transferLoop(description.frameTags.length, source, target, source.getTags(), description.frameTags, state.frameSlots, FrameSlotTransfer.legacySlotTransfer);
        // Transfer indexed frame slots
        transferLoop(description.indexedFrameTags.length, source, target, source.getIndexedTags(), description.indexedFrameTags, null, FrameSlotTransfer.indexedTransfer);
        // transfer auxiliary slots
        for (int auxSlot = 0; auxSlot < state.frameDescriptor.getNumberOfAuxiliarySlots(); auxSlot++) {
            target.setAuxiliarySlot(auxSlot, source.getAuxiliarySlot(auxSlot));
        }

        /*-
        int i = 0;
        while (i < state.frameSlots.length) {
            byte expectedTag = restore ? sourceTags[i] : destTags[i];
            byte actualTag = sourceTags[i];
        
            if (checkAndUpdateIncompatibleTags(destTags, i, expectedTag, actualTag)) {
                continue;
            }
            com.oracle.truffle.api.frame.FrameSlot slot = state.frameSlots[i];
            transferFrameSlot(source, target, slot, expectedTag);
            i++;
        }
        */
        /*-
        for (int i = 0; i < state.frameSlots.length; i++) {
            com.oracle.truffle.api.frame.FrameSlot slot = state.frameSlots[i];
            // When restoring parentFrame, source frame is known.
            byte expectedTag = restore ? sourceTags[i] : description.frameTags[i];
            byte actualTag = sourceTags[i];
        
            while (true) {
                boolean incompatibleTags = expectedTag != actualTag;
                if (incompatibleTags) {
                    // The tag for this slot may have changed; if so, deoptimize and update it.
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    destTags[i] = actualTag;
                    expectedTag = actualTag;
                    continue;
                }
                transferFrameSlot(source, target, slot, expectedTag);
                break;
            }
        }
        */
        /*-
        int slot = 0;
        while (slot < description.indexedFrameTags.length) {
            byte expectedTag = restore ? sourceTags[i] : destTags[i];
            byte actualTag = sourceTags[i];
        
            if (checkAndUpdateIncompatibleTags(destTags, i, expectedTag, actualTag)) {
                continue;
            }
            transferIndexedFrameSlot(source, target, slot, expectedTag);
            i++;
        }
        */
        /*-
        for (int slot = 0; slot < description.indexedFrameTags.length; slot++) {
            byte expectedTag = description.indexedFrameTags[slot];
            byte actualTag = source.getTag(slot);
        
            while (true) {
                boolean incompatibleTags = expectedTag != actualTag;
                if (incompatibleTags) {
                    // The tag for this slot may have changed; if so, deoptimize and update it.
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    description.indexedFrameTags[slot] = actualTag;
                    expectedTag = actualTag;
                    continue;
                }
                transferIndexedFrameSlot(source, target, slot, expectedTag);
                break;
            }
        }
        */
    }

    public void restoreFrame(FrameWithoutBoxing source, FrameWithoutBoxing target) {
        LazyState state = getLazyState();
        CompilerAsserts.partialEvaluationConstant(state);
        // The frames should use the same descriptor.
        validateDescriptors(source, target, state);

        // We can't reasonably have constant expected tags for parent frame restoration. Take
        // the hit on the transfer back.

        // transfer legacy frame slots
        byte[] sourceTags = source.getTags();
        transferLoop(state.frameSlots.length, source, target, sourceTags, sourceTags, state.frameSlots, FrameSlotTransfer.legacySlotTransfer);
        // transfer indexed frame slots
        sourceTags = source.getIndexedTags();
        transferLoop(state.frameDescriptor.getNumberOfSlots(), source, target, sourceTags, sourceTags, null, FrameSlotTransfer.indexedTransfer);
        // transfer auxiliary slots
        for (int auxSlot = 0; auxSlot < state.frameDescriptor.getNumberOfAuxiliarySlots(); auxSlot++) {
            target.setAuxiliarySlot(auxSlot, source.getAuxiliarySlot(auxSlot));
        }
    }

    private void validateDescriptors(FrameWithoutBoxing source, FrameWithoutBoxing target, LazyState state) {
        if (source.getFrameDescriptor() != state.frameDescriptor) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Source frame descriptor is different from the descriptor used for compilation.");
        } else if (target.getFrameDescriptor() != state.frameDescriptor) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Target frame descriptor is different from the descriptor used for compilation.");
        }
    }

    @ExplodeLoop
    private void transferLoop(
                    int length,
                    FrameWithoutBoxing source, FrameWithoutBoxing target,
                    byte[] sourceTags, byte[] expectedTags,
                    com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray, FrameSlotTransfer transfer) {
        int i = 0;
        while (i < length) {
            byte expectedTag = expectedTags[i];
            byte actualTag = sourceTags[i];

            boolean incompatibleTags = expectedTag != actualTag;
            if (incompatibleTags) {
                // The tag for this slot may have changed; if so, deoptimize and update it.
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedTags[i] = actualTag;
                continue;
            }

            transfer.transfer(source, target, i, expectedTag, frameSlotArray);
            i++;
        }
    }

    private static abstract class FrameSlotTransfer {
        private static final FrameSlotTransfer indexedTransfer = new FrameSlotTransfer() {
            @Override
            public void transfer(FrameWithoutBoxing source, FrameWithoutBoxing target, int slot, byte expectedTag, com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray) {
                transferIndexedFrameSlot(source, target, slot, expectedTag);
            }
        };

        private static final FrameSlotTransfer legacySlotTransfer = new FrameSlotTransfer() {
            @Override
            public void transfer(FrameWithoutBoxing source, FrameWithoutBoxing target, int slot, byte expectedTag, com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray) {
                com.oracle.truffle.api.frame.FrameSlot frameSlot = frameSlotArray[slot];
                transferFrameSlot(source, target, frameSlot, expectedTag);
            }
        };

        public abstract void transfer(FrameWithoutBoxing source, FrameWithoutBoxing target, int slot, byte expectedTag, com.oracle.truffle.api.frame.FrameSlot[] frameSlotArray);
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
                for (OsrEntryDescription entry : state.compilationMap.entries) {
                    if (entry != null) {
                        OptimizedCallTarget callTarget = entry.compilationTarget;
                        if (callTarget.isCompilationFailed()) {
                            markCompilationFailed();
                        }
                        callTarget.nodeReplaced(oldNode, newNode, reason);
                    }
                }
            });
        }
    }

    private void markCompilationFailed() {
        ((Node) osrNode).atomic(() -> {
            osrNode.setOSRMetadata(DISABLED);
            LazyState state = lazyState;
            if (state != null) {
                state.compilationMap.clear();
            }
        });
    }

    // for testing
    public Map<Integer, OptimizedCallTarget> getOSRCompilations() {
        return ((Node) osrNode).atomic(() -> getLazyState().compilationMap.asCallTargetMap());
    }

    public int getBackEdgeCount() {
        return backEdgeCount;
    }
}
