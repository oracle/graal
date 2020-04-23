/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

// Checkstyle: allow reflection

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.java.LambdaUtils;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.util.GuardedAnnotationAccess;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.stack.ThreadStackPrinter.StackFramePrintVisitor;
import com.oracle.svm.core.stack.ThreadStackPrinter.Stage0StackFramePrintVisitor;
import com.oracle.svm.core.stack.ThreadStackPrinter.Stage1StackFramePrintVisitor;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfos;
import com.oracle.svm.core.util.Counter;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

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
            case "arm64":
                arch = "aarch64";
                break;
            case "sparcv9":
                arch = "sparc";
                break;
        }
        return arch;
    }

    /**
     * @return true if the standalone libgraal is being built instead of a normal SVM image.
     */
    public static boolean isBuildingLibgraal() {
        return Services.IS_BUILDING_NATIVE_IMAGE;
    }

    /**
     * @return true if running in the standalone libgraal image.
     */
    public static boolean isInLibgraal() {
        return Services.IS_IN_NATIVE_IMAGE;
    }

    /**
     * Pattern for a single shell command argument that does not need to be quoted.
     */
    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-+=:,./]+");

    /**
     * Reliably quote a string as a single shell command argument.
     */
    public static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "''";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    public static String getShellCommandString(List<String> cmd, boolean multiLine) {
        StringBuilder sb = new StringBuilder();
        for (String arg : cmd) {
            sb.append(quoteShellArg(arg));
            if (multiLine) {
                sb.append(" \\\n");
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static FileDescriptor getFileDescriptor(FileOutputStream out) {
        return SubstrateUtil.cast(out, Target_java_io_FileOutputStream.class).fd;
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

    /**
     * Returns the length of a C {@code char*} string.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord strlen(CCharPointer str) {
        UnsignedWord n = WordFactory.zero();
        while (((Pointer) str).readByte(n) != 0) {
            n = n.add(1);
        }
        return n;
    }

    /**
     * Returns a pointer to the matched character or NULL if the character is not found.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CCharPointer strchr(CCharPointer str, int c) {
        int index = 0;
        while (true) {
            byte b = str.read(index);
            if (b == c) {
                return str.addressOf(index);
            }
            if (b == 0) {
                return WordFactory.zero();
            }
            index += 1;
        }
    }

    /**
     * The same as {@link Class#cast}. This method is available for use in places where either the
     * Java compiler or static analysis tools would complain about a cast because the cast appears
     * to violate the Java type system rules.
     *
     * The most prominent example are casts between a {@link TargetClass} and the original class,
     * i.e., two classes that appear to be unrelated from the Java type system point of view, but
     * are actually the same class.
     */
    @SuppressWarnings({"unused", "unchecked"})
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T> T cast(Object obj, Class<T> toType) {
        return (T) obj;
    }

    /**
     * Checks whether assertions are enabled in the VM.
     *
     * @return true if assertions are enabled.
     */
    @SuppressWarnings("all")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }

    @NodeIntrinsic(BreakpointNode.class)
    public static native void breakpoint(Object arg0);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isPowerOf2(long value) {
        return (value & (value - 1)) == 0;
    }

    /** The functional interface for a "thunk". */
    @FunctionalInterface
    public interface Thunk {

        /** The method to be supplied by the implementor. */
        void invoke();
    }

    private static volatile boolean diagnosticsInProgress = false;

    public static boolean isPrintDiagnosticsInProgress() {
        return diagnosticsInProgress;
    }

    /** Prints extensive diagnostic information to the given Log. */
    public static void printDiagnostics(Log log, Pointer sp, CodePointer ip) {
        printDiagnostics(log, sp, ip, WordFactory.nullPointer());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate during printing diagnostics.")
    static void printDiagnostics(Log log, Pointer sp, CodePointer ip, RegisterDumper.Context context) {
        log.newline();
        if (diagnosticsInProgress) {
            log.string("Error: printDiagnostics already in progress.").newline();
            log.newline();
            return;
        }
        diagnosticsInProgress = true;

        try {
            dumpRegisters(log, context);
        } catch (Exception e) {
            dumpException(log, "dumpRegisters", e);
        }

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

        IsolateThread currentThread = CurrentIsolate.getCurrentThread();
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

        dumpStacktrace(log, sp, ip);

        if (VMOperationControl.isFrozen()) {
            /* Only used for diagnostics - iterate all threads without locking the threads mutex. */
            for (IsolateThread vmThread = VMThreads.firstThreadUnsafe(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                if (vmThread == CurrentIsolate.getCurrentThread()) {
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
            DiagnosticThunkRegister.getSingleton().callDiagnosticThunks(log);
        } catch (Exception e) {
            dumpException(log, "callThunks", e);
        }

        diagnosticsInProgress = false;
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

    private static void dumpVMThreads(Log log) {
        log.string("VMThreads info:").newline();
        log.indent(true);
        /* Only used for diagnostics - iterate all threads without locking the threads mutex. */
        for (IsolateThread vmThread = VMThreads.firstThreadUnsafe(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            log.string("VMThread ").zhex(vmThread.rawValue()).spaces(2).string(VMThreads.StatusSupport.getStatusString(vmThread))
                            .spaces(2).object(JavaThreads.fromVMThread(vmThread)).newline();
        }
        log.indent(false);
    }

    private static void dumpVMThreadState(Log log, IsolateThread currentThread) {
        log.string("VM Thread State for current thread ").zhex(currentThread.rawValue()).string(":").newline();
        log.indent(true);
        VMThreadLocalInfos.dumpToLog(log, currentThread);
        log.indent(false);
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
    }

    private static void dumpRecentRuntimeCodeCacheOperations(Log log) {
        CodeInfoTable.getRuntimeCodeCache().logRecentOperations(log);
    }

    private static void dumpRuntimeCodeCacheTable(Log log) {
        CodeInfoTable.getRuntimeCodeCache().logTable(log);
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

    private static final Stage0StackFramePrintVisitor[] PRINT_VISITORS = new Stage0StackFramePrintVisitor[]{Stage0StackFramePrintVisitor.SINGLETON, Stage1StackFramePrintVisitor.SINGLETON,
                    StackFramePrintVisitor.SINGLETON};

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
        void callDiagnosticThunks(Log log) {
            for (int i = 0; i < diagnosticThunkRegistry.length; i += 1) {
                diagnosticThunkRegistry[i].invokeWithoutAllocation(log);
            }
        }
    }

    /**
     * Similar to {@link String#split} but with a fixed separator string instead of a regular
     * expression. This avoids making regular expression code reachable.
     */
    public static String[] split(String value, String separator) {
        int offset = 0;
        int next = 0;
        ArrayList<String> list = null;
        while ((next = value.indexOf(separator, offset)) != -1) {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(value.substring(offset, next));
            offset = next + separator.length();
        }

        if (offset == 0) {
            /* No match found. */
            return new String[]{value};
        }

        /* Add remaining segment. */
        list.add(value.substring(offset, value.length()));

        return list.toArray(new String[list.size()]);
    }

    public static String toHex(byte[] data) {
        return LambdaUtils.toHex(data);
    }

    public static String digest(String value) {
        return LambdaUtils.digest(value);
    }

    /**
     * Returns a short, reasonably descriptive, but still unique name for the provided method. The
     * name includes a digest of the fully qualified method name, which ensures uniqueness.
     */
    public static String uniqueShortName(ResolvedJavaMethod m) {
        StringBuilder fullName = new StringBuilder();
        fullName.append(m.getDeclaringClass().toClassName()).append(".").append(m.getName()).append("(");
        for (int i = 0; i < m.getSignature().getParameterCount(false); i++) {
            fullName.append(m.getSignature().getParameterType(i, null).toClassName()).append(",");
        }
        fullName.append(')');
        if (!m.isConstructor()) {
            fullName.append(m.getSignature().getReturnType(null).toClassName());
        }

        return stripPackage(m.getDeclaringClass().toJavaName()) + "_" +
                        (m.isConstructor() ? "constructor" : m.getName()) + "_" +
                        SubstrateUtil.digest(fullName.toString());
    }

    /**
     * Returns a short, reasonably descriptive, but still unique name for the provided
     * {@link Method}, {@link Constructor}, or {@link Field}. The name includes a digest of the
     * fully qualified method name, which ensures uniqueness.
     */
    public static String uniqueShortName(Member m) {
        StringBuilder fullName = new StringBuilder();
        fullName.append(m.getDeclaringClass().getName()).append(".");
        if (m instanceof Constructor) {
            fullName.append("<init>");
        } else {
            fullName.append(m.getName());
        }
        if (m instanceof Executable) {
            fullName.append("(");
            for (Class<?> c : ((Executable) m).getParameterTypes()) {
                fullName.append(c.getName()).append(",");
            }
            fullName.append(')');
            if (m instanceof Method) {
                fullName.append(((Method) m).getReturnType().getName());
            }
        }

        return stripPackage(m.getDeclaringClass().getTypeName()) + "_" +
                        (m instanceof Constructor ? "constructor" : m.getName()) + "_" +
                        SubstrateUtil.digest(fullName.toString());
    }

    private static String stripPackage(String qualifiedClassName) {
        return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1);
    }

    /**
     * Mangle the given method name according to our image's (default) mangling convention. A rough
     * requirement is that symbol names are valid symbol name tokens for the assembler. (This is
     * necessary to use them in linker command lines, which we currently do in
     * NativeImageGenerator.) These are of the form '[a-zA-Z\._\$][a-zA-Z0-9\$_]*'. We use the
     * underscore sign as an escape character. It is always followed by four hex digits representing
     * the escaped character in natural (big-endian) order. We do not allow the dollar sign, even
     * though it is legal, because it has special meaning in some shells and disturbs command lines.
     *
     * @param methodName a string to mangle
     * @return a mangled version of methodName
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static String mangleName(String methodName) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < methodName.length(); ++i) {
            char c = methodName.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (i == 0 && c == '.') || (i > 0 && c >= '0' && c <= '9')) {
                // it's legal in this position
                out.append(c);
            } else if (c == '_') {
                out.append("__");
            } else {
                out.append('_');
                // Checkstyle: stop
                out.append(String.format("%04x", (int) c));
                // Checkstyle: resume
            }
        }
        String mangled = out.toString();
        assert mangled.matches("[a-zA-Z\\._][a-zA-Z0-9_]*");
        //@formatter:off
        /*
         * To demangle, the following pipeline works for me (assuming no multi-byte characters):
         *
         * sed -r 's/\_([0-9a-f]{4})/\n\1\n/g' | sed -r 's#^[0-9a-f]{2}([0-9a-f]{2})#/usr/bin/printf "\\x\1"#e' | tr -d '\n'
         *
         * It's not strictly correct if the first characters after an escape sequence
         * happen to match ^[0-9a-f]{2}, but hey....
         */
        //@formatter:on
        return mangled;
    }

    /*
     * This function loads JavaFunction through MethodFilter and this is not allowed in NativeImage.
     * We put this functionality in a separate class.
     */
    public static class NativeImageLoadingShield {
        @Platforms(Platform.HOSTED_ONLY.class)
        public static boolean isNeverInline(ResolvedJavaMethod method) {
            String[] neverInline = SubstrateOptions.NeverInline.getValue();

            return GuardedAnnotationAccess.isAnnotationPresent(method, com.oracle.svm.core.annotate.NeverInline.class) ||
                            (neverInline != null && Arrays.stream(neverInline).anyMatch(re -> MethodFilter.parse(re).matches(method)));
        }
    }
}
