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

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LUDICROUSLY_FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Methods implementing the internals of {@link Reference} or providing access to them. These are
 * not injected into {@link Target_java_lang_ref_Reference} so that subclasses of {@link Reference}
 * cannot interfere with them.
 */
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

    public static <T> void clear(Reference<T> instance) {
        cast(instance).referent = null;
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#referent} as pointer. */
    public static <T> Pointer getReferentPointer(Reference<T> instance) {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.referentFieldOffset)));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getReferent(Reference<T> instance) {
        return (T) SubstrateUtil.cast(instance, Target_java_lang_ref_Reference.class).referent;
    }

    /** Barrier-less write of {@link Target_java_lang_ref_Reference#referent} as pointer. */
    public static <T> void setReferentPointer(Reference<T> instance, Pointer value) {
        ObjectAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.referentFieldOffset), value.toObject());
    }

    public static <T> Pointer getReferentFieldAddress(Reference<T> instance) {
        return Word.objectToUntrackedPointer(instance).add(WordFactory.unsigned(Target_java_lang_ref_Reference.referentFieldOffset));
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#discovered}. */
    public static <T> Reference<?> getNextDiscovered(Reference<T> instance) {
        return KnownIntrinsics.convertUnknownValue(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.discoveredFieldOffset)), Reference.class);
    }

    public static <T> Pointer getNextDiscoveredFieldAddress(Reference<T> instance) {
        return Word.objectToUntrackedPointer(instance).add(WordFactory.unsigned(Target_java_lang_ref_Reference.discoveredFieldOffset));
    }

    /** Barrier-less write of {@link Target_java_lang_ref_Reference#discovered}. */
    public static <T> void setNextDiscovered(Reference<T> instance, Reference<?> newNext) {
        ObjectAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.discoveredFieldOffset), newNext);
    }

    public static boolean hasQueue(Reference<?> instance) {
        return cast(instance).queue != Target_java_lang_ref_ReferenceQueue.NULL;
    }

    /*
     * We duplicate the JDK 11 reference processing code here so we can also use it with JDK 8.
     */

    // Checkstyle: allow synchronization

    private static final Object processPendingLock = new Object();
    private static boolean processPendingActive = false;

    public static void waitForPendingReferences() throws InterruptedException {
        Heap.getHeap().waitForReferencePendingList();
    }

    @SuppressFBWarnings(value = "NN_NAKED_NOTIFY", justification = "Notifies on progress, not a specific state change.")
    public static void processPendingReferences() {
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
        try {
            while (pendingList != null) {
                Target_java_lang_ref_Reference<?> ref = pendingList;
                pendingList = ref.discovered;
                ref.discovered = null;

                if (Target_jdk_internal_ref_Cleaner.class.isInstance(ref)) {
                    Target_jdk_internal_ref_Cleaner cleaner = Target_jdk_internal_ref_Cleaner.class.cast(ref);
                    // Cleaner catches all exceptions, cannot be overridden due to private c'tor
                    cleaner.clean();
                    synchronized (processPendingLock) {
                        processPendingLock.notifyAll();
                    }
                } else if (hasQueue(uncast(ref))) {
                    enqueueDirectly(ref);
                }
            }
        } catch (Throwable t) {
            VMError.shouldNotReachHere("ReferenceQueue and Cleaner must handle all potential exceptions", t);
        } finally {
            synchronized (processPendingLock) {
                processPendingActive = false;
                processPendingLock.notifyAll();
            }
        }
    }

    /** Enqueues, avoiding the potentially overridden {@link Reference#enqueue()}. */
    private static <T> void enqueueDirectly(Target_java_lang_ref_Reference<T> ref) {
        ref.queue.enqueue(ref);
    }

    @SuppressFBWarnings(value = "WA_NOT_IN_LOOP", justification = "Wait for progress, not necessarily completion.")
    public static boolean waitForReferenceProcessing() throws InterruptedException {
        synchronized (processPendingLock) {
            if (processPendingActive || Heap.getHeap().hasReferencePendingList()) {
                processPendingLock.wait(); // Wait for progress, not necessarily completion
                return true;
            } else {
                return false;
            }
        }
    }

    // Checkstyle: disallow synchronization

    public static long getSoftReferenceClock() {
        return Target_java_lang_ref_SoftReference.clock;
    }

    public static void updateSoftReferenceClock() {
        long now = TimeUtils.divideNanosToMillis(System.nanoTime()); // should be monotonous, ensure
        if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, now >= Target_java_lang_ref_SoftReference.clock)) {
            Target_java_lang_ref_SoftReference.clock = now;
        }
    }

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
        throw new GraalError("missing field " + fieldName + " in type " + type);
    }

    private ReferenceInternals() {
    }
}
