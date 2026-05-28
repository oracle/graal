/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.jdk.BacktraceHolder.KIND_SHIFT;
import static com.oracle.svm.core.jdk.BacktraceHolder.REFS_IN_IMAGEHEAP_COMPRESSED;
import static com.oracle.svm.core.jdk.BacktraceHolder.REFS_IN_IMAGEHEAP_UNCOMPRESSED;
import static com.oracle.svm.core.jdk.BacktraceHolder.REFS_IN_SIDE_ARRAY;

import java.util.Arrays;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Visits the stack frames and collects a backtrace in the internal format stored in
 * {@link Target_java_lang_Throwable#backtrace}.
 *
 * @implNote The visitor writes into a raw {@code long[]} first. If all source references can be
 *           encoded directly in that array, the raw array becomes the final backtrace. If movable
 *           references must be preserved, the visitor allocates a side {@code Object[]} and returns
 *           a {@link BacktraceHolder} that wraps both arrays. This second shape is mainly needed
 *           when interpreter support is enabled.
 */
final class BacktraceVisitor extends JavaStackFrameVisitor {
    public static final int NATIVE_FRAME_LIMIT_MARGIN = 10;

    /**
     * Number of frames stored (native instruction pointers or encoded Java source reference).
     */
    private int numFrames = 0;
    private final int limit = computeNativeLimit();

    /*
     * Empirical data suggests that most stack traces tend to be relatively short (<100). We choose
     * the initial size so that these cases do not need to reallocate the array.
     */
    static final int INITIAL_TRACE_SIZE = 80;
    private long[] rawBacktrace = new long[INITIAL_TRACE_SIZE];
    private Object[] sideBacktrace = null;
    private int rawIndex;
    private int sideIndex;

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

    static int determineSourceReferenceKind(Class<?> sourceClass, String sourceMethodName) {
        Heap h = Heap.getHeap();
        if (!h.isInImageHeap(sourceClass) || !h.isInImageHeap(sourceMethodName)) {
            return REFS_IN_SIDE_ARRAY;
        }

        return useCompressedReferences() ? REFS_IN_IMAGEHEAP_COMPRESSED : REFS_IN_IMAGEHEAP_UNCOMPRESSED;
    }

    /**
     * Encodes the line-number portion of a source reference for storage in
     * {@link Target_java_lang_Throwable#backtrace}. Line numbers can be positive for regular source
     * lines, or zero/negative to mark special source references.
     *
     * @implNote A line number ({@code int}) is stored as a negative {@code long} value to
     *           distinguish it from an ordinary {@link CodePointer}.
     * @see BacktraceHolder#isSourceReference(long)
     */
    static long encodeLineNumberWithKind(int lineNumber, int kind) {
        assert kind == REFS_IN_IMAGEHEAP_UNCOMPRESSED || kind == REFS_IN_IMAGEHEAP_COMPRESSED || kind == REFS_IN_SIDE_ARRAY;

        long v = (((long) kind) << KIND_SHIFT) | (lineNumber & 0xffff_ffffL);
        assert v < 0;
        return v;
    }

    static int saturatedMultiply(int a, int b) {
        long r = (long) a * (long) b;
        if ((int) r != r) {
            return Integer.MAX_VALUE;
        }
        return (int) r;
    }

    static long guaranteeNonZero(long rawValue) {
        VMError.guarantee(rawValue != 0, "Must not write 0 values to backtrace");
        return rawValue;
    }

    @Fold
    static boolean useCompressedReferences() {
        return ObjectLayout.singleton().getReferenceSize() == 4;
    }

    @Override
    public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo) {
        if (InterpreterSupport.isEnabled() || RuntimeCompilation.isEnabled() && !CodeInfoTable.isInAOTImageCode(ip)) {
            /*
             * GR-46090: better detection of interpreter frames needed. Right now this forces
             * exception handling to always go through the "encoded Java source reference" case as
             * soon the interpreter is enabled.
             */
            CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip);
            for (FrameInfoQueryResult frameInfo = queryResult.getFrameInfo(); frameInfo != null; frameInfo = frameInfo.getCaller()) {
                if (!dispatch(frameInfo, sp)) {
                    return false;
                }
            }
        } else {
            assert CodeInfoTable.isInAOTImageCode(ip);
            visitAOTFrame(ip);
        }
        return numFrames != limit;
    }

    @Override
    protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
        /*
         * Deoptimization copies the source-frame values into the DeoptimizedFrame and makes
         * the original frame content invalid. Pass null so source-frame translation cannot
         * read stack slots through the original SP.
         */
        Pointer deoptimizedSP = Word.nullPointer();
        for (VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
            FrameInfoQueryResult frameInfo = frame.getFrameInfo();
            if (!dispatch(frameInfo, deoptimizedSP)) {
                return false;
            }
        }
        return numFrames != limit;
    }

    private void visitAOTFrame(CodePointer ip) {
        VMError.guarantee(ip.isNonNull(), "Unexpected code pointer: 0");
        ensureRawCapacity(BacktraceHolder.slotsPerCodePointer());

        long rawValue = ip.rawValue();
        rawBacktrace[rawIndex] = rawValue;
        if (BacktraceHolder.isSourceReference(rawValue)) {
            throw VMError.shouldNotReachHere("Not a code pointer: 0x" + Long.toHexString(rawValue));
        }
        rawIndex++;
        numFrames++;
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo, Pointer sp) {
        if (!StackTraceUtils.shouldShowFrame(frameSourceInfo)) {
            /* Always ignore the frame. It is an internal frame of the VM. */
            return true;

        } else if (numFrames == 0 && Throwable.class.isAssignableFrom(frameSourceInfo.getSourceClass())) {
            /*
             * We are still in the constructor invocation chain at the beginning of the stack trace,
             * which is also filtered by the Java HotSpot VM.
             */
            return true;
        }
        int sourceLineNumber = frameSourceInfo.getSourceLineNumber();
        Class<?> sourceClass = frameSourceInfo.getSourceClass();
        String sourceMethodName = frameSourceInfo.getSourceMethodName();

        assert sourceClass != null && sourceMethodName != null;

        writeSourceReference(sourceLineNumber, sourceClass, sourceMethodName);
        numFrames++;
        return numFrames != limit;
    }

    private void writeSourceReference(int sourceLineNumber, Class<?> sourceClass, String sourceMethodName) {
        int kind = determineSourceReferenceKind(sourceClass, sourceMethodName);
        ensureRawCapacity(BacktraceHolder.slotsPerSourceReference(kind));

        rawBacktrace[rawIndex++] = encodeLineNumberWithKind(sourceLineNumber, kind);
        if (kind == REFS_IN_IMAGEHEAP_COMPRESSED) {
            assert useCompressedReferences();
            assert Heap.getHeap().isInImageHeap(sourceClass);
            assert Heap.getHeap().isInImageHeap(sourceMethodName);

            long sourceClassOop = guaranteeNonZero(ReferenceAccess.singleton().getCompressedRepresentation(sourceClass).rawValue());
            long sourceMethodNameOop = guaranteeNonZero(ReferenceAccess.singleton().getCompressedRepresentation(sourceMethodName).rawValue());

            assert (0xffff_ffff_0000_0000L & sourceClassOop) == 0L : "Compressed source class reference with high bits";
            assert (0xffff_ffff_0000_0000L & sourceMethodNameOop) == 0L : "Compressed source methode name reference with high bits";

            rawBacktrace[rawIndex++] = (sourceClassOop << 32) | sourceMethodNameOop;
        } else if (kind == REFS_IN_IMAGEHEAP_UNCOMPRESSED) {
            assert Heap.getHeap().isInImageHeap(sourceClass);
            assert Heap.getHeap().isInImageHeap(sourceMethodName);

            long sourceClassOop = guaranteeNonZero(Word.objectToUntrackedPointer(sourceClass).rawValue());
            long sourceMethodNameOop = guaranteeNonZero(Word.objectToUntrackedPointer(sourceMethodName).rawValue());

            rawBacktrace[rawIndex++] = sourceClassOop;
            rawBacktrace[rawIndex++] = sourceMethodNameOop;
        } else {
            assert kind == REFS_IN_SIDE_ARRAY;
            ensureSideCapacity(2);

            long sourceClassSideIndex = storeSide(sourceClass);
            long sourceMethodNameSideIndex = storeSide(sourceMethodName);
            rawBacktrace[rawIndex++] = (sourceClassSideIndex << 32) | sourceMethodNameSideIndex;
        }
    }

    private void ensureRawCapacity(int need) {
        assert need >= 0;
        if (rawIndex <= rawBacktrace.length - need) {
            return;
        }
        rawBacktrace = Arrays.copyOf(rawBacktrace, saturatedMultiply(rawBacktrace.length, 2));
    }

    private void ensureSideCapacity(int need) {
        assert need >= 0;
        if (sideBacktrace == null) {
            int size = Math.max(need, INITIAL_TRACE_SIZE / 8);
            sideBacktrace = new Object[size];
            return;
        }
        if (sideIndex <= sideBacktrace.length - need) {
            return;
        }
        sideBacktrace = Arrays.copyOf(sideBacktrace, saturatedMultiply(sideBacktrace.length, 2));
    }

    private int storeSide(Object value) {
        sideBacktrace[sideIndex] = value;
        return sideIndex++;
    }

    /**
     * Returns the collected backtrace in the representation expected by
     * {@link Target_java_lang_Throwable#backtrace}.
     * <p>
     * If no movable references were encountered, this returns the raw {@code long[]} directly.
     * Otherwise, it returns a {@link BacktraceHolder} that wraps the raw array together with the
     * side array for GC-tracked references.
     * <p>
     * We intentionally do not trim the arrays to {@code rawIndex} / {@code sideIndex}. The
     * assumption is that most exception stack traces are short-lived, so avoiding an extra copy on
     * every throw is preferable to minimizing the retained size of long-lived exceptions.
     */
    Object getBacktraceHolder() {
        try {
            return sideBacktrace != null ? new BacktraceHolder(rawBacktrace, sideBacktrace) : rawBacktrace;
        } finally {
            rawBacktrace = null;
            sideBacktrace = null;
        }
    }
}
