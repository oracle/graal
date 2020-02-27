package com.oracle.svm.core.heap;

import java.lang.ref.Reference;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.snippets.KnownIntrinsics;

// Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * Methods implementing the internals of {@link Reference} or providing access to them. These are
 * not injected into {@link Target_java_lang_ref_Reference} so that subclasses of {@link Reference}
 * cannot override them.
 */
public class ReferenceInternals {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /**
     * Garbage collection might run between the allocation of this object and before its constructor
     * is called, so this returns a flag that is set in the constructor (which is
     * {@link Uninterruptible}) and indicates whether the instance is fully initialized.
     */
    public static <T> boolean isInitialized(Target_java_lang_ref_Reference<T> instance) {
        return instance.initialized;
    }

    /** Provided for direct access because {@link Reference#clear()} can be overridden. */
    public static <T> void doClear(Target_java_lang_ref_Reference<T> instance) {
        instance.rawReferent = null;
    }

    /** Provided for direct access because {@link Reference#enqueue()} can be overridden. */
    public static <T> boolean doEnqueue(Target_java_lang_ref_Reference<T> instance) {
        Target_java_lang_ref_ReferenceQueue<? super T> q = getFutureQueue(instance);
        if (q != null) {
            return q.enqueue(instance);
        }
        return false;
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#rawReferent} as pointer. */
    public static <T> Pointer getReferentPointer(Target_java_lang_ref_Reference<T> instance) {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.rawReferentFieldOffset)));
    }

    /** Barrier-less write of {@link Target_java_lang_ref_Reference#rawReferent} as pointer. */
    public static <T> void setReferentPointer(Target_java_lang_ref_Reference<T> instance, Pointer value) {
        ObjectAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.rawReferentFieldOffset), value.toObject());
    }

    public static <T> boolean isDiscovered(Target_java_lang_ref_Reference<T> instance) {
        return instance.isDiscovered;
    }

    /** Barrier-less read of {@link Target_java_lang_ref_Reference#nextDiscovered}. */
    public static <T> Target_java_lang_ref_Reference<?> getNextDiscovered(Target_java_lang_ref_Reference<T> instance) {
        return KnownIntrinsics.convertUnknownValue(ObjectAccess.readObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.nextDiscoveredFieldOffset)),
                        Target_java_lang_ref_Reference.class);
    }

    public static <T> void clearDiscovered(Target_java_lang_ref_Reference<T> instance) {
        setNextDiscovered(instance, null, false);
    }

    public static <T> Target_java_lang_ref_Reference<T> setNextDiscovered(Target_java_lang_ref_Reference<T> instance, Target_java_lang_ref_Reference<?> newNext) {
        setNextDiscovered(instance, newNext, true);
        return instance;
    }

    /** Barrier-less write of {@link Target_java_lang_ref_Reference#nextDiscovered}. */
    private static <T> void setNextDiscovered(Target_java_lang_ref_Reference<T> instance, Target_java_lang_ref_Reference<?> newNext, boolean newIsDiscovered) {
        ObjectAccess.writeObject(instance, WordFactory.signed(Target_java_lang_ref_Reference.nextDiscoveredFieldOffset), newNext);
        instance.isDiscovered = newIsDiscovered;
    }

    /**
     * The address of the {@link Target_java_lang_ref_Reference#nextDiscovered} field of this
     * instance.
     */
    public static <T> Pointer getNextDiscoveredFieldPointer(Target_java_lang_ref_Reference<T> instance) {
        return Word.objectToUntrackedPointer(instance).add(WordFactory.signed(Target_java_lang_ref_Reference.nextDiscoveredFieldOffset));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> Target_java_lang_ref_ReferenceQueue<? super T> getFutureQueue(Target_java_lang_ref_Reference<T> instance) {
        return instance.futureQueue;
    }

    /**
     * Clears the queue on which this reference should eventually be enqueued and returns the
     * previous value, which may be {@code null} if this reference should not be put on a queue, but
     * also if the method has been called before -- such as, in a race to queue it.
     */
    @SuppressWarnings("unchecked")
    static <T> Target_java_lang_ref_ReferenceQueue<? super T> clearFutureQueue(Target_java_lang_ref_Reference<T> instance) {
        return (Target_java_lang_ref_ReferenceQueue<? super T>) UNSAFE.getAndSetObject(instance, Target_java_lang_ref_Reference.futureQueueFieldOffset, null);
    }

    /** Provided for direct access because {@link Reference#isEnqueued()} can be overridden. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> boolean testIsEnqueued(Target_java_lang_ref_Reference<T> instance) {
        return (instance.nextInQueue != instance);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> Target_java_lang_ref_Reference<?> getQueueNext(Target_java_lang_ref_Reference<T> instance) {
        return instance.nextInQueue;
    }

    static <T> void setQueueNext(Target_java_lang_ref_Reference<T> instance, Target_java_lang_ref_Reference<?> newNext) {
        assert newNext != instance : "Creating self-loop.";
        instance.nextInQueue = newNext;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> void clearQueueNext(Target_java_lang_ref_Reference<T> instance) {
        instance.nextInQueue = instance;
    }
}
