/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class StackTraceUtils {

    private static final Class<?>[] NO_CLASSES = new Class<?>[0];
    private static final StackTraceElement[] NO_ELEMENTS = new StackTraceElement[0];

    /**
     * Captures the stack trace of the current thread. In almost any context, calling
     * {@link JavaThreads#getStackTrace} for {@link Thread#currentThread()} is preferable.
     *
     * Captures at most {@link SubstrateOptions#maxJavaStackTraceDepth()} stack trace elements if
     * max depth > 0, or all if max depth <= 0.
     */
    public static StackTraceElement[] getCurrentThreadStackTrace(boolean filterExceptions, Pointer startSP, Pointer endSP) {
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(filterExceptions, SubstrateOptions.maxJavaStackTraceDepth());
        visitCurrentThreadStackFrames(startSP, endSP, visitor);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static void visitCurrentThreadStackFrames(Pointer startSP, Pointer endSP, StackFrameVisitor visitor) {
        JavaStackWalker.walkCurrentThread(startSP, endSP, visitor);
    }

    /**
     * Captures the stack trace of a thread (potentially the current thread) while stopped at a
     * safepoint. Used by {@link Thread#getStackTrace()} and {@link Thread#getAllStackTraces()}.
     *
     * Captures at most {@link SubstrateOptions#maxJavaStackTraceDepth()} stack trace elements if
     * max depth > 0, or all if max depth <= 0.
     */
    @NeverInline("Potentially starting a stack walk in the caller frame")
    public static StackTraceElement[] getStackTraceAtSafepoint(Thread thread) {
        assert VMOperation.isInProgressAtSafepoint();
        if (thread == null) {
            return NO_ELEMENTS;
        }
        return JavaThreads.getStackTraceAtSafepoint(thread, readCallerStackPointer());
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(IsolateThread isolateThread) {
        return getStackTraceAtSafepoint(isolateThread, Word.nullPointer());
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(IsolateThread isolateThread, Pointer endSP) {
        assert VMOperation.isInProgressAtSafepoint();
        if (isolateThread.isNull()) { // recently launched thread
            return NO_ELEMENTS;
        }
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(false, SubstrateOptions.maxJavaStackTraceDepth());
        JavaStackWalker.walkThread(isolateThread, endSP, visitor, null);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(IsolateThread isolateThread, Pointer startSP, Pointer endSP) {
        assert VMOperation.isInProgressAtSafepoint();
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(false, SubstrateOptions.maxJavaStackTraceDepth());
        JavaStackWalker.walkThread(isolateThread, startSP, endSP, Word.nullPointer(), visitor);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static Class<?>[] getClassContext(int skip, Pointer startSP) {
        GetClassContextVisitor visitor = new GetClassContextVisitor(skip);
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.trace.toArray(NO_CLASSES);
    }

    /**
     * Implements the shared semantic of Reflection.getCallerClass and StackWalker.getCallerClass.
     */
    public static Class<?> getCallerClass(Pointer startSP, boolean showLambdaFrames) {
        return getCallerClass(startSP, showLambdaFrames, 0, true);
    }

    public static Class<?> getCallerClass(Pointer startSP, boolean showLambdaFrames, int depth, boolean ignoreFirst) {
        GetCallerClassVisitor visitor = new GetCallerClassVisitor(showLambdaFrames, depth, ignoreFirst);
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.result;
    }

    /**
     * Indicates whether the frame should be displayed in the context of Java backtracing. Returns
     * true if so, and false otherwise. Backtracing means that there are no lambda or hidden frames
     * present. To learn more about backtracing, refer to {@link BacktraceDecoder}. For more
     * fine-grained control over what is displayed, see
     * {@link #shouldShowFrame(Class, String, boolean, boolean, boolean)}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(Class<?> clazz, String method) {
        return shouldShowFrame(clazz, method, false, true, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(FrameSourceInfo frameSourceInfo) {
        return shouldShowFrame(frameSourceInfo.getSourceClass(), frameSourceInfo.getSourceMethodName());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(FrameSourceInfo frameSourceInfo, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
        return shouldShowFrame(frameSourceInfo.getSourceClass(), frameSourceInfo.getSourceMethodName(), showLambdaFrames, showReflectFrames, showHiddenFrames);
    }

    /*
     * Note that this method is duplicated below to work on compiler metadata. Make sure to always
     * keep both versions in sync, otherwise intrinsifications by the compiler will return different
     * results than stack walking at run time.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(Class<?> clazz, String methodName, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
        if (showHiddenFrames) {
            /* No filtering, all frames including internal frames are shown. */
            return true;
        }

        if (clazz == null) {
            /*
             * We don't have a Java class. This must be an internal frame. This path mostly exists
             * to be defensive, there should actually never be a frame where we do not have a Java
             * class.
             */
            return false;
        }

        if (DynamicHub.fromClass(clazz).isVMInternal()) {
            return false;
        }

        if (!showLambdaFrames && DynamicHub.fromClass(clazz).isLambdaFormHidden()) {
            return false;
        }

        if (!showReflectFrames) {
            if (clazz == java.lang.reflect.Method.class && UninterruptibleUtils.String.equals("invoke", methodName)) {
                /*
                 * Ignore a reflective method invocation frame. Note that the classes cannot be
                 * annotated with @InternalFrame because 1) they are JDK classes and 2) only one
                 * method of each class is affected.
                 */
                return false;
            } else if ((clazz == java.lang.reflect.Constructor.class || clazz == java.lang.Class.class) && UninterruptibleUtils.String.equals("newInstance", methodName)) {
                /* Ignore a constructor invocation frame (see the comment above). */
                return false;
            }
        }

        if (clazz == Target_jdk_internal_vm_Continuation.class && (UninterruptibleUtils.String.startsWith(methodName, "enter") || UninterruptibleUtils.String.startsWith(methodName, "yield"))) {
            return false;
        }

        return true;
    }

    /*
     * Note that this method is duplicated (and commented) above for stack walking at run time. Make
     * sure to always keep both versions in sync.
     */
    public static boolean shouldShowFrame(MetaAccessProvider metaAccess, ResolvedJavaMethod method, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
        if (showHiddenFrames) {
            return true;
        }

        ResolvedJavaType clazz = method.getDeclaringClass();
        if (AnnotationAccess.isAnnotationPresent(clazz, InternalVMMethod.class)) {
            return false;
        }

        if (!showLambdaFrames && AnnotationAccess.isAnnotationPresent(clazz, LambdaFormHiddenMethod.class)) {
            return false;
        }

        if (!showReflectFrames && ((clazz.equals(metaAccess.lookupJavaType(java.lang.reflect.Method.class)) && "invoke".equals(method.getName())) ||
                        (clazz.equals(metaAccess.lookupJavaType(java.lang.reflect.Constructor.class)) && "newInstance".equals(method.getName())) ||
                        (clazz.equals(metaAccess.lookupJavaType(java.lang.Class.class)) && "newInstance".equals(method.getName())))) {
            return false;
        }

        return true;
    }

    public static boolean ignoredBySecurityStackWalk(MetaAccessProvider metaAccess, ResolvedJavaMethod method) {
        return !shouldShowFrame(metaAccess, method, true, false, false);
    }

    public static ClassLoader latestUserDefinedClassLoader(Pointer startSP) {
        GetLatestUserDefinedClassLoaderVisitor visitor = new GetLatestUserDefinedClassLoaderVisitor();
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.result;
    }

    public static StackTraceElement[] asyncGetStackTrace(Thread thread) {
        if (thread == null || !thread.isAlive()) {
            /* Avoid triggering a safepoint operation below if the thread is not even alive. */
            return NO_ELEMENTS;
        }
        GetStackTraceOperation vmOp = new GetStackTraceOperation(thread);
        vmOp.enqueue();
        return vmOp.result;
    }

    private static class GetStackTraceOperation extends JavaVMOperation {
        private final Thread thread;
        StackTraceElement[] result;

        GetStackTraceOperation(Thread thread) {
            super(VMOperationInfos.get(GetStackTraceOperation.class, "Get stack trace", SystemEffect.SAFEPOINT));
            this.thread = thread;
        }

        @Override
        protected void operate() {
            result = getStackTraceAtSafepoint(thread);
        }
    }

}

/**
 * Visits the stack frames and collects a backtrace in an internal format to be stored in
 * {@link Target_java_lang_Throwable#backtrace}.
 *
 * The {@link Target_java_lang_Throwable#backtrace} is a {@code long} array that either stores a
 * native instruction pointer (for AOT compiled methods) or an encoded Java source reference
 * containing a source line number, a source class and a source method name (for JIT compiled
 * methods). A native instruction pointer is always a single {@code long} element, while an encoded
 * Java source reference takes {@linkplain #entriesPerSourceReference() 2 elements} if references
 * are {@link #useCompressedReferences() compressed}, or 3 otherwise. Native instruction pointers
 * and source references can be mixed. The source line number of the source reference is
 * {@linkplain #encodeLineNumber encoded} in a way that it can be distinguished from a native
 * instruction pointer.
 *
 * <h2>Uncompressed References</h2>
 * 
 * <pre>
 *                      backtrace content      |   Number of Java frames
 *                    ---------------------------------------------------
 * backtrace[pos + 0] | native inst. pointer   |   0..n Java frames
 *                    --------------------------
 * backtrace[pos + 1] | encoded src line nr    |   1 Java frame
 * backtrace[pos + 2] | source class ref       |
 * backtrace[pos + 3] | source method name ref |
 *                    --------------------------
 *                    | ... remaining          |
 *                    --------------------------
 *                    | 0                      |   0 terminated if not all elements are used
 * </pre>
 *
 * <h2>Compressed References</h2>
 * 
 * <pre>
 *                      backtrace content                                   |   Number of Java frames
 *                    --------------------------------------------------------------------------------
 * backtrace[pos + 0] | native inst. pointer                                |   0..n Java frames
 *                    -------------------------------------------------------
 * backtrace[pos + 1] | encoded src line nr                                 |   1 Java frame
 * backtrace[pos + 2] | (source class ref) << 32 | (source method name ref) |
 *                    -------------------------------------------------------
 *                    | ... remaining                                       |
 *                    -------------------------------------------------------
 *                    | 0                                                   |   0 terminated if not all elements are used
 * </pre>
 *
 * @see #writeSourceReference writes the source references into the backtrace array
 * @see #visitAOTFrame writes a native instruction pointer into the backtrace array
 * @see BacktraceDecoder decodes the backtrace array
 *
 */
final class BacktraceVisitor extends JavaStackFrameVisitor {

    /**
     * Index into {@link #trace}.
     */
    private int index = 0;

    /**
     * Number of frames stored (native instruction pointers or encoded Java source reference).
     * Because Java frames take up more than one entry in {@link #trace} this number might be
     * different to {@link #index}.
     */
    private int numFrames = 0;
    private final int limit = computeNativeLimit();

    /*
     * Empirical data suggests that most stack traces tend to be relatively short (<100). We choose
     * the initial size so that these cases do not need to reallocate the array.
     */
    private static final int INITIAL_TRACE_SIZE = 80;
    private long[] trace = new long[INITIAL_TRACE_SIZE];

    public static final int NATIVE_FRAME_LIMIT_MARGIN = 10;

    @Fold
    static int entriesPerSourceReference() {
        return useCompressedReferences() ? 2 : 3;
    }

    /**
     * Gets the number of native frames to collect. Native frames and Java frames do not directly
     * relate. We cannot tell how many Java frames a native frame represents. Usually, a single
     * native represents multiple Java frames, but that is not true in general. Frames might be
     * skipped because they represent a {@link Throwable} constructor, or are otherwise special
     * ({@link StackTraceUtils#shouldShowFrame}). To mitigate this, we always decode
     * {@linkplain #NATIVE_FRAME_LIMIT_MARGIN a few more} native frames than the
     * {@linkplain SubstrateOptions#maxJavaStackTraceDepth() Java frame limit} and hope that we can
     * decode enough Java frames later on.
     *
     * @see SubstrateOptions#maxJavaStackTraceDepth()
     */
    private static int computeNativeLimit() {
        int maxJavaStackTraceDepth = SubstrateOptions.maxJavaStackTraceDepth();
        if (maxJavaStackTraceDepth <= 0) {
            /* Unlimited backtrace. */
            return Integer.MAX_VALUE;
        }
        int maxJavaStackTraceDepthExtended = maxJavaStackTraceDepth + NATIVE_FRAME_LIMIT_MARGIN;
        return maxJavaStackTraceDepthExtended > maxJavaStackTraceDepth ? maxJavaStackTraceDepthExtended : Integer.MAX_VALUE;
    }

    @Override
    public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo) {
        if (!InterpreterSupport.isEnabled() && CodeInfoTable.isInAOTImageCode(ip)) {
            visitAOTFrame(ip);
        } else {
            /*
             * GR-46090: better detection of interpreter frames needed. Right now this forces
             * exception handling to always go through the "encoded Java source reference" case as
             * soon the interpreter is enabled.
             */
            CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip);
            assert queryResult != null;

            for (FrameInfoQueryResult frameInfo = queryResult.getFrameInfo(); frameInfo != null; frameInfo = frameInfo.getCaller()) {
                if (!dispatchPossiblyInterpretedFrame(frameInfo, sp)) {
                    return false;
                }
            }
        }
        return numFrames != limit;
    }

    @Override
    protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
        for (DeoptimizedFrame.VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
            FrameInfoQueryResult frameInfo = frame.getFrameInfo();
            if (!dispatchPossiblyInterpretedFrame(frameInfo, originalSP)) {
                return false;
            }
        }
        return numFrames != limit;
    }

    private void visitAOTFrame(CodePointer ip) {
        long rawValue = ip.rawValue();
        VMError.guarantee(rawValue != 0, "Unexpected code pointer: 0");
        if (isSourceReference(rawValue)) {
            throw VMError.shouldNotReachHere("Not a code pointer: 0x" + Long.toHexString(rawValue));
        }
        ensureSize(index + 1);
        trace[index++] = rawValue;
        numFrames++;
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameSourceInfo)) {
            /* Always ignore the frame. It is an internal frame of the VM. */
            return true;

        } else if (index == 0 && Throwable.class.isAssignableFrom(frameSourceInfo.getSourceClass())) {
            /*
             * We are still in the constructor invocation chain at the beginning of the stack trace,
             * which is also filtered by the Java HotSpot VM.
             */
            return true;
        }
        int sourceLineNumber = frameSourceInfo.getSourceLineNumber();
        Class<?> sourceClass = frameSourceInfo.getSourceClass();
        String sourceMethodName = frameSourceInfo.getSourceMethodName();

        if (!(frameSourceInfo instanceof InterpreterFrameSourceInfo)) {
            VMError.guarantee(Heap.getHeap().isInImageHeap(sourceClass), "Source class must be in the image heap");
            VMError.guarantee(Heap.getHeap().isInImageHeap(sourceMethodName), "Source method name string must be in the image heap");
        }

        ensureSize(index + entriesPerSourceReference());
        writeSourceReference(trace, index, sourceLineNumber, sourceClass, sourceMethodName);
        index += entriesPerSourceReference();
        numFrames++;
        return numFrames != limit;
    }

    /**
     * Determines whether an entry in the {@link Target_java_lang_Throwable#backtrace} array is a
     * source reference, as written by {@link #writeSourceReference}.
     *
     * @implNote A source reference entry has their high bit set, i.e., it is a negative number in
     *           the two's complement representation (see {@link #encodeLineNumber}).
     * @see #writeSourceReference
     * @see #encodeLineNumber
     */
    public static boolean isSourceReference(long entry) {
        return entry < 0;
    }

    /**
     * Encodes line number information of a source reference to be stored in the
     * {@link Target_java_lang_Throwable#backtrace} array. Line numbers can be positive for regular
     * line number, or zero or negative for to mark special source references.
     *
     * @implNote A line number ({@code int}) is stored as a negative {@code long} value to
     *           distinguish it from an ordinary {@link CodePointer}.
     *
     * @see #isSourceReference
     */
    public static long encodeLineNumber(int lineNumber) {
        return 0xffffffff_00000000L | lineNumber;
    }

    /**
     * Decodes a line number previously encoded by {@link #encodeLineNumber}.
     */
    public static int decodeLineNumber(long entry) {
        return (int) entry;
    }

    /**
     * Writes source reference to a backtrace array.
     *
     * @see #readSourceLineNumber
     * @see #readSourceClass
     * @see #readSourceMethodName
     */
    static void writeSourceReference(long[] backtrace, int pos, int sourceLineNumber, Class<?> sourceClass, String sourceMethodName) {
        long encodedLineNumber = encodeLineNumber(sourceLineNumber);
        if (!isSourceReference(encodedLineNumber)) {
            throw VMError.shouldNotReachHere("Encoded line number looks like a code pointer: " + encodedLineNumber);
        }
        backtrace[pos] = encodedLineNumber;
        if (useCompressedReferences()) {
            long sourceClassOop = assertNonZero(ReferenceAccess.singleton().getCompressedRepresentation(sourceClass).rawValue());
            long sourceMethodNameOop = assertNonZero(ReferenceAccess.singleton().getCompressedRepresentation(sourceMethodName).rawValue());
            VMError.guarantee((0xffffffff_00000000L & sourceClassOop) == 0L, "Compressed source class reference with high bits");
            VMError.guarantee((0xffffffff_00000000L & sourceMethodNameOop) == 0L, "Compressed source methode name reference with high bits");
            backtrace[pos + 1] = (sourceClassOop << 32) | sourceMethodNameOop;
        } else {
            backtrace[pos + 1] = assertNonZero(Word.objectToUntrackedPointer(sourceClass).rawValue());
            backtrace[pos + 2] = assertNonZero(Word.objectToUntrackedPointer(sourceMethodName).rawValue());
        }
    }

    /**
     * Return the source line number of a source reference entry created by
     * {@link #writeSourceReference}.
     * 
     * @param backtrace the backtrace array
     * @param pos the start position of the source reference entry
     * @return the source line number
     *
     * @see #writeSourceReference
     */
    static int readSourceLineNumber(long[] backtrace, int pos) {
        return BacktraceVisitor.decodeLineNumber(backtrace[pos]);
    }

    /**
     * Return the source class of a source reference entry created by {@link #writeSourceReference}.
     *
     * @param backtrace the backtrace array
     * @param pos the start position of the source reference entry
     * @return the source class
     */
    static Class<?> readSourceClass(long[] backtrace, int pos) {
        if (useCompressedReferences()) {
            UnsignedWord ref = Word.unsigned(backtrace[pos + 1]).unsignedShiftRight(32);
            return (Class<?>) ReferenceAccess.singleton().uncompressReference(ref);
        } else {
            Word sourceClassPtr = Word.pointer(backtrace[pos + 1]);
            return sourceClassPtr.toObject(Class.class, true);
        }
    }

    /**
     * Return the source method name of a source reference entry created by
     * {@link #writeSourceReference}.
     *
     * @param backtrace the backtrace array
     * @param pos the start position of the source reference entry
     * @return the source method name
     */
    static String readSourceMethodName(long[] backtrace, int pos) {
        if (useCompressedReferences()) {
            UnsignedWord ref = Word.unsigned(backtrace[pos + 1]).and(Word.unsigned(0xffffffffL));
            return (String) ReferenceAccess.singleton().uncompressReference(ref);
        } else {
            Word sourceMethodNamePtr = Word.pointer(backtrace[pos + 2]);
            return sourceMethodNamePtr.toObject(String.class, true);
        }
    }

    /**
     * Determines whether compressed references are enabled. If so, two references can be packed in
     * a single {@code long} entry.
     */
    @Fold
    static boolean useCompressedReferences() {
        return ConfigurationValues.getObjectLayout().getReferenceSize() == 4;
    }

    private static long assertNonZero(long rawValue) {
        VMError.guarantee(rawValue != 0, "Must not write 0 values to backtrace");
        return rawValue;
    }

    private void ensureSize(int minLength) {
        if (minLength > trace.length) {
            trace = Arrays.copyOf(trace, saturatedMultiply(trace.length, 2));
        }
    }

    static int saturatedMultiply(int a, int b) {
        long r = (long) a * (long) b;
        if ((int) r != r) {
            return Integer.MAX_VALUE;
        }
        return (int) r;
    }

    /**
     * Gets the backtrace array.
     *
     * Tradeoff question: should we make a copy of the trace array to trim it to length index?
     * <ul>
     * <li>Benefit: lower memory footprint for exceptions that are long-lived.
     * <li>Downside: more work for copying for every exception.
     * </ul>
     * Currently, we do not trim the array. The assumption is that most exception stack traces are
     * short-lived and are never moved by the GC.
     */
    long[] getArray() {
        VMError.guarantee(trace != null, "Already acquired");
        VMError.guarantee(index == trace.length || trace[index] == 0, "Unterminated trace?");
        long[] tmp = trace;
        trace = null;
        return tmp;
    }
}

/**
 * Decodes the internal backtrace stored in {@link Target_java_lang_Throwable#backtrace} and creates
 * the corresponding {@link StackTraceElement} array.
 */
final class StackTraceBuilder extends BacktraceDecoder {

    static StackTraceElement[] build(long[] backtrace) {
        var stackTraceBuilder = new StackTraceBuilder();
        stackTraceBuilder.visitBacktrace(backtrace, Integer.MAX_VALUE, SubstrateOptions.maxJavaStackTraceDepth());
        return stackTraceBuilder.trace.toArray(new StackTraceElement[0]);
    }

    private final ArrayList<StackTraceElement> trace = new ArrayList<>();

    @Override
    protected void processSourceReference(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber) {
        StackTraceElement sourceReference = FrameSourceInfo.getSourceReference(sourceClass, sourceMethodName, sourceLineNumber);
        trace.add(sourceReference);
    }
}

class BuildStackTraceVisitor extends JavaStackFrameVisitor {
    private final boolean filterExceptions;
    final ArrayList<StackTraceElement> trace;
    final int limit;

    BuildStackTraceVisitor(boolean filterExceptions, int limit) {
        this.filterExceptions = filterExceptions;
        this.trace = new ArrayList<>();
        this.limit = limit;
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameSourceInfo)) {
            /* Always ignore the frame. It is an internal frame of the VM. */
            return true;

        } else if (filterExceptions && trace.size() == 0 && Throwable.class.isAssignableFrom(frameSourceInfo.getSourceClass())) {
            /*
             * We are still in the constructor invocation chain at the beginning of the stack trace,
             * which is also filtered by the Java HotSpot VM.
             */
            return true;
        }

        StackTraceElement sourceReference = frameSourceInfo.getSourceReference();
        trace.add(sourceReference);
        return trace.size() != limit;
    }
}

class GetCallerClassVisitor extends JavaStackFrameVisitor {
    private final boolean showLambdaFrames;
    private int depth;
    private boolean ignoreFirst;
    Class<?> result;

    GetCallerClassVisitor(boolean showLambdaFrames, int depth, boolean ignoreFirst) {
        this.showLambdaFrames = showLambdaFrames;
        this.ignoreFirst = ignoreFirst;
        this.depth = depth;
        assert depth >= 0;
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo) {
        assert depth >= 0;

        if (ignoreFirst) {
            /*
             * Skip the frame that contained the invocation of getCallerFrame() and continue the
             * stack walk. Note that this could be a frame related to reflection, but we still must
             * not ignore it: For example, Constructor.newInstance calls Reflection.getCallerClass
             * and for this check Constructor.newInstance counts as a frame. But if the actual
             * invoked constructor calls Reflection.getCallerClass, then Constructor.newInstance
             * does not count as as frame (handled by the shouldShowFrame check below because this
             * path was already taken for the constructor frame).
             */
            ignoreFirst = false;
            return true;

        } else if (!StackTraceUtils.shouldShowFrame(frameSourceInfo, showLambdaFrames, false, false)) {
            /*
             * Always ignore the frame. It is an internal frame of the VM or a frame related to
             * reflection.
             */
            return true;

        } else if (depth > 0) {
            /* Skip the number of frames specified by "depth". */
            depth--;
            return true;

        } else {
            /* Found the caller frame, remember it and end the stack walk. */
            result = frameSourceInfo.getSourceClass();
            return false;
        }
    }
}

class GetClassContextVisitor extends JavaStackFrameVisitor {
    private int skip;
    final ArrayList<Class<?>> trace;

    GetClassContextVisitor(final int skip) {
        trace = new ArrayList<>();
        this.skip = skip;
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo) {
        if (skip > 0) {
            skip--;
        } else if (StackTraceUtils.shouldShowFrame(frameSourceInfo, true, false, false)) {
            trace.add(frameSourceInfo.getSourceClass());
        }
        return true;
    }
}

class GetLatestUserDefinedClassLoaderVisitor extends JavaStackFrameVisitor {
    ClassLoader result;

    GetLatestUserDefinedClassLoaderVisitor() {
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameSourceInfo, true, true, false)) {
            // Skip internal frames.
            return true;
        }

        ClassLoader classLoader = frameSourceInfo.getSourceClass().getClassLoader();
        if (classLoader == null || isExtensionOrPlatformLoader(classLoader)) {
            // Skip bootstrap and platform/extension class loader.
            return true;
        }

        result = classLoader;
        return false;
    }

    private static boolean isExtensionOrPlatformLoader(ClassLoader classLoader) {
        return classLoader == Target_jdk_internal_loader_ClassLoaders.platformClassLoader();
    }
}

/* Reimplementation of JVM_GetStackAccessControlContext from JDK15 */
class StackAccessControlContextVisitor extends JavaStackFrameVisitor {
    final ArrayList<ProtectionDomain> localArray;
    boolean isPrivileged;
    ProtectionDomain previousProtectionDomain;
    AccessControlContext privilegedContext;

    StackAccessControlContextVisitor() {
        localArray = new ArrayList<>();
        isPrivileged = false;
        privilegedContext = null;
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameSourceInfo, true, false, false)) {
            return true;
        }

        Class<?> clazz = frameSourceInfo.getSourceClass();
        String method = frameSourceInfo.getSourceMethodName();

        ProtectionDomain protectionDomain;
        if (PrivilegedStack.length() > 0 && clazz.equals(AccessController.class) && method.equals("doPrivileged")) {
            isPrivileged = true;
            privilegedContext = PrivilegedStack.peekContext();
            protectionDomain = PrivilegedStack.peekCaller().getProtectionDomain();
        } else {
            protectionDomain = clazz.getProtectionDomain();
        }

        if ((protectionDomain != null) && (previousProtectionDomain == null || !previousProtectionDomain.equals(protectionDomain))) {
            localArray.add(protectionDomain);
            previousProtectionDomain = protectionDomain;
        }

        return !isPrivileged;
    }
}
