/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
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
 * {@link OptimizedRuntimeSupport#pollBytecodeOSRBackEdge}, all threads will observe the same
 * metadata instance. The JMM guarantees that the instance's final fields will be safely initialized
 * before it is published; the non-final + non-volatile fields (e.g., the back edge counter) may not
 * be, but we tolerate this inaccuracy in order to avoid volatile accesses in the hot path.
 */
public final class BytecodeOSRMetadata {
    // Marker object to indicate that OSR is disabled.
    public static final BytecodeOSRMetadata DISABLED = new BytecodeOSRMetadata(null, Integer.MAX_VALUE, 0);
    // Must be a power of 2 (polling uses bit masks). OSRCompilationThreshold is a multiple of this
    // interval.
    public static final int OSR_POLL_INTERVAL = 1024;

    /**
     * Default original stage for bytecode OSR compilation. In this stage,
     * {@link #incrementAndPoll() polling} will succeed only after a {@link #backEdgeCount backedge}
     * has been reported {@link #osrThreshold} times. After the first successful
     * {@link #requestOSRCompilation(int, OptimizedCallTarget, FrameWithoutBoxing) request for
     * compilation}, switch to {@link #HOT_STAGE}.
     */
    private static final byte FRESH_STAGE = 0;
    /**
     * Stage representing a method that has been submitted for OSR compilation at least once. In
     * this stage, {@link #incrementAndPoll() polling} succeeds every {@link #OSR_POLL_INTERVAL},
     * checking for available compiled code to jump to. New requests for compilation still only
     * happens once every {@link #secondaryOsrThreshold} - {@link #osrThreshold} reported backedges.
     */
    private static final byte HOT_STAGE = 1;
    /**
     * In this stage, no polling succeeds. The unit enters this stage if either:
     * <ul>
     * <li>OSR compilation is disabled (see {@link OptimizedRuntimeOptions#OSR}).</li>
     * <li>An attempt at compilation failed.</li>
     * <li>This unit deopts too much</li>
     * </ul>
     */
    private static final byte DISABLED_STAGE = 99;

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
     *
     * Used as a non-blocking synchronization mechanism around compilation requests.
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
            // In particular, we must keep alive:
            // - The frame descriptor
            // - (GR-38296) The map from target to entry description.
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
    private final int secondaryOsrThreshold;
    private final int maxCompilationReAttempts;
    private int backEdgeCount;
    private byte stage = FRESH_STAGE;

    BytecodeOSRMetadata(BytecodeOSRNode osrNode, int osrThreshold, int maxCompilationReAttempts) {
        this.osrNode = osrNode;
        this.osrThreshold = osrThreshold;
        this.secondaryOsrThreshold = Math.max(osrThreshold << 1, osrThreshold); // might overflow
        this.maxCompilationReAttempts = maxCompilationReAttempts;
        this.backEdgeCount = 0;
        if (osrNode == null) {
            this.stage = DISABLED_STAGE;
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
                    if (stage == FRESH_STAGE) {
                        // First attempt at compilation gets a free pass
                        requestOSRCompilation(target, lockedTarget, (FrameWithoutBoxing) parentFrame);
                        stage = HOT_STAGE;
                    }
                }
                return lockedTarget;
            });
        }

        // Case 1: code is still being compiled
        if (callTarget.isCompiling()) {
            return null;
        }
        // Case 2: code is compiled and valid
        boolean valid = callTarget.isValid();

        // Case 3: code is invalid; either give up or reschedule compilation
        if (!valid) {
            if (callTarget.isCompilationFailed()) {
                markOSRDisabled();
            } else if (backEdgeCount >= secondaryOsrThreshold) {
                requestOSRCompilation(target, callTarget, (FrameWithoutBoxing) parentFrame);
                // Can happen for very quick compilation or if background compilation is disabled.
                valid = callTarget.isValid();
            }
        }

        if (valid) {
            /*
             * Note: If disabled, though unlikely, it is still possible to call into an OSR compiled
             * method at this point. This is OK, as we leave enough information around to work with.
             */
            if (beforeTransfer != null) {
                beforeTransfer.run();
            }
            return callTarget.callOSR(osrNode.storeParentFrameInArguments(parentFrame));
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
        return stage == DISABLED_STAGE;
    }

    /**
     * Force disabling of OSR compilation for this method. Used for testing purposes.
     */
    public void forceDisable() {
        markOSRDisabled();
    }

    /**
     * No concurrency guarantees on this assignment. Other threads might still read an old value and
     * reassign. No way to do better without slowing down the polling path.
     * <p>
     * Currently, this is reset on a successful poll, if OSR compilation in already underway. The
     * goal is to restart the counter on OSR compilation submission to not bloat the compilation
     * queue, and only re-submit if the method is still hot.
     * <p>
     * Due to the raciness of accesses to the counter, this is only an approximation of the wanted
     * behavior. In particular, it differs in the following ways:
     * <ul>
     * <li>For long-running compilations, the counter might be reset even though it legitimately
     * counted back up to the threshold while the method was compiling.</li>
     * <li>Another thread might see the old value right as the previously submitted compilation
     * completed, thus skipping the re-counting.</li>
     * </ul>
     * <p>
     * Still, this should be an appropriate approximation, as even if those rare differences should
     * happen, it would still result in acceptable behavior.
     */
    private void resetCounter() {
        backEdgeCount = osrThreshold;
    }

    /**
     * Creates an OSR call target at the given dispatch target and requests compilation. The node's
     * AST lock should be held when this is invoked.
     */
    private OptimizedCallTarget createOSRTarget(int target, Object interpreterState, FrameDescriptor frameDescriptor, Object frameEntryState) {
        TruffleLanguage<?> language = OptimizedRuntimeAccessor.NODES.getLanguage(((Node) osrNode).getRootNode());
        return (OptimizedCallTarget) new BytecodeOSRRootNode(language, frameDescriptor, osrNode, target, interpreterState, frameEntryState).getCallTarget();

    }

    private void requestOSRCompilation(int target, OptimizedCallTarget callTarget, FrameWithoutBoxing frame) {
        OptimizedCallTarget previousCompilation = getCurrentlyCompiling();
        if (previousCompilation != null && !previousCompilation.isSubmittedForCompilation()) {
            // Completed compilation of the previously scheduled compilation. Clear the reference.
            currentlyCompiling.compareAndSet(previousCompilation, null);
        }
        if (!currentlyCompiling.compareAndSet(null, PLACEHOLDER)) {
            // Prevent multiple OSR compilations of the same method at different entry points.
            resetCounter();
            return;
        }
        compilationReAttempts.inc(target);
        if (compilationReAttempts.total() > maxCompilationReAttempts) {
            /*
             * Methods that gets OSR re-compiled too often bailout of OSR compilation. This has two
             * main advantages:
             *
             * - Deopt loops do not clog the compiler queue indefinitely
             *
             * - Mitigates possibilities of Stack Overflows arising from deopt loops in OSR.
             */
            markOSRDisabled();
            if (callTarget.getOptionValue(OptimizedRuntimeOptions.ThrowOnMaxOSRCompilationReAttemptsReached)) {
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
        resetCounter();
        boolean submitted = currentlyCompiling.compareAndSet(PLACEHOLDER, callTarget);
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

        OptimizedRuntimeAccessor.ACCESSOR.startOSRFrameTransfer(target);
        // Transfer indexed frame slots
        transferLoop(description.indexedFrameTags, source, target);
        // transfer auxiliary slots
        transferAuxiliarySlots(source, target, state);
    }

    /**
     * Transfer state from {@code source} to {@code target}. Can be used to transfer state from an
     * OSR frame to a parent frame. Overall less efficient than its
     * {@link #transferFrame(FrameWithoutBoxing, FrameWithoutBoxing, long, Object) counterpart},
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
        // GR-38646:
        //
        // Tag in the slots are not PE-constants here. They might be PEA-able, but that is too late;
        // the switch on the tag introduces way too many nodes in the graph early in the graph
        // building process. To prevent that, we transfer to interpreter here. Coming back from OSR
        // will transfer back anyway, this simply puts the transfer back earlier.
        //
        // Note:
        // - This is not an invalidating deopt.
        // - All normally completing OSR calls will enter here, so this might pollute the tracing of
        // transfer to interpreters.

        // Forces spawning of a frame state. This ensures we return to the interpreter state after
        // the complete execution of the OSR call.
        forceStateSplit();
        // Do not use an invalidating deopt here for two reasons:
        // - The compiler does not propagate the deopt upwards, meaning the return value computed in
        // compiled code will be restored during the transfer to interpreter.
        // - This is valid behavior. No need to re-compile.
        CompilerDirectives.transferToInterpreter();

        LazyState state = getLazyState();
        CompilerAsserts.partialEvaluationConstant(state);
        // The frames should use the same descriptor.
        validateDescriptors(source, target, state);

        // We can't reasonably have constant expected tags for parent frame restoration. We pass
        // state.frameDescriptor to restoreLoop in order to correctly account for static slots.

        // transfer indexed frame slots
        restoreLoop(state.frameDescriptor, source, target);
        // transfer auxiliary slots
        transferAuxiliarySlots(source, target, state);
    }

    @TruffleBoundary(allowInlining = false)
    private static void forceStateSplit() {
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
     * Transfer loop for copying over indexed frame slots from a source parent frame to a target OSR
     * frame.
     *
     * @param expectedTags The array of tags the source is expected to have. If compilation
     *            constant, frame slot accesses may be simplified.
     * @param source The frame to copy from
     * @param target The frame to copy to
     */
    @ExplodeLoop
    private static void transferLoop(
                    byte[] expectedTags,
                    FrameWithoutBoxing source, FrameWithoutBoxing target) {
        CompilerAsserts.partialEvaluationConstant(expectedTags.length);
        int i = 0;
        while (i < expectedTags.length) {
            byte actualTag = source.getTag(i);
            byte expectedTag = expectedTags[i];

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

    /**
     * Transfer loop for copying over indexed frame slots from a source OSR frame to a target parent
     * frame.
     *
     * @param frameDescriptor The common frame descriptor of source and target
     * @param source The frame to copy from
     * @param target The frame to copy to
     */
    private static void restoreLoop(
                    FrameDescriptor frameDescriptor,
                    FrameWithoutBoxing source, FrameWithoutBoxing target) {
        CompilerAsserts.neverPartOfCompilation();
        for (int i = 0; i < frameDescriptor.getNumberOfSlots(); i++) {
            byte tag = source.getTag(i);

            if (tag == 0 && frameDescriptor.getSlotKind(i) == FrameSlotKind.Static) {
                // When using static slots, the tags might never be initialized. We cannot rely
                // solely on the source frame instance tags in order to detect static slots and
                // distinguish them from non-static Object-type slots. Hence, if the tag is 0, we
                // check the FrameDescriptor whether the slot has a static kind.
                tag = FrameWithoutBoxing.STATIC_TAG;
            }

            transferIndexedFrameSlot(source, target, i, tag);
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
                    OptimizedRuntimeAccessor.ACCESSOR.transferOSRFrameStaticSlot(source, target, slot);
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

            stage = DISABLED_STAGE;
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
     *
     * The counter is shared across all bytecode OSR entry points. First time attempts at
     * compilation of an entry point does not increment the counter.
     *
     * Note that there is no synchronization happening in this class. Accesses should be made in
     * synchronized portions of code.
     */
    private static final class ReAttemptsCounter {
        private final Set<Integer> knownTargets = new HashSet<>(1);
        private int total = 0;

        public void inc(int target) {
            if (knownTargets.contains(target)) {
                // Further compilation attempt.
                total++;
            } else {
                // First compilation attempt, do not count.
                knownTargets.add(target);
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
