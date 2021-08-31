/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.Arrays;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeInfoHistory;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicWord;
import com.oracle.svm.core.locks.VMLockSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.stack.ThreadStackPrinter.StackFramePrintVisitor;
import com.oracle.svm.core.stack.ThreadStackPrinter.Stage0StackFramePrintVisitor;
import com.oracle.svm.core.stack.ThreadStackPrinter.Stage1StackFramePrintVisitor;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.util.Counter;

public class SubstrateDiagnostics {
    private static final FastThreadLocalBytes<CCharPointer> threadOnlyAttachedForCrashHandler = FastThreadLocalFactory.createBytes(() -> 1);
    private static final PrintDiagnosticsState state = new PrintDiagnosticsState();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setOnlyAttachedForCrashHandler(IsolateThread thread) {
        threadOnlyAttachedForCrashHandler.getAddress(thread).write((byte) 1);
    }

    private static boolean isThreadOnlyAttachedForCrashHandler(IsolateThread thread) {
        return threadOnlyAttachedForCrashHandler.getAddress(thread).read() != 0;
    }

    public static boolean isInProgress() {
        return state.diagnosticThread.get().isNonNull();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInProgressByCurrentThread() {
        return state.diagnosticThread.get() == CurrentIsolate.getCurrentThread();
    }

    public static int maxInvocations() {
        int result = 0;
        DiagnosticThunkRegister thunks = DiagnosticThunkRegister.getSingleton();
        for (int i = 0; i < thunks.size(); i++) {
            result += thunks.getThunk(i).maxInvocations();
        }
        return result;
    }

    public static void printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess) {
        if (value.notEqual(0) && !RuntimeCodeInfoMemory.singleton().printLocationInfo(log, value, allowJavaHeapAccess) && !VMThreads.printLocationInfo(log, value) &&
                        !Heap.getHeap().printLocationInfo(log, value, allowJavaHeapAccess)) {
            log.string("is an unknown value");
        }
    }

    /** Prints extensive diagnostic information to the given Log. */
    public static boolean print(Log log, Pointer sp, CodePointer ip) {
        return print(log, sp, ip, WordFactory.nullPointer(), false);
    }

    /**
     * Print diagnostics for the various subsystems. If a fatal error occurs while printing
     * diagnostics, it can happen that the same thread enters this method multiple times.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    public static boolean print(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context context, boolean frameHasCalleeSavedRegisters) {
        log.newline();
        // Save the state of the initial error so that this state is consistently used, even if
        // further errors occur while printing diagnostics.
        if (!state.trySet(log, sp, ip, context, frameHasCalleeSavedRegisters) && !isInProgressByCurrentThread()) {
            log.string("Error: printDiagnostics already in progress by another thread.").newline();
            log.newline();
            return false;
        }

        printDiagnosticsForCurrentState();
        return true;
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "This method is single threaded. The fields 'diagnosticThunkIndex' and 'invocationCount' are only volatile to ensure that the updated field values are written right away.")
    private static void printDiagnosticsForCurrentState() {
        assert isInProgressByCurrentThread();

        Log log = state.log;
        if (state.diagnosticThunkIndex > 0) {
            // An error must have happened earlier as the code for printing diagnostics was invoked
            // recursively.
            log.resetIndentation();
        }

        // Print the various sections of the diagnostics and skip all sections that were already
        // printed earlier.
        int numDiagnosticThunks = DiagnosticThunkRegister.getSingleton().size();
        while (state.diagnosticThunkIndex < numDiagnosticThunks) {
            DiagnosticThunk thunk = DiagnosticThunkRegister.getSingleton().getThunk(state.diagnosticThunkIndex);
            while (++state.invocationCount <= thunk.maxInvocations()) {
                try {
                    thunk.printDiagnostics(log, state.invocationCount);
                    break;
                } catch (Throwable e) {
                    dumpException(log, thunk, e);
                }
            }

            state.diagnosticThunkIndex++;
            state.invocationCount = 0;
        }

        // Reset the state so that another thread can print diagnostics.
        state.clear();
    }

    static void dumpRuntimeCompilation(Log log) {
        assert VMOperation.isInProgressAtSafepoint();
        try {
            RuntimeCodeInfoHistory.singleton().printRecentOperations(log, true);
        } catch (Exception e) {
            dumpException(log, "DumpCodeCacheHistory", e);
        }

        log.newline();
        try {
            RuntimeCodeInfoMemory.singleton().printTable(log, true);
        } catch (Exception e) {
            dumpException(log, "DumpRuntimeCodeInfoMemory", e);
        }

        log.newline();
        try {
            Deoptimizer.logRecentDeoptimizationEvents(log);
        } catch (Exception e) {
            dumpException(log, "DumpRecentDeoptimizations", e);
        }
    }

    private static void dumpException(Log log, DiagnosticThunk thunk, Throwable e) {
        dumpException(log, thunk.getClass().getName(), e);
    }

    private static void dumpException(Log log, String currentDumper, Throwable e) {
        log.newline().string("[!!! Exception while executing ").string(currentDumper).string(": ").string(e.getClass().getName()).string("]");
        log.resetIndentation().newline();
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    private static long getTotalFrameSize(Pointer sp, CodePointer ip) {
        DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
        if (deoptFrame != null) {
            return deoptFrame.getSourceTotalFrameSize();
        }

        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        if (untetheredInfo.isNonNull()) {
            Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
            try {
                CodeInfo codeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
                return getTotalFrameSize0(ip, codeInfo);
            } finally {
                CodeInfoAccess.releaseTether(untetheredInfo, tether);
            }
        }
        return -1;
    }

    @Uninterruptible(reason = "Wrap the now safe call to interruptibly look up the frame size.", calleeMustBe = false)
    private static long getTotalFrameSize0(CodePointer ip, CodeInfo codeInfo) {
        return CodeInfoAccess.lookupTotalFrameSize(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip));
    }

    private static void logFrameAnchors(Log log, IsolateThread thread) {
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(thread);
        if (anchor.isNull()) {
            log.string("No anchors").newline();
        }
        while (anchor.isNonNull()) {
            log.string("Anchor ").zhex(anchor.rawValue()).string(" LastJavaSP ").zhex(anchor.getLastJavaSP().rawValue()).string(" LastJavaIP ").zhex(anchor.getLastJavaIP().rawValue()).newline();
            anchor = anchor.getPreviousAnchor();
        }
    }

    private static class PrintDiagnosticsState {
        AtomicWord<IsolateThread> diagnosticThread = new AtomicWord<>();
        volatile int diagnosticThunkIndex;
        volatile int invocationCount;

        Log log;
        Pointer sp;
        CodePointer ip;
        RegisterDumper.Context context;
        boolean frameHasCalleeSavedRegisters;

        @SuppressWarnings("hiding")
        public boolean trySet(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context context, boolean frameHasCalleeSavedRegisters) {
            if (diagnosticThread.compareAndSet(WordFactory.nullPointer(), CurrentIsolate.getCurrentThread())) {
                assert diagnosticThunkIndex == 0;
                assert invocationCount == 0;
                this.log = log;
                this.sp = sp;
                this.ip = ip;
                this.context = context;
                this.frameHasCalleeSavedRegisters = frameHasCalleeSavedRegisters;
                return true;
            }
            return false;
        }

        public void clear() {
            log = null;
            sp = WordFactory.nullPointer();
            ip = WordFactory.nullPointer();
            context = WordFactory.nullPointer();
            frameHasCalleeSavedRegisters = false;

            diagnosticThunkIndex = 0;
            invocationCount = 0;

            diagnosticThread.set(WordFactory.nullPointer());
        }
    }

    private static class DumpRegisters extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 3;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            RegisterDumper.Context context = state.context;
            if (context.isNonNull()) {
                log.string("General purpose register values:").indent(true);
                RegisterDumper.singleton().dumpRegisters(log, context, invocationCount <= 2, invocationCount == 1);
                log.indent(false);
            } else if (CalleeSavedRegisters.supportedByPlatform() && state.frameHasCalleeSavedRegisters) {
                CalleeSavedRegisters.singleton().dumpRegisters(log, state.sp, invocationCount <= 2, invocationCount == 1);

            }
        }
    }

    private static class DumpInstructions extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 3;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            if (invocationCount < 3) {
                printBytesBeforeAndAfterIp(log, invocationCount);
            } else if (invocationCount == 3) {
                printWord(log);
            }
        }

        private static void printBytesBeforeAndAfterIp(Log log, int invocationCount) {
            // print 64 or 32 instruction bytes.
            int bytesToPrint = 64 >> invocationCount;
            hexDump(log, state.ip, bytesToPrint, bytesToPrint);
        }

        private static void printWord(Log log) {
            // just print one word starting at the ip
            hexDump(log, state.ip, 0, ConfigurationValues.getTarget().wordSize);
        }

        private static void hexDump(Log log, CodePointer ip, int bytesBefore, int bytesAfter) {
            log.string("Printing Instructions (ip=").zhex(ip).string("):").indent(true);
            log.hexdump(((Pointer) ip).subtract(bytesBefore), 1, bytesBefore + bytesAfter);
            log.indent(false).newline();
        }
    }

    private static class DumpTopOfCurrentThreadStack extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            Pointer sp = state.sp;
            log.string("Top of stack (sp=").zhex(sp).string("):").indent(true);

            UnsignedWord stackBase = VMThreads.StackBase.get();
            if (stackBase.equal(0)) {
                /*
                 * We have to be careful here and not dump too much of the stack: if there are not
                 * many frames on the stack, we segfault when going past the beginning of the stack.
                 */
                log.hexdump(state.sp, 8, 16);
            } else {
                int bytesToPrint = 512;
                UnsignedWord availableBytes = stackBase.subtract(sp);
                if (availableBytes.belowThan(bytesToPrint)) {
                    bytesToPrint = NumUtil.safeToInt(availableBytes.rawValue());
                }

                log.hexdump(sp, 8, bytesToPrint / 8);
            }
            log.indent(false).newline();
        }
    }

    private static class DumpDeoptStubPointer extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            if (DeoptimizationSupport.enabled()) {
                log.string("DeoptStubPointer address: ").zhex(DeoptimizationSupport.getDeoptStubPointer()).newline().newline();
            }
        }
    }

    private static class DumpTopFrame extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            // We already dump all safe values first, so there is nothing we could retry if an error
            // occurs.
            Pointer sp = state.sp;
            CodePointer ip = state.ip;

            log.string("Top frame info:").indent(true);
            if (sp.isNonNull() && ip.isNonNull()) {
                long totalFrameSize = getTotalFrameSize(sp, ip);
                DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
                if (deoptFrame != null) {
                    log.string("RSP ").zhex(sp).string(" frame was deoptimized:").newline();
                    log.string("SourcePC ").zhex(deoptFrame.getSourcePC()).newline();
                    log.string("SourceTotalFrameSize ").signed(totalFrameSize).newline();
                } else if (totalFrameSize != -1) {
                    log.string("TotalFrameSize in CodeInfoTable ").signed(totalFrameSize).newline();
                }

                if (totalFrameSize == -1) {
                    log.string("Does not look like a Java Frame. Use JavaFrameAnchors to find LastJavaSP:").newline();
                    JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor();
                    while (anchor.isNonNull() && anchor.getLastJavaSP().belowOrEqual(sp)) {
                        anchor = anchor.getPreviousAnchor();
                    }

                    if (anchor.isNonNull()) {
                        log.string("Found matching Anchor:").zhex(anchor).newline();
                        Pointer lastSp = anchor.getLastJavaSP();
                        log.string("LastJavaSP ").zhex(lastSp).newline();
                        CodePointer lastIp = anchor.getLastJavaIP();
                        log.string("LastJavaIP ").zhex(lastIp).newline();
                    }
                }
            }
            log.indent(false);
        }
    }

    private static class DumpThreads extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            dumpThreads(log, invocationCount == 1);
        }

        private static void dumpThreads(Log log, boolean accessThreadObject) {
            log.string("Threads:").indent(true);
            // Only used for diagnostics - iterate all threads without locking the thread mutex.
            for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                log.zhex(thread).spaces(1).string(VMThreads.StatusSupport.getStatusString(thread));
                if (accessThreadObject) {
                    Thread threadObj = JavaThreads.fromVMThread(thread);
                    log.string(" \"").string(threadObj.getName()).string("\" - ").object(threadObj);
                    if (threadObj.isDaemon()) {
                        log.string(", daemon");
                    }
                }
                log.string(", stack(").zhex(VMThreads.StackEnd.get(thread)).string(",").zhex(VMThreads.StackBase.get(thread)).string(")");
                log.newline();
            }
            log.indent(false);
        }
    }

    private static class DumpCurrentThreadLocals extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            printThreadLocals(log, invocationCount);
        }

        private static void printThreadLocals(Log log, int invocationCount) {
            IsolateThread currentThread = CurrentIsolate.getCurrentThread();
            if (isThreadOnlyAttachedForCrashHandler(currentThread)) {
                if (invocationCount == 1) {
                    log.string("The failing thread ").zhex(currentThread).string(" does not have a full set of VM thread locals as it is an unattached thread.").newline();
                    log.newline();
                }
            } else {
                log.string("VM thread locals for the failing thread ").zhex(currentThread).string(":").indent(true);
                VMThreadLocalInfos.dumpToLog(log, currentThread, invocationCount == 1);
                log.indent(false);
            }
        }
    }

    private static class DumpCurrentVMOperation extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            VMOperationControl.printCurrentVMOperation(log, invocationCount == 1);
            log.newline();
        }
    }

    private static class DumpVMOperationHistory extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            VMOperationControl.printRecentEvents(log, invocationCount == 1);
        }
    }

    private static class DumpCodeCacheHistory extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            if (DeoptimizationSupport.enabled()) {
                RuntimeCodeInfoHistory.singleton().printRecentOperations(log, invocationCount == 1);
            }
        }
    }

    private static class DumpRuntimeCodeInfoMemory extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            if (DeoptimizationSupport.enabled()) {
                RuntimeCodeInfoMemory.singleton().printTable(log, invocationCount == 1);
            }
        }
    }

    private static class DumpRecentDeoptimizations extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            if (DeoptimizationSupport.enabled()) {
                Deoptimizer.logRecentDeoptimizationEvents(log);
            }
        }
    }

    private static class DumpCounters extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            log.string("Counters:").indent(true);
            Counter.logValues();
            log.indent(false);
        }
    }

    private static class DumpCurrentThreadFrameAnchors extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            IsolateThread currentThread = CurrentIsolate.getCurrentThread();
            log.string("Java frame anchors for the failing thread ").zhex(currentThread).string(":").indent(true);
            logFrameAnchors(log, currentThread);
            log.indent(false);
        }
    }

    private static class DumpCurrentThreadDecodedStackTrace extends DiagnosticThunk {
        private static final Stage0StackFramePrintVisitor[] PRINT_VISITORS = new Stage0StackFramePrintVisitor[]{StackFramePrintVisitor.SINGLETON, Stage1StackFramePrintVisitor.SINGLETON,
                        Stage0StackFramePrintVisitor.SINGLETON};

        @Override
        public int maxInvocations() {
            return 3;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            Pointer sp = state.sp;
            CodePointer ip = state.ip;
            log.string("Stacktrace for the failing thread ").zhex(CurrentIsolate.getCurrentThread()).string(":").indent(true);
            ThreadStackPrinter.printStacktrace(sp, ip, PRINT_VISITORS[invocationCount - 1], log);
            log.indent(false);
        }
    }

    private static class DumpOtherStackTraces extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, int invocationCount) {
            if (VMOperation.isInProgressAtSafepoint()) {
                // Iterate all threads without checking if the thread mutex is locked (it should
                // be locked by this thread though because we are at a safepoint).
                for (IsolateThread vmThread = VMThreads.firstThreadUnsafe(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == CurrentIsolate.getCurrentThread()) {
                        continue;
                    }
                    try {
                        log.string("Thread ").zhex(vmThread).string(":").indent(true);
                        printFrameAnchors(log, vmThread);
                        printStackTrace(log, vmThread);
                        log.indent(false);
                    } catch (Exception e) {
                        dumpException(log, this, e);
                    }
                }
            }
        }

        private static void printFrameAnchors(Log log, IsolateThread vmThread) {
            log.string("Frame anchors:").indent(true);
            logFrameAnchors(log, vmThread);
            log.indent(false);
        }

        private static void printStackTrace(Log log, IsolateThread vmThread) {
            log.string("Stacktrace:").indent(true);
            JavaStackWalker.walkThread(vmThread, StackFramePrintVisitor.SINGLETON, log);
            log.redent(false);
        }
    }

    public abstract static class DiagnosticThunk {
        /**
         * Prints diagnostic information. This method may be invoked multiple times if an error
         * (e.g., exception or segfault) occurred during execution. However, the method will only be
         * invoked at most {@link #maxInvocations()} times. When the method is invoked for the first
         * time, the argument invocationCount is 1.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
        public abstract void printDiagnostics(Log log, int invocationCount);

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public abstract int maxInvocations();
    }

    public static class DiagnosticThunkRegister {
        DiagnosticThunk[] diagnosticThunks;

        /**
         * Get the register.
         *
         * This method is @Fold so anyone who uses it ensures there is a register.
         */
        @Fold
        /* { Checkstyle: allow synchronization. */
        public static synchronized DiagnosticThunkRegister getSingleton() {
            if (!ImageSingletons.contains(DiagnosticThunkRegister.class)) {
                ImageSingletons.add(DiagnosticThunkRegister.class, new DiagnosticThunkRegister());
            }
            return ImageSingletons.lookup(DiagnosticThunkRegister.class);
        }
        /* } Checkstyle: disallow synchronization. */

        @Platforms(Platform.HOSTED_ONLY.class)
        DiagnosticThunkRegister() {
            this.diagnosticThunks = new DiagnosticThunk[]{new DumpRegisters(), new DumpInstructions(), new DumpTopOfCurrentThreadStack(), new DumpDeoptStubPointer(), new DumpTopFrame(),
                            new DumpThreads(), new DumpCurrentThreadLocals(), new DumpCurrentVMOperation(), new DumpVMOperationHistory(), new DumpCodeCacheHistory(),
                            new DumpRuntimeCodeInfoMemory(), new DumpRecentDeoptimizations(), new DumpCounters(), new DumpCurrentThreadFrameAnchors(), new DumpCurrentThreadDecodedStackTrace(),
                            new DumpOtherStackTraces(), new VMLockSupport.DumpVMMutexes()};
        }

        /** Register a diagnostic thunk to be called after a segfault. */
        @Platforms(Platform.HOSTED_ONLY.class)
        /* { Checkstyle: allow synchronization. */
        public synchronized void register(DiagnosticThunk diagnosticThunk) {
            final DiagnosticThunk[] newArray = Arrays.copyOf(diagnosticThunks, diagnosticThunks.length + 1);
            newArray[newArray.length - 1] = diagnosticThunk;
            diagnosticThunks = newArray;
        }
        /* } Checkstyle: disallow synchronization. */

        @Fold
        int size() {
            return diagnosticThunks.length;
        }

        DiagnosticThunk getThunk(int index) {
            return diagnosticThunks[index];
        }
    }
}
