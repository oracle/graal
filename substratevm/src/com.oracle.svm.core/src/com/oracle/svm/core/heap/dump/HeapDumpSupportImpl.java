/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap.dump;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.TimeUtils;

import jdk.graal.compiler.api.replacements.Fold;

public class HeapDumpSupportImpl extends HeapDumping {
    private final HeapDumpWriter writer;
    private final HeapDumpOperation heapDumpOperation;
    private final VMMutex outOfMemoryHeapDumpMutex = new VMMutex("outOfMemoryHeapDump");

    private CCharPointer outOfMemoryHeapDumpPath;
    private boolean outOfMemoryHeapDumpAttempted;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapDumpSupportImpl(HeapDumpMetadata metadata) {
        this.writer = new HeapDumpWriter(metadata);
        this.heapDumpOperation = new HeapDumpOperation();
    }

    @Override
    public void initializeDumpHeapOnOutOfMemoryError() {
        assert outOfMemoryHeapDumpPath.isNull();
        String defaultFilename = getDefaultHeapDumpFilename("OOME");
        String heapDumpPath = getHeapDumpPath(defaultFilename);
        outOfMemoryHeapDumpPath = getFileSupport().allocateCPath(heapDumpPath);
    }

    @Override
    public void teardownDumpHeapOnOutOfMemoryError() {
        UntrackedNullableNativeMemory.free(outOfMemoryHeapDumpPath);
        outOfMemoryHeapDumpPath = WordFactory.nullPointer();
    }

    @Override
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "OutOfMemoryError heap dumping must not allocate.")
    public void dumpHeapOnOutOfMemoryError() {
        /*
         * Try exactly once to create an out-of-memory heap dump. If another thread triggers an
         * OutOfMemoryError while heap dumping is in progress, it needs to wait until heap dumping
         * finishes.
         */
        outOfMemoryHeapDumpMutex.lock();
        try {
            if (!outOfMemoryHeapDumpAttempted) {
                dumpHeapOnOutOfMemoryError0();
                outOfMemoryHeapDumpAttempted = true;
            }
        } finally {
            outOfMemoryHeapDumpMutex.unlock();
        }
    }

    private void dumpHeapOnOutOfMemoryError0() {
        CCharPointer path = outOfMemoryHeapDumpPath;
        if (path.isNull()) {
            Log.log().string("OutOfMemoryError heap dumping failed because the heap dump file path could not be allocated.").newline();
            return;
        }

        RawFileDescriptor fd = getFileSupport().create(path, FileCreationMode.CREATE_OR_REPLACE, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        if (!getFileSupport().isValid(fd)) {
            Log.log().string("OutOfMemoryError heap dumping failed because the heap dump file could not be created: ").string(path).newline();
            return;
        }

        try {
            Log.log().string("Dumping heap to ").string(path).string(" ...").newline();
            long start = System.currentTimeMillis();
            if (dumpHeap(fd, false)) {
                long fileSize = getFileSupport().size(fd);
                long elapsedMs = System.currentTimeMillis() - start;
                long seconds = elapsedMs / TimeUtils.millisPerSecond;
                long ms = elapsedMs % TimeUtils.millisPerSecond;
                Log.log().string("Heap dump file created [").signed(fileSize).string(" bytes in ").signed(seconds).character('.').signed(ms).string(" secs]").newline();
            }
        } finally {
            getFileSupport().close(fd);
        }
    }

    @Override
    public void dumpHeap(String filename, boolean gcBefore) throws IOException {
        RawFileDescriptor fd = getFileSupport().create(filename, FileCreationMode.CREATE_OR_REPLACE, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        if (!getFileSupport().isValid(fd)) {
            throw new IOException("Could not create the heap dump file: " + filename);
        }

        try {
            writeHeapTo(fd, gcBefore);
        } finally {
            getFileSupport().close(fd);
        }
    }

    private boolean dumpHeap(RawFileDescriptor fd, boolean gcBefore) {
        int size = SizeOf.get(HeapDumpVMOperationData.class);
        HeapDumpVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);
        data.setGCBefore(gcBefore);
        data.setRawFileDescriptor(fd);
        heapDumpOperation.enqueue(data);
        return data.getSuccess();
    }

    public void writeHeapTo(RawFileDescriptor fd, boolean gcBefore) throws IOException {
        boolean success = dumpHeap(fd, gcBefore);
        if (!success) {
            throw new IOException("An error occurred while writing the heap dump.");
        }
    }

    @Fold
    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    @RawStructure
    private interface HeapDumpVMOperationData extends NativeVMOperationData {
        @RawField
        boolean getGCBefore();

        @RawField
        void setGCBefore(boolean value);

        @RawField
        RawFileDescriptor getRawFileDescriptor();

        @RawField
        void setRawFileDescriptor(RawFileDescriptor fd);

        @RawField
        boolean getSuccess();

        @RawField
        void setSuccess(boolean value);
    }

    private class HeapDumpOperation extends NativeVMOperation {
        @Platforms(Platform.HOSTED_ONLY.class)
        HeapDumpOperation() {
            super(VMOperationInfos.get(HeapDumpOperation.class, "Write heap dump", VMOperation.SystemEffect.SAFEPOINT));
        }

        @Override
        @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Heap dumping must not allocate.")
        protected void operate(NativeVMOperationData d) {
            HeapDumpVMOperationData data = (HeapDumpVMOperationData) d;
            if (data.getGCBefore()) {
                Heap.getHeap().getGC().collectCompletely(GCCause.HeapDump);
            }

            try {
                boolean success = writer.dumpHeap(data.getRawFileDescriptor());
                data.setSuccess(success);
            } catch (Throwable e) {
                Log.log().string("An exception occurred during heap dumping. The data in the heap dump file may be corrupt: ").string(e.getClass().getName()).newline();
                data.setSuccess(false);
            }
        }
    }
}
