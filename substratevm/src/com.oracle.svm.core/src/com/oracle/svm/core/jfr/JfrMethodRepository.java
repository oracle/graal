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
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.jfr.utils.JfrVisitedTable;
import com.oracle.svm.core.locks.VMMutex;

/**
 * Repository that collects and writes used methods.
 */
public class JfrMethodRepository implements JfrRepository {
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

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    public long getMethodId(Class<?> clazz, String methodName, String methodSignature, int methodId, int methodModifier) {
        assert clazz != null;
        assert methodName != null;
        assert methodId > 0;

        JfrVisited jfrVisited = StackValue.get(JfrVisited.class);
        jfrVisited.setId(methodId);
        jfrVisited.setHash(methodId);

        mutex.lockNoTransition();
        try {
            JfrMethodEpochData epochData = getEpochData(false);
            if (!epochData.table.putIfAbsent(jfrVisited)) {
                return methodId;
            }

            /* New entry, so serialize it to the buffer. */
            if (epochData.buffer.isNull()) {
                epochData.buffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
            }

            JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
            JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.buffer);
            JfrNativeEventWriter.putLong(data, methodId);
            JfrNativeEventWriter.putLong(data, typeRepo.getClassId(clazz));
            JfrNativeEventWriter.putLong(data, symbolRepo.getSymbolId(methodName, false));
            JfrNativeEventWriter.putLong(data, symbolRepo.getSymbolId(methodSignature, false));
            JfrNativeEventWriter.putInt(data, methodModifier);
            JfrNativeEventWriter.putBoolean(data, !StackTraceUtils.shouldShowFrame(clazz, methodName));
            if (!JfrNativeEventWriter.commit(data)) {
                return methodId;
            }

            epochData.unflushedEntries++;
            /* The buffer may have been replaced with a new one. */
            epochData.buffer = data.getJfrBuffer();
            return methodId;
        } finally {
            mutex.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        mutex.lockNoTransition();
        try {
            JfrMethodEpochData epochData = getEpochData(!flushpoint);
            int count = epochData.unflushedEntries;
            if (count != 0) {
                writer.writeCompressedLong(JfrType.Method.getId());
                writer.writeCompressedInt(count);
                writer.write(epochData.buffer);
            }
            epochData.clear(flushpoint);
            return count == 0 ? EMPTY : NON_EMPTY;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Prevent epoch change.", callerMustBe = true)
    private JfrMethodEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    private static class JfrMethodEpochData {
        private final JfrVisitedTable table;
        private int unflushedEntries;
        private JfrBuffer buffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrMethodEpochData() {
            this.table = new JfrVisitedTable();
            this.unflushedEntries = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear(boolean flushpoint) {
            if (!flushpoint) {
                table.clear();
            }
            unflushedEntries = 0;
            JfrBufferAccess.reinitialize(buffer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            table.teardown();
            unflushedEntries = 0;
            JfrBufferAccess.free(buffer);
            buffer = WordFactory.nullPointer();
        }
    }
}
