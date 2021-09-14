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
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArrays;
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
import com.oracle.svm.core.util.VMError;

public class SubstrateDiagnostics {
    private static final FastThreadLocalBytes<CCharPointer> threadOnlyAttachedForCrashHandler = FastThreadLocalFactory.createBytes(() -> 1);
    private static final PrintDiagnosticsState fatalErrorState = new PrintDiagnosticsState();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setOnlyAttachedForCrashHandler(IsolateThread thread) {
        threadOnlyAttachedForCrashHandler.getAddress(thread).write((byte) 1);
    }

    private static boolean isThreadOnlyAttachedForCrashHandler(IsolateThread thread) {
        return threadOnlyAttachedForCrashHandler.getAddress(thread).read() != 0;
    }

    public static boolean isFatalErrorHandlingInProgress() {
        return fatalErrorState.diagnosticThread.get().isNonNull();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isFatalErrorHandlingThread() {
        return fatalErrorState.diagnosticThread.get() == CurrentIsolate.getCurrentThread();
    }

    public static int maxInvocations() {
        int result = 0;
        DiagnosticThunkRegister thunks = DiagnosticThunkRegister.getSingleton();
        for (int i = 0; i < thunks.size(); i++) {
            result += thunks.getThunk(i).maxInvocations();
        }
        return result;
    }

    public static void printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        if (value.notEqual(0) &&
                        !RuntimeCodeInfoMemory.singleton().printLocationInfo(log, value, allowJavaHeapAccess, allowUnsafeOperations) &&
                        !VMThreads.printLocationInfo(log, value, allowUnsafeOperations) &&
                        !Heap.getHeap().printLocationInfo(log, value, allowJavaHeapAccess, allowUnsafeOperations)) {
            log.string("is an unknown value");
        }
    }

    /**
     * See {@link #printInformation(Log, Pointer, CodePointer, RegisterDumper.Context, boolean)}.
     */
    public static void printInformation(Log log, Pointer sp, CodePointer ip) {
        printInformation(log, sp, ip, WordFactory.nullPointer(), false);
    }

    /**
     * This method prints diagnostic information. This method prints less information than
     * {@link #printFatalError} as it only uses reasonably safe operations and memory accesses. So,
     * as long as all parts of Native Image are fully functional, calling this method will never
     * cause a crash.
     */
    public static void printInformation(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context registerContext, boolean frameHasCalleeSavedRegisters) {
        // Stack allocate an error context.
        ErrorContext errorContext = StackValue.get(ErrorContext.class);
        errorContext.setStackPointer(sp);
        errorContext.setInstructionPointer(ip);
        errorContext.setRegisterContext(registerContext);
        errorContext.setFrameHasCalleeSavedRegisters(frameHasCalleeSavedRegisters);

        // Print all thunks in a reasonably safe way.
        int numDiagnosticThunks = DiagnosticThunkRegister.getSingleton().size();
        for (int i = 0; i < numDiagnosticThunks; i++) {
            DiagnosticThunk thunk = DiagnosticThunkRegister.getSingleton().getThunk(i);
            int diagnosticLevel = DiagnosticThunkRegister.getSingleton().getDefaultDiagnosticLevel(i) & DiagnosticLevel.SAFE;
            if (diagnosticLevel != DiagnosticLevel.DISABLED) {
                thunk.printDiagnostics(log, errorContext, diagnosticLevel, 1);
            }
        }
    }

    /**
     * See {@link #printFatalError(Log, Pointer, CodePointer, RegisterDumper.Context, boolean)}.
     */
    public static boolean printFatalError(Log log, Pointer sp, CodePointer ip) {
        return printFatalError(log, sp, ip, WordFactory.nullPointer(), false);
    }

    /**
     * This method prints extensive diagnostic information and is only called for fatal errors. This
     * method may use operations and memory accesses that are not necessarily 100% safe. So, even if
     * all parts of Native Image are fully functional, this method may cause crashes.
     * <p>
     * If a segfault handler is present, then this method will be called recursively multiple times
     * if further errors happen while printing diagnostics. On each recursive invocation, the level
     * of detail of the diagnostic output will be reduced gradually. This recursion will terminate
     * once the maximum amount of information is printed.
     * <p>
     * In scenarios without a segfault handler, it can happen that this method reliably causes a
     * subsequent error that crashes Native Image. In such a case, try to reduce the level of detail
     * of the diagnostic output (see {@link SubstrateOptions#DiagnosticDetails} to get a reasonably
     * complete diagnostic output.
     */
    public static boolean printFatalError(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context registerContext, boolean frameHasCalleeSavedRegisters) {
        log.newline();
        // Save the state of the initial error so that this state is consistently used, even if
        // further errors occur while printing diagnostics.
        if (!fatalErrorState.trySet(log, sp, ip, registerContext, frameHasCalleeSavedRegisters) && !isFatalErrorHandlingThread()) {
            log.string("Error: printDiagnostics already in progress by another thread.").newline();
            log.newline();
            return false;
        }

        printFatalErrorForCurrentState();
        return true;
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "This method is single threaded. The fields 'diagnosticThunkIndex' and 'invocationCount' are only volatile to ensure that the updated field values are written right away.")
    private static void printFatalErrorForCurrentState() {
        assert isFatalErrorHandlingThread();

        Log log = fatalErrorState.log;
        if (fatalErrorState.diagnosticThunkIndex > 0) {
            // An error must have happened earlier as the code for printing diagnostics was invoked
            // recursively.
            log.resetIndentation();
        }

        // Print the various sections of the diagnostics and skip all sections that were already
        // printed earlier.
        ErrorContext errorContext = fatalErrorState.getErrorContext();
        int numDiagnosticThunks = DiagnosticThunkRegister.getSingleton().size();
        while (fatalErrorState.diagnosticThunkIndex < numDiagnosticThunks) {
            int index = fatalErrorState.diagnosticThunkIndex;
            DiagnosticThunk thunk = DiagnosticThunkRegister.getSingleton().getThunk(index);
            int diagnosticLevel = DiagnosticThunkRegister.getSingleton().getDefaultDiagnosticLevel(index);
            if (diagnosticLevel != DiagnosticLevel.DISABLED) {
                while (++fatalErrorState.invocationCount <= thunk.maxInvocations()) {
                    try {
                        thunk.printDiagnostics(log, errorContext, diagnosticLevel, fatalErrorState.invocationCount);
                        break;
                    } catch (Throwable e) {
                        dumpException(log, thunk, e);
                    }
                }
            }

            fatalErrorState.diagnosticThunkIndex++;
            fatalErrorState.invocationCount = 0;
        }

        // Reset the state so that another thread can print diagnostics.
        fatalErrorState.clear();
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
            boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(DiagnosticLevel.SAFE);
            boolean allowUnsafeOperations = DiagnosticLevel.unsafeOperationsAllowed(DiagnosticLevel.SAFE);
            RuntimeCodeInfoMemory.singleton().printTable(log, true, false);
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

    public static void updateDiagnosticLevels(String thunks) {
        int pos = 0;
        int end;
        while ((end = thunks.indexOf(',', pos)) >= 0) {
            String entry = thunks.substring(pos, end);
            updateDiagnosticLevel(entry);
            pos = end + 1;
        }

        String entry = thunks.substring(pos);
        updateDiagnosticLevel(entry);
    }

    private static void updateDiagnosticLevel(String entry) {
        int pos = entry.indexOf(':');
        if (pos <= 0 || pos == entry.length() - 1) {
            throw VMError.shouldNotReachHere("Invalid value specified for " + SubstrateOptions.DiagnosticDetails.getValue() + ": '" + entry + "'");
        }

        String pattern = entry.substring(0, pos);
        int level = DiagnosticLevel.fromDiagnosticDetails(entry.substring(pos + 1));

        int numDiagnosticThunks = DiagnosticThunkRegister.getSingleton().size();
        int matches = 0;
        for (int i = 0; i < numDiagnosticThunks; i++) {
            DiagnosticThunk thunk = DiagnosticThunkRegister.getSingleton().getThunk(i);
            if (matches(thunk.getClass().getSimpleName(), pattern)) {
                DiagnosticThunkRegister.getSingleton().setDiagnosticLevel(i, level);
                matches++;
            }
        }

        VMError.guarantee(matches > 0, "The following pattern did not match any diagnostic thunk: '" + entry + "'");
    }

    private static boolean matches(String text, String pattern) {
        assert pattern.length() > 0;
        return matches(text, 0, pattern, 0);
    }

    private static boolean matches(String text, int textPos, String pattern, int patternPos) {
        while (textPos < text.length()) {
            if (pattern.charAt(patternPos) == '*') {
                while (!matches(text, textPos, pattern, patternPos + 1) && textPos < text.length()) {
                    textPos++;
                }
                return textPos < text.length();
            } else if (text.charAt(textPos) == pattern.charAt(patternPos)) {
                textPos++;
                patternPos++;
            } else {
                return false;
            }
        }

        while (patternPos < pattern.length() && pattern.charAt(patternPos) == '*') {
            patternPos++;
        }
        return patternPos == pattern.length();
    }

    private static class PrintDiagnosticsState {
        AtomicWord<IsolateThread> diagnosticThread = new AtomicWord<>();
        volatile int diagnosticThunkIndex;
        volatile int invocationCount;

        Log log;
        byte[] errorContextData;

        @Platforms(Platform.HOSTED_ONLY.class)
        PrintDiagnosticsState() {
            int errorContextSize = SizeOf.get(ErrorContext.class);
            errorContextData = new byte[errorContextSize];
        }

        public ErrorContext getErrorContext() {
            return NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(errorContextData), 0);
        }

        @SuppressWarnings("hiding")
        public boolean trySet(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context registerContext, boolean frameHasCalleeSavedRegisters) {
            if (diagnosticThread.compareAndSet(WordFactory.nullPointer(), CurrentIsolate.getCurrentThread())) {
                assert diagnosticThunkIndex == 0;
                assert invocationCount == 0;
                this.log = log;

                ErrorContext errorContext = getErrorContext();
                errorContext.setStackPointer(sp);
                errorContext.setInstructionPointer(ip);
                errorContext.setRegisterContext(registerContext);
                errorContext.setFrameHasCalleeSavedRegisters(frameHasCalleeSavedRegisters);
                return true;
            }
            return false;
        }

        public void clear() {
            log = null;

            ErrorContext errorContext = getErrorContext();
            errorContext.setStackPointer(WordFactory.nullPointer());
            errorContext.setInstructionPointer(WordFactory.nullPointer());
            errorContext.setRegisterContext(WordFactory.nullPointer());
            errorContext.setFrameHasCalleeSavedRegisters(false);

            diagnosticThunkIndex = 0;
            invocationCount = 0;

            diagnosticThread.set(WordFactory.nullPointer());
        }
    }

    private static class DumpRegisters extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 4;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            boolean printLocationInfo = invocationCount < 4;
            boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 3;
            boolean allowUnsafeOperations = DiagnosticLevel.unsafeOperationsAllowed(maxDiagnosticLevel) && invocationCount < 2;
            if (context.isNonNull()) {
                log.string("General purpose register values:").indent(true);
                RegisterDumper.singleton().dumpRegisters(log, context.getRegisterContext(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
                log.indent(false);
            } else if (CalleeSavedRegisters.supportedByPlatform() && context.frameHasCalleeSavedRegisters()) {
                CalleeSavedRegisters.singleton().dumpRegisters(log, context.getStackPointer(), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            if (invocationCount < 3) {
                printBytesBeforeAndAfterIp(log, context.getInstructionPointer(), invocationCount);
            } else if (invocationCount == 3) {
                printWord(log, context.getInstructionPointer());
            }
        }

        private static void printBytesBeforeAndAfterIp(Log log, CodePointer ip, int invocationCount) {
            // print 64 or 32 instruction bytes.
            int bytesToPrint = 64 >> invocationCount;
            hexDump(log, ip, bytesToPrint, bytesToPrint);
        }

        private static void printWord(Log log, CodePointer ip) {
            // just print one word starting at the ip
            hexDump(log, ip, 0, ConfigurationValues.getTarget().wordSize);
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            Pointer sp = context.getStackPointer();
            log.string("Top of stack (sp=").zhex(sp).string("):").indent(true);

            UnsignedWord stackBase = VMThreads.StackBase.get();
            if (stackBase.equal(0)) {
                /*
                 * We have to be careful here and not dump too much of the stack: if there are not
                 * many frames on the stack, we segfault when going past the beginning of the stack.
                 */
                log.hexdump(sp, 8, 16);
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            // We already dump all safe values first, so there is nothing we could retry if an error
            // occurs.
            Pointer sp = context.getStackPointer();
            CodePointer ip = context.getInstructionPointer();

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
            return 3;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 3;
            boolean allowUnsafeOperations = DiagnosticLevel.unsafeOperationsAllowed(maxDiagnosticLevel) && invocationCount < 2;
            if (allowUnsafeOperations || VMOperation.isInProgressAtSafepoint()) {
                // If we are not at a safepoint, then it is unsafe to access thread locals of
                // another thread as the IsolateThread could be freed at any time.
                log.string("Threads:").indent(true);
                for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                    log.zhex(thread).spaces(1).string(VMThreads.StatusSupport.getStatusString(thread));
                    if (allowJavaHeapAccess) {
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
    }

    private static class DumpCurrentThreadLocals extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            IsolateThread currentThread = CurrentIsolate.getCurrentThread();
            if (isThreadOnlyAttachedForCrashHandler(currentThread)) {
                if (invocationCount == 1) {
                    log.string("The failing thread ").zhex(currentThread).string(" does not have a full set of VM thread locals as it is an unattached thread.").newline();
                    log.newline();
                }
            } else {
                log.string("VM thread locals for the failing thread ").zhex(currentThread).string(":").indent(true);
                boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 2;
                VMThreadLocalInfos.dumpToLog(log, currentThread, allowJavaHeapAccess);
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 2;
            VMOperationControl.printCurrentVMOperation(log, allowJavaHeapAccess);
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 2;
            VMOperationControl.printRecentEvents(log, allowJavaHeapAccess);
        }
    }

    private static class DumpCodeCacheHistory extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            if (DeoptimizationSupport.enabled()) {
                boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 2;
                RuntimeCodeInfoHistory.singleton().printRecentOperations(log, allowJavaHeapAccess);
            }
        }
    }

    private static class DumpRuntimeCodeInfoMemory extends DiagnosticThunk {
        @Override
        public int maxInvocations() {
            return 3;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            if (DeoptimizationSupport.enabled()) {
                boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 3;
                boolean allowUnsafeOperations = DiagnosticLevel.unsafeOperationsAllowed(maxDiagnosticLevel) && invocationCount < 2;
                RuntimeCodeInfoMemory.singleton().printTable(log, allowJavaHeapAccess, allowUnsafeOperations);
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            Pointer sp = context.getStackPointer();
            CodePointer ip = context.getInstructionPointer();
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
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
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

    public static class DiagnosticLevel {
        // Individual bits.
        private static final int JAVA_HEAP_ACCESS = 0b001;
        private static final int UNSAFE_ACCESS = 0b010;

        // Predefined groups.
        static final int DISABLED = 1 << 31;
        private static final int MINIMAL = 0;
        private static final int SAFE = JAVA_HEAP_ACCESS;
        private static final int FULL = JAVA_HEAP_ACCESS | UNSAFE_ACCESS;

        public static boolean javaHeapAccessAllowed(int level) {
            return level >= 1;
        }

        public static boolean unsafeOperationsAllowed(int level) {
            return level >= 2;
        }

        public static int fromDiagnosticDetails(String details) {
            switch (details) {
                case "0":
                    return DISABLED;
                case "1":
                    return MINIMAL;
                case "2":
                    return SAFE;
                case "3":
                    return FULL;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    @RawStructure
    public interface ErrorContext extends PointerBase {
        @RawField
        Pointer getStackPointer();

        @RawField
        void setStackPointer(Pointer value);

        @RawField
        CodePointer getInstructionPointer();

        @RawField
        void setInstructionPointer(CodePointer value);

        @RawField
        RegisterDumper.Context getRegisterContext();

        @RawField
        void setRegisterContext(RegisterDumper.Context value);

        @RawField
        boolean frameHasCalleeSavedRegisters();

        @RawField
        void setFrameHasCalleeSavedRegisters(boolean value);
    }

    /**
     * Subclasses of this class can implement printing of custom diagnostic information. All
     * instances (of the subclasses) live in the image heap and may be used by multiple threads
     * concurrently.
     */
    public abstract static class DiagnosticThunk {
        /**
         * Prints diagnostic information. This method may be invoked multiple times if an error
         * (e.g., exception or segfault) occurred during execution. However, the method will only be
         * invoked at most {@link #maxInvocations()} times. When the method is invoked for the first
         * time, the argument invocationCount is 1.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
        public abstract void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount);

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public abstract int maxInvocations();
    }

    public static class DiagnosticThunkRegister {
        private DiagnosticThunk[] diagnosticThunks;
        private int[] defaultDiagnosticLevel;

        /**
         * Get the register.
         * <p>
         * This method is @Fold so anyone who uses it ensures there is a register.
         */
        @Fold
        /* Checkstyle: allow synchronization. */
        public static synchronized DiagnosticThunkRegister getSingleton() {
            if (!ImageSingletons.contains(DiagnosticThunkRegister.class)) {
                ImageSingletons.add(DiagnosticThunkRegister.class, new DiagnosticThunkRegister());
            }
            return ImageSingletons.lookup(DiagnosticThunkRegister.class);
        }
        /* Checkstyle: disallow synchronization. */

        @Platforms(Platform.HOSTED_ONLY.class)
        DiagnosticThunkRegister() {
            this.diagnosticThunks = new DiagnosticThunk[]{new DumpRegisters(), new DumpInstructions(), new DumpTopOfCurrentThreadStack(), new DumpDeoptStubPointer(), new DumpTopFrame(),
                            new DumpThreads(), new DumpCurrentThreadLocals(), new DumpCurrentVMOperation(), new DumpVMOperationHistory(), new DumpCodeCacheHistory(),
                            new DumpRuntimeCodeInfoMemory(), new DumpRecentDeoptimizations(), new DumpCounters(), new DumpCurrentThreadFrameAnchors(), new DumpCurrentThreadDecodedStackTrace(),
                            new DumpOtherStackTraces(), new VMLockSupport.DumpVMMutexes()};
            this.defaultDiagnosticLevel = new int[diagnosticThunks.length];
        }

        /**
         * Register a diagnostic thunk to be called after a segfault.
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        /* Checkstyle: allow synchronization. */
        public synchronized void register(DiagnosticThunk diagnosticThunk) {
            diagnosticThunks = Arrays.copyOf(diagnosticThunks, diagnosticThunks.length + 1);
            diagnosticThunks[diagnosticThunks.length - 1] = diagnosticThunk;

            defaultDiagnosticLevel = Arrays.copyOf(defaultDiagnosticLevel, defaultDiagnosticLevel.length + 1);
            defaultDiagnosticLevel[defaultDiagnosticLevel.length - 1] = DiagnosticLevel.FULL;
        }
        /* Checkstyle: disallow synchronization. */

        @Fold
        int size() {
            return diagnosticThunks.length;
        }

        DiagnosticThunk getThunk(int index) {
            return diagnosticThunks[index];
        }

        int getDefaultDiagnosticLevel(int index) {
            return defaultDiagnosticLevel[index];
        }

        void setDiagnosticLevel(int index, int value) {
            defaultDiagnosticLevel[index] = value;
        }
    }
}
