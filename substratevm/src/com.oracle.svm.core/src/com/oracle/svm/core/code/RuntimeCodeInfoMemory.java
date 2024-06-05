/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfoAccess.HasInstalledCode;
import com.oracle.svm.core.code.RuntimeCodeCache.CodeInfoVisitor;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Keeps track of {@link CodeInfo} structures of runtime-compiled methods (including invalidated and
 * not yet freed ones) and releases their memory on tear-down.
 * <p>
 * Implementation: linear probing hash table adapted from OpenJDK {@link java.util.IdentityHashMap}.
 * <p>
 * All methods in here need to be either uninterruptible or it must be ensured that they are only
 * called by the GC. This is necessary because the GC can invalidate code as well. So, it must be
 * guaranteed that none of these methods is executed when a GC is triggered as we would end up with
 * races between the application and the GC otherwise.
 */
public class RuntimeCodeInfoMemory {
    private static final int MAX_CODE_INFO_ENTRIES_TO_PRINT = 500_000;

    @Fold
    public static RuntimeCodeInfoMemory singleton() {
        return ImageSingletons.lookup(RuntimeCodeInfoMemory.class);
    }

    private final ReentrantLock lock;
    private NonmovableArray<UntetheredCodeInfo> table;
    private int count;

    private UnsignedWord codeSize;
    private UnsignedWord codeAndDataMemorySize;
    private UnsignedWord nativeMetadataSize;
    private UnsignedWord totalSize;

    private UnsignedWord peakCodeSize;
    private UnsignedWord peakCodeAndDataMemorySize;
    private UnsignedWord peakNativeMetadataSize;
    private UnsignedWord peakTotalSize;

    public static final class SizeCounters {
        private UnsignedWord codeSize;
        private UnsignedWord codeAndDataMemorySize;
        private UnsignedWord nativeMetadataSize;
        private UnsignedWord totalSize;

        public UnsignedWord codeSize() {
            return codeSize;
        }

        public UnsignedWord codeAndDataMemorySize() {
            return codeAndDataMemorySize;
        }

        public UnsignedWord nativeMetadataSize() {
            return nativeMetadataSize;
        }

        public UnsignedWord totalSize() {
            return totalSize;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    RuntimeCodeInfoMemory() {
        lock = new ReentrantLock();
    }

    public void clearPeakCodeAndDataCounters() {
        peakCodeAndDataMemorySize = WordFactory.zero();
    }

    public void clearPeakNativeMetadataCounters() {
        peakNativeMetadataSize = WordFactory.zero();
    }

    @Uninterruptible(reason = "Manipulate the counters atomically with regard to GC.")
    private void addToSizeCounters(CodeInfo codeInfo) {
        // must be done under this.lock
        UnsignedWord code = CodeInfoAccess.getCodeSize(codeInfo);
        UnsignedWord codeAndDataMemory = CodeInfoAccess.getCodeAndDataMemorySize(codeInfo);
        UnsignedWord nativeMetadata = CodeInfoAccess.getNativeMetadataSize(codeInfo);
        codeSize = codeSize.add(code);
        codeAndDataMemorySize = codeAndDataMemorySize.add(codeAndDataMemory);
        nativeMetadataSize = nativeMetadataSize.add(nativeMetadata);
        totalSize = totalSize.add(codeAndDataMemory).add(nativeMetadata);
        if (codeSize.aboveThan(peakCodeSize)) {
            peakCodeSize = codeSize;
        }
        if (codeAndDataMemorySize.aboveThan(peakCodeAndDataMemorySize)) {
            peakCodeAndDataMemorySize = codeAndDataMemorySize;
        }
        if (nativeMetadataSize.aboveThan(peakNativeMetadataSize)) {
            peakNativeMetadataSize = nativeMetadataSize;
        }
        if (totalSize.aboveThan(peakTotalSize)) {
            peakTotalSize = totalSize;
        }
    }

    @Uninterruptible(reason = "Manipulate the counters atomically with regard to GC.")
    private void subtractToSizeCounters(CodeInfo codeInfo) {
        // This is done when the code info is removed the table
        // code and data might actually have been released earlier in
        // RuntimeCodeInfoAccess.freePartially
        UnsignedWord code = CodeInfoAccess.getCodeSize(codeInfo);
        UnsignedWord codeAndDataMemory = CodeInfoAccess.getCodeAndDataMemorySize(codeInfo);
        UnsignedWord nativeMetadata = CodeInfoAccess.getNativeMetadataSize(codeInfo);
        codeSize = codeSize.subtract(code);
        codeAndDataMemorySize = codeAndDataMemorySize.subtract(codeAndDataMemory);
        nativeMetadataSize = nativeMetadataSize.subtract(nativeMetadata);
        totalSize = totalSize.subtract(codeAndDataMemory).subtract(nativeMetadata);
        assert codeSize.aboveOrEqual(0);
        assert codeAndDataMemorySize.aboveOrEqual(0);
        assert nativeMetadataSize.aboveOrEqual(0);
        assert totalSize.aboveOrEqual(0);
    }

    public SizeCounters getSizeCounters() {
        lock.lock();
        try {
            SizeCounters counters = new SizeCounters();
            fillSizeCounters(counters);
            return counters;
        } finally {
            lock.unlock();
        }
    }

    @Uninterruptible(reason = "Read the counters atomically with regard to GC.")
    private void fillSizeCounters(SizeCounters counters) {
        counters.codeSize = codeSize;
        counters.codeAndDataMemorySize = codeAndDataMemorySize;
        counters.nativeMetadataSize = nativeMetadataSize;
        counters.totalSize = totalSize;
    }

    public SizeCounters getPeakSizeCounters() {
        lock.lock();
        try {
            SizeCounters counters = new SizeCounters();
            fillPeakSizeCounters(counters);
            return counters;
        } finally {
            lock.unlock();
        }
    }

    @Uninterruptible(reason = "Read the counters atomically with regard to GC.")
    private void fillPeakSizeCounters(SizeCounters counters) {
        counters.codeSize = peakCodeSize;
        counters.codeAndDataMemorySize = peakCodeAndDataMemorySize;
        counters.nativeMetadataSize = peakNativeMetadataSize;
        counters.totalSize = peakTotalSize;

    }

    public int getCount() {
        return count;
    }

    public void add(CodeInfo info) {
        // It is fine that this method is interruptible as all the relevant work is done in the
        // uninterruptible method that is called below.
        assert !Heap.getHeap().isAllocationDisallowed();
        assert info.isNonNull();
        lock.lock();
        try {
            add0(info);
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(CodeInfo info) {
        assert !VMOperation.isGCInProgress() : "Must call removeDuringGC";
        assert info.isNonNull() : "null";
        lock.lock();
        try {
            return remove0(info);
        } finally {
            lock.unlock();
        }
    }

    public boolean removeDuringGC(CodeInfo info) {
        assert VMOperation.isGCInProgress() : "Otherwise, we would need to protect the CodeInfo from the GC.";
        assert info.isNonNull();
        return remove0(info);
    }

    @Uninterruptible(reason = "Manipulate hashtable atomically with regard to GC.")
    private void add0(CodeInfo info) {
        addToSizeCounters(info);
        if (table.isNull()) {
            table = NonmovableArrays.createWordArray(32, NmtCategory.Code);
        }
        int index;
        boolean resized;
        do {
            int length = NonmovableArrays.lengthOf(table);
            index = hashIndex(info, length);
            while (NonmovableArrays.getWord(table, index).isNonNull()) {
                assert NonmovableArrays.getWord(table, index).notEqual(info) : "Duplicate CodeInfo";
                index = nextIndex(index, length);
            }
            resized = false;
            int newCount = count + 1;
            if (newCount + (newCount << 1) > (length << 1)) { // enforce 3/4 load factor
                resized = resize(length << 1);
            }
        } while (resized);
        NonmovableArrays.setWord(table, index, info);
        count++;

        assert count > 0 : "invalid counter value";
    }

    @Uninterruptible(reason = "Manipulate hashtable atomically with regard to GC.")
    private boolean resize(int newLength) {
        assert SubstrateUtil.isPowerOf2(newLength);
        final int maxLength = 1 << 30;
        int oldLength = NonmovableArrays.lengthOf(table);
        if (oldLength == maxLength) {
            VMError.guarantee(count < maxLength - 1, "Maximum capacity exhausted");
            return false;
        }
        if (oldLength >= newLength) {
            return false;
        }
        NonmovableArray<UntetheredCodeInfo> oldTable = table;
        table = NonmovableArrays.createWordArray(newLength, NmtCategory.Code);
        for (int i = 0; i < oldLength; i++) {
            UntetheredCodeInfo tag = NonmovableArrays.getWord(oldTable, i);
            if (tag.isNonNull()) {
                NonmovableArrays.setWord(oldTable, i, WordFactory.zero());
                int u = hashIndex(tag, newLength);
                while (NonmovableArrays.getWord(table, u).isNonNull()) {
                    u = nextIndex(u, newLength);
                }
                NonmovableArrays.setWord(table, u, tag);
            }
        }
        NonmovableArrays.releaseUnmanagedArray(oldTable);
        return true;
    }

    @Uninterruptible(reason = "Manipulate hashtable atomically with regard to GC.")
    private boolean remove0(CodeInfo info) {
        int length = NonmovableArrays.lengthOf(table);
        int index = hashIndex(info, length);
        UntetheredCodeInfo entry = NonmovableArrays.getWord(table, index);
        while (entry.isNonNull()) {
            if (entry.equal(info)) {
                NonmovableArrays.setWord(table, index, WordFactory.zero());
                count--;
                assert count >= 0 : "invalid counter value";
                rehashAfterUnregisterAt(index);
                subtractToSizeCounters(info);
                return true;
            }
            index = nextIndex(index, length);
            entry = NonmovableArrays.getWord(table, index);
        }
        return false;
    }

    /** Rehashes possibly-colliding entries after deletion to preserve collision properties. */
    @Uninterruptible(reason = "Manipulate hashtable atomically with regard to GC.")
    private void rehashAfterUnregisterAt(int index) { // from IdentityHashMap: Knuth 6.4 Algorithm R
        int length = NonmovableArrays.lengthOf(table);
        int d = index;
        int i = nextIndex(d, length);
        UntetheredCodeInfo info = NonmovableArrays.getWord(table, i);
        while (info.isNonNull()) {
            int r = hashIndex(info, length);
            if ((i < r && (r <= d || d <= i)) || (r <= d && d <= i)) {
                NonmovableArrays.setWord(table, d, info);
                NonmovableArrays.setWord(table, i, WordFactory.zero());
                d = i;
            }
            i = nextIndex(i, length);
            info = NonmovableArrays.getWord(table, i);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkRuntimeMethodsDuringGC(CodeInfoVisitor visitor) {
        assert VMOperation.isGCInProgress() : "otherwise, we would need to make sure that the CodeInfo is not freeded by the GC";
        if (table.isNonNull()) {
            int length = NonmovableArrays.lengthOf(table);
            for (int i = 0; i < length;) {
                UntetheredCodeInfo untetheredInfo = NonmovableArrays.getWord(table, i);
                if (untetheredInfo.isNonNull()) {
                    /* We are during a GC, so no need for a tether. */
                    CodeInfo info = CodeInfoAccess.unsafeConvert(untetheredInfo);
                    callVisitor(visitor, info);
                }

                /*
                 * If the visitor removed the current entry from the table, then it is necessary to
                 * visit the now updated entry one more time. However, this could have the effect
                 * that some entries are visited more than once.
                 */
                if (untetheredInfo == NonmovableArrays.getWord(table, i)) {
                    i++;
                }
            }
        }
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object until it is tethered.")
    public void walkRuntimeMethods(CodeInfoVisitor visitor) {
        if (table.isNonNull()) {
            int length = NonmovableArrays.lengthOf(table);
            for (int i = 0; i < length; i++) {
                UntetheredCodeInfo untetheredInfo = NonmovableArrays.getWord(table, i);
                if (untetheredInfo.isNonNull()) {
                    Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                    try {
                        CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                        callVisitor(visitor, info);
                    } finally {
                        CodeInfoAccess.releaseTether(untetheredInfo, tether);
                    }
                    assert untetheredInfo == NonmovableArrays.getWord(table, i);
                }
            }
        }
    }

    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static void callVisitor(CodeInfoVisitor visitor, CodeInfo info) {
        visitor.visitCode(info);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int hashIndex(UntetheredCodeInfo tag, int length) {
        int h = (int) (tag.rawValue() >>> 32) * 31 + (int) tag.rawValue();
        // Multiply by -127, and left-shift to use least bit as part of hash
        return ((h << 1) - (h << 8)) & (length - 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int nextIndex(int index, int length) {
        return (index + 1 < length) ? (index + 1) : 0;
    }

    public void printTable(Log log, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        if (allowUnsafeOperations || VMOperation.isInProgressAtSafepoint()) {
            // If we are not at a safepoint, then the table could be freed at any time.
            log.string("RuntimeCodeInfoMemory contains ").signed(count).string(" methods:").indent(true);
            if (table.isNonNull()) {
                int printed = 0;
                for (int i = 0; i < NonmovableArrays.lengthOf(table); i++) {
                    if (printed >= MAX_CODE_INFO_ENTRIES_TO_PRINT) {
                        log.string("... (truncated)").newline();
                        break;
                    }

                    if (printCodeInfo(log, i, allowJavaHeapAccess)) {
                        printed++;
                    }
                }
            }
            log.indent(false);
        }
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    private boolean printCodeInfo(Log log, int i, boolean allowJavaHeapAccess) {
        UntetheredCodeInfo info = NonmovableArrays.getWord(table, i);
        if (info.isNonNull()) {
            /*
             * Newly created CodeInfo objects do not have a tether yet. So, we can't use tethering
             * to keep the CodeInfo object alive. Instead, we read all relevant values in
             * uninterruptible code and pass those values to interruptible code that does the
             * printing.
             */
            String name = null;
            SubstrateInstalledCode installedCode = null;
            HasInstalledCode hasInstalledCode = HasInstalledCode.Unknown;
            if (allowJavaHeapAccess) {
                name = UntetheredCodeInfoAccess.getName(info);
                installedCode = UntetheredCodeInfoAccess.getInstalledCode(info);
                hasInstalledCode = (installedCode != null) ? HasInstalledCode.Yes : HasInstalledCode.No;
            }
            printCodeInfo0(log, info, UntetheredCodeInfoAccess.getState(info), name, UntetheredCodeInfoAccess.getCodeStart(info), UntetheredCodeInfoAccess.getCodeEnd(info), hasInstalledCode,
                            installedCode);
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = "CodeInfo no longer needs to be protected from the GC.", calleeMustBe = false)
    private static void printCodeInfo0(Log log, UntetheredCodeInfo codeInfo, int state, String name, CodePointer codeStart, CodePointer codeEnd, HasInstalledCode hasInstalledCode,
                    SubstrateInstalledCode installedCode) {
        CodeInfoAccess.printCodeInfo(log, codeInfo, state, name, codeStart, codeEnd, hasInstalledCode, installedCode);
        log.newline();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void tearDown() {
        if (table.isNonNull()) {
            int length = NonmovableArrays.lengthOf(table);
            for (int i = 0; i < length; i++) {
                UntetheredCodeInfo untetheredInfo = NonmovableArrays.getWord(table, i);
                if (untetheredInfo.isNonNull()) {
                    Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                    try {
                        CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                        RuntimeCodeInfoAccess.releaseMethodInfoOnTearDown(info);
                    } finally {
                        CodeInfoAccess.releaseTetherUnsafe(untetheredInfo, tether);
                    }
                }
            }
            NonmovableArrays.releaseUnmanagedArray(table);
            table = NonmovableArrays.nullArray();
        }
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    public boolean printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        if (allowUnsafeOperations || VMOperation.isInProgressAtSafepoint()) {
            // If we are not at a safepoint, then the table could be freed at any time.
            if (table.isNonNull()) {
                for (int i = 0; i < NonmovableArrays.lengthOf(table); i++) {
                    UntetheredCodeInfo info = NonmovableArrays.getWord(table, i);
                    if (info.isNonNull()) {
                        if (info.equal(value)) {
                            String name = allowJavaHeapAccess ? UntetheredCodeInfoAccess.getName(info) : null;
                            printIsCodeInfoObject(log, name);
                            return true;
                        }

                        UnsignedWord codeInfoEnd = ((UnsignedWord) info).add(CodeInfoAccess.getSizeOfCodeInfo());
                        if (value.aboveOrEqual((UnsignedWord) info) && value.belowThan(codeInfoEnd)) {
                            String name = allowJavaHeapAccess ? UntetheredCodeInfoAccess.getName(info) : null;
                            printInsideCodeInfo(log, info, name);
                            return true;
                        }

                        UnsignedWord codeStart = (UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(info);
                        UnsignedWord codeEnd = (UnsignedWord) UntetheredCodeInfoAccess.getCodeEnd(info);
                        if (value.aboveOrEqual(codeStart) && value.belowOrEqual(codeEnd)) {
                            String name = allowJavaHeapAccess ? UntetheredCodeInfoAccess.getName(info) : null;
                            printInsideInstructions(log, value, info, codeStart, name);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Uninterruptible(reason = "CodeInfo no longer needs to be protected from the GC.", calleeMustBe = false)
    private static void printIsCodeInfoObject(Log log, String name) {
        log.string("is a CodeInfo object");
        if (name != null) {
            log.string(" (").string(name).string(")");
        }
    }

    @Uninterruptible(reason = "CodeInfo no longer needs to be protected from the GC.", calleeMustBe = false)
    private static void printInsideCodeInfo(Log log, UntetheredCodeInfo info, String name) {
        log.string("points inside the CodeInfo object ").zhex(info);
        if (name != null) {
            log.string(" (").string(name).string(")");
        }
    }

    @Uninterruptible(reason = "CodeInfo no longer needs to be protected from the GC.", calleeMustBe = false)
    private static void printInsideInstructions(Log log, UnsignedWord value, UntetheredCodeInfo info, UnsignedWord codeStart, String name) {
        log.string("is at codeStart+").unsigned(value.subtract(codeStart)).string(" of CodeInfo ").zhex(info);
        if (name != null) {
            log.string(" (").string(name).string(")");
        }
    }
}
