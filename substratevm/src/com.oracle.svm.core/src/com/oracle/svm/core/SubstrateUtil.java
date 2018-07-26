/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.Arrays;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.util.Counter;

public class SubstrateUtil {

    /**
     * Field that is true during native image generation, but false at run time.
     */
    public static final boolean HOSTED;

    static {
        /*
         * Static initializer runs on the hosting VM, setting field value to true during native
         * image generation. At run time, the substituted value from below is used, setting the
         * field value to false at run time.
         */
        HOSTED = true;
    }

    public static String getArchitectureName() {
        String arch = System.getProperty("os.arch");
        switch (arch) {
            case "x86_64":
                arch = "amd64";
                break;
            case "sparcv9":
                arch = "sparc";
                break;
        }
        return arch;
    }

    @TargetClass(com.oracle.svm.core.SubstrateUtil.class)
    static final class Target_com_oracle_svm_core_SubstrateUtil {
        @Alias @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true)//
        private static boolean HOSTED = false;
    }

    @TargetClass(java.io.FileOutputStream.class)
    static final class Target_java_io_FileOutputStream {
        @Alias//
        FileDescriptor fd;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static FileDescriptor getFileDescriptor(FileOutputStream out) {
        return KnownIntrinsics.unsafeCast(out, Target_java_io_FileOutputStream.class).fd;
    }

    /**
     * Convert C-style to Java-style command line arguments. The first C-style argument, which is
     * always the executable file name, is ignored.
     *
     * @param argc the number of arguments in the {@code argv} array.
     * @param argv a C {@code char**}.
     *
     * @return the command line argument strings in a Java string array.
     */
    public static String[] getArgs(int argc, CCharPointerPointer argv) {
        String[] args = new String[argc - 1];
        for (int i = 1; i < argc; ++i) {
            args[i - 1] = CTypeConversion.toJavaString(argv.read(i));
        }
        return args;
    }

    // TODO: Should this call the libc strlen function?
    /**
     * Returns the length of a C {@code char*} string.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static UnsignedWord strlen(CCharPointer str) {
        UnsignedWord n = WordFactory.zero();
        while (((Pointer) str).readByte(n) != 0) {
            n = n.add(1);
        }
        return n;
    }

    @TargetClass(className = "java.nio.DirectByteBuffer")
    @SuppressWarnings("unused")
    static final class Target_java_nio_DirectByteBuffer {
        @Alias
        Target_java_nio_DirectByteBuffer(long addr, int cap) {
        }

        @Alias
        public native long address();
    }

    /**
     * Wraps a pointer to C memory into a {@link ByteBuffer}.
     *
     * @param pointer The pointer to C memory.
     * @param size The size of the C memory.
     * @return A new {@link ByteBuffer} wrapping the pointer.
     */
    public static ByteBuffer wrapAsByteBuffer(PointerBase pointer, int size) {
        return KnownIntrinsics.unsafeCast(new Target_java_nio_DirectByteBuffer(pointer.rawValue(), size), ByteBuffer.class).order(ConfigurationValues.getTarget().arch.getByteOrder());
    }

    public static <T extends PointerBase> T getBaseAddress(MappedByteBuffer buffer) {
        return WordFactory.pointer(KnownIntrinsics.unsafeCast(buffer, Target_java_nio_DirectByteBuffer.class).address());
    }

    /**
     * Checks whether assertions are enabled in the VM.
     *
     * @return true if assertions are enabled.
     */
    @SuppressWarnings("all")
    public static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }

    @NodeIntrinsic(BreakpointNode.class)
    public static native void breakpoint(Object arg0);

    /**
     * Fast power of 2 test.
     */
    public static boolean isPowerOf2(long value) {
        return (value & (value - 1)) == 0;
    }

    /** The functional interface for a "thunk". */
    @FunctionalInterface
    public interface Thunk {

        /** The method to be supplied by the implementor. */
        void invoke();
    }

    private static final StackFrameVisitor Stage0Visitor = new ThreadStackPrinter.Stage0StackFrameVisitor();

    private static final StackFrameVisitor Stage1Visitor = new ThreadStackPrinter.Stage1StackFrameVisitor();

    private static volatile boolean diagnosticsInProgress = false;

    /**
     * Prints extensive diagnostic information to the given Log.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    public static void printDiagnostics(Log log, Pointer sp, CodePointer ip) {
        if (diagnosticsInProgress) {
            log.string("Error: printDiagnostics already in progress.").newline();
            BreakpointNode.breakpoint();
            return;
        }
        diagnosticsInProgress = true;
        log.newline();

        try {
            dumpJavaFrameAnchors(log);
        } catch (Exception e) {
            dumpException(log, "dumpJavaFrameAnchors", e);
        }

        try {
            dumpDeoptStubPointer(log);
        } catch (Exception e) {
            dumpException(log, "dumpDeoptStubPointer", e);
        }

        try {
            dumpTopFrame(log, sp, ip);
        } catch (Exception e) {
            dumpException(log, "dumpTopFrame", e);
        }

        try {
            dumpVMThreads(log);
        } catch (Exception e) {
            dumpException(log, "dumpVMThreads", e);
        }

        IsolateThread currentThread = CEntryPointContext.getCurrentIsolateThread();
        try {
            dumpVMThreadState(log, currentThread);
        } catch (Exception e) {
            dumpException(log, "dumpVMThreadState", e);
        }

        try {
            dumpRecentVMOperations(log);
        } catch (Exception e) {
            dumpException(log, "dumpRecentVMOperations", e);
        }

        dumpRuntimeCompilation(log);

        try {
            dumpCounters(log);
        } catch (Exception e) {
            dumpException(log, "dumpCounters", e);
        }

        try {
            dumpStacktraceRaw(log, sp);
        } catch (Exception e) {
            dumpException(log, "dumpStacktraceRaw", e);
        }

        try {
            dumpStacktraceStage0(log, sp, ip);
        } catch (Exception e) {
            dumpException(log, "dumpStacktraceStage0", e);
        }

        try {
            dumpStacktraceStage1(log, sp, ip);
        } catch (Exception e) {
            dumpException(log, "dumpStacktraceStage1", e);
        }

        try {
            dumpStacktrace(log, sp, ip);
        } catch (Exception e) {
            dumpException(log, "dumpStacktrace", e);
        }

        if (VMOperationControl.isFrozen()) {
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread != VMThreads.nullThread(); vmThread = VMThreads.nextThread(vmThread)) {
                if (vmThread == CEntryPointContext.getCurrentIsolateThread()) {
                    continue;
                }
                try {
                    dumpStacktrace(log, vmThread);
                } catch (Exception e) {
                    dumpException(log, "dumpStacktrace", e);
                }
            }
        }

        try {
            DiagnosticThunkRegister.getSingleton().callDiagnosticThunks();
        } catch (Exception e) {
            dumpException(log, "callThunks", e);
        }

        diagnosticsInProgress = false;
    }

    private static void dumpException(Log log, String context, Exception e) {
        log.newline().string("[!!! Exception during ").string(context).string(": ").string(e.getClass().getName()).string("]").newline();
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpJavaFrameAnchors(Log log) {
        log.string("JavaFrameAnchor dump:").newline();
        log.indent(true);
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor();
        if (anchor.isNull()) {
            log.string("No anchors").newline();
        }
        while (anchor.isNonNull()) {
            log.string("Anchor ").zhex(anchor.rawValue()).string(" LastJavaSP ").zhex(anchor.getLastJavaSP().rawValue()).newline();
            anchor = anchor.getPreviousAnchor();
        }
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpDeoptStubPointer(Log log) {
        log.string("DeoptStubPointer address: ").zhex(DeoptimizationSupport.getDeoptStubPointer().rawValue()).newline().newline();
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpTopFrame(Log log, Pointer sp, CodePointer ip) {
        log.string("TopFrame info:").newline();
        log.indent(true);
        if (sp.isNonNull() && ip.isNonNull()) {
            long totalFrameSize;
            DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
            if (deoptFrame != null) {
                log.string("RSP ").zhex(sp.rawValue()).string(" frame was deoptimized:").newline();
                log.string("SourcePC ").zhex(deoptFrame.getSourcePC().rawValue()).newline();
                totalFrameSize = deoptFrame.getSourceTotalFrameSize();
            } else {
                log.string("Lookup TotalFrameSize in CodeInfoTable:").newline();
                totalFrameSize = CodeInfoTable.lookupTotalFrameSize(ip);
            }
            log.string("SourceTotalFrameSize ").signed(totalFrameSize).newline();

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
                    CodePointer lastIp = FrameAccess.readReturnAddress(lastSp);
                    log.string("LastJavaIP ").zhex(lastIp.rawValue()).newline();
                }
            }
        }
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpVMThreads(Log log) {
        log.string("VMThreads info:").newline();
        log.indent(true);
        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread != VMThreads.nullThread(); vmThread = VMThreads.nextThread(vmThread)) {
            log.string("VMThread ").zhex(vmThread.rawValue()).spaces(2).string(VMThreads.StatusSupport.getStatusString(vmThread))
                            .spaces(2).object(JavaThreads.singleton().fromVMThread(vmThread)).newline();
        }
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpVMThreadState(Log log, IsolateThread currentThread) {
        log.string("VM Thread State for current thread ").zhex(currentThread.rawValue()).string(":").newline();
        log.indent(true);
        VMThreadLocalInfos.dumpToLog(log, currentThread);
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpRecentVMOperations(Log log) {
        log.string("VMOperation dump:").newline();
        log.indent(true);
        VMOperationControl.logRecentEvents(log);
        log.indent(false);
    }

    static void dumpRuntimeCompilation(Log log) {
        log.newline().string("RuntimeCodeCache dump:").newline();
        log.indent(true);
        try {
            dumpRecentRuntimeCodeCacheOperations(log);
        } catch (Exception e) {
            dumpException(log, "dumpRecentRuntimeCodeCacheOperations", e);
        }
        log.newline();
        try {
            dumpRuntimeCodeCacheTable(log);
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

    @NeverInline("catch implicit exceptions")
    private static void dumpRecentRuntimeCodeCacheOperations(Log log) {
        CodeInfoTable.getRuntimeCodeCache().logRecentOperations(log);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpRuntimeCodeCacheTable(Log log) {
        CodeInfoTable.getRuntimeCodeCache().logTable(log);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpRecentDeopts(Log log) {
        log.string("Deoptimizer dump:").newline();
        log.indent(true);
        Deoptimizer.logRecentDeoptimizationEvents(log);
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpCounters(Log log) {
        log.string("Dump Counters:").newline();
        log.indent(true);
        Counter.logValues();
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpStacktraceRaw(Log log, Pointer sp) {
        log.string("Raw Stacktrace:").newline();
        log.indent(true);
        log.hexdump(sp, 8, 128);
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpStacktraceStage0(Log log, Pointer sp, CodePointer ip) {
        log.string("Stacktrace Stage0:").newline();
        log.indent(true);
        JavaStackWalker.walkCurrentThread(sp, ip, Stage0Visitor);
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpStacktraceStage1(Log log, Pointer sp, CodePointer ip) {
        log.string("Stacktrace Stage1:").newline();
        log.indent(true);
        JavaStackWalker.walkCurrentThread(sp, ip, Stage1Visitor);
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpStacktrace(Log log, Pointer sp, CodePointer ip) {
        log.string("Full Stacktrace:").newline();
        log.indent(true);
        ThreadStackPrinter.printStacktrace(sp, ip);
        log.indent(false);
    }

    @NeverInline("catch implicit exceptions")
    private static void dumpStacktrace(Log log, IsolateThread vmThread) {
        log.string("Full Stacktrace for VMThread ").zhex(vmThread.rawValue()).string(":").newline();
        log.indent(true);
        JavaStackWalker.walkThread(vmThread, ThreadStackPrinter.AllocationFreeStackFrameVisitor);
        log.indent(false);
    }

    /** The functional interface for a "thunk" that does not allocate. */
    @FunctionalInterface
    public interface DiagnosticThunk {

        /** The method to be supplied by the implementor. */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
        void invokeWithoutAllocation();
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
            if (!ImageSingletons.contains(SubstrateUtil.DiagnosticThunkRegister.class)) {
                ImageSingletons.add(SubstrateUtil.DiagnosticThunkRegister.class, new DiagnosticThunkRegister());
            }
            return ImageSingletons.lookup(SubstrateUtil.DiagnosticThunkRegister.class);
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

        /** Call each registered diagnostic thunk. */
        void callDiagnosticThunks() {
            for (int i = 0; i < diagnosticThunkRegistry.length; i += 1) {
                diagnosticThunkRegistry[i].invokeWithoutAllocation();
            }
        }
    }
}
