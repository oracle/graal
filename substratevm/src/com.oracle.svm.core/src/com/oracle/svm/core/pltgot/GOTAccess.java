/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pltgot;

import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.locks.PlatformLockingSupport;
import com.oracle.svm.core.locks.PlatformLockingSupport.PlatformMutex;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

public class GOTAccess {
    private static final CGlobalData<Pointer> GOT_WRITABLE = CGlobalDataFactory.createWord(Word.<Word> signed(1));
    private static final CGlobalData<WordPointer> ACTIVE_WRITERS = CGlobalDataFactory.createWord();
    /**
     * Changing page protection calls the OS, so use a platform mutex instead of NativeSpinLockUtils.
     */
    private static final CGlobalData<PlatformMutex> GOT_PROTECTION_LOCK = CGlobalDataFactory.createBytes(() -> PlatformLockingSupport.singleton().mutexSize());

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int getGOTEntryOffsetFromHeapRegister(int gotEntry) {
        return -(gotEntry + 1) * SubstrateTarget.getWordSize();
    }

    @Uninterruptible(reason = "Writing to GOT must be uninterruptible because safepoints re-protect the GOT mapping when no writer leases are active.")
    public static void writeToGOTEntry(int gotEntry, UnsignedWord address) {
        acquireWriteLease();
        try {
            writeToGOTEntryWithLease(gotEntry, address);
        } finally {
            releaseWriteLease();
        }
    }

    @Uninterruptible(reason = "Writing to GOT must be uninterruptible because safepoints re-protect the GOT mapping when no writer leases are active.")
    public static void writeToGOTEntries(int[] gotEntries, int[] offsets, Pointer baseAddress) {
        assert gotEntries.length == offsets.length : "GOT entries and offsets must have the same length.";
        acquireWriteLease();
        try {
            for (int i = 0; i < gotEntries.length; i++) {
                writeToGOTEntryWithLease(gotEntries[i], baseAddress.add(offsets[i]));
            }
        } finally {
            releaseWriteLease();
        }
    }

    @Uninterruptible(reason = "Uses the no-transition GOT mutex, the lease and store must not be interrupted by a safepoint.", callerMustBe = true)
    private static void acquireWriteLease() {
        lockMutex();
        try {
            setGOTWritable(true);
            UnsignedWord writers = ACTIVE_WRITERS.get().read(0);
            ACTIVE_WRITERS.get().write(0, writers.add(1));
        } finally {
            unlockMutex();
        }
    }

    @Uninterruptible(reason = "Uses the no-transition GOT mutex, the lease and store must not be interrupted by a safepoint.", callerMustBe = true)
    private static void releaseWriteLease() {
        lockMutex();
        try {
            UnsignedWord writers = ACTIVE_WRITERS.get().read(0);
            VMError.guarantee(writers.aboveThan(0), "GOT writer count underflow.");
            ACTIVE_WRITERS.get().write(0, writers.subtract(1));
        } finally {
            unlockMutex();
        }
    }

    @Uninterruptible(reason = "Releasing the GOT mutex without a thread-state transition requires the caller to be uninterruptible.", callerMustBe = true)
    private static void unlockMutex() {
        PlatformLockingSupport.singleton().unlockMutex(GOT_PROTECTION_LOCK.get());
    }

    @Uninterruptible(reason = "Acquiring the GOT mutex without a thread-state transition requires the caller to be uninterruptible.", callerMustBe = true)
    private static void lockMutex() {
        PlatformLockingSupport.singleton().lockMutexNoTransition(GOT_PROTECTION_LOCK.get());
    }

    @Uninterruptible(reason = "Locking the GOT mutex requires the whole critical section to be uninterruptible.")
    static int initializeInFirstIsolate() {
        int ret = PlatformLockingSupport.singleton().initializeMutex(GOT_PROTECTION_LOCK.get());
        if (ret != 0) {
            return CEntryPointErrors.THREADING_INITIALIZATION_FAILED;
        }
        lockMutex();
        try {
            setGOTWritable(false);
        } finally {
            unlockMutex();
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Locking the GOT mutex requires the whole critical section to be uninterruptible.")
    public static void reprotectGOTAtSafepoint() {
        if (!GOTHeapSupport.hasGOTEntries()) {
            return;
        }
        lockMutex();
        try {
            if (ACTIVE_WRITERS.get().read(0).rawValue() == 0) {
                setGOTWritable(false);
            }
        } finally {
            unlockMutex();
        }
    }

    @Uninterruptible(reason = "The caller holds a GOT writer lease.", callerMustBe = true)
    private static void writeToGOTEntryWithLease(int gotEntry, UnsignedWord address) {
        Pointer gotStartAddress = GOTHeapSupport.GOT_START_ADDRESS.get().read();
        Pointer gotEndAddress = gotStartAddress.add(GOTHeapSupport.getPageAlignedGOTSize());
        gotEndAddress.writeWord(getGOTEntryOffsetFromHeapRegister(gotEntry), address);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isGOTWritable() {
        return GOT_WRITABLE.get().readWord(0).rawValue() != 0;
    }

    @Uninterruptible(reason = "The caller holds the no-transition GOT mutex.", callerMustBe = true)
    private static void setGOTWritable(boolean writable) {
        if (isGOTWritable() != writable) {
            Pointer gotMappingStartAddress = GOTHeapSupport.GOT_START_ADDRESS.get().read();
            int protection = VirtualMemoryProvider.Access.READ | (writable ? VirtualMemoryProvider.Access.WRITE : 0);
            int ret = VirtualMemoryProvider.get().protect(gotMappingStartAddress, GOTHeapSupport.getPageAlignedGOTSize(), protection);
            VMError.guarantee(ret == 0, "Failed to change GOT protection.");
            GOT_WRITABLE.get().writeWord(0, Word.signed(writable ? 1 : 0));
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Word readFromGOTEntry(int gotEntry) {
        Pointer gotStartAddress = GOTHeapSupport.GOT_START_ADDRESS.get().read();
        Pointer gotEndAddress = gotStartAddress.add(GOTHeapSupport.getPageAlignedGOTSize());
        return gotEndAddress.readWord(getGOTEntryOffsetFromHeapRegister(gotEntry));
    }

}
