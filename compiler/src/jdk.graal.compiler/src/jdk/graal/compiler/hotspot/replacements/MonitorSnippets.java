/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.hotspot.nodes.BeginLockScopeNode.beginLockScope;
import static jdk.graal.compiler.hotspot.nodes.EndLockScopeNode.endLockScope;
import static jdk.graal.compiler.hotspot.nodes.VMErrorNode.vmError;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.BASICLOCK_METADATA_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_LOCK_STACK_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_LOCK_STACK_TOP_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_MONITOR_OWNER_ID_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_OM_CACHE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_UNLOCKED_INFLATED_MONITOR_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_MISC_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_CXQ_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_ENTRY_LIST_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_OWNER_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_RECURSION_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_STACK_LOCKER_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_SUCC_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.diagnoseSyncOnValueBasedClasses;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.isCAssertEnabled;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadLockStackEndOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadLockStackTopOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadMonitorOwnerIDOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadOomCacheOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadUnlockedInflatedMonitorOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.jvmAccIsValueBasedClass;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassMiscFlagsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.lockMetadataOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.markWordLockMaskInPlace;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.monitorValue;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorEntryListOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorOwnerOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorRecursionsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorSuccOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.omCacheOopToMonitorDifference;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.omCacheOopToOopDifference;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.unlockedValue;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useObjectMonitorTable;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.verifyOop;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static jdk.graal.compiler.hotspot.replacements.HotspotSnippetsOptions.ProfileMonitors;
import static jdk.graal.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.MembarNode.memoryBarrier;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.graal.compiler.replacements.nodes.CStringConstant.cstring;
import static jdk.graal.compiler.word.Word.nullPointer;
import static jdk.graal.compiler.word.Word.zero;
import static org.graalvm.word.LocationIdentity.any;

import java.util.List;
import java.util.Objects;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.nodes.CurrentLockNode;
import jdk.graal.compiler.hotspot.nodes.MonitorCounterNode;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.DynamicCounterNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.java.CheckFastPathMonitorEnterNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.EscapeObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
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
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

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
 *  unused:22 hash:31 -->| unused_gap:4  age:4  self-fwd:1  lock:2 (normal object)
 *
 *  64 bits (with compact headers):
 *  --------
 *  nklass:22 hash:31 -->| unused_gap:4  age:4  self-fwd:1  lock:2 (normal object)
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
 *    [header          | 10]  monitor            inflated lock (UseObjectMonitorTable == true)
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
@SyncPort(from = "https://github.com/openjdk/jdk/blob/c2d76f9844aadf77a0b213a9169a7c5c8c8f1ffb/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L149-L578",
          sha1 = "ee04c86e8b12a9f8331c5e79f95a17b27615c845")
// @formatter:on
public class MonitorSnippets implements Snippets {

    @Snippet
    public static void checkMonitorenter(@ConstantParameter int lockCount, @ConstantParameter Register threadRegister, @ConstantParameter SpeculationLog.Speculation speculation) {
        /*
         * A FixedGuardNode is used instead of an IfNode/DeoptimizeNode because the libgraal snippet
         * processing can't insert a node that terminates control flow
         */
        FixedGuardNode.guard(canEnterWithoutSideEffect(lockCount, threadRegister), DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.RuntimeConstraint, speculation);
    }

    private static boolean canEnterWithoutSideEffect(int lockCount, Register threadRegister) {
        // The snippet instantiantion code ensures this is only called for lightweight locking
        final Word thread = registerAsWord(threadRegister);
        Word lockStackTop = Word.unsigned(thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION));
        Word newTop = lockStackTop.add(lockCount * wordSize());
        return !newTop.greaterOrEqual(javaThreadLockStackEndOffset(INJECTED_VMCONFIG));
    }

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
                    @ConstantParameter boolean trace, @ConstantParameter boolean synthetic, @ConstantParameter Counters counters) {
        HotSpotReplacementsUtil.verifyOop(object);

        // Load the mark word - this includes a null-check on object
        final Word mark = loadWordFromObject(object, markOffset(INJECTED_VMCONFIG));
        final Word lock = beginLockScope(lockDepth);
        final Word thread = registerAsWord(threadRegister);

        trace(trace, "           object: 0x%016lx\n", Word.objectToTrackedPointer(object));
        trace(trace, "             lock: 0x%016lx\n", lock);
        trace(trace, "             mark: 0x%016lx\n", mark);

        incCounter();

        if (!synthetic && diagnoseSyncOnValueBasedClasses(INJECTED_VMCONFIG)) {
            int flags = hub.readByte(klassMiscFlagsOffset(INJECTED_VMCONFIG), KLASS_MISC_FLAGS_LOCATION);
            if (probability(SLOW_PATH_PROBABILITY, (flags & jvmAccIsValueBasedClass(INJECTED_VMCONFIG)) != 0)) {
                monitorenterStubC(MONITORENTER, object, lock);
                return;
            }
        }

        if (!tryLightweightLocking(object, lock, mark, thread, trace, counters, stackPointerRegister)) {
            if (synthetic) {
                // The fast locking cases are never permitted to use the slow path.
                throw UnreachableNode.unreachable();
            } else {
                // slow-path runtime-call
                monitorenterStubC(MONITORENTER, object, lock);
            }
        }
    }

    // Cache the monitor for unlock
    private static void writeMonitorCache(Word lock, Word monitor) {
        if (useObjectMonitorTable(INJECTED_VMCONFIG)) {
            lock.writeWord(lockMetadataOffset(INJECTED_VMCONFIG), monitor, BASICLOCK_METADATA_LOCATION);
        }
    }

    private static boolean tryEnterInflated(Object object, Word lock, Word mark, Word thread, boolean trace, Counters counters) {
        Word monitor;

        if (useObjectMonitorTable(INJECTED_VMCONFIG)) {
            // Check first two entries outside the loop
            Pointer objectPointer = Word.objectToTrackedPointer(object);
            final Word oomCache = thread.add(javaThreadOomCacheOffset(INJECTED_VMCONFIG));
            final int stepSize = omCacheOopToOopDifference(INJECTED_VMCONFIG);
            final int oomCacheOffset = omCacheOopToMonitorDifference(INJECTED_VMCONFIG);

            if (probability(FREQUENT_PROBABILITY, objectPointer.equal(oomCache.readWord(0, JAVA_THREAD_OM_CACHE_LOCATION)))) {
                monitor = oomCache.readWord(oomCacheOffset, JAVA_THREAD_OM_CACHE_LOCATION);
            } else if (probability(FREQUENT_PROBABILITY, objectPointer.equal(oomCache.readWord(stepSize, JAVA_THREAD_OM_CACHE_LOCATION)))) {
                monitor = oomCache.readWord(stepSize + oomCacheOffset, JAVA_THREAD_OM_CACHE_LOCATION);
            } else {
                // Loop after unrolling, advance iterator.
                Word cacheIter = oomCache.add(stepSize * 2);

                while (true) { // TERMINATION ARGUMENT: runtime guarantees a null entry after
                               // OMCache::CAPACITY entries
                    Word cache = cacheIter.readWord(0, JAVA_THREAD_OM_CACHE_LOCATION);
                    if (probability(FREQUENT_PROBABILITY, objectPointer.equal(cache))) {
                        monitor = cacheIter.readWord(oomCacheOffset, JAVA_THREAD_OM_CACHE_LOCATION);
                        break;
                    } else if (probability(FREQUENT_PROBABILITY, cache.isNull())) {
                        // no cached monitor, fall back to stub call
                        return false;
                    }

                    cacheIter = cacheIter.add(stepSize);
                }
            }

            // Cache the monitor for unlock before trashing box. On failure to acquire
            // the lock, the slow path will reset the entry accordingly (see CacheSetter).
            writeMonitorCache(lock, monitor);
        } else {
            // mark is a pointer to the ObjectMonitor + monitorMask
            monitor = mark.subtract(monitorValue(INJECTED_VMCONFIG));
        }

        int ownerOffset = objectMonitorOwnerOffset(INJECTED_VMCONFIG);
        Word owner = monitor.readWord(ownerOffset, OBJECT_MONITOR_OWNER_LOCATION);
        Word newOwner = thread.readWord(javaThreadMonitorOwnerIDOffset(INJECTED_VMCONFIG), JAVA_THREAD_MONITOR_OWNER_ID_LOCATION);

        // The following owner null check is essential. In the case where the null check fails, it
        // avoids the subsequent bound-to-fail CAS operation, which would have caused the
        // invalidation of the L1 cache of the core that runs the lock owner thread, and thus causes
        // the lock to be held slightly longer.
        if (probability(FREQUENT_PROBABILITY, owner.equal(nullPointer()))) {
            // it appears unlocked (owner == null)
            if (probability(FREQUENT_PROBABILITY, monitor.logicCompareAndSwapWord(ownerOffset, owner, newOwner, OBJECT_MONITOR_OWNER_LOCATION))) {
                // success
                traceObject(trace, "+lock{heavyweight:cas}", object, true);
                counters.lockHeavyCas.inc();
                return true;
            } else {
                traceObject(trace, "+lock{heavyweight:failed-cas}", object, true);
                counters.lockHeavyFailedCas.inc();
            }
        } else if (probability(NOT_LIKELY_PROBABILITY, owner.equal(newOwner))) {
            int recursionsOffset = objectMonitorRecursionsOffset(INJECTED_VMCONFIG);
            Word recursions = monitor.readWord(recursionsOffset, OBJECT_MONITOR_RECURSION_LOCATION);
            monitor.writeWord(recursionsOffset, recursions.add(1), OBJECT_MONITOR_RECURSION_LOCATION);
            traceObject(trace, "+lock{heavyweight:recursive}", object, true);
            counters.lockHeavyRecursive.inc();
            return true;
        } else {
            traceObject(trace, "+lock{heavyweight:owned}", object, true);
            counters.lockHeavyOwned.inc();
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static boolean tryLightweightLocking(Object object, Word lock, Word mark, Word thread, boolean trace, Counters counters, Register stackPointerRegister) {
        writeMonitorCache(lock, Word.nullPointer());
        // Prefetch top
        // We assume `lockStackTop' is always positive and use `WordFactory.unsigned' to skip a sign
        // extension.
        Word lockStackTop = Word.unsigned(thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION));

        if (probability(SLOW_PATH_PROBABILITY, mark.and(monitorValue(INJECTED_VMCONFIG)).notEqual(0))) {
            // Inflated case
            return tryEnterInflated(object, lock, mark, thread, trace, counters);
        }

        // Check if lock-stack is full.
        // Note: hotspot forces 'greater' comparison by subtracting 1 from the end-offset. We still
        // use 'greaterEqual' because Graal will anyway transform the 'greater' operation into
        // 'greaterEqual'.
        if (probability(SLOW_PATH_PROBABILITY, lockStackTop.greaterOrEqual(javaThreadLockStackEndOffset(INJECTED_VMCONFIG)))) {
            traceObject(trace, "+lock{lightweight:fulllockstack}", object, true);
            counters.lockFastRuntimeConstraint.inc();
            return false;
        }

        Pointer objectPointer = Word.objectToTrackedPointer(object);
        if (probability(FAST_PATH_PROBABILITY, tryLightweightLockingHelper(object, objectPointer, mark, thread, trace, counters, lockStackTop))) {
            if (useObjectMonitorTable(INJECTED_VMCONFIG)) {
                // Need to reload top, clobbered by CAS.
                lockStackTop = Word.unsigned(thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION));
            }
            // Push object to lock-stack.
            // Here we don't re-read LockStack::_top as it is thread-local data.
            thread.writeWord(lockStackTop, objectPointer, JAVA_THREAD_LOCK_STACK_LOCATION);
            thread.writeInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), (int) (lockStackTop.rawValue()) + wordSize(), JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
            return true;
        }
        traceObject(trace, "+lock{lightweight:failed-cas}", object, true);
        counters.lockFastFailedCas.inc();
        return false;
    }

    private static boolean tryLightweightLockingHelper(Object object, Pointer objectPointer, Word mark, Word thread, boolean trace, Counters counters, Word lockStackTop) {
        // Check if immediately recursive.
        Word lastLock = thread.readWord(lockStackTop.add(-wordSize()), JAVA_THREAD_LOCK_STACK_LOCATION);
        if (probability(SLOW_PATH_PROBABILITY, objectPointer.equal(lastLock))) {
            traceObject(trace, "+lock{lightweight:recursive}", object, true);
            counters.lockFastRecursive.inc();
            return true;
        }

        // Try to lock. Transition lock bits 0b01 => 0b00
        Word markUnlocked = mark.or(unlockedValue(INJECTED_VMCONFIG));
        Word markLocked = markUnlocked.and(~unlockedValue(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, objectPointer.logicCompareAndSwapWord(markOffset(INJECTED_VMCONFIG), markUnlocked, markLocked, MARK_WORD_LOCATION))) {
            traceObject(trace, "+lock{lightweight:cas}", object, true);
            counters.lockFastCas.inc();
            return true;
        }

        return false;
    }

    @Snippet
    public static void monitorexit(Object object, @ConstantParameter int lockDepth, @ConstantParameter Register threadRegister, @ConstantParameter boolean trace,
                    @ConstantParameter Counters counters) {
        verifyOop(object);

        final Word thread = registerAsWord(threadRegister);
        final Word lock = CurrentLockNode.currentLock(lockDepth);

        trace(trace, "           object: 0x%016lx\n", Word.objectToTrackedPointer(object));
        trace(trace, "             lock: 0x%016lx\n", lock);

        if (!tryLightweightUnlocking(object, thread, lock, trace, counters)) {
            monitorexitStubC(MONITOREXIT, object, lock);
        }
        endLockScope();
        decCounter();
    }

    private static boolean tryLightweightUnlocking(Object object, Word thread, Word lock, boolean trace, Counters counters) {
        // Load top
        Word lockStackTop = Word.unsigned(thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION));
        Word newLockStackTop = lockStackTop.add(-wordSize());

        // Prefetch mark
        Word mark = loadWordFromObject(object, markOffset(INJECTED_VMCONFIG));

        // Check if obj is top of lock-stack.
        Pointer objectPointer = Word.objectToTrackedPointer(object);

        if (probability(SLOW_PATH_PROBABILITY, objectPointer.notEqual(thread.readWord(newLockStackTop, JAVA_THREAD_LOCK_STACK_LOCATION)))) {
            if (isCAssertEnabled(INJECTED_VMCONFIG)) {
                // TODO check object is not in the whole lock stack.
                // Pending on exporting LockStack::_base_offset. See
                // https://github.com/openjdk/jdk/blob/7f6bb71eb302e8388c959bdaa914b758a766d299/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L1102-L1109
                if (probability(NOT_FREQUENT_PROBABILITY, mark.and(monitorValue(INJECTED_VMCONFIG)).equal(0))) {
                    AssertionSnippets.vmMessageC(VM_MESSAGE_C, true, cstring("Fast Unlock not monitor"), 0L, 0L, 0L);
                }
            }
            return tryExitInflated(object, mark, thread, lock, trace, counters);
        }

        // Pop lock-stack.
        if (isCAssertEnabled(INJECTED_VMCONFIG)) {
            thread.writeWord(newLockStackTop, Word.zero(), JAVA_THREAD_LOCK_STACK_LOCATION);
        }
        thread.writeInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), (int) newLockStackTop.rawValue(), JAVA_THREAD_LOCK_STACK_TOP_LOCATION);

        // Check if recursive.
        if (probability(SLOW_PATH_PROBABILITY, objectPointer.equal(thread.readWord(lockStackTop.add(-2 * wordSize()), JAVA_THREAD_LOCK_STACK_LOCATION)))) {
            traceObject(trace, "-lock{lightweight:recursive}", object, false);
            counters.unlockFastRecursive.inc();
            return true;
        }

        // We elide the monitor check, let the CAS fail instead.

        // Try to unlock. Transition lock bits 0b00 => 0b01
        Word markLocked = mark.and(Word.unsigned(~markWordLockMaskInPlace(INJECTED_VMCONFIG)));
        Word markUnlocked = mark.or(unlockedValue(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, objectPointer.logicCompareAndSwapWord(markOffset(INJECTED_VMCONFIG), markLocked, markUnlocked, MARK_WORD_LOCATION))) {
            traceObject(trace, "-lock{lightweight:cas}", object, false);
            counters.unlockFastCas.inc();
            return true;
        }

        // Restore lock-stack.
        if (isCAssertEnabled(INJECTED_VMCONFIG)) {
            thread.writeWord(newLockStackTop, objectPointer, JAVA_THREAD_LOCK_STACK_LOCATION);
        }
        thread.writeInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), (int) lockStackTop.rawValue(), JAVA_THREAD_LOCK_STACK_TOP_LOCATION);
        traceObject(trace, "-lock{lightweight:failed-cas}", object, false);
        counters.unlockFastFailedCas.inc();
        return false;
    }

    private static boolean tryExitInflated(Object object, Word mark, Word thread, Word lock, boolean trace, Counters counters) {
        // Inflated case
        Word monitor;
        if (useObjectMonitorTable(INJECTED_VMCONFIG)) {
            monitor = lock.readWord(lockMetadataOffset(INJECTED_VMCONFIG), BASICLOCK_METADATA_LOCATION);
            if (probability(SLOW_PATH_PROBABILITY, monitor.isNull())) {
                traceObject(trace, "-lock{heavyweight:no-cache}", object, false);
                counters.unlockHeavyNoCache.inc();
                return false;
            }
        } else {
            // mark is a pointer to the ObjectMonitor + monitorMask
            monitor = mark.subtract(monitorValue(INJECTED_VMCONFIG));
        }

        int ownerOffset = objectMonitorOwnerOffset(INJECTED_VMCONFIG);

        int recursionsOffset = objectMonitorRecursionsOffset(INJECTED_VMCONFIG);
        Word recursions = monitor.readWord(recursionsOffset, OBJECT_MONITOR_RECURSION_LOCATION);
        if (probability(FAST_PATH_PROBABILITY, recursions.equal(0))) {
            // Set owner to null.
            memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
            monitor.writeWord(ownerOffset, zero());
            memoryBarrier(MembarNode.FenceKind.STORE_LOAD);
            // Note that we read the entry list after dropping the lock, so the values need not
            // form a stable snapshot.
            Word entryList = monitor.readWord(objectMonitorEntryListOffset(INJECTED_VMCONFIG), OBJECT_MONITOR_ENTRY_LIST_LOCATION);
            // Check if the entry list is empty.
            if (probability(FREQUENT_PROBABILITY, entryList.isNull())) {
                traceObject(trace, "-lock{heavyweight:simple}", object, false);
                counters.unlockHeavySimple.inc();
                return true;
            }
            // Check if there is a successor.
            Word succ = monitor.readWord(objectMonitorSuccOffset(INJECTED_VMCONFIG), OBJECT_MONITOR_SUCC_LOCATION);
            if (probability(FREQUENT_PROBABILITY, succ.isNonNull())) {
                // We manage to release the monitor before the other running thread even
                // notices.
                traceObject(trace, "-lock{heavyweight:transfer}", object, false);
                counters.unlockHeavyTransfer.inc();
                return true;
            } else {
                thread.writeWord(javaThreadUnlockedInflatedMonitorOffset(INJECTED_VMCONFIG), monitor, JAVA_THREAD_UNLOCKED_INFLATED_MONITOR_LOCATION);
            }
        } else {
            // Recursive inflated unlock
            monitor.writeWord(recursionsOffset, recursions.subtract(1), OBJECT_MONITOR_RECURSION_LOCATION);
            traceObject(trace, "-lock{heavyweight:recursive}", object, false);
            counters.unlockHeavyRecursive.inc();
            return true;
        }
        traceObject(trace, "-lock{heavyweight:stub}", object, false);
        counters.unlockHeavyStub.inc();
        return false;
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
        // Counters for the various paths for acquiring a lock.
        public final SnippetCounter lockFastCas;
        public final SnippetCounter lockFastFailedCas;
        public final SnippetCounter lockFastRecursive;
        public final SnippetCounter lockFastRuntimeConstraint;
        public final SnippetCounter lockHeavyCas;
        public final SnippetCounter lockHeavyFailedCas;
        public final SnippetCounter lockHeavyRecursive;
        public final SnippetCounter lockHeavyOwned;

        // Counters for the various paths for releasing a lock.
        public final SnippetCounter unlockFastCas;
        public final SnippetCounter unlockFastRecursive;
        public final SnippetCounter unlockFastFailedCas;
        public final SnippetCounter unlockHeavyStub;
        public final SnippetCounter unlockHeavyNoCache;
        public final SnippetCounter unlockHeavySimple;
        public final SnippetCounter unlockHeavyTransfer;
        public final SnippetCounter unlockHeavyRecursive;

        public Counters(SnippetCounter.Group.Factory factory) {
            SnippetCounter.Group enter = factory.createSnippetCounterGroup("MonitorEnters");
            SnippetCounter.Group exit = factory.createSnippetCounterGroup("MonitorExits");
            lockFastCas = new SnippetCounter(enter, "lock{fast:cas}", "fast locking, cas-locked");
            lockFastRecursive = new SnippetCounter(enter, "lock{fast:recursive}", "fast locking, recursive");
            lockFastFailedCas = new SnippetCounter(enter, "lock{fast:failed-cas}", "fast locking, failed cas, call stub");
            lockFastRuntimeConstraint = new SnippetCounter(enter, "lock{fast:runtime-constraint}", "fast locking, runtime constraint, call stub");
            lockHeavyCas = new SnippetCounter(enter, "lock{heavyweight:cas}", "heavyweight locking, cas-locked");
            lockHeavyRecursive = new SnippetCounter(enter, "lock{heavyweight:recursive}", "heavyweight locking, recursive");
            lockHeavyFailedCas = new SnippetCounter(enter, "lock{heavyweight:failed-cas}", "heavyweight locking, failed cas, call stub");
            lockHeavyOwned = new SnippetCounter(enter, "lock{heavyweight:owned}", "heavyweight locking, owned by other, call stub");

            unlockFastCas = new SnippetCounter(exit, "unlock{fast:cas}", "fast locking, cas-unlocked");
            unlockFastRecursive = new SnippetCounter(exit, "unlock{fast:recursive}", "fast locking, recursive");
            unlockFastFailedCas = new SnippetCounter(exit, "unlock{fast:failed-cas}", "fast locking, failed cas, call stub");
            unlockHeavySimple = new SnippetCounter(exit, "unlock{heavyweight:simple}", "heavyweight locking, unlocked an object monitor");
            unlockHeavyRecursive = new SnippetCounter(exit, "unlock{heavyweight:recursive}", "heavyweight locking, unlocked an object monitor, recursive");
            unlockHeavyTransfer = new SnippetCounter(exit, "unlock{heavyweight:transfer}", "heavyweight locking, unlocked an object monitor in the presence of ObjectMonitor::_succ");
            unlockHeavyStub = new SnippetCounter(exit, "unlock{heavyweight:stub}", "heavyweight locking, stub-unlocked an object with inflated monitor");
            unlockHeavyNoCache = new SnippetCounter(exit, "unlock{heavyweight:no-cache}", "heavyweight locking, no cached object monitor in the lock scope");
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo checkMonitorenter;
        private final SnippetInfo monitorenter;
        private final SnippetInfo monitorexit;
        private final SnippetInfo initCounter;
        private final SnippetInfo checkCounter;

        public final Counters counters;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, SnippetCounter.Group.Factory factory, HotSpotProviders providers) {
            super(options, providers);

            LocationIdentity[] enterLocations = new LocationIdentity[]{
                            JAVA_THREAD_LOCK_STACK_LOCATION,
                            JAVA_THREAD_LOCK_STACK_TOP_LOCATION,
                            JAVA_THREAD_OM_CACHE_LOCATION,
                            JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION,
                            JAVA_THREAD_MONITOR_OWNER_ID_LOCATION};
            LocationIdentity[] exitLocations = new LocationIdentity[]{
                            JAVA_THREAD_LOCK_STACK_LOCATION,
                            JAVA_THREAD_LOCK_STACK_TOP_LOCATION,
                            BASICLOCK_METADATA_LOCATION,
                            OBJECT_MONITOR_OWNER_LOCATION,
                            OBJECT_MONITOR_CXQ_LOCATION,
                            OBJECT_MONITOR_ENTRY_LIST_LOCATION,
                            OBJECT_MONITOR_RECURSION_LOCATION,
                            OBJECT_MONITOR_SUCC_LOCATION,
                            OBJECT_MONITOR_STACK_LOCKER_LOCATION,
                            MARK_WORD_LOCATION,
                            JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION};

            this.checkMonitorenter = snippet(providers, MonitorSnippets.class, "checkMonitorenter", enterLocations);
            this.monitorenter = snippet(providers, MonitorSnippets.class, "monitorenter", enterLocations);
            this.monitorexit = snippet(providers, MonitorSnippets.class, "monitorexit", exitLocations);
            this.initCounter = snippet(providers, MonitorSnippets.class, "initCounter");
            this.checkCounter = snippet(providers, MonitorSnippets.class, "checkCounter");

            this.counters = new Counters(factory);
        }

        public void lower(CheckFastPathMonitorEnterNode checkFastPathMonitorEnterNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = checkFastPathMonitorEnterNode.graph();
            Arguments args = new Arguments(checkMonitorenter, graph, tool.getLoweringStage());
            // Speculation.equals is too weak so it can incorrectly cache snippet graphs so just
            // disable caching of these graphs.
            args.setCacheable(false);
            args.add("lockCount", checkFastPathMonitorEnterNode.lockDepth());
            args.add("threadRegister", registers.getThreadRegister());
            args.add("speculation", graph.getSpeculationLog().speculate(MonitorEnterNode.MONITOR_ENTER_NO_SIDE_EFFECT.createSpeculationReason()));

            template(tool, checkFastPathMonitorEnterNode, args).instantiate(tool.getMetaAccess(), checkFastPathMonitorEnterNode, DEFAULT_REPLACER, args);
        }

        private static boolean isVirtualLock(FrameState frameState, int lockIdx) {
            MonitorIdNode monitorIdNode = frameState.monitorIdAt(lockIdx);
            if (monitorIdNode.isEliminated()) {
                return true;
            }

            ValueNode lock = frameState.lockAt(lockIdx);
            if (lock instanceof VirtualObjectNode virtualObject) {
                FrameState current = frameState;
                do {
                    if (current.virtualObjectMappingCount() > 0) {
                        for (EscapeObjectState state : current.virtualObjectMappings()) {
                            if (state instanceof VirtualObjectState && virtualObject == state.object()) {
                                return true;
                            }
                        }
                    }
                    current = current.outerFrameState();
                } while (current != null);
            }
            return false;
        }

        private static boolean verifyLockOrder(MonitorEnterNode monitorenterNode) {
            FrameState state = monitorenterNode.stateAfter();
            boolean subsequentLocksMustBeEliminated = false;
            for (int lockIdx = 0; lockIdx < state.locksSize(); lockIdx++) {
                if (subsequentLocksMustBeEliminated) {
                    if (!isVirtualLock(state, lockIdx)) {
                        return false;
                    }
                }
                if (state.monitorIdAt(lockIdx) == monitorenterNode.getMonitorId()) {
                    subsequentLocksMustBeEliminated = true;
                }
            }
            return true;
        }

        public void lower(MonitorEnterNode monitorenterNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = monitorenterNode.graph();
            checkBalancedMonitors(graph, tool);

            GraalError.guarantee(((ObjectStamp) monitorenterNode.object().stamp(NodeView.DEFAULT)).nonNull(), "should have a non-null stamp: %s", monitorenterNode);
            GraalError.guarantee(!monitorenterNode.getMonitorId().isEliminated(), "current monitor is eliminated: %s", monitorenterNode);
            GraalError.guarantee(verifyLockOrder(monitorenterNode), "locks are disordered: %s", monitorenterNode);

            Arguments args = new Arguments(monitorenter, graph, tool.getLoweringStage());
            args.add("object", monitorenterNode.object());
            args.add("hub", Objects.requireNonNull(monitorenterNode.getObjectData()));
            args.add("lockDepth", monitorenterNode.getMonitorId().getLockDepth());
            args.add("threadRegister", registers.getThreadRegister());
            args.add("stackPointerRegister", registers.getStackPointerRegister());
            args.add("trace", isTracingEnabledForType(monitorenterNode.object()) || isTracingEnabledForMethod(graph));
            args.add("synthetic", monitorenterNode.isSynthetic());
            args.add("counters", counters);

            template(tool, monitorenterNode, args).instantiate(tool.getMetaAccess(), monitorenterNode, DEFAULT_REPLACER, args);
        }

        public void lower(MonitorExitNode monitorexitNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = monitorexitNode.graph();

            Arguments args = new Arguments(monitorexit, graph, tool.getLoweringStage());
            args.add("object", monitorexitNode.object());
            args.add("lockDepth", monitorexitNode.getMonitorId().getLockDepth());
            args.add("threadRegister", registers.getThreadRegister());
            args.add("trace", isTracingEnabledForType(monitorexitNode.object()) || isTracingEnabledForMethod(graph));
            args.add("counters", counters);

            template(tool, monitorexitNode, args).instantiate(tool.getMetaAccess(), monitorexitNode, DEFAULT_REPLACER, args);
        }

        public static boolean isTracingEnabledForType(ValueNode object) {
            ResolvedJavaType type = StampTool.typeOrNull(object.stamp(NodeView.DEFAULT));
            String filter = HotspotSnippetsOptions.TraceMonitorsTypeFilter.getValue(object.getOptions());
            if (filter == null) {
                return false;
            } else {
                if (filter.isEmpty()) {
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
                if (filter.isEmpty()) {
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

                        Arguments args = new Arguments(checkCounter, graph, tool.getLoweringStage());
                        args.add("errMsg", new CStringConstant(msg));
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
