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
package com.oracle.svm.core.jdk;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnsafeMemorySupport;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.BasedOnJDKFile;

import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.word.Word;

@TargetClass(className = "jdk.internal.misc.Unsafe")
@SuppressWarnings({"static-method", "unused"})
final class Target_jdk_internal_misc_Unsafe_Core {

    @Substitute
    private long allocateMemory0(long bytes) {
        return NativeMemory.malloc(Word.unsigned(bytes), NmtCategory.Unsafe).rawValue();
    }

    @Substitute
    private long reallocateMemory0(long address, long bytes) {
        return NativeMemory.realloc(Word.unsigned(address), Word.unsigned(bytes), NmtCategory.Unsafe).rawValue();
    }

    @Substitute
    private void freeMemory0(long address) {
        NativeMemory.free(Word.unsigned(address));
    }

    @Substitute
    private void copyMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        UnsafeMemorySupport.get().unsafeCopyMemory(srcBase, srcOffset, destBase, destOffset, bytes);
    }

    @Substitute
    private void copySwapMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        UnsafeMemorySupport.get().unsafeCopySwapMemory(srcBase, srcOffset, destBase, destOffset, bytes, elemSize);
    }

    @Substitute
    private void setMemory0(Object destBase, long destOffset, long bytes, byte bvalue) {
        UnsafeMemorySupport.get().unsafeSetMemory(destBase, destOffset, bytes, bvalue);
    }

    @Substitute
    private int addressSize() {
        return ConfigurationValues.getTarget().wordSize;
    }

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int pageSize() {
        return (int) VirtualMemoryProvider.get().getGranularity().rawValue();
    }

    @Substitute
    @TargetElement(name = "arrayBaseOffset")
    public long arrayBaseOffset(Class<?> clazz) {
        return LayoutEncoding.getArrayBaseOffset(DynamicHub.fromClass(clazz).getLayoutEncoding()).rawValue();
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
        MembarNode.memoryBarrier(MembarNode.FenceKind.LOAD_ACQUIRE);
    }

    @Substitute
    public void storeFence() {
        MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_RELEASE);
    }

    @Substitute
    public void fullFence() {
        MembarNode.memoryBarrier(MembarNode.FenceKind.FULL);
    }

    @Substitute
    boolean shouldBeInitialized(Class<?> c) {
        return !DynamicHub.fromClass(c).isInitialized();
    }

    @Substitute
    public void ensureClassInitialized(Class<?> c) {
        DynamicHub.fromClass(c).ensureInitialized();
    }

    @Substitute
    private int getLoadAverage0(double[] loadavg, int nelems) {
        /* Adapted from `Unsafe_GetLoadAverage0` in `src/hotspot/share/prims/unsafe.cpp`. */
        if (ImageSingletons.contains(LoadAverageSupport.class)) {
            return ImageSingletons.lookup(LoadAverageSupport.class).getLoadAverage(loadavg, nelems);
        }
        return -1; /* The load average is unobtainable. */
    }

    @Substitute
    public Object getUncompressedObject(long address) {
        /* Adapted from `Unsafe_GetUncompressedObject` in `src/hotspot/share/prims/unsafe.cpp`. */
        return ReferenceAccess.singleton().readObjectAt(Word.pointer(address), false);
    }

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    private static native void registerNatives();

    @Delete
    private native long objectFieldOffset0(Field f);

    @Delete
    private native long knownObjectFieldOffset0(Class<?> c, String name);

    @Delete
    private native long staticFieldOffset0(Field f);

    @Delete
    private native Object staticFieldBase0(Field f);

    @Delete
    private native boolean shouldBeInitialized0(Class<?> c);

    @Delete
    private native void ensureClassInitialized0(Class<?> c);

    @Delete
    private native int arrayBaseOffset0(Class<?> arrayClass);

    @Delete
    private native int arrayIndexScale0(Class<?> arrayClass);

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/unsafe.cpp#L708-L712")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/unsafe.cpp#L649-L705")
    @SuppressWarnings("unused")
    private Class<?> defineClass0(String name, byte[] b, int off, int len, ClassLoader loader, ProtectionDomain protectionDomain) {
        // Note that if name is not null, it is a binary name in either / or .-form
        return RuntimeClassLoading.defineClass(loader, name, b, off, len, new RuntimeClassLoading.ClassDefinitionInfo(protectionDomain));
    }
}

@TargetClass(jdk.internal.access.SharedSecrets.class)
final class Target_jdk_internal_access_SharedSecrets {
    /**
     * The JavaIOAccess implementation installed by the class initializer of java.io.Console
     * captures state like "is a tty". The only way to remove such state is by resetting the field.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Target_jdk_internal_access_JavaIOAccess javaIOAccess;
}

@TargetClass(jdk.internal.access.JavaIOAccess.class)
final class Target_jdk_internal_access_JavaIOAccess {
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
