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
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoAccess.DummyValueInfoAllocator;
import com.oracle.svm.core.code.CodeInfoAccess.FrameInfoState;
import com.oracle.svm.core.code.CodeInfoAccess.SingleShotFrameInfoQueryResultAllocator;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.RuntimeCodeInfoHistory;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.code.UninterruptibleReusableTypeReader;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.hub.DynamicHub;
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
    private static final int MAX_THREADS_TO_PRINT = 100_000;
    private static final int MAX_FRAME_ANCHORS_TO_PRINT_PER_THREAD = 1000;

    private static final FastThreadLocalBytes<CCharPointer> threadOnlyAttachedForCrashHandler = FastThreadLocalFactory.createBytes(() -> 1, "SubstrateDiagnostics.threadOnlyAttachedForCrashHandler");
    private static final ImageCodeLocationInfoPrinter imageCodeLocationInfoPrinter = new ImageCodeLocationInfoPrinter();
    private static final Stage0StackFramePrintVisitor[] printVisitors = new Stage0StackFramePrintVisitor[]{new StackFramePrintVisitor(), new Stage1StackFramePrintVisitor(),
                    new Stage0StackFramePrintVisitor()};

    private static volatile boolean loopOnFatalError;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setOnlyAttachedForCrashHandler(IsolateThread thread) {
        threadOnlyAttachedForCrashHandler.getAddress(thread).write((byte) 1);
    }

    public static boolean isThreadOnlyAttachedForCrashHandler(IsolateThread thread) {
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
                        !imageCodeLocationInfoPrinter.printLocationInfo(log, value) &&
                        !RuntimeCodeInfoMemory.singleton().printLocationInfo(log, value, allowJavaHeapAccess, allowUnsafeOperations) &&
                        !VMThreads.printLocationInfo(log, value, allowUnsafeOperations) &&
                        !Heap.getHeap().printLocationInfo(log, value, allowJavaHeapAccess, allowUnsafeOperations)) {
            log.string("is an unknown value");
        }
    }

    @Uninterruptible(reason = "Called with a raw object pointer.", calleeMustBe = false)
    public static void printObjectInfo(Log log, Pointer ptr) {
        DynamicHub objHub = Heap.getHeap().getObjectHeader().readDynamicHubFromPointer(ptr);
        if (objHub == DynamicHub.fromClass(DynamicHub.class)) {
            // The pointer is already a hub, so print some information about the hub.
            DynamicHub hub = (DynamicHub) ptr.toObject();
            log.string("is the hub of ").string(hub.getName());
        } else {
            // The pointer is an object, so print some information about the object's hub.
            log.string("is an object of type ").string(objHub.getName());
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
     *
     * NOTE: Be aware that this method is not fully thread safe and that it may interfere with the
     * fatal error handling. So, don't use this method in production.
     */
    public static void printInformation(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context registerContext, boolean frameHasCalleeSavedRegisters) {
        // Stack allocate an error context.
        ErrorContext errorContext = UnsafeStackValue.get(ErrorContext.class);
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
            log.resetIndentation().newline();
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

        int printed = 0;
        while (anchor.isNonNull()) {
            if (printed >= MAX_FRAME_ANCHORS_TO_PRINT_PER_THREAD) {
                log.string("... (truncated)").newline();
                break;
            }

            log.string("Anchor ").zhex(anchor).string(" LastJavaSP ").zhex(anchor.getLastJavaSP()).string(" LastJavaIP ").zhex(anchor.getLastJavaIP()).newline();
            anchor = anchor.getPreviousAnchor();
            printed++;
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

            if (sp.isNonNull() && ip.isNonNull()) {
                log.string("Top frame info:").indent(true);
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
                log.indent(false);
            }
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
            /*
             * If we are not at a safepoint, then it is unsafe to access the thread locals of
             * another thread as the IsolateThread could be freed at any time.
             */
            if (allowUnsafeOperations || VMOperation.isInProgressAtSafepoint()) {
                log.string("Threads:").indent(true);

                int printed = 0;
                for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                    if (printed >= MAX_THREADS_TO_PRINT) {
                        log.string("... (truncated)").newline();
                        break;
                    }

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
                    printed++;
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
            boolean success = ThreadStackPrinter.printStacktrace(sp, ip, printVisitors[invocationCount - 1].reset(), log);

            if (!success && DiagnosticLevel.unsafeOperationsAllowed(maxDiagnosticLevel)) {
                /*
                 * If the stack pointer is not sufficiently aligned, then we might be in the middle
                 * of a call (i.e., only the return address and the arguments are on the stack). In
                 * that case, we can read the return address from the top of the stack, align the
                 * stack pointer, and start a stack walk in the caller.
                 */
                int expectedStackAlignment = ConfigurationValues.getTarget().stackAlignment;
                if (sp.unsignedRemainder(expectedStackAlignment).notEqual(0) && sp.unsignedRemainder(ConfigurationValues.getTarget().wordSize).equal(0)) {
                    log.newline();
                    log.string("WARNING: stack pointer is NOT aligned to ").signed(expectedStackAlignment).string(" bytes. Starting a stack walk in the most likely caller instead.").newline();
                    ip = sp.readWord(0);
                    sp = sp.add(ConfigurationValues.getTarget().wordSize);

                    ThreadStackPrinter.printStacktrace(sp, ip, printVisitors[invocationCount - 1].reset(), log);
                }
            }

            log.indent(false);
        }
    }

    private static class DumpOtherStackTraces extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 3;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            if (VMOperation.isInProgressAtSafepoint()) {
                // Iterate all threads without checking if the thread mutex is locked (it should
                // be locked by this thread though because we are at a safepoint).
                int printed = 0;
                for (IsolateThread vmThread = VMThreads.firstThreadUnsafe(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == CurrentIsolate.getCurrentThread()) {
                        continue;
                    }
                    if (printed >= MAX_THREADS_TO_PRINT) {
                        log.string("... (truncated)").newline();
                        break;
                    }

                    try {
                        log.string("Thread ").zhex(vmThread).string(":").indent(true);
                        printFrameAnchors(log, vmThread);
                        printStackTrace(log, vmThread, invocationCount);
                        log.indent(false);
                    } catch (Exception e) {
                        dumpException(log, this, e);
                    }
                    printed++;
                }
            }
        }

        private static void printFrameAnchors(Log log, IsolateThread vmThread) {
            log.string("Frame anchors:").indent(true);
            logFrameAnchors(log, vmThread);
            log.indent(false);
        }

        private static void printStackTrace(Log log, IsolateThread vmThread, int invocationCount) {
            log.string("Stacktrace:").indent(true);
            JavaStackWalker.walkThread(vmThread, printVisitors[invocationCount - 1].reset(), log);
            log.redent(false);
        }
    }

    private static class DumpAOTCompiledCodeInfo extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            CodeInfo info = CodeInfoTable.getImageCodeInfo();
            Pointer codeStart = (Pointer) CodeInfoAccess.getCodeStart(info);
            UnsignedWord codeSize = CodeInfoAccess.getCodeSize(info);
            Pointer codeEnd = codeStart.add(codeSize).subtract(1);

            log.string("AOT compiled code is mapped at ").zhex(codeStart).string(" - ").zhex(codeEnd).newline();
            log.newline();
        }
    }

    private static class ImageCodeLocationInfoPrinter {
        private final UninterruptibleReusableTypeReader frameInfoReader = new UninterruptibleReusableTypeReader();
        private final SingleShotFrameInfoQueryResultAllocator singleShotFrameInfoQueryResultAllocator = new SingleShotFrameInfoQueryResultAllocator();
        private final DummyValueInfoAllocator dummyValueInfoAllocator = new DummyValueInfoAllocator();
        private final FrameInfoState frameInfoState = new FrameInfoState();

        /**
         * If {@code value} points into AOT compiled code, then this method prints information about
         * the compilation unit.
         *
         * NOTE: this method may only be called by a single thread.
         */
        public boolean printLocationInfo(Log log, UnsignedWord value) {
            CodeInfo info = CodeInfoTable.getImageCodeInfo();
            if (info.equal(value)) {
                log.string("is the image CodeInfo object");
                return true;
            }

            UnsignedWord codeInfoEnd = ((UnsignedWord) info).add(CodeInfoAccess.getSizeOfCodeInfo());
            if (value.aboveOrEqual((UnsignedWord) info) && value.belowThan(codeInfoEnd)) {
                log.string("points inside the image CodeInfo object ").zhex(info);
                return true;
            }

            if (CodeInfoAccess.contains(info, (CodePointer) value)) {
                log.string("points into AOT compiled code ");

                frameInfoReader.reset();
                frameInfoState.reset();
                CodeInfoAccess.initFrameInfoReader(info, (CodePointer) value, frameInfoReader, frameInfoState);
                if (frameInfoState.entryOffset >= 0) {
                    FrameInfoQueryResult frameInfo;
                    FrameInfoQueryResult rootInfo = null;
                    do {
                        frameInfo = CodeInfoAccess.nextFrameInfo(info, frameInfoReader, singleShotFrameInfoQueryResultAllocator.reload(), dummyValueInfoAllocator, frameInfoState);
                        if (frameInfo != null) {
                            rootInfo = frameInfo;
                        }
                    } while (frameInfo != null);

                    rootInfo.log(log);
                }
                return true;
            }

            return false;
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
     * that live in the image heap.
     */
    public abstract static class DiagnosticThunk {
        /**
         * Prints diagnostic information. This method may be invoked by multiple threads
         * concurrently. When printing information, be careful that the output is limited to a sane
         * amount, even if data structures are corrupt (e.g., a cycle in a linked list).
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
                            new DumpOtherStackTraces(), new VMLockSupport.DumpVMMutexes(), new DumpAOTCompiledCodeInfo()};

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
