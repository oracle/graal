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
package com.oracle.svm.core.jfr;

import com.oracle.svm.core.jdk.UninterruptibleUtils;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;
import org.graalvm.word.Pointer;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.locks.VMMutex;

/**
 * In Native Image, we use {@link java.lang.String} objects that live in the image heap as symbols.
 */
public class JfrSymbolRepository implements JfrConstantPool {
    private final VMMutex mutex;
    private final JfrSymbolEpochData epochData0;
    private final JfrSymbolEpochData epochData1;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrSymbolRepository() {
        this.epochData0 = new JfrSymbolEpochData();
        this.epochData1 = new JfrSymbolEpochData();
        mutex = new VMMutex("jfrSymbolRepository");
    }

    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @Uninterruptible(reason = "Called by uninterruptible code.")
    private JfrSymbolEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getSymbolId(String imageHeapString, boolean previousEpoch) {
        return getSymbolId(imageHeapString, previousEpoch, false);
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getSymbolId(String imageHeapString, boolean previousEpoch, boolean replaceDotWithSlash) {
        if (imageHeapString == null) {
            return 0;
        }

        assert Heap.getHeap().isInImageHeap(imageHeapString);

        JfrSymbol symbol = StackValue.get(JfrSymbol.class);
        symbol.setValue(imageHeapString);
        symbol.setReplaceDotWithSlash(replaceDotWithSlash);

        long rawPointerValue = Word.objectToUntrackedPointer(imageHeapString).rawValue();
        int hashcode = (int) (rawPointerValue ^ (rawPointerValue >>> 32));
        symbol.setHash(hashcode);

        mutex.lockNoTransition();
        try {
            /*
             * Get an existing entry from the hashtable or insert a new entry. This needs to be
             * atomic to avoid races as this method can be executed by multiple threads
             * concurrently. For every inserted entry, a unique id is generated that is then used as
             * the JFR trace id.
             */
            JfrSymbolEpochData epochData = getEpochData(previousEpoch);
            boolean visited = false;
            if (!epochData.table.putIfAbsent(symbol)) {
                visited = true;
            }
            JfrSymbol entry = (JfrSymbol) epochData.table.get(symbol);
            if (entry.isNull()) {
                return 0;
            }
            if (!visited) {

                epochData.unflushedSymbolCount++;

                if (epochData.symbolBuffer.isNull()) {
                    // This will happen only on the first call.
                    epochData.symbolBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
                }
                JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
                JfrNativeEventWriterDataAccess.initialize(data, epochData.symbolBuffer);

                JfrNativeEventWriter.putLong(data, entry.getId());
                JfrNativeEventWriter.putByte(data, JfrChunkWriter.StringEncoding.UTF8_BYTE_ARRAY.byteValue);
                JfrNativeEventWriter.putInt(data, UninterruptibleUtils.String.modifiedUTF8Length(entry.getValue(), false));

                Pointer newPosition = UninterruptibleUtils.String.toModifiedUTF8(entry.getValue(),
                                data.getCurrentPos(), data.getEndPos(), false, entry.getReplaceDotWithSlash());
                data.setCurrentPos(newPosition);

                JfrNativeEventWriter.commit(data);

                /* The buffer may have been replaced with a new one. */
                epochData.symbolBuffer = data.getJfrBuffer();
            }
            return entry.getId();
        } finally {
            mutex.unlock();
        }
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
            JfrSymbolEpochData epochData = getEpochData(!flush);
            int numberOfSymbols = epochData.unflushedSymbolCount;
            if (numberOfSymbols == 0) {
                return EMPTY;
            }
            writer.writeCompressedLong(JfrType.Symbol.getId());
            writer.writeCompressedLong(numberOfSymbols);
            writer.write(epochData.symbolBuffer);
            JfrBufferAccess.reinitialize(epochData.symbolBuffer);
            epochData.unflushedSymbolCount = 0;

            if (!flush) {
                // Should be cleared only after epoch change
                epochData.clear();
            }
            return NON_EMPTY;
        } finally {
            maybeUnlock(flush);
        }
    }

    @RawStructure
    private interface JfrSymbol extends UninterruptibleEntry {
        @RawField
        long getId();

        @RawField
        void setId(long value);

        @PinnedObjectField
        @RawField
        String getValue();

        @PinnedObjectField
        @RawField
        void setValue(String value);

        @RawField
        boolean getReplaceDotWithSlash();

        @RawField
        void setReplaceDotWithSlash(boolean value);
    }

    private static class JfrSymbolHashtable extends AbstractUninterruptibleHashtable {
        private long nextId;

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected JfrSymbol[] createTable(int size) {
            return new JfrSymbol[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public JfrSymbol[] getTable() {
            return (JfrSymbol[]) super.getTable();
        }

        @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "image heap pointer comparison")
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            JfrSymbol a = (JfrSymbol) v0;
            JfrSymbol b = (JfrSymbol) v1;
            return a.getValue() == b.getValue() && a.getReplaceDotWithSlash() == b.getReplaceDotWithSlash();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry symbolOnStack) {
            JfrSymbol result = (JfrSymbol) copyToHeap(symbolOnStack, SizeOf.unsigned(JfrSymbol.class));
            result.setId(++nextId);
            return result;
        }
    }

    private static class JfrSymbolEpochData {
        private JfrBuffer symbolBuffer;
        private final JfrSymbolHashtable table;
        private int unflushedSymbolCount;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrSymbolEpochData() {
            table = new JfrSymbolHashtable();
            this.unflushedSymbolCount = 0;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            if (symbolBuffer.isNonNull()) {
                JfrBufferAccess.free(symbolBuffer);
            }
            symbolBuffer = WordFactory.nullPointer();
            table.teardown();
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear() {
            if (symbolBuffer.isNonNull()) {
                JfrBufferAccess.reinitialize(symbolBuffer);
            }
            table.clear();
            unflushedSymbolCount = 0;
        }

    }
}
