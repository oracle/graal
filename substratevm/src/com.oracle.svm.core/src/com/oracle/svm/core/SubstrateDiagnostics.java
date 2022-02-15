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

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RelevantForCompilationIsolates;

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.nodes.PauseNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.word.Word;
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
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.stack.ThreadStackPrinter.StackFramePrintVisitor;
import com.oracle.svm.core.stack.ThreadStackPrinter.Stage0StackFramePrintVisitor;
import com.oracle.svm.core.stack.ThreadStackPrinter.Stage1StackFramePrintVisitor;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.util.Counter;

public class SubstrateDiagnostics {
    private static final FastThreadLocalBytes<CCharPointer> threadOnlyAttachedForCrashHandler = FastThreadLocalFactory.createBytes(() -> 1, "SubstrateDiagnostics.threadOnlyAttachedForCrashHandler");
    private static volatile boolean loopOnFatalError;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setOnlyAttachedForCrashHandler(IsolateThread thread) {
        threadOnlyAttachedForCrashHandler.getAddress(thread).write((byte) 1);
    }

    private static boolean isThreadOnlyAttachedForCrashHandler(IsolateThread thread) {
        return threadOnlyAttachedForCrashHandler.getAddress(thread).read() != 0;
    }

    @Fold
    static FatalErrorState fatalErrorState() {
        return ImageSingletons.lookup(FatalErrorState.class);
    }

    public static boolean isFatalErrorHandlingInProgress() {
        return fatalErrorState().diagnosticThread.get().isNonNull();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isFatalErrorHandlingThread() {
        return fatalErrorState().diagnosticThread.get() == CurrentIsolate.getCurrentThread();
    }

    public static int maxInvocations() {
        int result = 0;
        DiagnosticThunkRegistry thunks = DiagnosticThunkRegistry.singleton();
        for (int i = 0; i < thunks.size(); i++) {
            result += thunks.getThunk(i).maxInvocationCount();
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
     * Prints less detailed information than {@link #printFatalError} but this method guarantees
     * that it won't cause a crash if all parts of Native Image are fully functional.
     */
    public static void printInformation(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context registerContext, boolean frameHasCalleeSavedRegisters) {
        // Stack allocate an error context.
        ErrorContext errorContext = StackValue.get(ErrorContext.class);
        errorContext.setStackPointer(sp);
        errorContext.setInstructionPointer(ip);
        errorContext.setRegisterContext(registerContext);
        errorContext.setFrameHasCalleeSavedRegisters(frameHasCalleeSavedRegisters);

        // Print all thunks in a reasonably safe way.
        int numDiagnosticThunks = DiagnosticThunkRegistry.singleton().size();
        for (int i = 0; i < numDiagnosticThunks; i++) {
            DiagnosticThunk thunk = DiagnosticThunkRegistry.singleton().getThunk(i);
            int invocationCount = DiagnosticThunkRegistry.singleton().getInitialInvocationCount(i);
            if (invocationCount <= thunk.maxInvocationCount()) {
                thunk.printDiagnostics(log, errorContext, DiagnosticLevel.SAFE, invocationCount);
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
     * Used to print extensive diagnostic information in case of a fatal error. This method may use
     * operations and memory accesses that are not necessarily 100% safe. So, even if all parts of
     * Native Image are fully functional, this method may cause crashes.
     * <p>
     * If a segfault handler is present, then this method may be called recursively multiple times
     * if further errors happen while printing diagnostics. On each recursive invocation, the level
     * of detail of the diagnostic output will be reduced gradually.
     * <p>
     * In scenarios without a segfault handler, it can happen that this method reliably causes a
     * subsequent error that crashes Native Image. In such a case, try to reduce the level of detail
     * of the diagnostic output (see {@link SubstrateOptions#DiagnosticDetails}) to get a reasonably
     * complete diagnostic output.
     */
    public static boolean printFatalError(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context registerContext, boolean frameHasCalleeSavedRegisters) {
        log.newline();
        /*
         * Save the state of the initial error so that this state is consistently used, even if
         * further errors occur while printing diagnostics.
         */
        if (!fatalErrorState().trySet(log, sp, ip, registerContext, frameHasCalleeSavedRegisters) && !isFatalErrorHandlingThread()) {
            log.string("Error: printFatalError already in progress by another thread.").newline();
            log.newline();
            return false;
        }

        /*
         * Execute an endless loop if requested. This makes it easier to attach a debugger lazily.
         * In the debugger, it is possible to change the value of loopOnFatalError to false if
         * necessary.
         */
        while (loopOnFatalError) {
            PauseNode.pause();
        }

        printFatalErrorForCurrentState();
        return true;
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "This method is single threaded. The fields 'diagnosticThunkIndex' and 'invocationCount' are only volatile to ensure that the updated field values are written right away.")
    private static void printFatalErrorForCurrentState() {
        assert isFatalErrorHandlingThread();

        FatalErrorState fatalErrorState = fatalErrorState();
        Log log = fatalErrorState.log;
        if (fatalErrorState.diagnosticThunkIndex > 0) {
            // An error must have happened earlier as the code for printing diagnostics was invoked
            // recursively.
            log.resetIndentation();
        }

        // Print the various sections of the diagnostics and skip all sections that were already
        // printed earlier.
        ErrorContext errorContext = fatalErrorState.getErrorContext();
        int numDiagnosticThunks = DiagnosticThunkRegistry.singleton().size();
        while (fatalErrorState.diagnosticThunkIndex < numDiagnosticThunks) {
            int index = fatalErrorState.diagnosticThunkIndex;
            DiagnosticThunk thunk = DiagnosticThunkRegistry.singleton().getThunk(index);

            // Start at the configured initial invocation count.
            if (fatalErrorState.invocationCount == 0) {
                fatalErrorState.invocationCount = DiagnosticThunkRegistry.singleton().getInitialInvocationCount(index) - 1;
            }

            while (++fatalErrorState.invocationCount <= thunk.maxInvocationCount()) {
                try {
                    thunk.printDiagnostics(log, errorContext, DiagnosticLevel.FULL, fatalErrorState.invocationCount);
                    break;
                } catch (Throwable e) {
                    dumpException(log, thunk, e);
                }
            }

            fatalErrorState.diagnosticThunkIndex++;
            fatalErrorState.invocationCount = 0;
        }

        // Flush the output and reset the state so that another thread can print diagnostics for a
        // fatal error.
        log.flush();
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
            RuntimeCodeInfoMemory.singleton().printTable(log, allowJavaHeapAccess, allowUnsafeOperations);
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

    public static void updateInitialInvocationCounts(String configuration) throws IllegalArgumentException {
        int pos = 0;
        int end;
        while ((end = configuration.indexOf(',', pos)) >= 0) {
            String entry = configuration.substring(pos, end);
            updateInitialInvocationCount(entry);
            pos = end + 1;
        }

        String entry = configuration.substring(pos);
        updateInitialInvocationCount(entry);
    }

    private static void updateInitialInvocationCount(String entry) throws IllegalArgumentException {
        int pos = entry.indexOf(':');
        if (pos <= 0 || pos == entry.length() - 1) {
            throw new IllegalArgumentException("'" + entry + "' has an invalid format.");
        }

        String pattern = entry.substring(0, pos);
        int initialInvocationCount = parseInvocationCount(entry, pos);

        int matches = 0;
        int numDiagnosticThunks = DiagnosticThunkRegistry.singleton().size();
        for (int i = 0; i < numDiagnosticThunks; i++) {
            DiagnosticThunk thunk = DiagnosticThunkRegistry.singleton().getThunk(i);
            // Checkstyle: allow Class.getSimpleName
            if (matches(thunk.getClass().getSimpleName(), pattern)) {
                // Checkstyle: disallow Class.getSimpleName
                DiagnosticThunkRegistry.singleton().setInitialInvocationCount(i, initialInvocationCount);
                matches++;
            }
        }

        if (matches == 0) {
            throw new IllegalArgumentException("the pattern '" + entry + "' not match any diagnostic thunk.");
        }
    }

    private static int parseInvocationCount(String entry, int pos) {
        int initialInvocationCount = 0;
        try {
            initialInvocationCount = Integer.parseInt(entry.substring(pos + 1));
        } catch (NumberFormatException e) {
            // handled below
        }

        if (initialInvocationCount < 1) {
            throw new IllegalArgumentException("'" + entry + "' does not specify an integer value >= 1.");
        }
        return initialInvocationCount;
    }

    private static boolean matches(String text, String pattern) {
        assert pattern.length() > 0;
        return matches(text, 0, pattern, 0);
    }

    private static boolean matches(String text, int t, String pattern, int p) {
        int textPos = t;
        int patternPos = p;
        while (textPos < text.length()) {
            if (patternPos >= pattern.length()) {
                return false;
            } else if (pattern.charAt(patternPos) == '*') {
                // Wildcard at the end of the pattern matches everything.
                if (patternPos + 1 >= pattern.length()) {
                    return true;
                }

                while (textPos < text.length()) {
                    if (matches(text, textPos, pattern, patternPos + 1)) {
                        return true;
                    }
                    textPos++;
                }
                return false;
            } else if (text.charAt(textPos) == pattern.charAt(patternPos)) {
                textPos++;
                patternPos++;
            } else {
                return false;
            }
        }

        // Filter wildcards at the end of the pattern in case we ran out of text.
        while (patternPos < pattern.length() && pattern.charAt(patternPos) == '*') {
            patternPos++;
        }
        return patternPos == pattern.length();
    }

    public static class FatalErrorState {
        AtomicWord<IsolateThread> diagnosticThread;
        volatile int diagnosticThunkIndex;
        volatile int invocationCount;
        Log log;
        private final byte[] errorContextData;

        @Platforms(Platform.HOSTED_ONLY.class)
        public FatalErrorState() {
            diagnosticThread = new AtomicWord<>();
            diagnosticThunkIndex = 0;
            invocationCount = 0;
            log = null;

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

    private static class DumpCurrentTimestamp extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Current timestamp: ").unsigned(System.currentTimeMillis()).newline().newline();
        }
    }

    private static class DumpRegisters extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 4;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            boolean printLocationInfo = invocationCount < 4;
            boolean allowJavaHeapAccess = DiagnosticLevel.javaHeapAccessAllowed(maxDiagnosticLevel) && invocationCount < 3;
            boolean allowUnsafeOperations = DiagnosticLevel.unsafeOperationsAllowed(maxDiagnosticLevel) && invocationCount < 2;
            if (context.getRegisterContext().isNonNull()) {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            Pointer sp = context.getStackPointer();
            UnsignedWord stackBase = VMThreads.StackBase.get();
            log.string("Top of stack (sp=").zhex(sp).string("):").indent(true);

            int bytesToPrint = computeBytesToPrint(sp, stackBase);
            log.hexdump(sp, 8, bytesToPrint / 8);
            log.indent(false).newline();
        }

        private static int computeBytesToPrint(Pointer sp, UnsignedWord stackBase) {
            if (stackBase.equal(0)) {
                /*
                 * We have to be careful here and not dump too much of the stack: if there are not
                 * many frames on the stack, we segfault when going past the beginning of the stack.
                 */
                return 128;
            }

            int bytesToPrint = 512;
            UnsignedWord availableBytes = stackBase.subtract(sp);
            if (availableBytes.belowThan(bytesToPrint)) {
                bytesToPrint = NumUtil.safeToInt(availableBytes.rawValue());
            }
            return bytesToPrint;
        }
    }

    private static class DumpDeoptStubPointer extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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

                    int safepointBehavior = SafepointBehavior.getSafepointBehaviorVolatile(thread);
                    log.string(" (").string(SafepointBehavior.toString(safepointBehavior)).string(")");

                    if (allowJavaHeapAccess) {
                        Thread threadObj = PlatformThreads.fromVMThread(thread);
                        log.string(" \"").string(threadObj.getName()).string("\" - ").zhex(Word.objectToUntrackedPointer(threadObj));
                        if (threadObj != null && threadObj.isDaemon()) {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Counters:").indent(true);
            Counter.logValues(log);
            log.indent(false);
        }
    }

    private static class DumpCurrentThreadFrameAnchors extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        public int maxInvocationCount() {
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
        private static final int SAFE = JAVA_HEAP_ACCESS;
        private static final int FULL = JAVA_HEAP_ACCESS | UNSAFE_ACCESS;

        public static boolean javaHeapAccessAllowed(int level) {
            return (level & JAVA_HEAP_ACCESS) != 0;
        }

        public static boolean unsafeOperationsAllowed(int level) {
            return (level & UNSAFE_ACCESS) != 0;
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
     * Can be used to implement printing of custom diagnostic information. Instances are singletons
     * that live in the image heap. The same instance can be used by multiple threads concurrently.
     */
    public abstract static class DiagnosticThunk {
        /**
         * Prints diagnostic information. This method may be invoked by multiple threads
         * concurrently.
         *
         * If an error (e.g., exception or segfault) occurs while executing this method, then the
         * same thread may execute this method multiple times sequentially. The parameter
         * {@code invocationCount} is incremented for each sequential invocation. A typical
         * implementation of {@link #printDiagnostics} will reduce the amount of diagnostic output
         * when the {@code invocationCount} increases. This also reduces the risk of errors and
         * makes it more likely that the method finishes executing successfully.
         *
         * @param log the output to which the diagnostics should be printed.
         * @param context contextual data for the error, e.g., register information.
         * @param maxDiagnosticLevel specifies which kind of operations the diagnostic thunk may
         *            perform, see {@link DiagnosticLevel}.
         * @param invocationCount this value is >= 1 and <= {@link #maxInvocationCount()}).
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
        public abstract void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount);

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public abstract int maxInvocationCount();
    }

    public static class DiagnosticThunkRegistry {
        private DiagnosticThunk[] diagnosticThunks;
        private int[] initialInvocationCount;

        @Fold
        public static synchronized DiagnosticThunkRegistry singleton() {
            if (!ImageSingletons.contains(DiagnosticThunkRegistry.class)) {
                ImageSingletons.add(DiagnosticThunkRegistry.class, new DiagnosticThunkRegistry());
            }
            return ImageSingletons.lookup(DiagnosticThunkRegistry.class);
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        DiagnosticThunkRegistry() {
            this.diagnosticThunks = new DiagnosticThunk[]{new DumpCurrentTimestamp(), new DumpRegisters(), new DumpInstructions(), new DumpTopOfCurrentThreadStack(), new DumpDeoptStubPointer(),
                            new DumpTopFrame(), new DumpThreads(), new DumpCurrentThreadLocals(), new DumpCurrentVMOperation(), new DumpVMOperationHistory(), new DumpCodeCacheHistory(),
                            new DumpRuntimeCodeInfoMemory(), new DumpRecentDeoptimizations(), new DumpCounters(), new DumpCurrentThreadFrameAnchors(), new DumpCurrentThreadDecodedStackTrace(),
                            new DumpOtherStackTraces(), new VMLockSupport.DumpVMMutexes()};

            this.initialInvocationCount = new int[diagnosticThunks.length];
            Arrays.fill(initialInvocationCount, 1);
        }

        /**
         * Register a diagnostic thunk to be called after a segfault.
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        public synchronized void register(DiagnosticThunk diagnosticThunk) {
            diagnosticThunks = Arrays.copyOf(diagnosticThunks, diagnosticThunks.length + 1);
            diagnosticThunks[diagnosticThunks.length - 1] = diagnosticThunk;

            initialInvocationCount = Arrays.copyOf(initialInvocationCount, initialInvocationCount.length + 1);
            initialInvocationCount[initialInvocationCount.length - 1] = 1;
        }

        @Fold
        int size() {
            return diagnosticThunks.length;
        }

        DiagnosticThunk getThunk(int index) {
            return diagnosticThunks[index];
        }

        int getInitialInvocationCount(int index) {
            return initialInvocationCount[index];
        }

        void setInitialInvocationCount(int index, int value) {
            initialInvocationCount[index] = value;
        }
    }

    public static class Options {
        @Option(help = "Execute an endless loop before printing diagnostics for a fatal error.", type = OptionType.Debug)//
        public static final RuntimeOptionKey<Boolean> LoopOnFatalError = new RuntimeOptionKey<>(false, RelevantForCompilationIsolates) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                SubstrateDiagnostics.loopOnFatalError = newValue;
            }
        };
    }
}
