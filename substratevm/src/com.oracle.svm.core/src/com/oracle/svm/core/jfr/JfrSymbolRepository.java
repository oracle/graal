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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.collections.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.collections.UninterruptibleEntry;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jdk.UninterruptibleUtils.CharReplacer;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.word.Word;

/**
 * In Native Image, we use {@link java.lang.String} objects that live in the image heap as symbols.
 */
public class JfrSymbolRepository implements JfrRepository {
    private final VMMutex mutex;
    private final JfrSymbolEpochData epochData0;
    private final JfrSymbolEpochData epochData1;
    private final CharReplacer dotWithSlash;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrSymbolRepository() {
        this.mutex = new VMMutex("jfrSymbolRepository");
        this.epochData0 = new JfrSymbolEpochData();
        this.epochData1 = new JfrSymbolEpochData();
        this.dotWithSlash = new ReplaceDotWithSlash();
    }

    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    public long getSymbolId(String imageHeapString, boolean previousEpoch) {
        return getSymbolId(imageHeapString, previousEpoch, false);
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    public long getSymbolId(String imageHeapString, boolean previousEpoch, boolean replaceDotWithSlash) {
        if (imageHeapString == null) {
            return 0;
        }

        assert Heap.getHeap().isInImageHeap(imageHeapString);

        JfrSymbol symbol = StackValue.get(JfrSymbol.class);
        symbol.setValue(imageHeapString);
        symbol.setReplaceDotWithSlash(replaceDotWithSlash);

        long rawPointerValue = Word.objectToUntrackedPointer(imageHeapString).rawValue();
        symbol.setHash(UninterruptibleUtils.Long.hashCode(rawPointerValue));

        /*
         * Get an existing entry from the hashtable or insert a new entry. This needs to be atomic
         * to avoid races as this method can be executed by multiple threads concurrently. For every
         * inserted entry, a unique id is generated that is then used as the JFR trace id.
         */
        mutex.lockNoTransition();
        try {
            JfrSymbolEpochData epochData = getEpochData(previousEpoch);
            JfrSymbol existingEntry = (JfrSymbol) epochData.table.get(symbol);
            if (existingEntry.isNonNull()) {
                return existingEntry.getId();
            }

            JfrSymbol newEntry = (JfrSymbol) epochData.table.putNew(symbol);
            if (newEntry.isNull()) {
                return 0L;
            }

            /* New entry, so serialize it to the buffer. */
            if (epochData.buffer.isNull()) {
                epochData.buffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
            }

            CharReplacer charReplacer = newEntry.getReplaceDotWithSlash() ? dotWithSlash : null;
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.buffer);

            JfrNativeEventWriter.putLong(data, newEntry.getId());
            JfrNativeEventWriter.putString(data, newEntry.getValue(), charReplacer);
            if (!JfrNativeEventWriter.commit(data)) {
                return 0L;
            }

            epochData.unflushedEntries++;
            /* The buffer may have been replaced with a new one. */
            epochData.buffer = data.getJfrBuffer();
            return newEntry.getId();
        } finally {
            mutex.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        mutex.lockNoTransition();
        try {
            JfrSymbolEpochData epochData = getEpochData(!flushpoint);
            int count = epochData.unflushedEntries;
            if (count != 0) {
                writer.writeCompressedLong(JfrType.Symbol.getId());
                writer.writeCompressedLong(count);
                writer.write(epochData.buffer);
            }
            epochData.clear(flushpoint);
            return count == 0 ? EMPTY : NON_EMPTY;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    private JfrSymbolEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
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
        private static long nextId;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrSymbolHashtable() {
            super(NmtCategory.JFR);
        }

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
            if (result.isNonNull()) {
                result.setId(++nextId);
            }
            return result;
        }
    }

    private static class JfrSymbolEpochData {
        private final JfrSymbolHashtable table;
        private int unflushedEntries;
        private JfrBuffer buffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrSymbolEpochData() {
            this.table = new JfrSymbolHashtable();
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

        void teardown() {
            table.teardown();
            unflushedEntries = 0;
            JfrBufferAccess.free(buffer);
            buffer = WordFactory.nullPointer();
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
