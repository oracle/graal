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
import java.nio.charset.StandardCharsets;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.thread.VMOperation;

/**
 * In Native Image, we use {@link java.lang.String} objects that live in the image heap as symbols.
 */
public class JfrSymbolRepository implements JfrRepository {
    private final JfrSymbolHashtable table;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrSymbolRepository() {
        table = new JfrSymbolHashtable();
    }

    public void teardown() {
        table.teardown();
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getSymbolId(Class<?> clazz) {
        return getSymbolId(clazz.getName(), true);
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getSymbolId(String imageHeapString) {
        return getSymbolId(imageHeapString, false);
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private long getSymbolId(String imageHeapString, boolean replaceDotWithSlash) {
        assert Heap.getHeap().isInImageHeap(imageHeapString);

        JfrSymbol symbol = StackValue.get(JfrSymbol.class);
        symbol.setValue(imageHeapString);
        symbol.setReplaceDotWithSlash(replaceDotWithSlash);

        long rawPointerValue = Word.objectToUntrackedPointer(imageHeapString).rawValue();
        int hashcode = (int) (rawPointerValue ^ (rawPointerValue >>> 32));
        symbol.setHash(hashcode);

        return table.add(symbol);
    }

    @Override
    public void write(JfrChunkWriter writer) throws IOException {
        // TODO: Epoch-based constant pool writing should allow for
        // writing outside of a safepoint. However symbols can be
        // marked in use directly by native events so the symbol
        // repository probably needs to be epoch based as well
        assert VMOperation.isInProgressAtSafepoint();
        writer.writeCompressedLong(JfrTypes.Symbol.getId());
        writer.writeCompressedLong(table.getSize());

        JfrSymbol[] entries = table.getTable();
        for (int i = 0; i < entries.length; i++) {
            JfrSymbol entry = entries[i];
            if (entry.isNonNull()) {
                while (entry.isNonNull()) {
                    writeSymbol(writer, entry);
                    entry = entry.getNext();
                }
            }
        }
    }

    private void writeSymbol(JfrChunkWriter writer, JfrSymbol symbol) throws IOException {
        writer.writeCompressedLong(symbol.getId());
        writer.writeByte(JfrChunkWriter.TYPE_UTF8);
        byte[] value = symbol.getValue().getBytes(StandardCharsets.UTF_8);
        if (symbol.getReplaceDotWithSlash()) {
            replaceDotWithSlash(value);
        }
        writer.writeCompressedInt(value.length);
        writer.writeBytes(value);
    }

    private void replaceDotWithSlash(byte[] utf8String) {
        for (int i = 0; i < utf8String.length; i++) {
            // '.' is 46 and '/' is 47 for StandardCharsets.UTF_8
            if (utf8String[i] == 46) {
                utf8String[i] = 47;
            }
        }
    }

    @Override
    public boolean hasItems() {
        return table.getSize() > 0;
    }

    @RawStructure
    private interface JfrSymbol extends UninterruptibleEntry<JfrSymbol> {
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

    private static class JfrSymbolHashtable extends UninterruptibleHashtable<JfrSymbol> {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected JfrSymbol[] createTable(int size) {
            return new JfrSymbol[size];
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected void free(JfrSymbol t) {
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(t);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected boolean isEqual(JfrSymbol a, JfrSymbol b) {
            return a.getValue() == b.getValue() && a.getReplaceDotWithSlash() == b.getReplaceDotWithSlash();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @Override
        protected JfrSymbol copyToHeap(JfrSymbol symbolOnStack) {
            UnsignedWord size = SizeOf.unsigned(JfrSymbol.class);
            JfrSymbol symbolOnHeap = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(size);
            if (symbolOnHeap.isNonNull()) {
                MemoryUtil.copyConjointMemoryAtomic((Pointer) symbolOnStack, (Pointer) symbolOnHeap, size);
                return symbolOnHeap;
            }
            return WordFactory.nullPointer();
        }
    }
}
