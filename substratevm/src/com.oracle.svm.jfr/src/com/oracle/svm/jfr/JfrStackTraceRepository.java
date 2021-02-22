/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import java.io.IOException;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawPointerTo;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jfr.JfrMethodRepository.MethodInfo;

/**
 * An incomplete and only roughly sketched implementation of how the recording of stack traces could
 * look like.
 */
public class JfrStackTraceRepository implements JfrRepository {
    private final JfrMethodRepository methodRepo;
    private final JfrStackVisitor visitor;
    private final JfrStackTraceHashtable table;

    private int maxFrameCount;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrStackTraceRepository(JfrMethodRepository methodRepo) {
        this.methodRepo = methodRepo;
        this.visitor = new JfrStackVisitor();
        this.table = new JfrStackTraceHashtable();
        this.maxFrameCount = 0;
    }

    public void teardown() {
        table.teardown();
    }

    @Uninterruptible(reason = "Stack walking, can be called in the allocation slowpath, and epoch must not change while in this method.")
    @NeverInline("Starts a stack walk in the caller frame.")
    public long recordStackTrace(int skipCount) {
        // TODO: HotSpot uses a separate thread-local buffer for stack frames (see
        // JfrThreadLocal::install_stackframes()). That might be the safest and most performant
        // way...
        int stackTraceBytes = SizeOf.get(JfrStackTrace.class);
        JfrStackTrace stackTrace = StackValue.get(stackTraceBytes);
        MemoryUtil.fillToMemoryAtomic((Pointer) stackTrace, WordFactory.unsigned(stackTraceBytes), (byte) 0);

        int maxFrames = this.maxFrameCount;
        JfrStackFrames frames = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(JfrStackFrame.class).multiply(maxFrames));
        if (frames.isNonNull()) {
            stackTrace.setStackFrames(frames);
            try {
                fillInStackTrace(stackTrace, maxFrames, skipCount);
                return table.add(stackTrace);
            } finally {
                ImageSingletons.lookup(UnmanagedMemorySupport.class).free(stackTrace.getStackFrames());
            }
        }
        return 0L;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void fillInStackTrace(JfrStackTrace stackTrace, int maxFrames, int skipCount) {
        // TODO: do some kind of uninterruptible stack walking using the JfrStackVisitor to fetch
        // the Java stack frames
    }

    @Override
    public void write(JfrChunkWriter writer) throws IOException {
        assert VMOperation.isInProgressAtSafepoint();
        writer.writeCompressedLong(JfrTypes.StackTrace.getId());
        writer.writeCompressedLong(table.getSize());

        // TODO: write the stack traces to the file
    }

    /**
     * Stateless stack visitor that can be used to collect a JFR stack trace.
     */
    private static class JfrStackVisitor {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean visitFrame(FrameInfo frame, JfrStackTrace stackTrace, int maxFrameCount, int skipCount) {
            // TODO: we need a custom stack iteration as we can't allocate ANY data
            // on the Java heap (this code is also called directly before triggering a GC) and we
            // need the information about the Java stack (not just the frames).
            if (stackTrace.getFrameCount() >= maxFrameCount) {
                stackTrace.setTruncated(true);
                return false;
            }

            // TODO: apply the frame skip count

            int frameIndex = stackTrace.getFrameCount();
            stackTrace.setFrameCount(frameIndex + 1);
            JfrStackFrame jfrFrame = stackTrace.getStackFrames().addressOf(frameIndex);

            MethodInfo methodInfo = frame.getMethodInfo();
            int bci = frame.getBci();
            byte type = getFrameType(frame);

            jfrFrame.setMethod(methodInfo);
            jfrFrame.setSourceLineNumber(frame.getSourceLineNumber());
            jfrFrame.setBci(bci);
            jfrFrame.setType(type);

            int hash = (stackTrace.getHash() << 2) + ((methodInfo.getId() >>> 2)) + (bci << 4) + type;
            stackTrace.setHash(hash);
            return true;
        }
    }

    private static byte getFrameType(FrameInfo frame) {
        if (frame.isAOTCompiledCode()) {
            return JfrFrameType.FRAME_AOT_COMPILED.getId();
        } else if (frame.isNativeMethod()) {
            return JfrFrameType.FRAME_NATIVE.getId();
        } else if (frame.isJITCompiledCode()) {
            return JfrFrameType.FRAME_JIT_COMPILED.getId();
        } else {
            throw VMError.shouldNotReachHere("Unknown type of stack frame.");
        }
    }

    // TODO: just a dummy implementation
    private static class FrameInfo {
        private MethodInfo methodInfo;

        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        public int getSourceLineNumber() {
            return 0;
        }

        public int getBci() {
            return 0;
        }

        public boolean isAOTCompiledCode() {
            return false;
        }

        public boolean isJITCompiledCode() {
            return false;
        }

        public boolean isNativeMethod() {
            return false;
        }
    }

    private static class JfrStackTraceHashtable extends UninterruptibleHashtable<JfrStackTrace> {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected JfrStackTrace[] createTable(int size) {
            return new JfrStackTrace[size];
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected void free(JfrStackTrace t) {
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(t);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected boolean isEqual(JfrStackTrace a, JfrStackTrace b) {
            throw VMError.shouldNotReachHere("Not yet implemented");
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected JfrStackTrace copyToHeap(JfrStackTrace stackTraceOnStack) {
            UnsignedWord sizeOfStackTrace = SizeOf.unsigned(JfrStackTrace.class);
            JfrStackTrace stackTraceOnHeap = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(sizeOfStackTrace);
            if (stackTraceOnHeap.isNonNull()) {
                MemoryUtil.copyConjointMemoryAtomic((Pointer) stackTraceOnStack, (Pointer) stackTraceOnHeap, sizeOfStackTrace);

                // Copy the stack frames as well.
                UnsignedWord sizeOfFrames = SizeOf.unsigned(JfrStackFrame.class).multiply(stackTraceOnStack.getFrameCount());
                JfrStackFrames toFrames = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(sizeOfFrames);
                if (toFrames.isNonNull()) {
                    JfrStackFrames fromFrames = stackTraceOnStack.getStackFrames();
                    MemoryUtil.copyConjointMemoryAtomic((Pointer) fromFrames, (Pointer) toFrames, sizeOfFrames);
                    stackTraceOnHeap.setStackFrames(toFrames);
                    return stackTraceOnHeap;
                }

                // Allocation failed, so free all other memory as well.
                ImageSingletons.lookup(UnmanagedMemorySupport.class).free(stackTraceOnHeap);
            }
            return WordFactory.nullPointer();
        }
    }

    @RawStructure
    private interface JfrStackTrace extends UninterruptibleEntry<JfrStackTrace> {
        @RawField
        int getFrameCount();

        @RawField
        void setFrameCount(int value);

        @RawField
        boolean getTruncated();

        @RawField
        JfrStackFrames getStackFrames();

        @RawField
        void setStackFrames(JfrStackFrames value);

        @RawField
        void setTruncated(boolean value);
    }

    @RawPointerTo(JfrStackFrame.class)
    public interface JfrStackFrames extends PointerBase {
        JfrStackFrame addressOf(long index);

        void write(JfrStackFrame value);

        JfrStackFrame read();
    }

    @RawStructure
    private interface JfrStackFrame extends PointerBase {
        @PinnedObjectField
        @RawField
        Object getMethod();

        @PinnedObjectField
        @RawField
        void setMethod(Object value);

        @RawField
        int getSourceLineNumber();

        @RawField
        void setSourceLineNumber(int value);

        @RawField
        int getBci();

        @RawField
        void setBci(int value);

        @RawField
        byte getType();

        @RawField
        void setType(byte value);
    }
}
