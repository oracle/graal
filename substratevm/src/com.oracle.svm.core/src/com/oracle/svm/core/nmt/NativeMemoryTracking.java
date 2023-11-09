/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.nmt;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.headers.LibCSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.internal.misc.Unsafe;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/**
 * This is the class that handles native memory tracking (NMT). There are two components to NMT:
 * tracking malloc/realloc/calloc, and tracking virtual memory usage.
 * <p>
 * </p>
 * <p>
 * Malloc/realloc/calloc: Malloc headers ({@link MallocHeader}) are used for caching data about the
 * allocation. This is necessary because that data is needed to deaccount the tracked memory when it
 * is freed. There are some scenarios where an implementation dependent on malloc header requires
 * special precautions:
 * </p>
 *
 * <ol>
 * <li>When native memory is allocated outside of java. This might be in a C library for example.
 * When it comes time to free the memory, it is important that the NMT infrastructure, if enabled,
 * does not assume a malloc header exists and attempt to access it.
 * {@link UnmanagedMemorySupport#untrackedFree(PointerBase)} must be used in this case.
 * <li>When raw {@link LibCSupport} allocation methods are called directly. This is problematic
 * because such allocated blocks will not contain malloc headers. If NMT is enabled it will assume
 * headers exist and access incorrect memory locations. This must be avoided.
 * <li>During the phase pre-NMT initialization. Before runtime options are parsed, it is unknown
 * whether NMT should be enabled/disabled. During this time, it is assumed that NMT will later be
 * enabled, so malloc headers are allocated. This increases the accuracy of the tracking. However,
 * if NMT is later disabled upon initialization, there will be a <b>mix of allocated blocks that
 * have headers and do some that do not </b>. This is a problem when it comes time to free the
 * blocks. For this reason, there are methods in this class that handle pre-init allocations using a
 * look-up table to cache addresses seen before initialization. This is the same reason NMT cannot
 * be enabled/disabled after initialization.
 * <li>If native allocations are done before the image heap is mapped. This is not strictly a
 * problem related to malloc headers, but must still be avoided. If such situations are unavoidable,
 * NMT code must be bypassed using raw {@link LibCSupport} methods for both allocation and
 * deallocation.
 * </ol>
 */
public class NativeMemoryTracking {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(NativeMemoryTracking.class, "preInitLock");

    /**
     * Can't use VmMutex because it tracks owners and this class may be used by unattached threads
     * (calloc). This lock is only used during the pre-init phase.
     */
    @SuppressWarnings("unused") private volatile int preInitLock;

    private final PreInitTable preInitTable;
    private volatile boolean enabled;
    private volatile boolean initialized;

    private MallocMemorySnapshot mallocMemorySnapshot;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeMemoryTracking() {
        enabled = true;
        initialized = false;
        mallocMemorySnapshot = new MallocMemorySnapshot();
        preInitTable = new PreInitTable();
    }

    @Fold
    static UnsignedWord getHeaderSize0() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(MallocHeader.class), WordFactory.unsigned(ConfigurationValues.getTarget().wordSize));
    }

    @Fold
    static LibCSupport libc() {
        return ImageSingletons.lookup(LibCSupport.class);
    }

    @Fold
    static NativeMemoryTracking get() {
        return ImageSingletons.lookup(NativeMemoryTracking.class);
    }

    /** This must be called after the image heap is mapped. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initialize(boolean enabled) {
        if (HasNmtSupport.get()) {
            get().initialize0(enabled);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void initialize0(boolean enabled) {
        assert !initialized;
        // Block until all in-progress pre-init operations are completed
        lockNoTransition();
        try {
            this.enabled = enabled;
            // After this point, the preInit table becomes read only.
            initialized = true;
        } finally {
            unlock();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getHeaderSize() {
        if (HasNmtSupport.get() && get().enabled) {
            return getHeaderSize0();
        }
        return WordFactory.zero();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getAllocationSize(Pointer outerPointer) {
        if (HasNmtSupport.get() && get().enabled) {
            MallocHeader header = (MallocHeader) outerPointer;
            return header.getSize();
        }
        return -1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getAllocationCategory(Pointer outerPointer) {
        if (HasNmtSupport.get() && get().enabled) {
            MallocHeader header = (MallocHeader) outerPointer;
            return header.getFlag();
        }
        return -1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Pointer recordMalloc(Pointer outerPointer, UnsignedWord size, int flag) {
        if (HasNmtSupport.get() && outerPointer.isNonNull()) {
            return get().recordMalloc0(outerPointer, size, flag);
        }
        return outerPointer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private Pointer recordMalloc0(Pointer outerPointer, UnsignedWord size, int flag) {
        if (enabled) {
            UnsignedWord headerSize = getHeaderSize0();
            mallocMemorySnapshot.getInfoByCategory(flag).recordMalloc(size);
            mallocMemorySnapshot.getInfoByCategory(NmtFlag.mtNMT.ordinal()).recordMalloc(headerSize);
            mallocMemorySnapshot.getTotalInfo().recordMalloc(size.add(headerSize));
            return initializeHeader(outerPointer, size, flag);
        }
        return outerPointer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer initializeHeader(Pointer outerPointer, UnsignedWord size, int flag) {
        MallocHeader mallocHeader = (MallocHeader) outerPointer;
        mallocHeader.setSize(size.rawValue());
        mallocHeader.setFlag(flag);
        return ((Pointer) mallocHeader).add(NativeMemoryTracking.getHeaderSize0());
    }

    /**
     * This is only needed for {@link PreInitTable} since we must use raw malloc/free there but
     * still want to track its allocations.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void recordMallocWithoutHeader(UnsignedWord size, int flag) {
        if (HasNmtSupport.get()) {
            get().recordMallocWithoutHeader0(size, flag);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void recordMallocWithoutHeader0(UnsignedWord size, int flag) {
        if (enabled) {
            mallocMemorySnapshot.getInfoByCategory(flag).recordMalloc(size);
            mallocMemorySnapshot.getTotalInfo().recordMalloc(size);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void deaccountMalloc(PointerBase innerPtr) {
        if (HasNmtSupport.get() && get().enabled) {
            MallocHeader header = (MallocHeader) ((Pointer) innerPtr).subtract(getHeaderSize0());
            get().deaccountMalloc0(header.getSize(), header.getFlag());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void deaccountMalloc(long size, int flag) {
        if (HasNmtSupport.get() && size > 0 && flag >= 0) {
            get().deaccountMalloc0(size, flag);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void deaccountMalloc0(long size, int flag) {
        if (enabled) {
            mallocMemorySnapshot.getInfoByCategory(flag).deaccountMalloc(size);
            mallocMemorySnapshot.getInfoByCategory(NmtFlag.mtNMT.ordinal()).deaccountMalloc(getHeaderSize0().rawValue());
            mallocMemorySnapshot.getTotalInfo().deaccountMalloc(size + getHeaderSize0().rawValue());
        }
    }

    /**
     * This is only needed for {@link PreInitTable} since we must use raw malloc/free there but
     * still want to track its allocations.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void deaccountMallocWithoutHeader(long size, int flag) {
        if (HasNmtSupport.get() && size > 0 && flag >= 0) {
            get().deaccountMallocWithoutHeader0(size, flag);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void deaccountMallocWithoutHeader0(long size, int flag) {
        if (enabled) {
            mallocMemorySnapshot.getInfoByCategory(flag).deaccountMalloc(size);
            mallocMemorySnapshot.getTotalInfo().deaccountMalloc(size);
        }
    }

    public static long getMallocByCategory(NmtFlag flag) {
        if (HasNmtSupport.get()) {
            return get().mallocMemorySnapshot.getInfoByCategory(flag.ordinal()).getSize();
        }
        return -1;
    }

    /** Prints stats contained in the current snapshot. */
    public static void printStats() {
        if (HasNmtSupport.get()) {
            get().printStats0();
        }
    }

    private void printStats0() {
        if (!enabled) {
            return;
        }
        LogUtils.info("Total current malloc size:" + mallocMemorySnapshot.getTotalInfo().getSize() + "B");
        LogUtils.info("Total current malloc count:" + mallocMemorySnapshot.getTotalInfo().getCount());

        for (int i = 0; i < NmtFlag.values().length; i++) {
            LogUtils.info(NmtFlag.values()[i].getName() + " current malloc size:" + mallocMemorySnapshot.getInfoByCategory(i).getSize() + "B");
            LogUtils.info(NmtFlag.values()[i].getName() + " current malloc count:" + mallocMemorySnapshot.getInfoByCategory(i).getCount());
        }

    }

    /**
     * This method is needed because locking is required to make the allocation, LUT insertion, and
     * initialization check atomic. If the malloc was handled here, returnAddress holds the location
     * of the malloc block payload.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean handlePreInitMallocs(UnsignedWord size, int flag, ReturnAddress returnAddress) {
        if (HasNmtSupport.get()) {
            return get().handlePreInitMallocs0(size, flag, returnAddress);
        }
        return false;
    }

    /**
     * Special handling is needed here becuase we need ot check initialization state and add an
     * address to the LUT.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean handlePreInitMallocs0(UnsignedWord size, int flag, ReturnAddress returnAddress) {
        // For speed, check if initialized before acquiring lock
        if (!initialized) {
            lockNoTransition();
            try {
                // Double check after acquiring lock. If it's since been initialized, proceed
                // normally.
                if (initialized) {
                    return false;
                }
                // Still uninitialized. Allocate and add to LUT.
                Pointer outerPointer = libc().malloc(size.add(getHeaderSize0()));
                recordPreInitAlloc(outerPointer, size, flag, returnAddress);
                return true;
            } finally {
                unlock();
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean handlePreInitCallocs(UnsignedWord size, int flag, ReturnAddress returnAddress) {
        if (HasNmtSupport.get()) {
            return get().handlePreInitCallocs0(size, flag, returnAddress);
        }
        return false;
    }

    /**
     * Similar to
     * {@link NativeMemoryTracking#handlePreInitMallocs0(UnsignedWord, int, ReturnAddress)}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean handlePreInitCallocs0(UnsignedWord size, int flag, ReturnAddress returnAddress) {
        if (!initialized) {
            lockNoTransition();
            try {
                if (initialized) {
                    return false;
                }
                Pointer outerPointer = libc().calloc(WordFactory.unsigned(1), size.add(getHeaderSize()));
                recordPreInitAlloc(outerPointer, size, flag, returnAddress);
                return true;
            } finally {
                unlock();
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean handlePreInitReallocs(Pointer oldInnerPtr, UnsignedWord size, int flag, ReturnAddress returnAddress) {
        if (HasNmtSupport.get()) {
            return get().handlePreInitReallocs0(oldInnerPtr, size, flag, returnAddress);
        }
        return false;
    }

    /**
     * This method is needed because locking is required to make the allocation, LUT insertion, and
     * initialization check atomic.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean handlePreInitReallocs0(Pointer oldInnerPtr, UnsignedWord size, int flag, ReturnAddress returnAddress) {
        // For speed, check if initialized before acquiring lock
        if (!initialized) {
            lockNoTransition();
            try {
                // Double check after acquiring lock. If still uninitialized, perform realloc and do
                // LUT updates.
                if (!initialized) {
                    // Retrieve necessary data from the old block
                    Pointer oldOuterPointer = oldInnerPtr.subtract(NativeMemoryTracking.getHeaderSize0());
                    long oldSize = getAllocationSize(oldOuterPointer);
                    int oldCategory = getAllocationCategory(oldOuterPointer);

                    // Perform the realloc
                    Pointer newOuterPointer = libc().realloc(oldOuterPointer, size.add(NativeMemoryTracking.getHeaderSize0()));

                    if (newOuterPointer.isNonNull()) {
                        // Deaccount old block. Order matters here in case old/new have same
                        // address.
                        deaccountMalloc0(oldSize, oldCategory);
                        preInitTable.remove(oldInnerPtr);

                        // Account new block
                        recordPreInitAlloc(newOuterPointer, size, flag, returnAddress);
                    } else {
                        returnAddress.set(WordFactory.nullPointer());
                    }
                    return true;
                }
            } finally {
                unlock();
            }
        }

        // Post init.
        if (!enabled && preInitTable.get(oldInnerPtr).isNonNull()) {
            // There is a header from pre-init time, but tracking has since been disabled.

            // Malloc a new block with the given size and no header
            Pointer newBlock = libc().malloc(size);
            returnAddress.set(newBlock);
            if (newBlock.isNonNull()) {
                // Copy payload from original block to new block
                Pointer oldOuterPointer = oldInnerPtr.subtract(getHeaderSize0());
                UnsignedWord oldSize = WordFactory.unsigned(getAllocationSize(oldOuterPointer));
                UnsignedWord amountToCopy = size.belowThan(oldSize) ? size : oldSize;
                libc().memcpy(newBlock, oldInnerPtr, amountToCopy);
                // Don't raw free the old block to avoid libc returning the same address later.
            }
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void recordPreInitAlloc(Pointer newOuterPointer, UnsignedWord size, int flag, ReturnAddress returnAddress) {
        if (newOuterPointer.isNull()) {
            returnAddress.set(WordFactory.nullPointer());
            return;
        }
        Pointer newInnerPtr = recordMalloc0(newOuterPointer, size, flag);
        preInitTable.putIfAbsent(newInnerPtr);
        returnAddress.set(newInnerPtr);
    }

    /** Special handling is needed for pre-init mallocs if NMT is disabled post-init. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean handlePreInitFrees(Pointer innerPtr) {
        if (HasNmtSupport.get()) {
            return get().handlePreInitFrees0(innerPtr);
        }
        return false;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean handlePreInitFrees0(Pointer innerPtr) {
        if (!initialized) {
            lockNoTransition();
            try {
                if (!initialized) {
                    deaccountMalloc(innerPtr);
                    preInitTable.remove(innerPtr);
                    libc().free(innerPtr.subtract(getHeaderSize0()));
                    return true;
                }
            } finally {
                unlock();
            }
        }

        // Post init.
        if (!enabled && preInitTable.get(innerPtr).isNonNull()) {
            // If NMT is now disabled and we're dealing with a pre-init block.
            // There is a header from pre-init time, but tracking has since been disabled.
            // Do nothing more. Don't Raw free the old block to avoid libc returning the same
            // address later.
            return true;

        }
        // Either NMT is still enabled or we're dealing with a block allocated after initialization.
        // We can proceed with tracking normally.
        return false;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    private void lockNoTransition() {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.", callerMustBe = true)
    private void unlock() {
        JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
    }

    public static RuntimeSupport.Hook shutdownHook() {
        return isFirstIsolate -> {
            printStats();
        };
    }

    public static RuntimeSupport.Hook startupHook() {
        return isFirstIsolate -> {
            initialize(SubstrateOptions.NativeMemoryTracking.getValue());
        };
    }
}
