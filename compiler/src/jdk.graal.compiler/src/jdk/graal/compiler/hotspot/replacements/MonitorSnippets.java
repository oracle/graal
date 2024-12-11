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
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_MISC_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_CXQ_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_ENTRY_LIST_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_OWNER_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_RECURSION_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_STACK_LOCKER_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJECT_MONITOR_SUCC_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.diagnoseSyncOnValueBasedClasses;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.heldMonitorCountOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.isCAssertEnabled;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadLockStackEndOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadLockStackTopOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadMonitorOwnerIDOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadOomCacheOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.javaThreadUnlockedInflatedMonitorOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.jvmAccIsValueBasedClass;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassAccessFlagsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassMiscFlagsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.lockMetadataOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.markWordLockMaskInPlace;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.monitorValue;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorCxqOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorEntryListOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorOwnerOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorRecursionsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.objectMonitorSuccOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.omCacheOopToMonitorDifference;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.omCacheOopToOopDifference;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.pageSize;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.shouldUseKlassMiscFlags;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.unlockedValue;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.unusedMark;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useLightweightLocking;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useObjectMonitorTable;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.useStackLocking;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.verifyOop;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static jdk.graal.compiler.hotspot.replacements.HotspotSnippetsOptions.ProfileMonitors;
import static jdk.graal.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.MembarNode.memoryBarrier;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.graal.compiler.replacements.nodes.CStringConstant.cstring;
import static org.graalvm.word.LocationIdentity.any;
import static org.graalvm.word.WordFactory.nullPointer;
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
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
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
@SyncPort(from = "https://github.com/openjdk/jdk/blob/c113f82f78c7d9be1ac297aebfeb6051f0f904fb/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L175-L499",
          sha1 = "7bf54a5cd262e46c33067da8674032974e80af08")
// @formatter:on
public class MonitorSnippets implements Snippets {

    /**
     * This helper method is for bypassing the mandatory branch probabilities for snippets.
     *
     * See also {@code jdk.graal.compiler.core.test.VerifySnippetProbabilities}
     */
    @Fold
    public static boolean isJDK21() {
        return JavaVersionUtil.JAVA_SPEC == 21;
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

        if (diagnoseSyncOnValueBasedClasses(INJECTED_VMCONFIG)) {
            int flags = shouldUseKlassMiscFlags() ? hub.readByte(klassMiscFlagsOffset(INJECTED_VMCONFIG), KLASS_MISC_FLAGS_LOCATION)
                            : hub.readInt(klassAccessFlagsOffset(INJECTED_VMCONFIG), KLASS_ACCESS_FLAGS_LOCATION);
            if (probability(SLOW_PATH_PROBABILITY, (flags & jvmAccIsValueBasedClass(INJECTED_VMCONFIG)) != 0)) {
                monitorenterStubC(MONITORENTER, object, lock);
                return;
            }
        }

        if (tryFastPathLocking(object, stackPointerRegister, trace, counters, mark, lock, thread)) {
            if (isJDK21() || useStackLocking(INJECTED_VMCONFIG)) {
                incrementHeldMonitorCount(thread);
            }
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
        Word newOwner = isJDK21() ? thread : thread.readWord(javaThreadMonitorOwnerIDOffset(INJECTED_VMCONFIG), JAVA_THREAD_MONITOR_OWNER_ID_LOCATION);

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

    private static boolean tryStackLocking(Object object, Word lock, Word mark, Word thread, boolean trace, Counters counters, Register stackPointerRegister) {
        if (probability(SLOW_PATH_PROBABILITY, mark.and(monitorValue(INJECTED_VMCONFIG)).notEqual(0))) {
            // Inflated case
            // Set the lock slot's displaced mark to unused. Any non-0 value suffices.
            lock.writeWord(lockMetadataOffset(INJECTED_VMCONFIG), WordFactory.unsigned(unusedMark(INJECTED_VMCONFIG)), BASICLOCK_METADATA_LOCATION);
            return tryEnterInflated(object, lock, mark, thread, trace, counters);
        }

        Pointer objectPointer = Word.objectToTrackedPointer(object);

        // Create the unlocked mark word pattern
        Word unlockedMark = mark.or(unlockedValue(INJECTED_VMCONFIG));
        trace(trace, "     unlockedMark: 0x%016lx\n", unlockedMark);

        // Copy this unlocked mark word into the lock slot on the stack
        lock.writeWord(lockMetadataOffset(INJECTED_VMCONFIG), unlockedMark, BASICLOCK_METADATA_LOCATION);

        // Test if the object's mark word is unlocked, and if so, store the (address of) the
        // lock slot into the object's mark word.
        //
        // Since pointer cas operations are volatile accesses, previous stores cannot float
        // below it.
        Word currentMark = objectPointer.compareAndSwapWord(markOffset(INJECTED_VMCONFIG), unlockedMark, lock, MARK_WORD_LOCATION);
        if (probability(FAST_PATH_PROBABILITY, currentMark.equal(unlockedMark))) {
            traceObject(trace, "+lock{stack:cas}", object, true);
            counters.lockFastCas.inc();
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
                lock.writeWord(lockMetadataOffset(INJECTED_VMCONFIG), zero(), BASICLOCK_METADATA_LOCATION);
                traceObject(trace, "+lock{stack:recursive}", object, true);
                counters.lockFastRecursive.inc();
                return true;
            }
            traceObject(trace, "+lock{stack:failed-cas}", object, true);
            counters.lockFastFailedCas.inc();
        }
        return false;
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/c113f82f78c7d9be1ac297aebfeb6051f0f904fb/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L501-L667",
              sha1 = "1c396a67926e92a2cbc64c27fe667142e9ec157d")
    // @formatter:on
    @SuppressWarnings("unused")
    private static boolean tryLightweightLocking(Object object, Word lock, Word mark, Word thread, boolean trace, Counters counters, Register stackPointerRegister) {
        writeMonitorCache(lock, WordFactory.nullPointer());
        // Prefetch top
        // We assume `lockStackTop' is always positive and use `WordFactory.unsigned' to skip a sign
        // extension.
        Word lockStackTop = WordFactory.unsigned(thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION));

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
                lockStackTop = WordFactory.unsigned(thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION));
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

        if (tryFastPathUnlocking(object, trace, counters, thread, lock)) {
            if (isJDK21() || useStackLocking(INJECTED_VMCONFIG)) {
                decrementHeldMonitorCount(thread);
            }
        } else {
            monitorexitStubC(MONITOREXIT, object, lock);
        }
        endLockScope();
        decCounter();
    }

    /**
     * Dispatch to the appropriate unlocking strategy based on the {@code LockingMode} flag value.
     */
    private static boolean tryFastPathUnlocking(Object object, boolean trace, Counters counters, Word thread, Word lock) {
        if (useLightweightLocking(INJECTED_VMCONFIG)) {
            return tryLightweightUnlocking(object, thread, lock, trace, counters);
        } else if (useStackLocking(INJECTED_VMCONFIG)) {
            return tryStackUnlocking(object, thread, lock, trace, counters);
        } else {
            // LM_MONITOR case, i.e., use heavy monitor directly
            return false;
        }
    }

    private static boolean tryStackUnlocking(Object object, Word thread, Word lock, boolean trace, Counters counters) {
        final Word displacedMark = lock.readWord(lockMetadataOffset(INJECTED_VMCONFIG), BASICLOCK_METADATA_LOCATION);

        if (probability(NOT_LIKELY_PROBABILITY, displacedMark.equal(0))) {
            // Recursive locking => done
            traceObject(trace, "-lock{stack:recursive}", object, false);
            counters.unlockFastRecursive.inc();
            return true;
        }

        Word mark = loadWordFromObject(object, markOffset(INJECTED_VMCONFIG));

        if (probability(SLOW_PATH_PROBABILITY, mark.and(monitorValue(INJECTED_VMCONFIG)).notEqual(0))) {
            return tryExitInflated(object, mark, thread, lock, trace, counters);
        }

        if (probability(VERY_FAST_PATH_PROBABILITY, Word.objectToTrackedPointer(object).logicCompareAndSwapWord(markOffset(INJECTED_VMCONFIG),
                        lock, displacedMark, MARK_WORD_LOCATION))) {
            traceObject(trace, "-lock{stack:cas}", object, false);
            counters.unlockFastCas.inc();
            return true;
        }

        // The object's mark word was not pointing to the displaced header
        traceObject(trace, "-lock{stack:failed-cas}", object, false);
        counters.unlockFastFailedCas.inc();
        return false;
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/78b80150e009745b8f28d36c3836f18ad0ca921f/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L669-L838",
              sha1 = "34c70e9dd2d3093e82189a25c056e7fdb1aff506")
    // @formatter:on
    private static boolean tryLightweightUnlocking(Object object, Word thread, Word lock, boolean trace, Counters counters) {
        // Load top
        Word lockStackTop = WordFactory.unsigned(thread.readInt(javaThreadLockStackTopOffset(INJECTED_VMCONFIG), JAVA_THREAD_LOCK_STACK_TOP_LOCATION));
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
            thread.writeWord(newLockStackTop, WordFactory.zero(), JAVA_THREAD_LOCK_STACK_LOCATION);
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
        Word markLocked = mark.and(WordFactory.unsigned(~markWordLockMaskInPlace(INJECTED_VMCONFIG)));
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
            if (isJDK21()) {
                // recursions == 0
                Word entryList = monitor.readWord(objectMonitorEntryListOffset(INJECTED_VMCONFIG), OBJECT_MONITOR_ENTRY_LIST_LOCATION);
                Word cxq = monitor.readWord(objectMonitorCxqOffset(INJECTED_VMCONFIG), OBJECT_MONITOR_CXQ_LOCATION);
                if (probability(FREQUENT_PROBABILITY, entryList.or(cxq).equal(0))) {
                    // entryList == 0 && cxq == 0
                    // Nobody is waiting, success
                    // release_store
                    memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
                    monitor.writeWord(ownerOffset, nullPointer(), OBJECT_MONITOR_OWNER_LOCATION);
                    traceObject(trace, "-lock{heavyweight:simple}", object, false);
                    counters.unlockHeavySimple.inc();
                    return true;
                } else {
                    int succOffset = objectMonitorSuccOffset(INJECTED_VMCONFIG);
                    Word succ = monitor.readWord(succOffset, OBJECT_MONITOR_SUCC_LOCATION);
                    if (probability(FREQUENT_PROBABILITY, succ.isNonNull())) {
                        // There may be a thread spinning on this monitor. Temporarily setting
                        // the monitor owner to null, and hope that the other thread will grab it.
                        monitor.writeWordVolatile(ownerOffset, nullPointer());
                        succ = monitor.readWordVolatile(succOffset, OBJECT_MONITOR_SUCC_LOCATION);
                        if (probability(NOT_FREQUENT_PROBABILITY, succ.isNonNull())) {
                            // We manage to release the monitor before the other running thread even
                            // notices.
                            traceObject(trace, "-lock{heavyweight:transfer}", object, false);
                            counters.unlockHeavyTransfer.inc();
                            return true;
                        } else {
                            // Either the monitor is grabbed by a spinning thread, or the spinning
                            // thread parks. Now we attempt to reset the owner of the monitor.
                            if (probability(FREQUENT_PROBABILITY, !monitor.logicCompareAndSwapWord(ownerOffset, zero(), thread, OBJECT_MONITOR_OWNER_LOCATION))) {
                                // The monitor is stolen.
                                traceObject(trace, "-lock{heavyweight:transfer}", object, false);
                                counters.unlockHeavyTransfer.inc();
                                return true;
                            }
                        }
                    }
                }
            } else {
                // Set owner to null.
                memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
                monitor.writeWord(ownerOffset, zero());
                memoryBarrier(MembarNode.FenceKind.STORE_LOAD);
                // Note that we read the EntryList and then the cxq after dropping the
                // lock, so the values need not form a stable snapshot. In particular,
                // after reading the (empty) EntryList, another thread could acquire
                // and release the lock, moving any entries in the cxq to the
                // EntryList, causing the current thread to see an empty cxq and
                // conclude there are no waiters. But this is okay as the thread that
                // moved the cxq is responsible for waking the successor.
                Word entryList = monitor.readWord(objectMonitorEntryListOffset(INJECTED_VMCONFIG), OBJECT_MONITOR_ENTRY_LIST_LOCATION);
                Word cxq = monitor.readWord(objectMonitorCxqOffset(INJECTED_VMCONFIG), OBJECT_MONITOR_CXQ_LOCATION);
                // Check if the entry lists are empty.
                if (probability(FREQUENT_PROBABILITY, entryList.or(cxq).equal(0))) {
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

    private static void incrementHeldMonitorCount(Word thread) {
        updateHeldMonitorCount(thread, 1);
    }

    private static void decrementHeldMonitorCount(Word thread) {
        updateHeldMonitorCount(thread, -1);
    }

    private static void updateHeldMonitorCount(Word thread, int increment) {
        Word heldMonitorCount = thread.readWord(heldMonitorCountOffset(INJECTED_VMCONFIG), JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION);
        thread.writeWord(heldMonitorCountOffset(INJECTED_VMCONFIG), heldMonitorCount.add(increment), JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION);
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
                                JAVA_THREAD_OM_CACHE_LOCATION,
                                JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION,
                                JAVA_THREAD_MONITOR_OWNER_ID_LOCATION};
                exitLocations = new LocationIdentity[]{
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
            } else {
                enterLocations = new LocationIdentity[]{
                                JAVA_THREAD_HOLD_MONITOR_COUNT_LOCATION,
                                JAVA_THREAD_MONITOR_OWNER_ID_LOCATION};
                exitLocations = new LocationIdentity[]{
                                BASICLOCK_METADATA_LOCATION,
                                OBJECT_MONITOR_OWNER_LOCATION,
                                OBJECT_MONITOR_CXQ_LOCATION,
                                OBJECT_MONITOR_ENTRY_LIST_LOCATION,
                                OBJECT_MONITOR_RECURSION_LOCATION,
                                OBJECT_MONITOR_SUCC_LOCATION,
                                OBJECT_MONITOR_STACK_LOCKER_LOCATION,
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
            args.add("lockDepth", monitorenterNode.getMonitorId().getLockDepth());
            args.add("threadRegister", registers.getThreadRegister());
            args.add("stackPointerRegister", registers.getStackPointerRegister());
            args.add("trace", isTracingEnabledForType(monitorenterNode.object()) || isTracingEnabledForMethod(graph));
            args.add("counters", counters);

            template(tool, monitorenterNode, args).instantiate(tool.getMetaAccess(), monitorenterNode, DEFAULT_REPLACER, args);
        }

        public void lower(MonitorExitNode monitorexitNode, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = monitorexitNode.graph();

            Arguments args = new Arguments(monitorexit, graph.getGuardsStage(), tool.getLoweringStage());
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
