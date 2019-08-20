/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.RingBuffer;

public class RuntimeCodeInfo {

    public static class Options {
        @Option(help = "Print logging information for runtime code cache modifications")//
        public static final RuntimeOptionKey<Boolean> TraceCodeCache = new RuntimeOptionKey<>(false);
    }

    private final RingBuffer<String> recentCodeCacheOperations = new RingBuffer<>();
    private long codeCacheOperationSequenceNumber;

    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "RuntimeCodeInfo");
    private final Counter lookupMethodCount = new Counter(counters, "lookupMethod", "");
    private final Counter addMethodCount = new Counter(counters, "addMethod", "");
    private final Counter invalidateMethodCount = new Counter(counters, "invalidateMethod", "");

    static final String INFO_ADD = "Add";
    static final String INFO_INVALIDATE = "Invalidate";

    private static final int INITIAL_TABLE_SIZE = 100;

    private NonmovableArray<CodeInfo> methodInfos;
    private int numMethods;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeCodeInfo() {
    }

    /** Tear down the heap, return all allocated virtual memory chunks to VirtualMemoryProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        NonmovableArrays.releaseUnmanagedArray(methodInfos);
        methodInfos = NonmovableArrays.nullArray();

        RuntimeMethodInfoMemory.singleton().tearDown(); // releases all CodeInfos from our table too
    }

    /**
     * Looking up a method is lock-free: it is called frequently during stack walking, so locking or
     * even a {@link VMOperation} would be too slow. The lookup must access the {@link #methodInfos}
     * array, which is modified non-atomically when adding or removing methods. All modifications
     * are done from within a {@link VMOperation}. Making this method {@link Uninterruptible}
     * ensures that we see one consistent snapshot of the array, without the possibility for a
     * concurrent modification.
     */
    @Uninterruptible(reason = "methodInfos is accessed without holding a lock, so must not be interrupted by a safepoint that can add/remove code")
    protected CodeInfo lookupMethod(CodePointer ip) {
        lookupMethodCount.inc();
        assert verifyTable();
        if (numMethods == 0) {
            return WordFactory.nullPointer();
        }

        int idx = binarySearch(methodInfos, 0, numMethods, ip);
        if (idx >= 0) {
            /* Exact hit, ip is the begin of the method. */
            return NonmovableArrays.getWord(methodInfos, idx);
        }

        int insertionPoint = -idx - 1;
        if (insertionPoint == 0) {
            /* ip is below the first method, so no hit. */
            assert ((UnsignedWord) ip).belowThan((UnsignedWord) CodeInfoAccess.getCodeStart(NonmovableArrays.getWord(methodInfos, 0)));
            return WordFactory.nullPointer();
        }

        CodeInfo info = NonmovableArrays.getWord(methodInfos, insertionPoint - 1);
        assert ((UnsignedWord) ip).aboveThan((UnsignedWord) CodeInfoAccess.getCodeStart(info));
        if (((UnsignedWord) ip).subtract((UnsignedWord) CodeInfoAccess.getCodeStart(info)).aboveOrEqual(CodeInfoAccess.getCodeSize(info))) {
            /* ip is not within the range of a method. */
            return WordFactory.nullPointer();
        }

        return info;
    }

    /* Copied and adapted from Arrays.binarySearch. */
    @Uninterruptible(reason = "called from uninterruptible code")
    private static int binarySearch(NonmovableArray<CodeInfo> a, int fromIndex, int toIndex, CodePointer key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            CodePointer midVal = CodeInfoAccess.getCodeStart(NonmovableArrays.getWord(a, mid));

            if (((UnsignedWord) midVal).belowThan((UnsignedWord) key)) {
                low = mid + 1;
            } else if (((UnsignedWord) midVal).aboveThan((UnsignedWord) key)) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return -(low + 1);  // key not found.
    }

    public void addMethod(CodeInfo info) {
        VMOperation.enqueueBlockingSafepoint("AddMethod", () -> {
            InstalledCodeObserverSupport.activateObservers(RuntimeMethodInfoAccess.getCodeObserverHandles(info));
            long num = logMethodOperation(info, INFO_ADD);
            addMethodOperation(info);
            logMethodOperationEnd(num);
        });
    }

    private void addMethodOperation(CodeInfo info) {
        VMOperation.guaranteeInProgress("Modifying code tables that are used by the GC");
        addMethodCount.inc();
        assert verifyTable();
        if (Options.TraceCodeCache.getValue()) {
            Log.log().string("[" + INFO_ADD + " method: ");
            logMethod(Log.log(), info);
            Log.log().string("]").newline();
        }

        if (methodInfos.isNull() || numMethods >= NonmovableArrays.lengthOf(methodInfos)) {
            enlargeTable();
            assert verifyTable();
        }
        assert numMethods < NonmovableArrays.lengthOf(methodInfos);

        int idx = binarySearch(methodInfos, 0, numMethods, CodeInfoAccess.getCodeStart(info));
        assert idx < 0 : "must not find code already in table";
        int insertionPoint = -idx - 1;
        NonmovableArrays.arraycopy(methodInfos, insertionPoint, methodInfos, insertionPoint + 1, numMethods - insertionPoint);
        numMethods++;
        NonmovableArrays.setWord(methodInfos, insertionPoint, info);

        if (Options.TraceCodeCache.getValue()) {
            logTable();
        }
        assert verifyTable();
    }

    private void enlargeTable() {
        int newTableSize = numMethods * 2;
        if (newTableSize < INITIAL_TABLE_SIZE) {
            newTableSize = INITIAL_TABLE_SIZE;
        }
        NonmovableArray<CodeInfo> newMethodInfos = NonmovableArrays.createWordArray(newTableSize);
        if (methodInfos.isNonNull()) {
            NonmovableArrays.arraycopy(methodInfos, 0, newMethodInfos, 0, NonmovableArrays.lengthOf(methodInfos));
            NonmovableArrays.releaseUnmanagedArray(methodInfos);
        }
        methodInfos = newMethodInfos;
    }

    protected void invalidateMethod(CodeInfo info) {
        VMOperation.guaranteeInProgress("Modifying code tables that are used by the GC");
        invalidateMethodCount.inc();
        assert verifyTable();
        if (Options.TraceCodeCache.getValue()) {
            Log.log().string("[" + INFO_INVALIDATE + " method: ");
            logMethod(Log.log(), info);
            Log.log().string("]").newline();
        }

        SubstrateInstalledCode installedCode = RuntimeMethodInfoAccess.getInstalledCode(info);
        if (installedCode != null) {
            assert !installedCode.isValid() || CodeInfoAccess.getCodeStart(info).rawValue() == installedCode.getAddress();
            /*
             * Until this point, the InstalledCode is valid. It can be invoked, and frames can be on
             * the stack. All the metadata must be valid until this point. Make it non-entrant,
             * i.e., ensure it cannot be invoked any more.
             */
            installedCode.clearAddress();
        }

        InstalledCodeObserverSupport.removeObservers(RuntimeMethodInfoAccess.getCodeObserverHandles(info));

        /*
         * Deoptimize all invocations that are on the stack. This performs a stack walk, so all
         * metadata must be intact (even though the method was already marked as non-invokable).
         */
        Deoptimizer.deoptimizeInRange(CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info), false);
        /*
         * Now it is guaranteed that the InstalledCode is not on the stack and cannot be invoked
         * anymore, so we can free the code and all metadata.
         */

        /* Remove info entry from our table. */
        int idx = binarySearch(methodInfos, 0, numMethods, CodeInfoAccess.getCodeStart(info));
        assert idx >= 0 : "info must be in table";
        NonmovableArrays.arraycopy(methodInfos, idx + 1, methodInfos, idx, numMethods - (idx + 1));
        numMethods--;
        NonmovableArrays.setWord(methodInfos, numMethods, WordFactory.nullPointer());

        RuntimeMethodInfoAccess.partialReleaseAfterInvalidate(info);

        if (Options.TraceCodeCache.getValue()) {
            logTable();
        }
        assert verifyTable();
    }

    @Uninterruptible(reason = "called from uninterruptible code")
    private boolean verifyTable() {
        if (methodInfos.isNull()) {
            assert numMethods == 0 : "a1";
            return true;
        }

        assert numMethods <= NonmovableArrays.lengthOf(methodInfos) : "a11";

        for (int i = 0; i < numMethods; i++) {
            CodeInfo info = NonmovableArrays.getWord(methodInfos, i);
            assert info.isNonNull() : "a20";
            assert i == 0 || ((UnsignedWord) CodeInfoAccess.getCodeStart(NonmovableArrays.getWord(methodInfos, i - 1)))
                            .belowThan((UnsignedWord) CodeInfoAccess.getCodeStart(NonmovableArrays.getWord(methodInfos, i))) : "a22";
            assert i == 0 || ((UnsignedWord) CodeInfoAccess.getCodeEnd(NonmovableArrays.getWord(methodInfos, i - 1))).belowOrEqual((UnsignedWord) CodeInfoAccess.getCodeStart(info)) : "a23";
        }

        for (int i = numMethods; i < NonmovableArrays.lengthOf(methodInfos); i++) {
            assert NonmovableArrays.getWord(methodInfos, i).isNull() : "a31";
        }
        return true;
    }

    public void logTable() {
        logTable(Log.log());
    }

    public void logRecentOperations(Log log) {
        log.string("== [Recent RuntimeCodeCache operations: ");
        recentCodeCacheOperations.foreach((context, entry) -> {
            Log.log().newline().string(entry);
        });
        log.string("]").newline();
    }

    public void logTable(Log log) {
        log.string("== [RuntimeCodeCache: ").signed(numMethods).string(" methods");
        for (int i = 0; i < numMethods; i++) {
            CodeInfo info = NonmovableArrays.getWord(methodInfos, i);
            log.newline().hex(CodeInfoAccess.getCodeStart(info)).string("  ");
            logMethod(log, info);
        }
        log.string("]").newline();
    }

    private static void logMethod(Log log, CodeInfo info) {
        log.string(CodeInfoAccess.getName(info));
        log.string("  ip: ").hex(CodeInfoAccess.getCodeStart(info)).string(" - ").hex(CodeInfoAccess.getCodeEnd(info));
        log.string("  size: ").unsigned(CodeInfoAccess.getCodeSize(info));
        /*
         * Note that we are not trying to output the InstalledCode object. It is not a pinned
         * object, so when log printing (for, e.g., a fatal error) occurs during a GC, then the VM
         * could segfault.
         */
    }

    long logMethodOperation(CodeInfo info, String kind) {
        long current = ++codeCacheOperationSequenceNumber;
        StringBuilderLog log = new StringBuilderLog();
        log.string(kind).string(": ");
        logMethod(log, info);
        log.string(" ").unsigned(current).string(":{");
        recentCodeCacheOperations.append(log.getResult());
        return current;
    }

    void logMethodOperationEnd(long operationNumber) {
        StringBuilderLog log = new StringBuilderLog();
        log.string("}:").unsigned(operationNumber);
        recentCodeCacheOperations.append(log.getResult());
    }

    public boolean walkRuntimeMethods(MemoryWalker.Visitor visitor) {
        VMOperation.guaranteeInProgress("Modifying code tables that are used by the GC");
        boolean continueVisiting = true;
        for (int i = 0; (continueVisiting && (i < numMethods)); i += 1) {
            continueVisiting = visitor.visitCode(NonmovableArrays.getWord(methodInfos, i),
                            ImageSingletons.lookup(CodeInfoMemoryWalker.class));
        }
        return continueVisiting;
    }
}
