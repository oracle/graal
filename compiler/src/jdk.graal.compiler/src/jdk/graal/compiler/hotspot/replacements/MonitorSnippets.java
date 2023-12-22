/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_OPTIONVALUES;
import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.STACK_INSPECTABLE_LEAF;
import static jdk.graal.compiler.hotspot.nodes.AcquiredCASLockNode.mark;
import static jdk.graal.compiler.hotspot.nodes.BeginLockScopeNode.beginLockScope;
import static jdk.graal.compiler.hotspot.nodes.EndLockScopeNode.endLockScope;
import static jdk.graal.compiler.hotspot.nodes.VMErrorNode.vmError;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.DISPLACED_MARK_WORD_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_LOCK_STACK_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_LOCK_STACK_TOP_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_CXQ_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_ENTRY_LIST_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_OWNER_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_RECURSION_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_SUCC_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.isCAssertEnabled;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadLockStackEndOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadLockStackTopOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.lockDisplacedMarkOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.lockMaskInPlace;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.monitorMask;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorAnonymousOwner;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorCxqOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorEntryListOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorOwnerOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorRecursionsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorSuccOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.pageSize;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.unlockedMask;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useLightweightLocking;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useStackLocking;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.verifyOop;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static jdk.graal.compiler.hotspot.replacements.HotspotSnippetsOptions.ProfileMonitors;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.MembarNode.memoryBarrier;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static org.graalvm.word.LocationIdentity.any;
import static org.graalvm.word.WordFactory.unsigned;
import static org.graalvm.word.WordFactory.zero;

import java.util.List;
import java.util.Objects;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.nodes.CurrentLockNode;
import jdk.graal.compiler.hotspot.nodes.MonitorCounterNode;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.DynamicCounterNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.CStringConstant;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Snippets used for implementing the monitorenter and monitorexit instructions.
 *
 * Comment below is reproduced from {@code markWord.hpp} for convenience:
 *
 * <pre>
 * Bit-format of an object header (most significant first, big endian layout below):
 *
 *  64 bits:
 *  --------
 *  unused:25 hash:31 -->| unused_gap:1  age:4  unused_gap:1  lock:2 (normal object)
 *
 *  - hash contains the identity hash value: largest value is
 *    31 bits, see os::random().  Also, 64-bit vm's require
 *    a hash value no bigger than 32 bits because they will not
 *    properly generate a mask larger than that: see library_call.cpp
 *
 *  - the two lock bits are used to describe three states: locked/unlocked and monitor.
 *
 *    [ptr             | 00]  locked             ptr points to real header on stack (stack-locking in use)
 *    [header          | 00]  locked             locked regular object header (fast-locking in use)
 *    [header          | 01]  unlocked           regular object header
 *    [ptr             | 10]  monitor            inflated lock (header is swapped out)
 *    [ptr             | 11]  marked             used to mark an object
 *    [0 ............ 0| 00]  inflating          inflation in progress (stack-locking in use)
 *
 *    We assume that stack/thread pointers have the lowest two bits cleared.
 *
 *  - INFLATING() is a distinguished markword value of all zeros that is
 *    used when inflating an existing stack-lock into an ObjectMonitor.
 *    See below for is_being_inflated() and INFLATING().
 * </pre>
 *
 * Note that {@code Thread::allocate} enforces {@code JavaThread} objects to be aligned
 * appropriately to comply with the layouts above.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/0d4de8a71f063e44618f43ddd862a91aed647f48/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L475-L956",
          sha1 = "802ae57f78741f7b08e32e0e6a4c7ad1119285d3")
// @formatter:on
public class MonitorSnippets implements Snippets {

    /**
     * The monitorenter snippet is slightly different from the HotSpot code:
     *
     * 1. when LockingMode=LM_MONITOR, we won't attempt inlined inflated locking, but go to C stub
     * directly;
     *
     * 2. our inlined inflated locking first checks if the owner is null before making the expensive
     * CAS operation.
     */
    @Snippet
    public static void monitorenter(Object object, KlassPointer hub, @ConstantParameter int lockDepth, @ConstantParameter Register threadRegister, @ConstantParameter Register stackPointerRegister,
                    @ConstantParameter boolean trace, @ConstantParameter Counters counters) {
        HotSpotReplacementsUtil.verifyOop(object);

        // Load the mark word - this includes a null-check on object
        final Word mark = loadWordFromObject(object, markOffset(INJECTED_VMCONFIG));
        final Word lock = beginLockScope(lockDepth);
        final Word thread = registerAsWord(threadRegister);

        trace(trace, "           object: 0x%016lx\n", Word.objectToTrackedPointer(object));
        trace(trace, "             lock: 0x%016lx\n", lock);
        trace(trace, "             mark: 0x%016lx\n", mark);

        incCounter();

        if (HotSpotReplacementsUtil.diagnoseSyncOnValueBasedClasses(INJECTED_VMCONFIG)) {
            if (hub.readWord(HotSpotReplacementsUtil.klassAccessFlagsOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION).and(
                            HotSpotReplacementsUtil.jvmAccIsValueBasedClass(INJECTED_VMCONFIG)).notEqual(0)) {
                monitorenterStubC(MONITORENTER, object, lock);
                return;
            }
        }

        if (tryFastPathLocking(object, stackPointerRegister, trace, counters, mark, lock, thread)) {
            incrementHeldMonitorCount(thread);
        } else {
            // slow-path runtime-call
            monitorenterStubC(MONITORENTER, object, lock);
        }
    }

    /**
     * Dispatch to the appropriate locking strategy based on the {@code LockingMode} flag value.
     */
    private static boolean tryFastPathLocking(Object object, Register stackPointerRegister, boolean trace, Counters counters, Word mark, Word lock, Word thread) {
        if (useLightweightLocking(INJECTED_VMCONFIG)) {
            return tryLightweightLocking(object, lock, mark, thread, trace, counters, stackPointerRegister);
        } else if (useStackLocking(INJECTED_VMCONFIG)) {
            return tryStackLocking(object, lock, mark, thread, trace, counters, stackPointerRegister);
        } else {
            // LM_MONITOR case
            return false;
        }
    }

    private static boolean tryEnterInflated(Object object, Word lock, Word mark, Word thread, boolean trace, Counters counters) {
        // Set the lock slot's displaced mark to unused. Any non-0 value suffices.
        lock.writeWord(HotSpotReplacementsUtil.lockDisplacedMarkOffset(INJECTED_VMCONFIG), WordFactory.unsigned(3), HotSpotReplacementsUtil.DISPLACED_MARK_WORD_LOCATION);
        // mark is a pointer to the ObjectMonitor + monitorMask
        Word monitor = mark.subtract(HotSpotReplacementsUtil.monitorMask(INJECTED_VMCONFIG));
        int ownerOffset = HotSpotReplacementsUtil.objectMonitorOwnerOffset(INJECTED_VMCONFIG);
        Word owner = monitor.readWord(ownerOffset, HotSpotReplacementsUtil.OBJECT_MONITOR_OWNER_LOCATION);
        // The following owner null check is essential. In the case where the null check fails, it
        // avoids the subsequent bound-to-fail CAS operation, which would have caused the
        // invalidation of the L1 cache of the core that runs the lock owner thread, and thus causes
        // the lock to be held slightly longer.
        if (probability(FREQUENT_PROBABILITY, owner.equal(0))) {
            // it appears unlocked (owner == 0)
            if (probability(FREQUENT_PROBABILITY, monitor.logicCompareAndSwapWord(ownerOffset, owner, thread, HotSpotReplacementsUtil.OBJECT_MONITOR_OWNER_LOCATION))) {
                // success
                traceObject(trace, "+lock{inflated:cas}", object, true);
                counters.inflatedCas.inc();
                return true;
            } else {
                traceObject(trace, "+lock{stub:inflated:failed-cas}", object, true);
                counters.inflatedFailedCas.inc();
            }
        } else if (probability(NOT_LIKELY_PROBABILITY, owner.equal(thread))) {
            int recursionsOffset = HotSpotReplacementsUtil.objectMonitorRecursionsOffset(INJECTED_VMCONFIG);
            Word recursions = monitor.readWord(recursionsOffset, HotSpotReplacementsUtil.OBJECT_MONITOR_RECURSION_LOCATION);
            monitor.writeWord(recursionsOffset, recursions.add(1), HotSpotReplacementsUtil.OBJECT_MONITOR_RECURSION_LOCATION);
            traceObject(trace, "+lock{inflated:recursive}", object, true);
            counters.inflatedRecursive.inc();
            return true;
        } else {
            traceObject(trace, "+lock{stub:inflated:owned}", object, true);
            counters.inflatedOwned.inc();
        }
        return false;
    }

    private static boolean tryStackLocking(Object object, Word lock, Word mark, Word thread, boolean trace, Counters counters, Register stackPointerRegister) {
        if (probability(SLOW_PATH_PROBABILITY, mark.and(monitorMask(INJECTED_VMCONFIG)).notEqual(0))) {
            // Inflated case
            return tryEnterInflated(object, lock, mark, thread, trace, counters);
        }

        Pointer objectPointer = Word.objectToTrackedPointer(object);

        // Create the unlocked mark word pattern
        Word unlockedMark = mark.or(unlockedMask(INJECTED_VMCONFIG));
        trace(trace, "     unlockedMark: 0x%016lx\n", unlockedMark);

        // Copy this unlocked mark word into the lock slot on the stack
        lock.writeWord(lockDisplacedMarkOffset(INJECTED_VMCONFIG), unlockedMark, DISPLACED_MARK_WORD_LOCATION);

        // Test if the object's mark word is unlocked, and if so, store the (address of) the
        // lock slot into the object's mark word.
        //
        // Since pointer cas operations are volatile accesses, previous stores cannot float
        // below it.
        Word currentMark = objectPointer.compareAndSwapWord(markOffset(INJECTED_VMCONFIG), unlockedMark, lock, MARK_WORD_LOCATION);
        if (probability(FAST_PATH_PROBABILITY, currentMark.equal(unlockedMark))) {
            traceObject(trace, "+lock{stack:cas}", object, true);
            counters.lockCas.inc();
            mark(object);
            return true;
        } else {
            trace(trace, "      currentMark: 0x%016lx\n", currentMark);
            // The mark word in the object header was not the same.
            // Either the object is locked by another thread or is already locked
            // by the current thread. The latter is true if the mark word
            // is a stack pointer into the current thread's stack, i.e.:
            //
            // 1) (currentMark & aligned_mask) == 0
            // 2) rsp <= currentMark
            // 3) currentMark <= rsp + page_size
            //
            // These 3 tests can be done by evaluating the following expression:
            //
            // (currentMark - rsp) & (aligned_mask - page_size)
            //
            // assuming both the stack pointer and page_size have their least
            // significant 2 bits cleared and page_size is a power of 2
            final Word alignedMask = unsigned(wordSize() - 1);
            final Word stackPointer = registerAsWord(stackPointerRegister);
            if (probability(FAST_PATH_PROBABILITY, currentMark.subtract(stackPointer).and(alignedMask.subtract(pageSize(INJECTED_VMCONFIG))).equal(0))) {
                // Recursively locked => write 0 to the lock slot
                lock.writeWord(lockDisplacedMarkOffset(INJECTED_VMCONFIG), zero(), DISPLACED_MARK_WORD_LOCATION);
                traceObject(trace, "+lock{stack:cas:recursive}", object, true);
                counters.lockCasRecursive.inc();
                return true;
            }
            traceObject(trace, "+lock{stub:stack:failed-cas}", object, true);
            counters.lockStubFailedCas.inc();
        }
        return false;
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/ce8399fd6071766114f5f201b6e44a7abdba9f5a/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L9963-L9997",
              sha1 = "7a02d52b6b621959389e574984ca20b52100fe5e")
    // @formatter:on
    private static boolean tryLightweightLocking(Object object, Word lock, Word mark, Word thread, boolean trace, Counters counters, Register stackPointerRegister) {
        if (probability(SLOW_PATH_PROBABILITY, mark.and(monitorMask(INJECTED_VMCONFIG)).notEqual(0))) {
            // Inflated case
            return tryEnterInflated(object, lock, mark, thread, trace, counters);
        }

        Pointer objectPointer = Word.objectToTrackedPointer(object);
        // First we need to check if the lock-stack has room for pushing the object reference.
        // Note: hotspot forces 'greater' comparison by subtracting 1 from the end-offset. We still
        // use 'greaterEqual' because Graal will anyway transform the 'greater' operation into
        // 'greaterEqual'.
        int lockStackTop = thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
        if (probability(SLOW_PATH_PROBABILITY, lockStackTop >= javaThreadLockStackEndOffset(INJECTED_VMCONFIG))) {
            traceObject(trace, "+lock{stub:lightweight:fulllockstack}", object, true);
            return false;
        }
        // Now we attempt to take the fast-lock.
        // Clear lock_mask bits (locked state).
        Word markLocked = mark.and(~lockMaskInPlace(INJECTED_VMCONFIG));
        Word markUnlocked = markLocked.or(unlockedMask(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, objectPointer.logicCompareAndSwapWord(markOffset(INJECTED_VMCONFIG), markUnlocked, markLocked, MARK_WORD_LOCATION))) {
            // If successful, push object to lock-stack.
            // Here we don't re-read LockStack::_top as it is thread-local data.
            thread.writeWord(lockStackTop, objectPointer, JAVA_THREAD_LOCK_STACK_LOCATION);
            thread.writeInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), lockStackTop + wordSize(), JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
            traceObject(trace, "+lock{lightweight:cas}", object, true);
            counters.lockCas.inc();
            return true;
        }

        traceObject(trace, "+lock{stub:lightweight:failed-cas}", object, true);
        counters.lockStubFailedCas.inc();
        return false;
    }

    @Snippet
    public static void monitorexit(Object object, @ConstantParameter int lockDepth, @ConstantParameter Register threadRegister, @ConstantParameter boolean trace,
                    @ConstantParameter Counters counters) {
        verifyOop(object);

        final Word thread = registerAsWord(threadRegister);
        final Word lock = CurrentLockNode.currentLock(lockDepth);
        final Word displacedMark = lock.readWord(lockDisplacedMarkOffset(INJECTED_VMCONFIG), DISPLACED_MARK_WORD_LOCATION);

        trace(trace, "           object: 0x%016lx\n", Word.objectToTrackedPointer(object));
        trace(trace, "             lock: 0x%016lx\n", lock);
        trace(trace, "    displacedMark: 0x%016lx\n", displacedMark);

        if (tryFastPathUnlocking(object, trace, counters, thread, lock, displacedMark)) {
            decrementHeldMonitorCount(thread);
        } else {
            monitorexitStubC(MONITOREXIT, object, lock);
        }
        endLockScope();
        decCounter();
    }

    /**
     * Dispatch to the appropriate unlocking strategy based on the {@code LockingMode} flag value.
     */
    private static boolean tryFastPathUnlocking(Object object, boolean trace, Counters counters, Word thread, Word lock, Word displacedMark) {
        if (useLightweightLocking(INJECTED_VMCONFIG)) {
            return tryLightweightUnlocking(object, thread, trace, counters);
        } else if (useStackLocking(INJECTED_VMCONFIG)) {
            return tryStackUnlocking(object, displacedMark, thread, lock, trace, counters);
        } else {
            // LM_MONITOR case
            return false;
        }
    }

    private static boolean tryStackUnlocking(Object object, Word displacedMark, Word thread, Word lock, boolean trace, Counters counters) {
        if (probability(NOT_LIKELY_PROBABILITY, displacedMark.equal(0))) {
            // Recursive locking => done
            traceObject(trace, "-lock{recursive}", object, false);
            counters.unlockCasRecursive.inc();
            return true;
        }

        Word mark = loadWordFromObject(object, markOffset(INJECTED_VMCONFIG));

        if (probability(SLOW_PATH_PROBABILITY, mark.and(monitorMask(INJECTED_VMCONFIG)).notEqual(0))) {
            return tryExitInflated(object, thread, trace, counters);
        }

        if (probability(VERY_FAST_PATH_PROBABILITY, Word.objectToTrackedPointer(object).logicCompareAndSwapWord(markOffset(INJECTED_VMCONFIG),
                        lock, displacedMark, MARK_WORD_LOCATION))) {
            traceObject(trace, "-lock{stack:cas}", object, false);
            counters.unlockCas.inc();
            return true;
        }

        // The object's mark word was not pointing to the displaced header
        traceObject(trace, "-lock{stack:stub}", object, false);
        counters.unlockStub.inc();
        return false;
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/ce8399fd6071766114f5f201b6e44a7abdba9f5a/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L9999-L10028",
              sha1 = "666343ea6d941f68ed863a396d9496cb3ce1b69b")
    // @formatter:on
    private static boolean tryLightweightUnlocking(Object object, Word thread, boolean trace, Counters counters) {
        Word mark = loadWordFromObject(object, markOffset(INJECTED_VMCONFIG));

        if (probability(SLOW_PATH_PROBABILITY, mark.and(monitorMask(INJECTED_VMCONFIG)).notEqual(0))) {
            return tryExitInflated(object, thread, trace, counters);
        }

        Pointer objectPointer = Word.objectToTrackedPointer(object);
        // Mark-word must be lock_mask now, try to swing it back to unlocked_value.
        Word markUnlocked = mark.or(unlockedMask(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, objectPointer.logicCompareAndSwapWord(markOffset(INJECTED_VMCONFIG), mark, markUnlocked, MARK_WORD_LOCATION))) {
            // Pop the lock object from the lock-stack.
            int lockStackTop = thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
            int newLockStackTop = lockStackTop - wordSize();
            thread.writeInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), newLockStackTop, JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
            if (isCAssertEnabled(INJECTED_VMCONFIG)) {
                thread.writeWord(newLockStackTop, WordFactory.zero(), JAVA_THREAD_LOCK_STACK_LOCATION);
            }
            traceObject(trace, "-lock{lightweight:cas}", object, false);
            counters.unlockCas.inc();
            return true;
        }

        traceObject(trace, "-lock{lightweight:stub}", object, false);
        counters.unlockStub.inc();
        return false;
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/0d4de8a71f063e44618f43ddd862a91aed647f48/src/hotspot/cpu/x86/c2_CodeStubs_x86.cpp#L84-L95",
              sha1 = "1c41ad80112cc047315b4a6a7b005b433c318b76")
    // @formatter:on
    private static boolean tryExitInflated(Object object, Word thread, boolean trace, Counters counters) {
        // Inflated case
        // mark is a pointer to the ObjectMonitor + monitorMask
        Word mark = loadWordFromObject(object, markOffset(INJECTED_VMCONFIG));
        Word monitor = mark.subtract(monitorMask(INJECTED_VMCONFIG));
        int ownerOffset = objectMonitorOwnerOffset(INJECTED_VMCONFIG);

        if (useLightweightLocking(INJECTED_VMCONFIG)) {
            Word owner = monitor.readWord(ownerOffset, OBJECT_MONITOR_OWNER_LOCATION);
            // If the owner is ANONYMOUS, we need to fix it
            if (probability(SLOW_PATH_PROBABILITY, owner.and(WordFactory.signed(objectMonitorAnonymousOwner(INJECTED_VMCONFIG))).notEqual(0))) {
                monitor.writeWord(ownerOffset, thread, OBJECT_MONITOR_OWNER_LOCATION);
                int lockStackTop = thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
                int newLockStackTop = lockStackTop - wordSize();
                thread.writeInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), newLockStackTop, JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
                if (isCAssertEnabled(INJECTED_VMCONFIG)) {
                    thread.writeWord(newLockStackTop, WordFactory.zero(), JAVA_THREAD_LOCK_STACK_LOCATION);
                }
            }
        }

        int recursionsOffset = objectMonitorRecursionsOffset(INJECTED_VMCONFIG);
        Word recursions = monitor.readWord(recursionsOffset, OBJECT_MONITOR_RECURSION_LOCATION);
        if (probability(FAST_PATH_PROBABILITY, recursions.equal(0))) {
            // recursions == 0
            int cxqOffset = objectMonitorCxqOffset(INJECTED_VMCONFIG);
            Word cxq = monitor.readWord(cxqOffset, OBJECT_MONITOR_CXQ_LOCATION);
            int entryListOffset = objectMonitorEntryListOffset(INJECTED_VMCONFIG);
            Word entryList = monitor.readWord(entryListOffset, OBJECT_MONITOR_ENTRY_LIST_LOCATION);
            if (probability(FREQUENT_PROBABILITY, cxq.or(entryList).equal(0))) {
                // cxq == 0 && entryList == 0
                // Nobody is waiting, success
                // release_store
                memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
                monitor.writeWord(ownerOffset, zero());
                traceObject(trace, "-lock{inflated:simple}", object, false);
                counters.unlockInflatedSimple.inc();
                return true;
            } else {
                int succOffset = objectMonitorSuccOffset(INJECTED_VMCONFIG);
                Word succ = monitor.readWord(succOffset, OBJECT_MONITOR_SUCC_LOCATION);
                if (probability(FREQUENT_PROBABILITY, succ.isNonNull())) {
                    // There may be a thread spinning on this monitor. Temporarily setting
                    // the monitor owner to null, and hope that the other thread will grab it.
                    monitor.writeWordVolatile(ownerOffset, zero());
                    succ = monitor.readWordVolatile(succOffset, OBJECT_MONITOR_SUCC_LOCATION);
                    if (probability(NOT_FREQUENT_PROBABILITY, succ.isNonNull())) {
                        // We manage to release the monitor before the other running thread even
                        // notices.
                        traceObject(trace, "-lock{inflated:transfer}", object, false);
                        counters.unlockInflatedTransfer.inc();
                        return true;
                    } else {
                        // Either the monitor is grabbed by a spinning thread, or the spinning
                        // thread parks. Now we attempt to reset the owner of the monitor.
                        if (probability(FREQUENT_PROBABILITY, !monitor.logicCompareAndSwapWord(ownerOffset, zero(), thread, OBJECT_MONITOR_OWNER_LOCATION))) {
                            // The monitor is stolen.
                            traceObject(trace, "-lock{inflated:transfer}", object, false);
                            counters.unlockInflatedTransfer.inc();
                            return true;
                        }
                    }
                }
            }
        } else {
            // Recursive inflated unlock
            monitor.writeWord(recursionsOffset, recursions.subtract(1), OBJECT_MONITOR_RECURSION_LOCATION);
            counters.unlockInflatedRecursive.inc();
            traceObject(trace, "-lock{stub:recursive}", object, false);
            return true;
        }
        counters.unlockStubInflated.inc();
        traceObject(trace, "-lock{stub:inflated}", object, false);
        return false;
    }

    private static void incrementHeldMonitorCount(Word thread) {
        updateHeldMonitorCount(thread, 1);
    }

    private static void decrementHeldMonitorCount(Word thread) {
        updateHeldMonitorCount(thread, -1);
    }

    private static void updateHeldMonitorCount(Word thread, int increment) {
        Word heldMonitorCount = thread.readWord(HotSpotReplacementsUtil.heldMonitorCountOffset(INJECTED_VMCONFIG), HotSpotReplacementsUtil.JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION);
        thread.writeWord(HotSpotReplacementsUtil.heldMonitorCountOffset(INJECTED_VMCONFIG), heldMonitorCount.add(increment), HotSpotReplacementsUtil.JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION);
    }

    @Fold
    static boolean doProfile(@Fold.InjectedParameter OptionValues options) {
        return ProfileMonitors.getValue(options);
    }

    private static void traceObject(boolean enabled, String action, Object object, boolean enter) {
        if (doProfile(INJECTED_OPTIONVALUES)) {
            DynamicCounterNode.counter(enter ? "number of monitor enters" : "number of monitor exits", action, 1, false);
        }
        if (enabled) {
            Log.print(action);
            Log.print(' ');
            Log.printlnObject(object);
        }
    }

    private static void trace(boolean enabled, String format, WordBase value) {
        if (enabled) {
            Log.printf(format, value.rawValue());
        }
    }

    private static final LocationIdentity MONITOR_COUNTER_LOCATION = NamedLocationIdentity.mutable("MonitorCounter");

    @Fold
    static boolean verifyBalancedMonitors(@Fold.InjectedParameter OptionValues options) {
        return HotspotSnippetsOptions.VerifyBalancedMonitors.getValue(options);
    }

    static void incCounter() {
        if (verifyBalancedMonitors(INJECTED_OPTIONVALUES)) {
            final Word counter = MonitorCounterNode.counter();
            final int count = counter.readInt(0, MONITOR_COUNTER_LOCATION);
            counter.writeInt(0, count + 1, MONITOR_COUNTER_LOCATION);
        }
    }

    static void decCounter() {
        if (verifyBalancedMonitors(INJECTED_OPTIONVALUES)) {
            final Word counter = MonitorCounterNode.counter();
            final int count = counter.readInt(0, MONITOR_COUNTER_LOCATION);
            counter.writeInt(0, count - 1, MONITOR_COUNTER_LOCATION);
        }
    }

    @Snippet
    private static void initCounter() {
        final Word counter = MonitorCounterNode.counter();
        counter.writeInt(0, 0, MONITOR_COUNTER_LOCATION);
    }

    @Snippet
    private static void checkCounter(@ConstantParameter CStringConstant errMsg) {
        final Word counter = MonitorCounterNode.counter();
        final int count = counter.readInt(0, MONITOR_COUNTER_LOCATION);
        if (count != 0) {
            vmError(errMsg, count);
        }
    }

    public static class Counters {
        /**
         * Counters for the various paths for acquiring a lock. The counters whose names start with
         * {@code "lock"} are mutually exclusive. The other counters are for paths that may be
         * shared.
         */
        public final SnippetCounter lockCas; // shared by stack/lightweight locking
        public final SnippetCounter lockStubFailedCas; // shared by stack/lightweight locking
        public final SnippetCounter lockCasRecursive;
        public final SnippetCounter inflatedCas;
        public final SnippetCounter inflatedFailedCas;
        public final SnippetCounter inflatedRecursive;
        public final SnippetCounter inflatedOwned;

        /**
         * Counters for the various paths for releasing a lock. The counters whose names start with
         * {@code "unlock"} are mutually exclusive. The other counters are for paths that may be
         * shared.
         */
        public final SnippetCounter unlockCas; // shared by stack/lightweight locking
        public final SnippetCounter unlockStub; // shared by stack/lightweight locking
        public final SnippetCounter unlockCasRecursive;
        public final SnippetCounter unlockStubInflated;
        public final SnippetCounter unlockInflatedSimple;
        public final SnippetCounter unlockInflatedTransfer;
        public final SnippetCounter unlockInflatedRecursive;

        public Counters(SnippetCounter.Group.Factory factory) {
            SnippetCounter.Group enter = factory.createSnippetCounterGroup("MonitorEnters");
            SnippetCounter.Group exit = factory.createSnippetCounterGroup("MonitorExits");
            lockCas = new SnippetCounter(enter, "lock{cas}", "cas-locked an object");
            lockStubFailedCas = new SnippetCounter(enter, "lock{stub:failed-cas}", "stub-locked, failed cas");
            lockCasRecursive = new SnippetCounter(enter, "lock{cas:recursive}", "cas-locked, recursive");
            inflatedCas = new SnippetCounter(enter, "lock{inflated:cas}", "heavyweight-locked, cas-locked");
            inflatedFailedCas = new SnippetCounter(enter, "lock{inflated:failed-cas}", "heavyweight-locked, failed cas");
            inflatedRecursive = new SnippetCounter(enter, "lock{inflated:recursive}", "heavyweight-locked, recursive");
            inflatedOwned = new SnippetCounter(enter, "lock{inflated:owned}", "heavyweight-locked, already owned");

            unlockCas = new SnippetCounter(exit, "unlock{cas}", "cas-unlocked an object");
            unlockStub = new SnippetCounter(exit, "unlock{stub}", "stub-unlocked an object");
            unlockCasRecursive = new SnippetCounter(exit, "unlock{cas:recursive}", "cas-unlocked an object, recursive");
            unlockStubInflated = new SnippetCounter(exit, "unlock{stub:inflated}", "stub-unlocked an object with inflated monitor");
            unlockInflatedSimple = new SnippetCounter(exit, "unlock{inflated}", "unlocked an object monitor");
            unlockInflatedTransfer = new SnippetCounter(exit, "unlock{inflated:transfer}", "unlocked an object monitor in the presence of ObjectMonitor::_succ");
            unlockInflatedRecursive = new SnippetCounter(exit, "unlock{inflated:recursive}", "unlocked an object monitor, recursive");
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo monitorenter;
        private final SnippetInfo monitorexit;
        private final SnippetInfo initCounter;
        private final SnippetInfo checkCounter;

        public final Counters counters;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, SnippetCounter.Group.Factory factory, HotSpotProviders providers, GraalHotSpotVMConfig config) {
            super(options, providers);

            LocationIdentity[] enterLocations;
            LocationIdentity[] exitLocations;

            if (useLightweightLocking(config)) {
                enterLocations = new LocationIdentity[]{
                                JAVA_THREAD_LOCK_STACK_LOCATION,
                                JAVA_THREAD_LOCK_STACK_TOP_LOCATION,
                                JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION};
                exitLocations = new LocationIdentity[]{
                                JAVA_THREAD_LOCK_STACK_LOCATION,
                                JAVA_THREAD_LOCK_STACK_TOP_LOCATION,
                                DISPLACED_MARK_WORD_LOCATION,
                                OBJECT_MONITOR_OWNER_LOCATION,
                                OBJECT_MONITOR_CXQ_LOCATION,
                                OBJECT_MONITOR_ENTRY_LIST_LOCATION,
                                OBJECT_MONITOR_RECURSION_LOCATION,
                                OBJECT_MONITOR_SUCC_LOCATION,
                                MARK_WORD_LOCATION,
                                JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION};
            } else {
                enterLocations = new LocationIdentity[]{JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION};
                exitLocations = new LocationIdentity[]{
                                DISPLACED_MARK_WORD_LOCATION,
                                OBJECT_MONITOR_OWNER_LOCATION,
                                OBJECT_MONITOR_CXQ_LOCATION,
                                OBJECT_MONITOR_ENTRY_LIST_LOCATION,
                                OBJECT_MONITOR_RECURSION_LOCATION,
                                OBJECT_MONITOR_SUCC_LOCATION,
                                MARK_WORD_LOCATION,
                                JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION};
            }

            this.monitorenter = snippet(providers, MonitorSnippets.class, "monitorenter", enterLocations);
            this.monitorexit = snippet(providers, MonitorSnippets.class, "monitorexit", exitLocations);
            this.initCounter = snippet(providers, MonitorSnippets.class, "initCounter");
            this.checkCounter = snippet(providers, MonitorSnippets.class, "checkCounter");

            this.counters = new Counters(factory);
        }

        public void lower(MonitorEnterNode monitorenterNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = monitorenterNode.graph();
            checkBalancedMonitors(graph, tool);

            assert ((ObjectStamp) monitorenterNode.object().stamp(NodeView.DEFAULT)).nonNull();

            Arguments args = new Arguments(monitorenter, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("object", monitorenterNode.object());
            args.add("hub", Objects.requireNonNull(monitorenterNode.getObjectData()));
            args.addConst("lockDepth", monitorenterNode.getMonitorId().getLockDepth());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("stackPointerRegister", registers.getStackPointerRegister());
            args.addConst("trace", isTracingEnabledForType(monitorenterNode.object()) || isTracingEnabledForMethod(graph));
            args.addConst("counters", counters);

            template(tool, monitorenterNode, args).instantiate(tool.getMetaAccess(), monitorenterNode, DEFAULT_REPLACER, args);
        }

        public void lower(MonitorExitNode monitorexitNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = monitorexitNode.graph();

            Arguments args = new Arguments(monitorexit, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("object", monitorexitNode.object());
            args.addConst("lockDepth", monitorexitNode.getMonitorId().getLockDepth());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", isTracingEnabledForType(monitorexitNode.object()) || isTracingEnabledForMethod(graph));
            args.addConst("counters", counters);

            template(tool, monitorexitNode, args).instantiate(tool.getMetaAccess(), monitorexitNode, DEFAULT_REPLACER, args);
        }

        public static boolean isTracingEnabledForType(ValueNode object) {
            ResolvedJavaType type = StampTool.typeOrNull(object.stamp(NodeView.DEFAULT));
            String filter = HotspotSnippetsOptions.TraceMonitorsTypeFilter.getValue(object.getOptions());
            if (filter == null) {
                return false;
            } else {
                if (filter.length() == 0) {
                    return true;
                }
                if (type == null) {
                    return false;
                }
                return (type.getName().contains(filter));
            }
        }

        public static boolean isTracingEnabledForMethod(StructuredGraph graph) {
            String filter = HotspotSnippetsOptions.TraceMonitorsMethodFilter.getValue(graph.getOptions());
            if (filter == null) {
                return false;
            } else {
                if (filter.length() == 0) {
                    return true;
                }
                if (graph.method() == null) {
                    return false;
                }
                return (graph.method().format("%H.%n").contains(filter));
            }
        }

        /**
         * If balanced monitor checking is enabled then nodes are inserted at the start and all
         * return points of the graph to initialize and check the monitor counter respectively.
         */
        private void checkBalancedMonitors(StructuredGraph graph, LoweringTool tool) {
            if (HotspotSnippetsOptions.VerifyBalancedMonitors.getValue(options)) {
                NodeIterable<MonitorCounterNode> nodes = graph.getNodes().filter(MonitorCounterNode.class);
                if (nodes.isEmpty()) {
                    // Only insert the nodes if this is the first monitorenter being lowered.
                    JavaType returnType = initCounter.getMethod().getSignature().getReturnType(initCounter.getMethod().getDeclaringClass());
                    StampPair returnStamp = StampFactory.forDeclaredType(graph.getAssumptions(), returnType, false);
                    MethodCallTargetNode callTarget = graph.add(new MethodCallTargetNode(InvokeKind.Static, initCounter.getMethod(), ValueNode.EMPTY_ARRAY, returnStamp, null));
                    InvokeNode invoke = graph.add(new InvokeNode(callTarget, 0));
                    invoke.setStateAfter(graph.start().stateAfter());
                    graph.addAfterFixed(graph.start(), invoke);

                    StructuredGraph inlineeGraph = tool.getReplacements().getSnippet(initCounter.getMethod(), null, null, null, invoke.graph().trackNodeSourcePosition(),
                                    invoke.getNodeSourcePosition(), invoke.getOptions());
                    InliningUtil.inline(invoke, inlineeGraph, false, null);

                    List<ReturnNode> rets = graph.getNodes(ReturnNode.TYPE).snapshot();
                    for (ReturnNode ret : rets) {
                        returnType = checkCounter.getMethod().getSignature().getReturnType(checkCounter.getMethod().getDeclaringClass());
                        String msg = "unbalanced monitors in " + graph.method().format("%H.%n(%p)") + ", count = %d";
                        ConstantNode errMsg = ConstantNode.forConstant(tool.getConstantReflection().forString(msg), tool.getMetaAccess(), graph);
                        returnStamp = StampFactory.forDeclaredType(graph.getAssumptions(), returnType, false);
                        callTarget = graph.add(new MethodCallTargetNode(InvokeKind.Static, checkCounter.getMethod(), new ValueNode[]{errMsg}, returnStamp, null));
                        invoke = graph.add(new InvokeNode(callTarget, 0));
                        Bytecode code = new ResolvedJavaMethodBytecode(graph.method());
                        FrameState stateAfter = new FrameState(null, code, BytecodeFrame.AFTER_BCI, ValueNode.EMPTY_ARRAY, ValueNode.EMPTY_ARRAY, 0, null, null, ValueNode.EMPTY_ARRAY, null,
                                        FrameState.StackState.BeforePop);
                        invoke.setStateAfter(graph.add(stateAfter));
                        graph.addBeforeFixed(ret, invoke);

                        Arguments args = new Arguments(checkCounter, graph.getGuardsStage(), tool.getLoweringStage());
                        args.addConst("errMsg", new CStringConstant(msg));
                        inlineeGraph = template(tool, invoke, args).copySpecializedGraph(graph.getDebug());
                        InliningUtil.inline(invoke, inlineeGraph, false, null);
                    }
                }
            }
        }
    }

    public static final HotSpotForeignCallDescriptor MONITORENTER = new HotSpotForeignCallDescriptor(SAFEPOINT, HAS_SIDE_EFFECT, any(), "monitorenter", void.class, Object.class, Word.class);
    public static final HotSpotForeignCallDescriptor MONITOREXIT = new HotSpotForeignCallDescriptor(STACK_INSPECTABLE_LEAF, HAS_SIDE_EFFECT, any(), "monitorexit", void.class, Object.class,
                    Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void monitorenterStubC(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object, Word lock);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void monitorexitStubC(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object, Word lock);
}
