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

//Checkstyle: allow reflection

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.reflect.Field;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.ExcludeFromReferenceMap;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

// Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * The implementation of {@link java.lang.ref.Reference}, which is the abstract base class of all
 * non-strong reference classes, the basis of the {@linkplain com.oracle.svm.core.jdk.CleanerSupport
 * cleaner mechanism,} and subject to special treatment by the garbage collector.
 * <p>
 * This class serves three purposes:
 * <ul>
 * <li>It has a {@linkplain #rawReferent reference to an object,} which is not strong. Therefore, if
 * the object is not otherwise strongly reachable, the garbage collector can choose to reclaim it
 * and will then set our reference (and possibly others) to {@code null}.
 * <li>It has {@linkplain #nextDiscovered linkage} to be part of a linked list of reference objects
 * that are discovered during garbage collection, when allocation is restricted.
 * <li>It has {@linkplain #nextInQueue linkage} to optionally become part of a
 * {@linkplain #futureQueue linked reference queue,} which is used to clean up resources associated
 * with reclaimed objects.
 * </ul>
 */
@UnknownClass
@TargetClass(java.lang.ref.Reference.class)
@Substitute
public final class Target_java_lang_ref_Reference<T> {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "rawReferent", declClass = Target_java_lang_ref_Reference.class) //
    private static long rawReferentFieldOffset;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "nextDiscovered", declClass = Target_java_lang_ref_Reference.class) //
    private static long nextDiscoveredFieldOffset;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "futureQueue", declClass = Target_java_lang_ref_Reference.class) //
    private static long futureQueueFieldOffset;

    /** @see #isInitialized */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeTrue.class) //
    protected final boolean initialized;

    /**
     * The object we reference. The field must not be in the regular reference map since we do all
     * the garbage collection support manually. The garbage collector performs Pointer-level access
     * to the field. This is fine from the point of view of the static analysis, because the field
     * stores by the garbage collector do not change the type of the referent.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeReferenceValue.class) //
    @ExcludeFromReferenceMap("Field is manually processed by the garbage collector.") //
    protected T rawReferent;

    /**
     * Whether this reference is currently {@linkplain #nextDiscovered on a list} of references
     * discovered during garbage collection.
     * <p>
     * This cannot be replaced with the same self-link trick that is used for {@link #nextInQueue}
     * because during reference discovery, our reference object could have been moved, but
     * {@link #nextDiscovered} might not have been updated yet, and {@code this == next} would fail.
     * ({@link #nextDiscovered} != null is not valid either because there might not be a next node)
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    protected boolean isDiscovered;

    @SuppressWarnings("unused") //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    protected Target_java_lang_ref_Reference<?> nextDiscovered;

    /**
     * The queue to which this reference object will be added when the referent becomes unreachable.
     * This field becomes {@code null} when the reference object is enqueued.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeQueueValue.class) //
    protected volatile Target_java_lang_ref_ReferenceQueue<? super T> futureQueue;

    /**
     * If this reference is on a {@linkplain Target_java_lang_ref_ReferenceQueue queue}, the next
     * reference object on the queue. If the reference is not (yet) on a queue, set to {@code this}.
     */
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeThisInstanceValue.class) //
    private Target_java_lang_ref_Reference<?> nextInQueue;

    @Substitute
    Target_java_lang_ref_Reference(T referent) {
        this(referent, null);
    }

    @Substitute
    @Uninterruptible(reason = "The initialization of the fields must be atomic with respect to collection.")
    Target_java_lang_ref_Reference(T referent, Target_java_lang_ref_ReferenceQueue<? super T> queue) {
        this.rawReferent = referent;
        this.nextDiscovered = null;
        this.isDiscovered = false;
        this.futureQueue = queue;
        Target_java_lang_ref_ReferenceQueue.clearQueuedState(this);
        this.initialized = true;
    }

    @Substitute
    public T get() {
        return rawReferent;
    }

    @Substitute
    public void clear() {
        doClear();
    }

    @Substitute
    public boolean enqueue() {
        Target_java_lang_ref_ReferenceQueue<? super T> q = getFutureQueue();
        if (q != null) {
            return q.enqueue(this);
        }
        return false;
    }

    @Substitute
    public boolean isEnqueued() {
        return isInQueue();
    }

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @SuppressWarnings("unused")
    private static boolean tryHandlePending(boolean waitForNotify) {
        throw VMError.unimplemented();
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings("unused")
    private static boolean waitForReferenceProcessing() {
        throw VMError.unimplemented();
    }

    @Override
    @KeepOriginal //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    protected native Object clone() throws CloneNotSupportedException;

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    // @ForceInline
    @SuppressWarnings("unused")
    public static void reachabilityFence(Object ref) {
        GraalDirectives.blackhole(ref);
    }

    /**
     * Garbage collection might run between the allocation of this object and before its constructor
     * is called, so this returns a flag that is set in the constructor (which is
     * {@link Uninterruptible}) and indicates whether the instance is fully initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /** Provided for direct access because {@link #clear()} can be overridden. */
    public void doClear() {
        rawReferent = null;
    }

    /** Barrier-less read of {@link #rawReferent} as pointer. */
    public Pointer getReferentPointer() {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(this, WordFactory.signed(rawReferentFieldOffset)));
    }

    /** Barrier-less write of {@link #rawReferent} as pointer. */
    public void setReferentPointer(Pointer value) {
        ObjectAccess.writeObject(this, WordFactory.signed(rawReferentFieldOffset), value.toObject());
    }

    public boolean isDiscovered() {
        return isDiscovered;
    }

    /** Barrier-less read of {@link #nextDiscovered}. */
    public Target_java_lang_ref_Reference<?> getNextDiscovered() {
        return KnownIntrinsics.convertUnknownValue(ObjectAccess.readObject(this, WordFactory.signed(nextDiscoveredFieldOffset)), Target_java_lang_ref_Reference.class);
    }

    public void clearDiscovered() {
        setNextDiscovered(null, false);
    }

    public Target_java_lang_ref_Reference<T> setNextDiscovered(Target_java_lang_ref_Reference<?> newNext) {
        setNextDiscovered(newNext, true);
        return this;
    }

    /** Barrier-less write of {@link #nextDiscovered}. */
    private void setNextDiscovered(Target_java_lang_ref_Reference<?> newNext, boolean newIsDiscovered) {
        ObjectAccess.writeObject(this, WordFactory.signed(nextDiscoveredFieldOffset), newNext);
        isDiscovered = newIsDiscovered;
    }

    /** The address of the {@link #nextDiscovered} field of this instance. */
    public Pointer getNextDiscoveredFieldPointer() {
        return Word.objectToUntrackedPointer(this).add(WordFactory.signed(nextDiscoveredFieldOffset));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Target_java_lang_ref_ReferenceQueue<? super T> getFutureQueue() {
        return futureQueue;
    }

    /**
     * Clears the queue on which this reference should eventually be enqueued and returns the
     * previous value, which may be {@code null} if this reference should not be put on a queue, but
     * also if the method has been called before -- such as, in a race to queue it.
     */
    @SuppressWarnings("unchecked")
    Target_java_lang_ref_ReferenceQueue<? super T> clearFutureQueue() {
        return (Target_java_lang_ref_ReferenceQueue<? super T>) UNSAFE.getAndSetObject(this, futureQueueFieldOffset, null);
    }

    /** Provided for direct access because {@link #isEnqueued()} can be overridden. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInQueue() {
        return (nextInQueue != this);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Target_java_lang_ref_Reference<?> getQueueNext() {
        return nextInQueue;
    }

    void setQueueNext(Target_java_lang_ref_Reference<?> newNext) {
        assert newNext != this : "Creating self-loop.";
        nextInQueue = newNext;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void clearQueueNext() {
        nextInQueue = this;
    }

    public static final class TestingBackDoor {

        private TestingBackDoor() {
        }

        @NeverInline("Prevent the access from moving around")
        public static Pointer getReferentPointer(Reference<?> that) {
            return SubstrateUtil.cast(that, Target_java_lang_ref_Reference.class).getReferentPointer();
        }

        public static Reference<?> getNextDiscoveredReference(Reference<?> that) {
            Target_java_lang_ref_Reference<?> cast = SubstrateUtil.cast(that, Target_java_lang_ref_Reference.class);
            Target_java_lang_ref_Reference<?> next = cast.getNextDiscovered();
            return SubstrateUtil.cast(next, Reference.class);
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeReferenceValue implements CustomFieldValueComputer {

    private static final Field REFERENT_FIELD = ReflectionUtil.lookupField(Reference.class, "referent");

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        if (receiver instanceof PhantomReference) {
            /*
             * PhantomReference does not allow access to its object, so it is mostly useless to have
             * a PhantomReference on the image heap. But some JDK code uses it, e.g., for marker
             * values, so we cannot disallow PhantomReference for the image heap.
             */
            return null;
        }
        try {
            /*
             * Some subclasses of Reference overwrite Reference.get() to throw an error. Therefore,
             * we need to access the field directly using reflection.
             */
            return REFERENT_FIELD.get(receiver);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeQueueValue implements CustomFieldValueComputer {

    private static final Field QUEUE_FIELD = ReflectionUtil.lookupField(Reference.class, "queue");

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        try {
            return QUEUE_FIELD.get(receiver);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeThisInstanceValue implements CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return receiver;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ComputeTrue implements CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return true;
    }
}
