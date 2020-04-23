/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.RingBuffer;

public class RuntimeCodeCache {

    public static class Options {
        @Option(help = "Print logging information for runtime code cache modifications")//
        public static final RuntimeOptionKey<Boolean> TraceCodeCache = new RuntimeOptionKey<>(false);

        @Option(help = "Allocate code cache with write access, allowing inlining of objects", type = OptionType.Expert)//
        public static final RuntimeOptionKey<Boolean> WriteableCodeCache = new RuntimeOptionKey<>(false);
    }

    private final RingBuffer<CodeCacheLogEntry> recentCodeCacheOperations = new RingBuffer<>(30, () -> new CodeCacheLogEntry());
    private long codeCacheOperationSequenceNumber;

    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "RuntimeCodeInfo");
    private final Counter lookupMethodCount = new Counter(counters, "lookupMethod", "");
    private final Counter addMethodCount = new Counter(counters, "addMethod", "");
    private final Counter invalidateMethodCount = new Counter(counters, "invalidateMethod", "");
    private final CodeNotOnStackVerifier codeNotOnStackVerifier = new CodeNotOnStackVerifier();

    static final String INFO_ADD = "Add";
    static final String INFO_INVALIDATE = "Invalidate";

    private static final int INITIAL_TABLE_SIZE = 100;

    private NonmovableArray<UntetheredCodeInfo> codeInfos;
    private int numCodeInfos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeCodeCache() {
    }

    /** Tear down the heap, return all allocated virtual memory chunks to VirtualMemoryProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        NonmovableArrays.releaseUnmanagedArray(codeInfos);
        codeInfos = NonmovableArrays.nullArray();

        // releases all CodeInfos from our table too
        RuntimeCodeInfoMemory.singleton().tearDown();
    }

    /**
     * Looking up a method is lock-free: it is called frequently during stack walking, so locking or
     * even a {@link VMOperation} would be too slow. The lookup must access the {@link #codeInfos}
     * array, which is modified non-atomically when adding or removing methods. All modifications
     * are done from within a {@link VMOperation}. Making this method {@link Uninterruptible}
     * ensures that we see one consistent snapshot of the array, without the possibility for a
     * concurrent modification.
     */
    @Uninterruptible(reason = "codeInfos is accessed without holding a lock, so must not be interrupted by a safepoint that can add/remove code", callerMustBe = true)
    protected UntetheredCodeInfo lookupCodeInfo(CodePointer ip) {
        lookupMethodCount.inc();
        assert verifyTable();
        if (numCodeInfos == 0) {
            return WordFactory.nullPointer();
        }

        int idx = binarySearch(codeInfos, 0, numCodeInfos, ip);
        if (idx >= 0) {
            /* Exact hit, ip is the begin of the method. */
            return NonmovableArrays.getWord(codeInfos, idx);
        }

        int insertionPoint = -idx - 1;
        if (insertionPoint == 0) {
            /* ip is below the first method, so no hit. */
            assert ((UnsignedWord) ip).belowThan((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(NonmovableArrays.getWord(codeInfos, 0)));
            return WordFactory.nullPointer();
        }

        UntetheredCodeInfo info = NonmovableArrays.getWord(codeInfos, insertionPoint - 1);
        assert ((UnsignedWord) ip).aboveThan((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(info));
        if (((UnsignedWord) ip).subtract((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(info)).aboveOrEqual(UntetheredCodeInfoAccess.getCodeSize(info))) {
            /* ip is not within the range of a method. */
            return WordFactory.nullPointer();
        }

        return info;
    }

    /* Copied and adapted from Arrays.binarySearch. */
    @Uninterruptible(reason = "called from uninterruptible code")
    private static int binarySearch(NonmovableArray<UntetheredCodeInfo> a, int fromIndex, int toIndex, CodePointer key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            CodePointer midVal = UntetheredCodeInfoAccess.getCodeStart(NonmovableArrays.getWord(a, mid));

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
        VMOperation.guaranteeInProgressAtSafepoint("Modifying code tables that are used by the GC");
        InstalledCodeObserverSupport.activateObservers(RuntimeCodeInfoAccess.getCodeObserverHandles(info));
        long num = logMethodOperation(info, INFO_ADD);
        addMethodOperation(info);
        logMethodOperationEnd(num);
    }

    private void addMethodOperation(CodeInfo info) {
        addMethodCount.inc();
        assert verifyTable();
        if (Options.TraceCodeCache.getValue()) {
            Log.log().string("[" + INFO_ADD + " method: ");
            logCodeInfo(Log.log(), info);
            Log.log().string("]").newline();
        }

        if (codeInfos.isNull() || numCodeInfos >= NonmovableArrays.lengthOf(codeInfos)) {
            enlargeTable();
            assert verifyTable();
        }
        assert numCodeInfos < NonmovableArrays.lengthOf(codeInfos);

        int idx = binarySearch(codeInfos, 0, numCodeInfos, CodeInfoAccess.getCodeStart(info));
        assert idx < 0 : "must not find code already in table";
        int insertionPoint = -idx - 1;
        NonmovableArrays.arraycopy(codeInfos, insertionPoint, codeInfos, insertionPoint + 1, numCodeInfos - insertionPoint);
        numCodeInfos++;
        NonmovableArrays.setWord(codeInfos, insertionPoint, info);

        if (Options.TraceCodeCache.getValue()) {
            logTable();
        }
        assert verifyTable();
    }

    private void enlargeTable() {
        int newTableSize = numCodeInfos * 2;
        if (newTableSize < INITIAL_TABLE_SIZE) {
            newTableSize = INITIAL_TABLE_SIZE;
        }
        NonmovableArray<UntetheredCodeInfo> newCodeInfos = NonmovableArrays.createWordArray(newTableSize);
        if (codeInfos.isNonNull()) {
            NonmovableArrays.arraycopy(codeInfos, 0, newCodeInfos, 0, NonmovableArrays.lengthOf(codeInfos));
            NonmovableArrays.releaseUnmanagedArray(codeInfos);
        }
        codeInfos = newCodeInfos;
    }

    protected void invalidateMethod(CodeInfo info) {
        prepareInvalidation(info);

        /*
         * Deoptimize all invocations that are on the stack. This performs a stack walk, so all
         * metadata must be intact (even though the method was already marked as non-invokable).
         */
        Deoptimizer.deoptimizeInRange(CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info), false);

        finishInvalidation(info);
    }

    protected void invalidateNonStackMethod(CodeInfo info) {
        prepareInvalidation(info);
        assert codeNotOnStackVerifier.verify(info);
        finishInvalidation(info);
    }

    private void prepareInvalidation(CodeInfo info) {
        VMOperation.guaranteeInProgressAtSafepoint("Modifying code tables that are used by the GC");
        invalidateMethodCount.inc();
        assert verifyTable();
        if (Options.TraceCodeCache.getValue()) {
            Log.log().string("[" + INFO_INVALIDATE + " method: ");
            logCodeInfo(Log.log(), info);
            Log.log().string("]").newline();
        }

        SubstrateInstalledCode installedCode = RuntimeCodeInfoAccess.getInstalledCode(info);
        if (installedCode != null) {
            assert !installedCode.isValid() || CodeInfoAccess.getCodeStart(info).rawValue() == installedCode.getAddress();
            /*
             * Until this point, the InstalledCode is valid. It can be invoked, and frames can be on
             * the stack. All the metadata must be valid until this point. Make it non-entrant,
             * i.e., ensure it cannot be invoked any more.
             */
            installedCode.clearAddress();
        }

    }

    private void finishInvalidation(CodeInfo info) {
        /*
         * Now it is guaranteed that the InstalledCode is not on the stack and cannot be invoked
         * anymore, so we can free the code and all metadata.
         */

        /* Remove info entry from our table. */
        int idx = binarySearch(codeInfos, 0, numCodeInfos, CodeInfoAccess.getCodeStart(info));
        assert idx >= 0 : "info must be in table";
        NonmovableArrays.arraycopy(codeInfos, idx + 1, codeInfos, idx, numCodeInfos - (idx + 1));
        numCodeInfos--;
        NonmovableArrays.setWord(codeInfos, numCodeInfos, WordFactory.nullPointer());

        RuntimeCodeInfoAccess.partialReleaseAfterInvalidate(info);

        if (Options.TraceCodeCache.getValue()) {
            logTable();
        }
        assert verifyTable();
    }

    @Uninterruptible(reason = "called from uninterruptible code")
    private boolean verifyTable() {
        if (codeInfos.isNull()) {
            assert numCodeInfos == 0 : "a1";
            return true;
        }

        assert numCodeInfos <= NonmovableArrays.lengthOf(codeInfos) : "a11";

        for (int i = 0; i < numCodeInfos; i++) {
            UntetheredCodeInfo info = NonmovableArrays.getWord(codeInfos, i);
            assert info.isNonNull() : "a20";
            assert i == 0 || ((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(NonmovableArrays.getWord(codeInfos, i - 1)))
                            .belowThan((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(NonmovableArrays.getWord(codeInfos, i))) : "a22";
            assert i == 0 || ((UnsignedWord) UntetheredCodeInfoAccess.getCodeEnd(NonmovableArrays.getWord(codeInfos, i - 1)))
                            .belowOrEqual((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(info)) : "a23";
        }

        for (int i = numCodeInfos; i < NonmovableArrays.lengthOf(codeInfos); i++) {
            assert NonmovableArrays.getWord(codeInfos, i).isNull() : "a31";
        }
        return true;
    }

    public void logTable() {
        logTable(Log.log());
    }

    private static final RingBuffer.Consumer<CodeCacheLogEntry> consumer = (context, e) -> e.log(Log.log());

    public void logRecentOperations(Log log) {
        log.string("== [Recent RuntimeCodeCache operations: ");
        recentCodeCacheOperations.foreach(consumer);
        log.string("]").newline();
    }

    public void logTable(Log log) {
        log.string("== [RuntimeCodeCache: ").signed(numCodeInfos).string(" methods");
        for (int i = 0; i < numCodeInfos; i++) {
            logCodeInfo(log, i);
        }
        log.string("]").newline();
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    private void logCodeInfo(Log log, int i) {
        UntetheredCodeInfo untetheredInfo = NonmovableArrays.getWord(codeInfos, i);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
            logCodeInfo0(log, info);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Pass the now protected CodeInfo to interruptible code.", calleeMustBe = false)
    private static void logCodeInfo0(Log log, CodeInfo info) {
        log.newline().hex(CodeInfoAccess.getCodeStart(info)).string("  ");
        logCodeInfo(log, info);
    }

    private static void logCodeInfo(Log log, CodeInfo info) {
        logCodeInfo(log, CodeInfoAccess.getName(info), CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info), CodeInfoAccess.getCodeSize(info));
    }

    private static void logCodeInfo(Log log, String codeName, CodePointer codeStart, CodePointer codeEnd, UnsignedWord codeSize) {
        log.string(codeName);
        log.string("  ip: ").hex(codeStart).string(" - ").hex(codeEnd);
        log.string("  size: ").unsigned(codeSize);
        /*
         * Note that we are not trying to output the InstalledCode object. It is not a pinned
         * object, so when log printing (for, e.g., a fatal error) occurs during a GC, then the VM
         * could segfault.
         */
    }

    long logMethodOperation(CodeInfo info, String kind) {
        long current = ++codeCacheOperationSequenceNumber;
        recentCodeCacheOperations.next().setValues(current, kind, CodeInfoAccess.getName(info), CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info), CodeInfoAccess.getCodeSize(info));
        return current;
    }

    void logMethodOperationEnd(long operationNumber) {
        recentCodeCacheOperations.next().setValues(operationNumber, null, null, WordFactory.nullPointer(), WordFactory.nullPointer(), WordFactory.unsigned(0));
    }

    public boolean walkRuntimeMethods(MemoryWalker.Visitor visitor) {
        VMOperation.guaranteeInProgress("Modifying code tables that are used by the GC");
        boolean continueVisiting = true;
        for (int i = 0; (continueVisiting && (i < numCodeInfos)); i += 1) {
            continueVisiting = walkRuntimeMethod(visitor, i);
        }
        return continueVisiting;
    }

    @Uninterruptible(reason = "Must prevent the GC from freeing the CodeInfo object.")
    private boolean walkRuntimeMethod(MemoryWalker.Visitor visitor, int i) {
        boolean continueVisiting;
        UntetheredCodeInfo untetheredInfo = NonmovableArrays.getWord(codeInfos, i);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo codeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            continueVisiting = visitRuntimeMethod0(visitor, codeInfo);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
        return continueVisiting;
    }

    @Uninterruptible(reason = "Pass the now protected CodeInfo to interruptible code.", calleeMustBe = false)
    private static boolean visitRuntimeMethod0(MemoryWalker.Visitor visitor, CodeInfo codeInfo) {
        return visitor.visitCode(codeInfo, ImageSingletons.lookup(CodeInfoMemoryWalker.class));
    }

    private static class CodeCacheLogEntry {
        private long sequenceNumber;
        private String kind;
        private String codeName;
        private CodePointer codeStart;
        private CodePointer codeEnd;
        private UnsignedWord codeSize;

        @Platforms(Platform.HOSTED_ONLY.class)
        CodeCacheLogEntry() {
        }

        public void setValues(long sequenceNumber, String kind, String codeName, CodePointer codeStart, CodePointer codeEnd, UnsignedWord codeSize) {
            this.sequenceNumber = sequenceNumber;
            this.kind = kind;
            this.codeName = codeName;
            this.codeStart = codeStart;
            this.codeEnd = codeEnd;
            this.codeSize = codeSize;
        }

        public void log(Log log) {
            log.newline();
            if (kind != null) {
                log.string(kind).string(": ");
                logCodeInfo(log, codeName, codeStart, codeEnd, codeSize);
                log.string(" ").unsigned(sequenceNumber).string(":{");
            } else {
                log.string("}:").unsigned(sequenceNumber);
            }
        }
    }

    private static final class CodeNotOnStackVerifier extends StackFrameVisitor {
        private CodeInfo codeInfoToCheck;

        @Platforms(Platform.HOSTED_ONLY.class)
        CodeNotOnStackVerifier() {
        }

        @NeverInline("Starting a stack walk.")
        public boolean verify(CodeInfo info) {
            this.codeInfoToCheck = info;

            Pointer sp = readCallerStackPointer();
            JavaStackWalker.walkCurrentThread(sp, this);
            if (SubstrateOptions.MultiThreaded.getValue()) {
                for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == CurrentIsolate.getCurrentThread()) {
                        continue;
                    }
                    JavaStackWalker.walkThread(vmThread, this);
                }
            }
            return true;
        }

        @Override
        public boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo currentCodeInfo, DeoptimizedFrame deoptimizedFrame) {
            assert currentCodeInfo != codeInfoToCheck;
            return true;
        }
    }

    /** This is the interface that clients have to implement. */
    public interface CodeInfoVisitor {
        /**
         * Visit compiled code, using the provided access methods. Return true if visiting should
         * continue, else false.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while visiting code.")
        <T extends CodeInfo> boolean visitCode(T codeInfo);
    }
}
