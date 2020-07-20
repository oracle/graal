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
import java.util.function.BooleanSupplier;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.ExcludeFromReferenceMap;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Substitution of {@link Reference}, which is the abstract base class of all non-strong reference
 * classes, the basis of the cleaner mechanism, and subject to special treatment by the garbage
 * collector.
 * <p>
 * Implementation methods are in the separate class {@link ReferenceInternals} because
 * {@link Reference} can be subclassed and subclasses could otherwise inadvertently override
 * injected methods by declaring methods with identical names and signatures.
 * <p>
 * This class serves three purposes:
 * <ul>
 * <li>It has a {@linkplain #referent reference to an object,} which is excluded from the reference
 * map. If the object is not otherwise strongly reachable, the garbage collector can choose to
 * reclaim it and will then set our reference (and possibly others) to {@code null}.
 * <li>It has {@linkplain #discovered linkage} to become part of a linked list of reference objects
 * that are discovered during garbage collection, when allocation is restricted, or become part of a
 * linked list of reference objects which are pending to be added to a reference queue.
 * <li>It has {@linkplain #next linkage} to optionally become part of a {@linkplain #queue linked
 * reference queue,} which is used to clean up resources associated with reclaimed objects.
 * </ul>
 */
@UnknownClass
@TargetClass(Reference.class)
@Substitute
public final class Target_java_lang_ref_Reference<T> {
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = ReferenceInternals.REFERENT_FIELD_NAME, declClass = Target_java_lang_ref_Reference.class) //
    static long referentFieldOffset;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "discovered", declClass = Target_java_lang_ref_Reference.class) //
    static long discoveredFieldOffset;

    /**
     * The object we reference. The field must not be in the regular reference map since we do all
     * the garbage collection support manually. The garbage collector performs Pointer-level access
     * to the field. This is fine from the point of view of the static analysis, because the field
     * stores by the garbage collector do not change the type of the referent.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeReferenceValue.class) //
    @ExcludeFromReferenceMap(reason = "Field is manually processed by the garbage collector.") //
    T referent;

    @SuppressWarnings("unused") //
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    @ExcludeFromReferenceMap(reason = "Some GCs process this field manually.", onlyIf = NotCardRememberedSetHeap.class) //
    transient Target_java_lang_ref_Reference<?> discovered;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ComputeQueueValue.class) //
    volatile Target_java_lang_ref_ReferenceQueue<? super T> queue;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    volatile Reference<?> next;

    @Substitute
    Target_java_lang_ref_Reference(T referent) {
        this(referent, null);
    }

    @Substitute
    @Uninterruptible(reason = "The initialization of the fields must be atomic with respect to collection.")
    Target_java_lang_ref_Reference(T referent, Target_java_lang_ref_ReferenceQueue<? super T> queue) {
        this.referent = referent;
        this.queue = (queue == null) ? Target_java_lang_ref_ReferenceQueue.NULL : queue;
    }

    @KeepOriginal
    native T get();

    @KeepOriginal
    native void clear();

    @KeepOriginal
    native boolean enqueue();

    @KeepOriginal
    native boolean isEnqueued();

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @SuppressWarnings("unused")
    static boolean tryHandlePending(boolean waitForNotify) {
        /*
         * This method in JDK 8 was replaced by waitForReferenceProcessing in JDK 11. On JDK 8, it
         * helped with reference handling by handling a single reference (if one is available). The
         * only caller (apart from the reference handling thread itself) in the JDK is
         * `Bits.reserveMemory`, which passes `false` as the parameter `waitForNotify`. So our
         * substitution, which always waits, is a considerable change in semantics. However, since
         * `Bits.reserveMemory` did not change much between JDK 8 and JDK 11, this is OK.
         */
        try {
            return ReferenceInternals.waitForReferenceProcessing();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            /*
             * The caller might loop until "there is no more progress", i.e., until this method
             * returns false. So returning true could lead to an infinite loop in the caller that is
             * not interruptible.
             */
            return false;
        }
    }

    /** May be used by {@code JavaLangRefAccess} via {@code SharedSecrets}. */
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    static boolean waitForReferenceProcessing() throws InterruptedException {
        return ReferenceInternals.waitForReferenceProcessing();
    }

    @Override
    @KeepOriginal //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    protected native Object clone() throws CloneNotSupportedException;

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings("unused")
    static void reachabilityFence(Object ref) {
        GraalDirectives.blackhole(ref);
    }
}

/** We provide our own {@link com.oracle.svm.core.heap.ReferenceHandler}. */
@TargetClass(value = Reference.class, innerClass = "ReferenceHandler")
@Delete
final class Target_java_lang_ref_Reference_ReferenceHandler {
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
class NotCardRememberedSetHeap implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !SubstrateOptions.UseCardRememberedSetHeap.getValue();
    }
}
