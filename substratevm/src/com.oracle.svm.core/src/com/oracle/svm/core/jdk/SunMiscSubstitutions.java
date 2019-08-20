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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.VirtualMemoryProvider;

import jdk.vm.ci.code.MemoryBarriers;
import sun.misc.Unsafe;

@TargetClass(classNameProvider = Package_jdk_internal_misc.class, className = "Unsafe")
@SuppressWarnings({"static-method"})
final class Target_Unsafe_Core {

    @TargetElement(onlyWith = JDK8OrEarlier.class)
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

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    private long allocateMemory0(long bytes) {
        return UnmanagedMemory.malloc(WordFactory.unsigned(bytes)).rawValue();
    }

    @TargetElement(onlyWith = JDK8OrEarlier.class)
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

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    private long reallocateMemory0(long address, long bytes) {
        return UnmanagedMemory.realloc(WordFactory.unsigned(address), WordFactory.unsigned(bytes)).rawValue();
    }

    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @Substitute
    private void freeMemory(long address) {
        if (address != 0L) {
            UnmanagedMemory.free(WordFactory.unsigned(address));
        }
    }

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    private void freeMemory0(long address) {
        UnmanagedMemory.free(WordFactory.unsigned(address));
    }

    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @Substitute
    @Uninterruptible(reason = "Converts Object to Pointer.")
    private void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        MemoryUtil.copyConjointMemoryAtomic(
                        Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset)),
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes));
    }

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    @Uninterruptible(reason = "Converts Object to Pointer.")
    private void copyMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        MemoryUtil.copyConjointMemoryAtomic(
                        Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset)),
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes));
    }

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    @Uninterruptible(reason = "Converts Object to Pointer.")
    private void copySwapMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        MemoryUtil.copyConjointSwap(
                        Word.objectToUntrackedPointer(srcBase).add(WordFactory.unsigned(srcOffset)),
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes), WordFactory.unsigned(elemSize));
    }

    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @Substitute
    @Uninterruptible(reason = "Converts Object to Pointer.")
    private void setMemory(Object destBase, long destOffset, long bytes, byte bvalue) {
        MemoryUtil.fillToMemoryAtomic(
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes), bvalue);
    }

    @TargetElement(onlyWith = JDK11OrLater.class)
    @Substitute
    @Uninterruptible(reason = "Converts Object to Pointer.")
    private void setMemory0(Object destBase, long destOffset, long bytes, byte bvalue) {
        MemoryUtil.fillToMemoryAtomic(
                        Word.objectToUntrackedPointer(destBase).add(WordFactory.unsigned(destOffset)),
                        WordFactory.unsigned(bytes), bvalue);
    }

    @TargetElement(onlyWith = JDK8OrEarlier.class)
    @Substitute
    private int addressSize() {
        /*
         * No substitution necessary for JDK 11 or later because there the method is already
         * implemented exactly like this.
         */
        return Unsafe.ADDRESS_SIZE;
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
    private void throwException(Throwable t) throws Throwable {
        throw t;
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

@TargetClass(className = "jdk.internal.ref.PhantomCleanable", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_PhantomCleanable {
}

@TargetClass(className = "jdk.internal.ref.WeakCleanable", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_WeakCleanable {
}

@TargetClass(className = "jdk.internal.ref.SoftCleanable", onlyWith = JDK11OrLater.class)
final class Target_jdk_internal_ref_SoftCleanable {
}

@Platforms(Platform.HOSTED_ONLY.class)
class Package_jdk_internal_perf implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass annotation) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
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

@TargetClass(classNameProvider = Package_jdk_internal_access.class, className = "SharedSecrets")
final class Target_jdk_internal_access_SharedSecrets {
    @Substitute
    private static Target_jdk_internal_access_JavaAWTAccess getJavaAWTAccess() {
        return null;
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_access.class, className = "JavaAWTAccess")
final class Target_jdk_internal_access_JavaAWTAccess {
}

@TargetClass(classNameProvider = Package_jdk_internal_access.class, className = "JavaLangAccess")
final class Target_jdk_internal_access_JavaLangAccess {
}

@Platforms(Platform.HOSTED_ONLY.class)
class Package_jdk_internal_loader implements Function<TargetClass, String> {
    @Override
    public String apply(TargetClass annotation) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
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

@TargetClass(className = "sun.reflect.misc.MethodUtil")
final class Target_sun_reflect_misc_MethodUtil {
    @Substitute
    private static Object invoke(Method m, Object obj, Object[] params) throws InvocationTargetException, IllegalAccessException {
        return m.invoke(obj, params);
    }
}

/** Dummy class to have a class with the file's name. */
public final class SunMiscSubstitutions {
}
