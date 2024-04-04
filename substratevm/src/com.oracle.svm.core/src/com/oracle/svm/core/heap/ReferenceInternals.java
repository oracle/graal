/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.BasedOnJDKClass;
import com.oracle.svm.core.util.TimeUtils;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Methods implementing the internals of {@link Reference} or providing access to them. These are
 * not injected into {@link Target_java_lang_ref_Reference} so that subclasses of {@link Reference}
 * cannot interfere with them.
 */
@BasedOnJDKClass(Reference.class)
public final class ReferenceInternals {
    public static final String REFERENT_FIELD_NAME = "referent";

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> Target_java_lang_ref_Reference<T> cast(Reference<T> instance) {
        return SubstrateUtil.cast(instance, Target_java_lang_ref_Reference.class);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> Reference<T> uncast(Target_java_lang_ref_Reference<T> instance) {
        return SubstrateUtil.cast(instance, Reference.class);
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#referent} as a pointer. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> Pointer getReferentPointer(Reference<T> instance) {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.referentFieldOffset)));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getReferent(Reference<T> instance) {
        return (T) SubstrateUtil.cast(instance, Target_java_lang_ref_Reference.class).referent;
    }

    /** Write {@link Target_java_lang_ref_Reference#referent}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setReferent(Reference<?> instance, Object value) {
        BarrieredAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.referentFieldOffset), value);
    }

    @Uninterruptible(reason = "Must be atomic with regard to garbage collection.")
    public static boolean refersTo(Reference<?> instance, Object value) {
        // JDK-8188055
        return value == ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.referentFieldOffset));
    }

    public static void clear(Reference<?> instance) {
        /*
         * `Reference.clear0` was added to fix following issues:
         *
         * JDK-8256517: This issue only affects GCs that do the reference processing concurrently
         * (i.e., Shenandoah and ZGC). G1 only processes references at safepoints, so this shouldn't
         * be an issue for Native Image
         *
         * JDK-8240696: This issue affects G1.
         *
         * This barrier-less write is to resolve JDK-8240696.
         */
        ObjectAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.referentFieldOffset), null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> Pointer getReferentFieldAddress(Reference<T> instance) {
        return Word.objectToUntrackedPointer(instance).add(WordFactory.unsigned(Target_java_lang_ref_Reference.referentFieldOffset));
    }

    public static long getReferentFieldOffset() {
        return Target_java_lang_ref_Reference.referentFieldOffset;
    }

    /** Read {@link Target_java_lang_ref_Reference#discovered}. */
    public static <T> Reference<?> getNextDiscovered(Reference<T> instance) {
        return uncast(cast(instance).discovered);
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#discovered} as a pointer. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> Pointer getDiscoveredPointer(Reference<T> instance) {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.discoveredFieldOffset)));
    }

    public static long getQueueFieldOffset() {
        return Target_java_lang_ref_Reference.queueFieldOffset;
    }

    public static long getNextFieldOffset() {
        return Target_java_lang_ref_Reference.nextFieldOffset;
    }

    public static long getNextDiscoveredFieldOffset() {
        return Target_java_lang_ref_Reference.discoveredFieldOffset;
    }

    public static boolean isAnyReferenceFieldOffset(long offset) {
        return offset == getReferentFieldOffset() || offset == getQueueFieldOffset() || offset == getNextFieldOffset() || offset == getNextDiscoveredFieldOffset();
    }

    /** Write {@link Target_java_lang_ref_Reference#discovered}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> void setNextDiscovered(Reference<T> instance, Reference<?> newNext) {
        BarrieredAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.discoveredFieldOffset), newNext);
    }

    public static boolean hasQueue(Reference<?> instance) {
        return cast(instance).queue != Target_java_lang_ref_ReferenceQueue.NULL;
    }

    /*
     * We duplicate the JDK 11 reference processing code here so we can also use it with JDK 8.
     */

    private static final Object processPendingLock = new Object();
    private static boolean processPendingActive = false;

    @NeverInline("Ensure that every exception can be caught, including implicit exceptions.")
    public static void waitForPendingReferences() throws InterruptedException {
        Heap.getHeap().waitForReferencePendingList();
    }

    @NeverInline("Ensure that every exception can be caught, including implicit exceptions.")
    @SuppressFBWarnings(value = "NN_NAKED_NOTIFY", justification = "Notifies on progress, not a specific state change.")
    public static void processPendingReferences() {
        /*
         * Note that catching an OutOfMemoryError (e.g. from failing to allocate another exception)
         * from here and resuming is not advisable because it can break synchronization. We should
         * generally see these only when processing individual references or cleaners.
         */

        Target_java_lang_ref_Reference<?> pendingList;
        synchronized (processPendingLock) {
            if (processPendingActive) {
                /*
                 * If there is no dedicated reference handler thread, this method may be called by
                 * multiple threads after a garbage collection, but only one of them can retrieve
                 * and process the list of newly pending references.
                 */
                return;
            }
            pendingList = cast(Heap.getHeap().getAndClearReferencePendingList());
            if (pendingList == null) {
                return;
            }
            processPendingActive = true;
        }

        // Process all references that were discovered by the GC.
        do {
            while (pendingList != null) {
                Target_java_lang_ref_Reference<?> ref = pendingList;
                pendingList = ref.discovered;
                ref.discovered = null;

                if (Target_jdk_internal_ref_Cleaner.class.isInstance(ref)) {
                    Target_jdk_internal_ref_Cleaner cleaner = Target_jdk_internal_ref_Cleaner.class.cast(ref);
                    // Cleaner catches all exceptions, cannot be overridden due to private c'tor
                    cleaner.clean();
                    synchronized (processPendingLock) {
                        // Notify any waiters that progress has been made. This improves latency
                        // for nio.Bits waiters, which are the only important ones.
                        processPendingLock.notifyAll();
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    Target_java_lang_ref_ReferenceQueue<? super Object> queue = SubstrateUtil.cast(ref.queue, Target_java_lang_ref_ReferenceQueue.class);
                    if (queue != Target_java_lang_ref_ReferenceQueue.NULL) {
                        // Enqueues, avoiding the potentially overridden Reference.enqueue().
                        queue.enqueue(ref);
                    }
                }
            }

            synchronized (processPendingLock) {
                /*
                 * If we do not have a dedicated reference handler thread, then it is essential to
                 * recheck if the GC created a new pending list in the meanwhile. Otherwise, pending
                 * references might not be processed as the thread that performed the GC may have
                 * skipped reference processing (processPendingActive was true for a while).
                 */
                pendingList = cast(Heap.getHeap().getAndClearReferencePendingList());
                if (pendingList == null) {
                    processPendingActive = false;
                }

                // We processed at least a few references, so notify potential waiters about the
                // progress.
                processPendingLock.notifyAll();
            }
        } while (pendingList != null);
    }

    @SuppressFBWarnings(value = "WA_NOT_IN_LOOP", justification = "Wait for progress, not necessarily completion.")
    public static boolean waitForReferenceProcessing() throws InterruptedException {
        assert !VMOperation.isInProgress() : "could cause a deadlock";
        assert !ReferenceHandlerThread.isReferenceHandlerThread() : "would cause a deadlock";

        if (ReferenceHandler.isExecutedManually()) {
            /*
             * When the reference handling is executed manually, then we don't know when pending
             * references will be processed. So, we must not block when there are pending references
             * as this could cause deadlocks.
             */
            return false;
        }

        synchronized (processPendingLock) {
            if (processPendingActive || Heap.getHeap().hasReferencePendingList()) {
                processPendingLock.wait(); // Wait for progress, not necessarily completion
                return true;
            } else {
                return false;
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getSoftReferenceClock() {
        return Target_java_lang_ref_SoftReference.clock;
    }

    public static void updateSoftReferenceClock() {
        long now = TimeUtils.divideNanosToMillis(System.nanoTime()); // should be monotonous, ensure
        if (probability(EXTREMELY_FAST_PATH_PROBABILITY, now >= Target_java_lang_ref_SoftReference.clock)) {
            Target_java_lang_ref_SoftReference.clock = now;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getSoftReferenceTimestamp(SoftReference<?> instance) {
        Target_java_lang_ref_SoftReference<?> ref = SubstrateUtil.cast(instance, Target_java_lang_ref_SoftReference.class);
        return ref.timestamp;
    }

    public static ResolvedJavaType getReferenceType(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(Reference.class);
    }

    public static ResolvedJavaField getReferentField(MetaAccessProvider metaAccess) {
        return getField(getReferenceType(metaAccess), ReferenceInternals.REFERENT_FIELD_NAME);
    }

    private static ResolvedJavaField getField(ResolvedJavaType type, String fieldName) {
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        throw new GraalError("Missing field " + fieldName + " in type " + type);
    }

    private ReferenceInternals() {
    }
}
