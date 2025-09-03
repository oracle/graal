/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public final class ParserConstantPool extends ConstantPool {

    public ParserConstantPool(byte[] tags, int[] entries, Symbol<?>[] symbols, int majorVersion, int minorVersion) {
        super(tags, entries, symbols, majorVersion, minorVersion);
    }

    @Override
    @TruffleBoundary
    public RuntimeException classFormatError(String message) {
        throw new ParserException.ClassFormatError(message);
    }

    @Override
    public ParserConstantPool getParserConstantPool() {
        return this;
    }

    public ParserConstantPool patchForHiddenClass(int thisKlassIndex, Symbol<?> newName) {
        int newNameIndex = entries.length;
        int newSymbolIndex = symbols.length;

        byte[] newTags = Arrays.copyOf(tags, tags.length + 1);
        int[] newEntries = Arrays.copyOf(entries, entries.length + 1);
        Symbol<?>[] newSymbols = Arrays.copyOf(symbols, symbols.length + 1);

        // Append a new UTF8 constant.
        newSymbols[newSymbolIndex] = newName;
        newTags[newNameIndex] = CONSTANT_Utf8;
        newEntries[newNameIndex] = newSymbolIndex;

        // Patch hidden class name index.
        newEntries[thisKlassIndex] = newNameIndex;

        // This will get resolved in the ObjectKlass constructor
        // See initSelfReferenceInPool
        return new ParserConstantPool(newTags, newEntries, newSymbols, majorVersion, minorVersion);
    }

    /**
     * Creates a precise dump of the symbolic CP information, unlike {@link #toRawBytes()}, which
     * may not be precisely reversible. The {@link ParserConstantPool} can be de-serialized with
     * {@link #fromBytesForSerialization(byte[], Symbolify)}.
     */
    public byte[] toBytesForSerialization() {
        int byteSize = 0;
        byteSize += Integer.BYTES; // majorVersion
        byteSize += Integer.BYTES; // minorVersion
        byteSize += Integer.BYTES; // length
        assert tags.length == entries.length;
        byteSize += tags.length; // tags
        byteSize += entries.length * Integer.BYTES; // entries
        byteSize += Integer.BYTES; // symbols.length
        for (Symbol<?> symbol : symbols) {
            byteSize += Integer.BYTES; // sym.length
            byteSize += symbol.length(); // sym.bytes
        }
        byte[] bytes = new byte[byteSize];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.putInt(majorVersion);
        bb.putInt(minorVersion);
        bb.putInt(tags.length);
        bb.put(tags);
        for (int entry : entries) {
            bb.putInt(entry);
        }
        bb.putInt(symbols.length);
        for (Symbol<?> symbol : symbols) {
            bb.putInt(symbol.length());
            symbol.writeTo(bb);
        }
        assert !bb.hasRemaining();
        return bytes;
    }

    @FunctionalInterface
    public interface Symbolify<T extends ModifiedUTF8> extends Function<ByteSequence, Symbol<T>> {
        default Symbol<T> apply(String string) {
            return apply(ByteSequence.create(string));
        }
    }

    /**
     * Recovers a symbolic CP information, from bytes produces by {@link #toRawBytes()}.
     */
    public static ParserConstantPool fromBytesForSerialization(byte[] bytes, Symbolify<? extends ModifiedUTF8> symbolify) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        int majorVersion = bb.getInt();
        int minorVersion = bb.getInt();
        int length = bb.getInt();
        byte[] tags = new byte[length];
        bb.get(tags);
        int[] entries = new int[length];
        for (int i = 0; i < length; i++) {
            entries[i] = bb.getInt();
        }
        int symbolsLength = bb.getInt();
        Symbol<?>[] symbols = new Symbol<?>[symbolsLength];
        for (int i = 0; i < symbolsLength; i++) {
            int symbolLength = bb.getInt();
            byte[] symbolBytes = new byte[symbolLength];
            bb.get(symbolBytes);
            symbols[i] = symbolify.apply(ByteSequence.wrap(symbolBytes));
        }
        assert !bb.hasRemaining();
        return new ParserConstantPool(tags, entries, symbols, majorVersion, minorVersion);
    }
}
