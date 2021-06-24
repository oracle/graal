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
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicWord;
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

import jdk.vm.ci.code.CodeUtil;

public class SubstrateDiagnostics {
    private static final int REGISTERS = 1;
    private static final int FRAME_ANCHORS = REGISTERS << 1;
    private static final int DEOPT_STUB_POINTERS = FRAME_ANCHORS << 1;
    private static final int TOP_FRAME = DEOPT_STUB_POINTERS << 1;
    private static final int THREADS_FULL = TOP_FRAME << 1;
    private static final int THREADS_REDUCED = TOP_FRAME << 1;
    private static final int THREAD_STATES = THREADS_REDUCED << 1;
    private static final int VM_OPERATIONS = THREAD_STATES << 1;
    private static final int RUNTIME_COMPILATIONS = VM_OPERATIONS << 1;
    private static final int COUNTERS = RUNTIME_COMPILATIONS << 1;
    private static final int CURRENT_THREAD_RAW_STACKTRACE = COUNTERS << 1;
    private static final int CURRENT_THREAD_DECODED_STACKTRACE = CURRENT_THREAD_RAW_STACKTRACE << 1;
    private static final int OTHER_STACK_TRACES = CURRENT_THREAD_DECODED_STACKTRACE << 1;
    private static final int INSTRUCTIONS = OTHER_STACK_TRACES << 1;
    private static final int NUM_NAMED_SECTIONS = CodeUtil.log2(INSTRUCTIONS << 1);

    private static final Stage0StackFramePrintVisitor[] PRINT_VISITORS = new Stage0StackFramePrintVisitor[]{Stage0StackFramePrintVisitor.SINGLETON, Stage1StackFramePrintVisitor.SINGLETON,
                    StackFramePrintVisitor.SINGLETON};

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

    public static boolean isInProgressByCurrentThread() {
        return state.diagnosticThread.get() == CurrentIsolate.getCurrentThread();
    }

    /**
     * The segfault handler will invoke {@link #print} recursively if a fatal error happens while
     * printing diagnostics. The value returned by this method can be used to limit the maximum
     * recursion depth if necessary.
     */
    public static int maxRetries() {
        return NUM_NAMED_SECTIONS + DiagnosticThunkRegister.getSingleton().size();
    }

    /** Prints extensive diagnostic information to the given Log. */
    public static void print(Log log, Pointer sp, CodePointer ip) {
        print(log, sp, ip, WordFactory.nullPointer());
    }

    /**
     * Print diagnostics for the various subsystems. If a fatal error occurs while printing
     * diagnostics, it can happen that the same thread enters this method multiple times.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    static void print(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context context) {
        log.newline();
        // Save the state of the initial error so that this state is consistently used, even if
        // further errors occur while printing diagnostics.
        if (!state.trySet(log, sp, ip, context) && !isInProgressByCurrentThread()) {
            log.string("Error: printDiagnostics already in progress by another thread.").newline();
            log.newline();
            return;
        }

        printDiagnosticsForCurrentState();
    }

    private static void printDiagnosticsForCurrentState() {
        assert isInProgressByCurrentThread();

        Log log = state.log;
        Pointer sp = state.sp;
        CodePointer ip = state.ip;
        IsolateThread currentThread = CurrentIsolate.getCurrentThread();

        if (state.diagnosticSections > 0) {
            log.newline();
            log.string("An error occurred while printing diagnostics. The remaining part of this section will be skipped.").newline();
            log.resetIndentation();
        }

        // Print the various sections of the diagnostics and skip all sections that were already
        // printed earlier.
        if (shouldPrint(REGISTERS)) {
            try {
                dumpRegisters(log, state.context);
            } catch (Exception e) {
                dumpException(log, "dumpRegisters", e);
            }
        }

        if (shouldPrint(FRAME_ANCHORS)) {
            try {
                dumpJavaFrameAnchors(log);
            } catch (Exception e) {
                dumpException(log, "dumpJavaFrameAnchors", e);
            }
        }

        if (shouldPrint(DEOPT_STUB_POINTERS)) {
            try {
                dumpDeoptStubPointer(log);
            } catch (Exception e) {
                dumpException(log, "dumpDeoptStubPointer", e);
            }
        }

        if (shouldPrint(TOP_FRAME)) {
            try {
                dumpTopFrame(log, sp, ip);
            } catch (Exception e) {
                dumpException(log, "dumpTopFrame", e);
            }
        }

        if (shouldPrint(THREADS_FULL)) {
            try {
                dumpVMThreads(log, true);
                skip(THREADS_REDUCED);
            } catch (Exception e) {
                dumpException(log, "dumpVMThreads", e);
            }
        }

        if (shouldPrint(THREADS_REDUCED)) {
            try {
                dumpVMThreads(log, false);
            } catch (Exception e) {
                dumpException(log, "dumpVMThreads", e);
            }
        }

        if (shouldPrint(THREAD_STATES)) {
            try {
                dumpVMThreadState(log, currentThread);
            } catch (Exception e) {
                dumpException(log, "dumpVMThreadState", e);
            }
        }

        if (shouldPrint(VM_OPERATIONS)) {
            try {
                dumpRecentVMOperations(log);
            } catch (Exception e) {
                dumpException(log, "dumpRecentVMOperations", e);
            }
        }

        if (shouldPrint(RUNTIME_COMPILATIONS)) {
            dumpRuntimeCompilation(log);
        }

        if (shouldPrint(COUNTERS)) {
            try {
                dumpCounters(log);
            } catch (Exception e) {
                dumpException(log, "dumpCounters", e);
            }
        }

        if (shouldPrint(CURRENT_THREAD_RAW_STACKTRACE)) {
            try {
                dumpStacktraceRaw(log, sp);
            } catch (Exception e) {
                dumpException(log, "dumpStacktraceRaw", e);
            }
        }

        if (shouldPrint(CURRENT_THREAD_DECODED_STACKTRACE)) {
            dumpStacktrace(log, sp, ip);
        }

        if (shouldPrint(OTHER_STACK_TRACES)) {
            if (VMOperation.isInProgressAtSafepoint()) {
                // Iterate all threads without locking the threads.
                for (IsolateThread vmThread = VMThreads.firstThreadUnsafe(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                    if (vmThread == currentThread) {
                        continue;
                    }
                    try {
                        dumpStacktrace(log, vmThread);
                    } catch (Exception e) {
                        dumpException(log, "dumpStacktrace", e);
                    }
                }
            }
        }

        if (shouldPrint(INSTRUCTIONS)) {
            dumpInstructions(log, ip);
        }

        int numDiagnosticThunks = DiagnosticThunkRegister.getSingleton().size();
        while (state.diagnosticThunkIndex < numDiagnosticThunks) {
            try {
                int index = state.diagnosticThunkIndex++;
                DiagnosticThunkRegister.getSingleton().callDiagnosticThunk(log, index);
            } catch (Exception e) {
                dumpException(log, "callThunks", e);
            }
        }

        state.clear();
    }

    private static boolean shouldPrint(int sectionBit) {
        if ((state.diagnosticSections & sectionBit) == 0) {
            state.diagnosticSections |= sectionBit;
            return true;
        }
        return false;
    }

    private static void skip(int sectionBit) {
        state.diagnosticSections |= sectionBit;
    }

    private static void dumpException(Log log, String context, Exception e) {
        log.newline().string("[!!! Exception during ").string(context).string(": ").string(e.getClass().getName()).string("]").newline();
    }

    private static void dumpRegisters(Log log, RegisterDumper.Context context) {
        if (context.isNonNull()) {
            log.string("General Purpose Register Set values:").newline();
            log.indent(true);
            RegisterDumper.singleton().dumpRegisters(log, context);
            log.indent(false);
        }
    }

    private static void dumpJavaFrameAnchors(Log log) {
        log.string("JavaFrameAnchor dump:").newline();
        log.indent(true);
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor();
        if (anchor.isNull()) {
            log.string("No anchors").newline();
        }
        while (anchor.isNonNull()) {
            log.string("Anchor ").zhex(anchor.rawValue()).string(" LastJavaSP ").zhex(anchor.getLastJavaSP().rawValue()).string(" LastJavaIP ").zhex(anchor.getLastJavaIP().rawValue()).newline();
            anchor = anchor.getPreviousAnchor();
        }
        log.indent(false);
    }

    private static void dumpDeoptStubPointer(Log log) {
        if (DeoptimizationSupport.enabled()) {
            log.string("DeoptStubPointer address: ").zhex(DeoptimizationSupport.getDeoptStubPointer().rawValue()).newline().newline();
        }
    }

    private static void dumpTopFrame(Log log, Pointer sp, CodePointer ip) {
        log.string("TopFrame info:").newline();
        log.indent(true);
        if (sp.isNonNull() && ip.isNonNull()) {
            long totalFrameSize = getTotalFrameSize(sp, ip);
            DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
            if (deoptFrame != null) {
                log.string("RSP ").zhex(sp.rawValue()).string(" frame was deoptimized:").newline();
                log.string("SourcePC ").zhex(deoptFrame.getSourcePC().rawValue()).newline();
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
                    log.string("Found matching Anchor:").zhex(anchor.rawValue()).newline();
                    Pointer lastSp = anchor.getLastJavaSP();
                    log.string("LastJavaSP ").zhex(lastSp.rawValue()).newline();
                    CodePointer lastIp = anchor.getLastJavaIP();
                    log.string("LastJavaIP ").zhex(lastIp.rawValue()).newline();
                }
            }
        }
        log.indent(false);
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

    private static void dumpVMThreads(Log log, String prefix, boolean accessThreadObject) {
        log.string(prefix).string(" VMThreads info:").newline();
        log.indent(true);
        /* Only used for diagnostics - iterate all threads without locking the threads mutex. */
        for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            log.zhex(thread.rawValue()).string(VMThreads.StatusSupport.getStatusString(thread));
            if (accessThreadObject) {
                Thread threadObj = JavaThreads.fromVMThread(thread);
                log.string(" \"").string(threadObj.getName()).string("\" - ").object(threadObj).string(")");
                if (threadObj.isDaemon()) {
                    log.string(" daemon ");
                }
            }
            log.string(", stack(").zhex(VMThreads.StackEnd.get(thread)).string(",").zhex(VMThreads.StackBase.get(thread)).string(")");
            log.newline();
        }
        log.indent(false);
    }

    private static void dumpVMThreadState(Log log, IsolateThread currentThread) {
        if (isThreadOnlyAttachedForCrashHandler(currentThread)) {
            log.string("The current thread ").zhex(currentThread.rawValue()).string(" does not have a VM Thread State as it is an unattached thread.").newline();
            log.newline();
        } else {
            log.string("VM Thread State for current thread ").zhex(currentThread.rawValue()).string(":").newline();
            log.indent(true);
            VMThreadLocalInfos.dumpToLog(log, currentThread);
            log.indent(false);
        }
    }

    private static void dumpRecentVMOperations(Log log) {
        log.string("VMOperation dump:").newline();
        log.indent(true);
        VMOperationControl.logRecentEvents(log);
        log.indent(false);
    }

    static void dumpRuntimeCompilation(Log log) {
        if (DeoptimizationSupport.enabled()) {
            log.newline().string("RuntimeCodeCache dump:").newline();
            log.indent(true);
            try {
                CodeInfoTable.getRuntimeCodeCache().logRecentOperations(log);
            } catch (Exception e) {
                dumpException(log, "dumpRecentRuntimeCodeCacheOperations", e);
            }
            log.newline();
            try {
                CodeInfoTable.getRuntimeCodeCache().logTable(log);
            } catch (Exception e) {
                dumpException(log, "dumpRuntimeCodeCacheTable", e);
            }
            log.indent(false);

            try {
                dumpRecentDeopts(log);
            } catch (Exception e) {
                dumpException(log, "dumpRecentDeopts", e);
            }
        }
    }

    private static void dumpRecentDeopts(Log log) {
        log.string("Deoptimizer dump:").newline();
        log.indent(true);
        Deoptimizer.logRecentDeoptimizationEvents(log);
        log.indent(false);
    }

    private static void dumpCounters(Log log) {
        log.string("Dump Counters:").newline();
        log.indent(true);
        Counter.logValues();
        log.indent(false);
    }

    private static void dumpStacktraceRaw(Log log, Pointer sp) {
        log.string("Raw Stacktrace:").newline();
        log.indent(true);
        /*
         * We have to be careful here and not dump too much of the stack: if there are not many
         * frames on the stack, we segfault when going past the beginning of the stack.
         */
        log.hexdump(sp, 8, 16);
        log.indent(false);
    }

    private static void dumpStacktrace(Log log, Pointer sp, CodePointer ip) {
        for (int i = 0; i < PRINT_VISITORS.length; i++) {
            try {
                log.string("Stacktrace Stage ").signed(i).string(":").newline();
                log.indent(true);
                ThreadStackPrinter.printStacktrace(sp, ip, PRINT_VISITORS[i], log);
                log.indent(false);
            } catch (Exception e) {
                dumpException(log, "dumpStacktrace", e);
            }
        }
    }

    private static void dumpStacktrace(Log log, IsolateThread vmThread) {
        log.string("Full Stacktrace for VMThread ").zhex(vmThread.rawValue()).string(":").newline();
        log.indent(true);
        JavaStackWalker.walkThread(vmThread, StackFramePrintVisitor.SINGLETON, log);
        log.indent(false);
    }

    private static void dumpInstructions(Log log, CodePointer ip) {
        log.string("Instructions (ip=").hex(ip).string(")").newline();
        log.indent(true);
        log.hexdump(((Pointer) ip).subtract(32), 1, 64);
        log.indent(false);
    }

    private static class PrintDiagnosticsState {
        AtomicWord<IsolateThread> diagnosticThread = new AtomicWord<>();
        volatile int diagnosticSections;
        volatile int diagnosticThunkIndex;

        Log log;
        Pointer sp;
        CodePointer ip;
        RegisterDumper.Context context;

        @SuppressWarnings("hiding")
        public boolean trySet(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context context) {
            if (diagnosticThread.compareAndSet(WordFactory.nullPointer(), CurrentIsolate.getCurrentThread())) {
                assert diagnosticSections == 0;
                assert diagnosticThunkIndex == 0;
                this.log = log;
                this.sp = sp;
                this.ip = ip;
                this.context = context;
                return true;
            }
            return false;
        }

        public void clear() {
            log = null;
            sp = WordFactory.nullPointer();
            ip = WordFactory.nullPointer();
            context = WordFactory.nullPointer();

            diagnosticThunkIndex = 0;
            diagnosticSections = 0;

            diagnosticThread.set(WordFactory.nullPointer());
        }
    }

    /** The functional interface for a "thunk" that does not allocate. */
    @FunctionalInterface
    public interface DiagnosticThunk {

        /** The method to be supplied by the implementor. */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
        void invokeWithoutAllocation(Log log);
    }

    public static class DiagnosticThunkRegister {

        DiagnosticThunk[] diagnosticThunkRegistry;

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
            this.diagnosticThunkRegistry = new DiagnosticThunk[0];
        }

        /** Register a diagnostic thunk to be called after a segfault. */
        @Platforms(Platform.HOSTED_ONLY.class)
        /* { Checkstyle: allow synchronization. */
        public synchronized void register(DiagnosticThunk diagnosticThunk) {
            final DiagnosticThunk[] newArray = Arrays.copyOf(diagnosticThunkRegistry, diagnosticThunkRegistry.length + 1);
            newArray[newArray.length - 1] = diagnosticThunk;
            diagnosticThunkRegistry = newArray;
        }
        /* } Checkstyle: disallow synchronization. */

        @Fold
        int size() {
            return diagnosticThunkRegistry.length;
        }

        /** Call each registered diagnostic thunk. */
        void callDiagnosticThunk(Log log, int index) {
            diagnosticThunkRegistry[index].invokeWithoutAllocation(log);
        }
    }
}
