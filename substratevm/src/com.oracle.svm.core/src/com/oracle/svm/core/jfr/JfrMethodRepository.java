/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.jfr.utils.JfrVisitedTable;
import com.oracle.svm.core.locks.VMMutex;

/**
 * Repository that collects and writes used methods.
 */
public class JfrMethodRepository implements JfrConstantPool {
    private final VMMutex mutex;
    private final JfrMethodEpochData epochData0;
    private final JfrMethodEpochData epochData1;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrMethodRepository() {
        this.epochData0 = new JfrMethodEpochData();
        this.epochData1 = new JfrMethodEpochData();
        this.mutex = new VMMutex("jfrMethodRepository");
    }

    @Uninterruptible(reason = "Releasing repository buffers.")
    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getMethodId(Class<?> clazz, String methodName, int methodId) {
        assert clazz != null;
        assert methodName != null;
        assert methodId > 0;

        mutex.lockNoTransition();
        try {
            return getMethodId0(clazz, methodName, methodId);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private long getMethodId0(Class<?> clazz, String methodName, int methodId) {
        JfrVisited jfrVisited = StackValue.get(JfrVisited.class);
        jfrVisited.setId(methodId);
        jfrVisited.setHash(methodId);

        JfrMethodEpochData epochData = getEpochData(false);
        if (!epochData.visitedMethods.putIfAbsent(jfrVisited)) {
            return methodId;
        }

        if (epochData.methodBuffer.isNull()) {
            // This will happen only on the first call.
            epochData.methodBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }

        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.methodBuffer);
        JfrNativeEventWriter.putLong(data, methodId);
        JfrNativeEventWriter.putLong(data, typeRepo.getClassId(clazz));
        JfrNativeEventWriter.putLong(data, symbolRepo.getSymbolId(methodName, false));
        /* Dummy value for signature. */
        JfrNativeEventWriter.putLong(data, symbolRepo.getSymbolId("()V", false));
        /* Dummy value for modifiers. */
        JfrNativeEventWriter.putShort(data, (short) 0);
        /* Dummy value for isHidden. */
        JfrNativeEventWriter.putBoolean(data, false);
        JfrNativeEventWriter.commit(data);

        /* The buffer may have been replaced with a new one. */
        epochData.methodBuffer = data.getJfrBuffer();
        epochData.unflushedMethodCount++;
        return methodId;
    }

    @Uninterruptible(reason = "Locking without transition.")
    private void maybeLock(boolean flush) {
        if (flush) {
            mutex.lockNoTransition();
        }
    }

    @Uninterruptible(reason = "Locking without transition.")
    private void maybeUnlock(boolean flush) {
        if (flush) {
            mutex.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Must not be interrupted for operations that emit events, potentially writing to this pool.")
    public int write(JfrChunkWriter writer, boolean flush) {
        maybeLock(flush);
        try {
            JfrMethodEpochData epochData = getEpochData(!flush);
            int count = writeMethods(writer, epochData);
            if (!flush) {
                epochData.clear();
            }
            return count;
        } finally {
            maybeUnlock(flush);
        }
    }

    @Uninterruptible(reason = "May write current epoch data.")
    private static int writeMethods(JfrChunkWriter writer, JfrMethodEpochData epochData) {
        int numberOfMethods = epochData.unflushedMethodCount;
        if (numberOfMethods == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.Method.getId());
        writer.writeCompressedInt(numberOfMethods);
        writer.write(epochData.methodBuffer);
        JfrBufferAccess.reinitialize(epochData.methodBuffer);
        epochData.unflushedMethodCount = 0;
        return NON_EMPTY;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private JfrMethodEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    private static class JfrMethodEpochData {
        private JfrBuffer methodBuffer;
        private final JfrVisitedTable visitedMethods;
        private int unflushedMethodCount;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrMethodEpochData() {
            this.visitedMethods = new JfrVisitedTable();
            this.unflushedMethodCount = 0;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            visitedMethods.teardown();
            if (methodBuffer.isNonNull()) {
                JfrBufferAccess.free(methodBuffer);
            }
            methodBuffer = WordFactory.nullPointer();
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear() {
            visitedMethods.clear();
            if (methodBuffer.isNonNull()) {
                JfrBufferAccess.reinitialize(methodBuffer);
            }
            unflushedMethodCount = 0;
        }
    }
}
