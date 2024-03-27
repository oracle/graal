/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;

import jdk.graal.compiler.api.replacements.Fold;

public abstract class Heap {
    @Fold
    public static Heap getHeap() {
        return ImageSingletons.lookup(Heap.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected Heap() {
    }

    /**
     * Notifies the heap that a new thread was attached to the VM. This allows to initialize
     * heap-specific datastructures, e.g., the TLAB.
     */
    @Uninterruptible(reason = "Called during startup.")
    public abstract void attachThread(IsolateThread isolateThread);

    /**
     * Notifies the heap that a thread will be detached from the VM. This allows to cleanup
     * heap-specific resources, e.g., the TLAB. This method is called for every thread except the
     * main thread (i.e., the one that maps the image heap).
     */
    @Uninterruptible(reason = "Thread is detaching and holds the THREAD_MUTEX.")
    public abstract void detachThread(IsolateThread isolateThread);

    public abstract void suspendAllocation();

    public abstract void resumeAllocation();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isAllocationDisallowed();

    public abstract GC getGC();

    public abstract RuntimeCodeInfoGCSupport getRuntimeCodeInfoGCSupport();

    /**
     * Walk all the objects in the heap. Must only be executed as part of a VM operation that causes
     * a safepoint.
     */
    public abstract boolean walkObjects(ObjectVisitor visitor);

    /**
     * Walk all native image heap objects. Must only be executed as part of a VM operation that
     * causes a safepoint.
     */
    public abstract boolean walkImageHeapObjects(ObjectVisitor visitor);

    /**
     * Walk all heap objects except the native image heap objects. Must only be executed as part of
     * a VM operation that causes a safepoint.
     */
    public abstract boolean walkCollectedHeapObjects(ObjectVisitor visitor);

    /** Returns the number of classes in the heap (initialized as well as uninitialized). */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract int getClassCount();

    /** Visits all loaded classes in the heap (see {@link PredefinedClassesSupport}). */
    public void visitLoadedClasses(Consumer<Class<?>> visitor) {
        for (Class<?> clazz : getAllClasses()) {
            if (DynamicHub.fromClass(clazz).isLoaded()) {
                visitor.accept(clazz);
            }
        }
    }

    /**
     * Get all known classes. Intentionally protected to prevent access to classes that have not
     * been "loaded" yet, see {@link PredefinedClassesSupport}.
     */
    protected abstract List<Class<?>> getAllClasses();

    /**
     * Get the ObjectHeader implementation that this Heap uses.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract ObjectHeader getObjectHeader();

    /** Tear down the heap and free all allocated virtual memory chunks. */
    @Uninterruptible(reason = "Tear-down in progress.")
    public abstract boolean tearDown();

    /** Prepare the heap for a safepoint. */
    public abstract void prepareForSafepoint();

    /** Reset the heap to the normal execution state. */
    public abstract void endSafepoint();

    /** Returns a multiple to which the heap address space should be aligned to at runtime. */
    @Fold
    public abstract int getPreferredAddressSpaceAlignment();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Pointer getImageHeapStart() {
        Pointer heapBase = (Pointer) Isolates.getHeapBase(CurrentIsolate.getIsolate());
        return heapBase.add(Heap.getHeap().getImageHeapOffsetInAddressSpace());
    }

    /**
     * Returns an offset relative to the heap base, at which the image heap should be mapped into
     * the address space.
     */
    @Fold
    public abstract int getImageHeapOffsetInAddressSpace();

    /**
     * Returns true if the given object is located in the image heap.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isInImageHeap(Object object);

    /**
     * Returns true if the object at the given address is located in the image heap. Depending on
     * the used GC, this method may only work reliably for pointers that point to the start of an
     * object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isInImageHeap(Pointer objectPtr);

    /** Whether the object is in the primary image heap, as opposed to an auxiliary image heap. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isInPrimaryImageHeap(Object object);

    /** Whether the object is in the primary image heap, as opposed to an auxiliary image heap. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean isInPrimaryImageHeap(Pointer objectPtr);

    /**
     * If the automatic reference handling is disabled (see
     * {@link com.oracle.svm.core.SubstrateOptions.ConcealedOptions#AutomaticReferenceHandling}),
     * then this method can be called to do the reference handling manually. On execution, the
     * current thread will enqueue pending {@link Reference}s into their corresponding
     * {@link ReferenceQueue}s and it will execute pending cleaners.
     *
     * This method must not be called from within a VM operation as this could result in deadlocks.
     * Furthermore, it is up to the caller to ensure that this method is only called in places where
     * neither the reference handling nor the cleaner execution can cause any unexpected side
     * effects on the application behavior.
     *
     * If the automatic reference handling is enabled, then this method is a no-op.
     */
    public abstract void doReferenceHandling();

    /**
     * Determines if the heap currently has {@link Reference} objects that are pending to be
     * {@linkplain ReferenceQueue enqueued}.
     */
    public abstract boolean hasReferencePendingList();

    /** Blocks until the heap has pending {@linkplain Reference references}. */
    public abstract void waitForReferencePendingList() throws InterruptedException;

    /** Unblocks any threads in {@link #waitForReferencePendingList()}. */
    public abstract void wakeUpReferencePendingListWaiters();

    /**
     * Atomically get the list of pending {@linkplain Reference references} and clears (resets) it.
     * May return {@code null}.
     */
    public abstract Reference<?> getAndClearReferencePendingList();

    /**
     * If the passed value is within the Java heap, this method prints some information about that
     * value and returns true. Otherwise, the method returns false.
     */
    public abstract boolean printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations);

    /**
     * Notify the GC that the value of a GC-relevant option changed.
     */
    public abstract void optionValueChanged(RuntimeOptionKey<?> key);

    /**
     * Returns the number of bytes that were allocated by the given thread. The caller of this
     * method must ensure that the given {@link IsolateThread} remains alive during the execution of
     * this method.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract long getThreadAllocatedMemory(IsolateThread thread);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract UnsignedWord getUsedMemoryAfterLastGC();

    /** Consider all references in the given object as needing remembered set entries. */
    @Uninterruptible(reason = "Ensure that no GC can occur between modification of the object and this call.", callerMustBe = true)
    public abstract void dirtyAllReferencesOf(Object obj);

    /**
     * Returns the longest time (in ms) that has elapsed since the last time that the whole heap has
     * been examined by a garbage collection.
     */
    public abstract long getMillisSinceLastWholeHeapExamined();

    /**
     * Retrieves a salt value for computing the {@linkplain System#identityHashCode identity hash
     * code} of the passed object (and potentially other objects) from its address. The same salt
     * value will be returned for this object at least until the next garbage collection.
     *
     * Implementations must use {@link IdentityHashCodeSupport#IDENTITY_HASHCODE_SALT_LOCATION}.
     */
    @Uninterruptible(reason = "Ensure that no GC can occur between this call and usage of the salt.", callerMustBe = true)
    public abstract long getIdentityHashSalt(Object obj);
}
