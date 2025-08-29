/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.deopt.Deoptimizer.Options.LazyDeoptimization;
import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;
import static com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode.WRITE;
import static com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode.CREATE_OR_REPLACE;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.RawFileOperationSupport;
import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.Counter;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.word.Word;

public class RuntimeCodeCache {

    public static class Options {
        @Option(help = "Print logging information for runtime code cache modifications")//
        public static final RuntimeOptionKey<Boolean> TraceCodeCache = new RuntimeOptionKey<>(false);

        @Option(help = "Allocate code cache with write access, allowing inlining of objects", type = OptionType.Expert)//
        public static final RuntimeOptionKey<Boolean> WriteableCodeCache = new RuntimeOptionKey<>(false, RelevantForCompilationIsolates) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                if (newValue && !SubstrateUtil.HOSTED && Platform.includedIn(Platform.AARCH64.class)) {
                    throw new IllegalArgumentException("Enabling " + getName() + " is not supported on this platform.");
                }
            }
        };
    }

    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "RuntimeCodeInfo");
    private final Counter lookupMethodCount = new Counter(counters, "lookupMethod", "");
    private final Counter addMethodCount = new Counter(counters, "addMethod", "");
    private final Counter invalidateMethodCount = new Counter(counters, "invalidateMethod", "");
    private final CodeNotOnStackVerifier codeNotOnStackVerifier = new CodeNotOnStackVerifier();

    private static final int INITIAL_TABLE_SIZE = 100;

    private NonmovableArray<UntetheredCodeInfo> codeInfos;
    private int numCodeInfos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeCodeCache() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        NonmovableArrays.releaseUnmanagedArray(codeInfos);
        codeInfos = NonmovableArrays.nullArray();
        numCodeInfos = 0;

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
            return Word.nullPointer();
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
            return Word.nullPointer();
        }

        UntetheredCodeInfo info = NonmovableArrays.getWord(codeInfos, insertionPoint - 1);
        assert ((UnsignedWord) ip).aboveThan((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(info));
        if (((UnsignedWord) ip).subtract((UnsignedWord) UntetheredCodeInfoAccess.getCodeStart(info)).aboveOrEqual(UntetheredCodeInfoAccess.getCodeSize(info))) {
            /* ip is not within the range of a method. */
            return Word.nullPointer();
        }

        return info;
    }

    /* Copied and adapted from Arrays.binarySearch. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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

    private static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    private static void dumpMethod(CodeInfo info) {
        String tmpDirPath = getFileSupport().getTempDirectory();
        String prefix = System.nanoTime() + "_";
        String methodName = SubstrateUtil.sanitizeForFileName(CodeInfoAccess.getName(info));
        String suffix = "_" + CodeInfoAccess.getTier(info) + ".bin";

        // Check that the file name size does not exceed the 255 chars
        int maxMethodNameSize = 255 - prefix.length() - suffix.length();
        if (methodName.length() > maxMethodNameSize) {
            methodName = methodName.substring(maxMethodNameSize);
        }

        String filePath = tmpDirPath + "/" + prefix + methodName + suffix;

        RawFileOperationSupport.RawFileDescriptor fd = getFileSupport().create(filePath, CREATE_OR_REPLACE, WRITE);
        if (!getFileSupport().isValid(fd)) {
            Log.log().string("Failed to dump runtime compiled code: '").string(filePath).string("' could not be created.").newline();
            return;
        }

        try {
            CCharPointer codeStart = (CCharPointer) CodeInfoAccess.getCodeStart(info);

            UnsignedWord codeSize = CodeInfoAccess.getCodeSize(info);
            if (!RawFileOperationSupport.bigEndian().write(fd, (Pointer) codeStart, codeSize)) {
                Log.log().string("Dumping method to ").string(filePath).string(" failed").newline();
            }
        } finally {
            getFileSupport().close(fd);
        }
    }

    public void addMethod(CodeInfo info) {
        assert VMOperation.isInProgressAtSafepoint() : "invalid state";
        InstalledCodeObserverSupport.activateObservers(RuntimeCodeInfoAccess.getCodeObserverHandles(info));

        addMethod0(info);
        RuntimeCodeInfoHistory.singleton().logAdd(info);

        if (SubstrateOptions.hasDumpRuntimeCompiledMethodsSupport()) {
            dumpMethod(info);
        }
    }

    @Uninterruptible(reason = "Modifying code tables that are used by the GC")
    private void addMethod0(CodeInfo info) {
        addMethodCount.inc();
        assert verifyTable();
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

        assert verifyTable();
    }

    @Uninterruptible(reason = "Modifying code tables that are used by the GC")
    private void enlargeTable() {
        int newTableSize = numCodeInfos * 2;
        if (newTableSize < INITIAL_TABLE_SIZE) {
            newTableSize = INITIAL_TABLE_SIZE;
        }
        NonmovableArray<UntetheredCodeInfo> newCodeInfos = NonmovableArrays.createWordArray(newTableSize, NmtCategory.Code);
        if (codeInfos.isNonNull()) {
            NonmovableArrays.arraycopy(codeInfos, 0, newCodeInfos, 0, NonmovableArrays.lengthOf(codeInfos));
            NonmovableArrays.releaseUnmanagedArray(codeInfos);
        }
        codeInfos = newCodeInfos;
    }

    protected void invalidateMethod(CodeInfo info) {
        assert VMOperation.isInProgressAtSafepoint() : "illegal state";
        prepareInvalidation(info);

        /*
         * Deoptimize all invocations that are on the stack. This performs a stack walk, so all
         * metadata must be intact (even though the method was already marked as non-invokable).
         */
        Deoptimizer.deoptimizeInRange(CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info), false, CurrentIsolate.getCurrentThread());

        boolean removeNow = !LazyDeoptimization.getValue();
        continueInvalidation(info, removeNow);
    }

    protected void invalidateNonStackMethod(CodeInfo info) {
        assert VMOperation.isGCInProgress() : "may only be called by the GC";
        prepareInvalidation(info);
        assert codeNotOnStackVerifier.verify(info);

        /*
         * This method is called by the GC, so we must call continueInvalidation with removeNow
         * being true, so that code is actually removed from the code cache.
         */
        continueInvalidation(info, true);
    }

    private void prepareInvalidation(CodeInfo info) {
        invalidateMethodCount.inc();
        assert verifyTable();

        SubstrateInstalledCode installedCode = RuntimeCodeInfoAccess.getInstalledCode(info);
        if (installedCode != null) {
            assert !installedCode.isAlive() || CodeInfoAccess.getCodeStart(info).rawValue() == installedCode.getAddress() : installedCode;
            /*
             * Until here, the InstalledCode may be valid (can be invoked) or alive (frames can be
             * on the stack). All the metadata must be valid until this point. Ensure it is
             * non-entrant, that is, it cannot be invoked any more.
             */
            installedCode.clearAddress();
        }
    }

    private void continueInvalidation(CodeInfo info, boolean removeNow) {
        if (removeNow) {
            /* If removeNow, then the CodeInfo is immediately removed from the code cache. */
            InstalledCodeObserverSupport.removeObservers(RuntimeCodeInfoAccess.getCodeObserverHandles(info));
            removeFromCodeCache(info);
            RuntimeCodeInfoHistory.singleton().logInvalidate(info);
        } else {
            /*
             * Otherwise, we leave the CodeInfo to be collected by GC after no stack activations are
             * remaining by marking it as non-entrant. Note that the corresponding InstalledCode
             * object is fully invalidated at that point (this is a major difference to normal
             * non-entrant code, where the InstalledCode object remains valid).
             */
            if (CodeInfoAccess.getState(info) < CodeInfo.STATE_NON_ENTRANT) {
                CodeInfoAccess.setState(info, CodeInfo.STATE_NON_ENTRANT);
                RuntimeCodeInfoHistory.singleton().logInvalidate(info);
            }
        }
    }

    /**
     * Remove info entry from our table. This should only be called when the CodeInfo is no longer
     * on the stack and cannot be invoked anymore
     */
    @Uninterruptible(reason = "Modifying code tables that are used by the GC")
    private void removeFromCodeCache(CodeInfo info) {
        int idx = binarySearch(codeInfos, 0, numCodeInfos, CodeInfoAccess.getCodeStart(info));
        assert idx >= 0 : "info must be in table";
        NonmovableArrays.arraycopy(codeInfos, idx + 1, codeInfos, idx, numCodeInfos - (idx + 1));
        numCodeInfos--;
        NonmovableArrays.setWord(codeInfos, numCodeInfos, Word.nullPointer());

        RuntimeCodeInfoAccess.markAsRemovedFromCodeCache(info);
        assert verifyTable();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                if (vmThread == CurrentIsolate.getCurrentThread()) {
                    continue;
                }
                JavaStackWalker.walkThread(vmThread, this);
            }
            return true;
        }

        @Override
        public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo currentCodeInfo) {
            assert currentCodeInfo != codeInfoToCheck : currentCodeInfo.rawValue();
            return true;
        }

        @Override
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
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
        void visitCode(CodeInfo codeInfo);
    }
}
