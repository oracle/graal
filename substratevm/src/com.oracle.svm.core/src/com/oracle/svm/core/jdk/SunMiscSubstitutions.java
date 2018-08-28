/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

// Checkstyle: allow reflection

import java.lang.ref.ReferenceQueue;
import java.util.function.Function;

import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.code.MemoryBarriers;
import sun.misc.Unsafe;

@TargetClass(sun.misc.Unsafe.class)
@SuppressWarnings({"static-method"})
final class Target_sun_misc_Unsafe {

    @Substitute
    private long allocateMemory(long bytes) {
        if (bytes < 0L || (Unsafe.ADDRESS_SIZE == 4 && bytes > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException();
        }
        Pointer result = UnmanagedMemory.malloc(WordFactory.unsigned(bytes));
        if (result.equal(0)) {
            throw new OutOfMemoryError();
        }
        return result.rawValue();
    }

    @Substitute
    private long reallocateMemory(long address, long bytes) {
        if (bytes == 0) {
            return 0L;
        } else if (bytes < 0L || (Unsafe.ADDRESS_SIZE == 4 && bytes > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException();
        }
        Pointer result;
        if (address != 0L) {
            result = UnmanagedMemory.realloc(WordFactory.unsigned(address), WordFactory.unsigned(bytes));
        } else {
            result = UnmanagedMemory.malloc(WordFactory.unsigned(bytes));
        }
        if (result.equal(0)) {
            throw new OutOfMemoryError();
        }
        return result.rawValue();
    }

    @Substitute
    private void freeMemory(long address) {
        if (address != 0L) {
            UnmanagedMemory.free(WordFactory.unsigned(address));
        }
    }

    @Substitute
    @Uninterruptible(reason = "Converts Object to Pointer.")
    private void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        MemoryUtil.copyConjointMemoryAtomic(Word.objectToUntrackedPointer(srcBase).add(WordFactory.signed(srcOffset)), Word.objectToUntrackedPointer(destBase).add(WordFactory.signed(destOffset)),
                        WordFactory.unsigned(bytes));
    }

    @Substitute
    @Uninterruptible(reason = "Converts Object to Pointer.")
    private void setMemory(Object destBase, long destOffset, long bytes, byte bvalue) {
        MemoryUtil.fillToMemoryAtomic(Word.objectToUntrackedPointer(destBase).add(WordFactory.signed(destOffset)), WordFactory.unsigned(bytes), bvalue);
    }

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int pageSize() {
        return (int) VirtualMemoryProvider.get().getGranularity().rawValue();
    }

    @Substitute
    public int arrayBaseOffset(Class<?> clazz) {
        return (int) LayoutEncoding.getArrayBaseOffset(DynamicHub.fromClass(clazz).getLayoutEncoding()).rawValue();
    }

    @Substitute
    public int arrayIndexScale(Class<?> clazz) {
        return LayoutEncoding.getArrayIndexScale(DynamicHub.fromClass(clazz).getLayoutEncoding());
    }

    @Substitute
    private void throwException(Throwable t) {
        /* Make the Java compiler happy by pretending we are throwing a non-checked exception. */
        throw KnownIntrinsics.unsafeCast(t, RuntimeException.class);
    }

    @Substitute
    public void loadFence() {
        final int fence = MemoryBarriers.LOAD_LOAD | MemoryBarriers.LOAD_STORE;
        MembarNode.memoryBarrier(fence);
    }

    @Substitute
    public void storeFence() {
        final int fence = MemoryBarriers.STORE_LOAD | MemoryBarriers.STORE_STORE;
        MembarNode.memoryBarrier(fence);
    }

    @Substitute
    public void fullFence() {
        final int fence = MemoryBarriers.LOAD_LOAD | MemoryBarriers.LOAD_STORE | MemoryBarriers.STORE_LOAD | MemoryBarriers.STORE_STORE;
        MembarNode.memoryBarrier(fence);
    }

    @Substitute
    boolean shouldBeInitialized(Class<?> c) {
        return !DynamicHub.fromClass(c).isInitialized();
    }

    @Substitute
    public void ensureClassInitialized(Class<?> c) {
        DynamicHub.fromClass(c).ensureInitialized();
    }
}

@TargetClass(className = "sun.misc.MessageUtils", onlyWith = JDK8OrEarlier.class)
final class Target_sun_misc_MessageUtils {

    /*
     * Low-level logging support in the JDK. Methods must not use char-to-byte conversions (because
     * they are used to report errors in the converters). We just redirect to the low-level SVM log
     * infrastructure.
     */

    @Substitute
    private static void toStderr(String msg) {
        Log.log().string(msg);
    }

    @Substitute
    private static void toStdout(String msg) {
        Log.log().string(msg);
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class Package_jdk_internal_ref implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass annotation) {
        if (GraalServices.Java8OrEarlier) {
            return "sun.misc." + annotation.className();
        } else {
            return "jdk.internal.ref." + annotation.className();
        }
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_ref.class, className = "Cleaner")
final class Target_jdk_internal_ref_Cleaner {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    static Target_jdk_internal_ref_Cleaner first;

    /**
     * Contrary to the comment on {@code sun.misc.Cleaner}.dummyQueue, in SubstrateVM the queue can
     * have Cleaner instances on it, because SubstrateVM does not have a ReferenceHandler thread to
     * clean instances, so SubstrateVM puts them on the queue and drains the queue after collections
     * in {@link SunMiscSupport#drainCleanerQueue()}.
     * <p>
     * Cleaner instances that do bad things are even worse in SubstrateVM than they are in the
     * HotSpot VM, because they are run on the thread that started a collection.
     * <p>
     * Changing the access from `private` to `protected`, and reinitializing to an empty queue.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    static ReferenceQueue<Object> dummyQueue = new ReferenceQueue<>();

    @Alias
    native void clean();
}

@TargetClass(className = "jdk.internal.ref.CleanerImpl", onlyWith = JDK9OrLater.class)
final class Target_jdk_internal_ref_CleanerImpl {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$PhantomCleanableRef")//
    Target_jdk_internal_ref_PhantomCleanable phantomCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$WeakCleanableRef")//
    Target_jdk_internal_ref_WeakCleanable weakCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.ref.CleanerImpl$SoftCleanableRef")//
    Target_jdk_internal_ref_SoftCleanable softCleanableList;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "java.lang.ref.ReferenceQueue")//
    ReferenceQueue<Object> queue;
}

@TargetClass(className = "jdk.internal.ref.PhantomCleanable", onlyWith = JDK9OrLater.class)
final class Target_jdk_internal_ref_PhantomCleanable {
}

@TargetClass(className = "jdk.internal.ref.WeakCleanable", onlyWith = JDK9OrLater.class)
final class Target_jdk_internal_ref_WeakCleanable {
}

@TargetClass(className = "jdk.internal.ref.SoftCleanable", onlyWith = JDK9OrLater.class)
final class Target_jdk_internal_ref_SoftCleanable {
}

@Platforms(Platform.HOSTED_ONLY.class)
class Package_jdk_internal_perf implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass annotation) {
        if (GraalServices.Java8OrEarlier) {
            return "sun.misc." + annotation.className();
        } else {
            return "jdk.internal.perf." + annotation.className();
        }
    }
}

/** PerfCounter methods that access the lb field fail with SIGSEV. */
@TargetClass(classNameProvider = Package_jdk_internal_perf.class, className = "PerfCounter")
final class Target_jdk_internal_perf_PerfCounter {

    @Substitute
    @SuppressWarnings("static-method")
    public long get() {
        return 0;
    }

    @Substitute
    public void set(@SuppressWarnings("unused") long var1) {
    }

    @Substitute
    public void add(@SuppressWarnings("unused") long var1) {
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "SharedSecrets")
final class Target_jdk_internal_misc_SharedSecrets {
    @Substitute
    private static Target_jdk_internal_misc_JavaAWTAccess getJavaAWTAccess() {
        return null;
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "JavaAWTAccess")
final class Target_jdk_internal_misc_JavaAWTAccess {
}

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "JavaLangAccess")
final class Target_jdk_internal_misc_JavaLangAccess {
}

@Platforms(Platform.HOSTED_ONLY.class)
class Package_jdk_internal_loader implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass annotation) {
        if (GraalServices.Java8OrEarlier) {
            return "sun.misc." + annotation.className();
        } else {
            return "jdk.internal.loader." + annotation.className();
        }
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_loader.class, className = "URLClassPath", innerClass = "JarLoader")
@Delete
final class Target_sun_misc_URLClassPath_JarLoader {
}

/** Dummy class to have a class with the file's name. */
public final class SunMiscSubstitutions {
}
