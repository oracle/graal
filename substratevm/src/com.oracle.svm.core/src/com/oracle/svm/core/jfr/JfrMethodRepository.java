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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.hub.DynamicHub;
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
    public long getMethodId(FrameInfoQueryResult stackTraceElement) {
        JfrMethodEpochData epochData = getEpochData(false);
        mutex.lockNoTransition();
        try {
            return getMethodId0(stackTraceElement, epochData);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private static long getMethodId0(FrameInfoQueryResult stackTraceElement, JfrMethodEpochData epochData) {
        if (epochData.methodBuffer.isNull()) {
            // This will happen only on the first call.
            epochData.methodBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }

        JfrVisited jfrVisited = StackValue.get(JfrVisited.class);
        int methodId = stackTraceElement.getMethodID();
        jfrVisited.setId(methodId);
        jfrVisited.setHash(stackTraceElement.hashCode());
        if (!epochData.visitedMethods.putIfAbsent(jfrVisited)) {
            return methodId;
        }

        JfrSymbolRepository symbolRepo = SubstrateJVM.getSymbolRepository();
        JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.methodBuffer);

        /* JFR Method id. */
        JfrNativeEventWriter.putLong(data, methodId);
        /* Class. */
        JfrNativeEventWriter.putLong(data, typeRepo.getClassId(stackTraceElement.getSourceClass()));
        /* Method name. */
        JfrNativeEventWriter.putLong(data, symbolRepo.getSymbolId(stackTraceElement.getSourceMethodName(), false));
        /* Method description. */
        JfrNativeEventWriter.putLong(data, symbolRepo.getSymbolId("()V", false));
        /* Method modifier. */
        JfrNativeEventWriter.putInt(data, 0);
        /* Is hidden class? */
        JfrNativeEventWriter.putBoolean(data, SubstrateUtil.isHiddenClass(DynamicHub.fromClass(stackTraceElement.getSourceClass())));
        JfrNativeEventWriter.commit(data);

        // Maybe during writing, the thread buffer was replaced with a new (larger) one, so we
        // need to update the repository pointer as well.
        epochData.methodBuffer = data.getJfrBuffer();
        return methodId;
    }

    @Override
    public int write(JfrChunkWriter writer) {
        JfrMethodEpochData epochData = getEpochData(true);
        int count = writeMethods(writer, epochData);
        epochData.clear();
        return count;
    }

    private static int writeMethods(JfrChunkWriter writer, JfrMethodEpochData epochData) {
        int numberOfMethods = epochData.visitedMethods.getSize();
        if (numberOfMethods == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.Method.getId());
        writer.writeCompressedInt(numberOfMethods);
        writer.write(epochData.methodBuffer);

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

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrMethodEpochData() {
            this.visitedMethods = new JfrVisitedTable();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            visitedMethods.teardown();
            if (methodBuffer.isNonNull()) {
                JfrBufferAccess.free(methodBuffer);
            }
            methodBuffer = WordFactory.nullPointer();
        }

        void clear() {
            visitedMethods.clear();
            if (methodBuffer.isNonNull()) {
                JfrBufferAccess.reinitialize(methodBuffer);
            }
        }
    }
}
