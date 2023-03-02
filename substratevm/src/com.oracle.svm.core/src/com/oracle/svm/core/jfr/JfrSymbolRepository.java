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

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import com.oracle.svm.core.jdk.UninterruptibleUtils.CharReplacer;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.locks.VMMutex;

/**
 * In Native Image, we use {@link java.lang.String} objects that live in the image heap as symbols.
 */
public class JfrSymbolRepository implements JfrConstantPool {
    private final VMMutex mutex;
    private final CharReplacer dotWithSlash;
    private final JfrSymbolEpochData epochData0;
    private final JfrSymbolEpochData epochData1;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrSymbolRepository() {
        this.mutex = new VMMutex("jfrSymbolRepository");
        this.dotWithSlash = new ReplaceDotWithSlash();
        this.epochData0 = new JfrSymbolEpochData();
        this.epochData1 = new JfrSymbolEpochData();
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

        /*
         * Get an existing entry from the hashtable or insert a new entry. This needs to be atomic
         * to avoid races as this method can be executed by multiple threads concurrently. For every
         * inserted entry, a unique id is generated that is then used as the JFR trace id.
         */
        mutex.lockNoTransition();
        try {
            JfrSymbolEpochData epochData = getEpochData(previousEpoch);
            JfrSymbol existingEntry = epochData.table.get(symbol);
            if (existingEntry.isNonNull()) {
                return existingEntry.getId();
            }

            JfrSymbol newEntry = epochData.table.putNew(symbol);
            if (newEntry.isNull()) {
                return 0L;
            }

            /* We have a new symbol, so serialize it to the buffer. */
            epochData.unflushedSymbolCount++;

            if (epochData.symbolBuffer.isNull()) {
                epochData.symbolBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
            }

            CharReplacer charReplacer = replaceDotWithSlash ? dotWithSlash : null;
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.symbolBuffer);

            JfrNativeEventWriter.putLong(data, newEntry.getId());
            JfrNativeEventWriter.putByte(data, JfrChunkWriter.StringEncoding.UTF8_BYTE_ARRAY.byteValue);
            JfrNativeEventWriter.putString(data, imageHeapString, charReplacer);
            JfrNativeEventWriter.commit(data);

            /* The buffer may have been replaced with a new one. */
            epochData.symbolBuffer = data.getJfrBuffer();

            return newEntry.getId();
        } finally {
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

            epochData.clear(flush);
            return NON_EMPTY;
        } finally {
            maybeUnlock(flush);
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

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public JfrSymbol get(UninterruptibleEntry valueOnStack) {
            return (JfrSymbol) super.get(valueOnStack);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public JfrSymbol putNew(UninterruptibleEntry valueOnStack) {
            return (JfrSymbol) super.putNew(valueOnStack);
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
        private final JfrSymbolHashtable table;
        private JfrBuffer symbolBuffer;
        private int unflushedSymbolCount;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrSymbolEpochData() {
            this.table = new JfrSymbolHashtable();
            this.unflushedSymbolCount = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear(boolean flush) {
            if (symbolBuffer.isNonNull()) {
                JfrBufferAccess.reinitialize(symbolBuffer);
            }
            if (!flush) {
                /* The IDs must be stable for the whole epoch, so only clear after epoch change. */
                table.clear();
            }
            unflushedSymbolCount = 0;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            JfrBufferAccess.free(symbolBuffer);
            symbolBuffer = WordFactory.nullPointer();
            table.teardown();
        }
    }

    private static class ReplaceDotWithSlash implements CharReplacer {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public char replace(char ch) {
            if (ch == '.') {
                return '/';
            }
            return ch;
        }
    }
}
