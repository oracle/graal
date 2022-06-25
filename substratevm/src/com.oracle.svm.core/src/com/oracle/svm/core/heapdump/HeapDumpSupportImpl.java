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
package com.oracle.svm.core.heapdump;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.io.IOException;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.HeapDumpSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.VMOperation;

public class HeapDumpSupportImpl implements HeapDumpSupport {
    private final HeapDumpWriter writer;
    private final HeapDumpOperation heapDumpOperation;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapDumpSupportImpl() {
        this.writer = new HeapDumpWriter();
        this.heapDumpOperation = new HeapDumpOperation();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMetadata(byte[] value) {
        this.writer.setMetadata(value);
    }

    @Override
    public void dumpHeap(String outputFile, boolean live) throws IOException {
        writeHeapTo(outputFile, live);
    }

    public void writeHeapTo(String filename, boolean gcBefore) throws IOException {
        RawFileOperationSupport.RawFileDescriptor fd = getFileSupport().create(filename, FileCreationMode.CREATE_OR_REPLACE, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        if (!getFileSupport().isValid(fd)) {
            throw new IOException("Could not create the heap dump file: " + filename);
        }

        try {
            int size = SizeOf.get(HeapDumpVMOperationData.class);
            HeapDumpVMOperationData data = StackValue.get(size);
            UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

            data.setGCBefore(gcBefore);
            data.setRawFileDescriptor(fd);
            heapDumpOperation.enqueue(data);

            if (!data.getSuccess()) {
                throw new IOException("An error occurred while writing the heap dump.");
            }
        } finally {
            getFileSupport().close(fd);
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
        RawFileOperationSupport.RawFileDescriptor getRawFileDescriptor();

        @RawField
        void setRawFileDescriptor(RawFileOperationSupport.RawFileDescriptor fd);

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
                System.gc();
            }
            boolean success = writer.dumpHeap(data.getRawFileDescriptor());
            data.setSuccess(success);
        }
    }
}
