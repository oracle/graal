/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrBuffer;
import com.oracle.svm.core.jfr.JfrBufferAccess;
import com.oracle.svm.core.jfr.JfrBufferType;
import com.oracle.svm.core.jfr.JfrChunkWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrRepository;
import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.locks.VMMutex;

import jdk.graal.compiler.word.Word;

public final class JfrOldObjectRepository implements JfrRepository {
    private static final int OBJECT_DESCRIPTION_MAX_LENGTH = 100;

    private final VMMutex mutex;
    private final JfrOldObjectEpochData epochData0;
    private final JfrOldObjectEpochData epochData1;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectRepository() {
        this.mutex = new VMMutex("jfrOldObjectRepository");
        this.epochData0 = new JfrOldObjectEpochData();
        this.epochData1 = new JfrOldObjectEpochData();
    }

    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    public long serializeOldObject(Object obj) {
        mutex.lockNoTransition();
        try {
            JfrOldObjectEpochData epochData = getEpochData(false);
            if (epochData.buffer.isNull()) {
                epochData.buffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
            }

            long id = JfrOldObjectEpochData.nextId;
            Word pointer = Word.objectToUntrackedPointer(obj);
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.buffer);
            JfrNativeEventWriter.putLong(data, id);
            JfrNativeEventWriter.putLong(data, pointer.rawValue());
            JfrNativeEventWriter.putLong(data, SubstrateJVM.getTypeRepository().getClassId(obj.getClass()));
            writeDescription(obj, data);
            JfrNativeEventWriter.putLong(data, 0L); // GC root
            if (!JfrNativeEventWriter.commit(data)) {
                return 0L;
            }

            JfrOldObjectEpochData.nextId++;
            epochData.unflushedEntries++;
            /* The buffer may have been replaced with a new one. */
            epochData.buffer = data.getJfrBuffer();
            return id;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void writeDescription(Object obj, JfrNativeEventWriterData data) {
        if (obj instanceof ThreadGroup group) {
            writeDescription(data, "Thread Group: ", group.getName());
        } else if (obj instanceof Thread thread) {
            writeDescription(data, "Thread Name: ", thread.getName());
        } else if (obj instanceof Class<?> clazz) {
            writeDescription(data, "Class Name: ", clazz.getName());
        } else {
            // Size description not implemented since that relies on runtime reflection.
            JfrNativeEventWriter.putString(data, null);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void writeDescription(JfrNativeEventWriterData data, String prefix, String text) {
        if (text == null || text.isEmpty()) {
            JfrNativeEventWriter.putString(data, text);
            return;
        }

        Pointer buffer = UnsafeStackValue.get(OBJECT_DESCRIPTION_MAX_LENGTH);
        Pointer bufferEnd = buffer.add(OBJECT_DESCRIPTION_MAX_LENGTH);

        int prefixLength = UninterruptibleUtils.String.modifiedUTF8Length(prefix, false);
        int textLength = UninterruptibleUtils.String.modifiedUTF8Length(text, false);
        assert prefixLength < OBJECT_DESCRIPTION_MAX_LENGTH - 3;

        boolean tooLong = false;
        int totalLength = prefixLength + textLength;
        if (totalLength > OBJECT_DESCRIPTION_MAX_LENGTH) {
            totalLength = OBJECT_DESCRIPTION_MAX_LENGTH;
            textLength = OBJECT_DESCRIPTION_MAX_LENGTH - prefixLength - 3;
            tooLong = true;
        }

        Pointer pos = UninterruptibleUtils.String.toModifiedUTF8(prefix, buffer, bufferEnd, false);
        pos = UninterruptibleUtils.String.toModifiedUTF8(text, textLength, pos, bufferEnd, false, null);

        if (tooLong) {
            pos.writeByte(0, (byte) '.');
            pos.writeByte(1, (byte) '.');
            pos.writeByte(2, (byte) '.');
        }

        JfrNativeEventWriter.putString(data, buffer, totalLength);
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        mutex.lockNoTransition();
        try {
            JfrOldObjectEpochData epochData = getEpochData(!flushpoint);
            int count = epochData.unflushedEntries;
            if (count != 0) {
                writer.writeCompressedLong(JfrType.OldObject.getId());
                writer.writeCompressedInt(count);
                writer.write(epochData.buffer);
            }
            epochData.clear(flushpoint);
            return count == 0 ? EMPTY : NON_EMPTY;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    private JfrOldObjectEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    private static class JfrOldObjectEpochData {
        private static long nextId = 1;

        private int unflushedEntries;
        private JfrBuffer buffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrOldObjectEpochData() {
            this.unflushedEntries = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear(@SuppressWarnings("unused") boolean flushpoint) {
            /* Flushpoint/epoch change can use same code. */
            unflushedEntries = 0;
            JfrBufferAccess.reinitialize(buffer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            unflushedEntries = 0;
            JfrBufferAccess.free(buffer);
            buffer = WordFactory.nullPointer();
        }
    }
}
