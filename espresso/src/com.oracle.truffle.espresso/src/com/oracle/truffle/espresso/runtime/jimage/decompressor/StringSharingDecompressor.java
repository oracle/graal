/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime.jimage.decompressor;

import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Class;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Double;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Fieldref;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Float;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Integer;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_InterfaceMethodref;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_InvokeDynamic;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Long;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_MethodHandle;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_MethodType;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Methodref;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Module;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_NameAndType;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Package;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_String;
import static com.oracle.truffle.espresso.classfile.ConstantPool.CONSTANT_Utf8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.espresso.runtime.jimage.BasicImageReader;

/**
 * A Decompressor that reconstructs the constant pool of classes.
 */
public class StringSharingDecompressor implements ResourceDecompressor {
    public static final String NAME = "compact-cp";

    public static final int EXTERNALIZED_STRING = 23;
    public static final int EXTERNALIZED_STRING_DESCRIPTOR = 25;

    private static void transfert(ByteBuffer from, ByteBuffer to, int len) {
        // in 16, use java.nio.ByteBuffer#put(int, java.nio.ByteBuffer, int, int)
        ByteBuffer dup = from.duplicate();
        int newFromPosition = from.position() + len;
        dup.limit(newFromPosition);
        to.put(dup);
        from.position(newFromPosition);
    }

    private static void transfert(ByteBuffer from, int fromPosition, ByteBuffer to, int len) {
        // in 16, use java.nio.ByteBuffer#put(int, java.nio.ByteBuffer, int, int)
        ByteBuffer dup = from.duplicate();
        dup.position(fromPosition);
        dup.limit(dup.position() + len);
        to.put(dup);
    }

    public static ByteBuffer normalize(StringsProvider provider, ByteBuffer input, int originalSize) {
        ByteBuffer output = ByteBuffer.allocate(originalSize).order(ByteOrder.BIG_ENDIAN);
        ByteOrder originalOrder = input.order();
        input.order(ByteOrder.BIG_ENDIAN);
        try {
            output.putLong(input.getLong());  // magic/4, minor/2, major/2
            char count = input.getChar();
            output.putChar(count);
            int i = 1;
            while (i < count) {
                byte tag = input.get();
                switch (tag) {
                    case CONSTANT_Utf8: {
                        output.put(tag);
                        char len = input.getChar();
                        output.putChar(len);
                        transfert(input, output, len);
                        break;
                    }
                    case EXTERNALIZED_STRING: {
                        int index = CompressedIndexes.readInt(input);
                        ByteBuffer orig = provider.getRawString(index);
                        output.put(CONSTANT_Utf8);
                        output.putChar((char) orig.remaining());
                        output.put(orig);
                        break;
                    }
                    case EXTERNALIZED_STRING_DESCRIPTOR: {
                        output.put(CONSTANT_Utf8);
                        int lengthMark = output.position();
                        output.position(lengthMark + 2);
                        reconstruct(provider, input, output);
                        int length = output.position() - lengthMark - 2;
                        output.putChar(lengthMark, (char) length);
                        break;
                    }
                    case CONSTANT_Long:
                    case CONSTANT_Double: {
                        i++;
                        output.put(tag);
                        output.putLong(input.getLong());
                        break;
                    }
                    case CONSTANT_Integer:
                    case CONSTANT_Float:
                    case CONSTANT_Fieldref:
                    case CONSTANT_Methodref:
                    case CONSTANT_InterfaceMethodref:
                    case CONSTANT_NameAndType:
                    case CONSTANT_InvokeDynamic: {
                        output.put(tag);
                        output.putInt(input.getInt());
                        break;
                    }
                    case CONSTANT_MethodHandle: {
                        output.put(tag);
                        output.put(input.get());
                        output.put(input.get());
                        output.put(input.get());
                        break;
                    }
                    case CONSTANT_Class:
                    case CONSTANT_String:
                    case CONSTANT_MethodType:
                    case CONSTANT_Module:
                    case CONSTANT_Package: {
                        output.put(tag);
                        output.putShort(input.getShort());
                        break;
                    }
                    default: {
                        throw new RuntimeException("Unexpected tag: " + (tag & 0xff));
                    }
                }
                i++;
            }
        } finally {
            input.order(originalOrder);
        }
        output.order(originalOrder);
        output.put(input);
        if (output.hasRemaining()) {
            BasicImageReader.LOGGER.warning("StringSharingDecompressor output was smaller than expected: " + output.remaining() + "bytes of output remaining");
        }
        return output.flip();
    }

    private static void reconstruct(StringsProvider reader, ByteBuffer input, ByteBuffer output) {
        ByteBuffer desc = reader.getRawString(CompressedIndexes.readInt(input));
        int indiciesLimit = CompressedIndexes.readInt(input) + input.position();
        if (indiciesLimit > input.limit()) {
            throw new RuntimeException("Indices larger than input");
        }
        int runStart = desc.position(); // consecutive run of non-L descriptor chars
        for (int i = desc.position(); i < desc.limit(); i++) {
            byte c = desc.get(i);
            if (c == 'L') {
                // copy previous run
                // the current 'L' is copied below explicitly
                int runLength = i - runStart;
                if (runLength > 0) {
                    transfert(desc, runStart, output, runLength);
                }
                runStart = i + 1;
                // unpack type
                ByteBuffer pkg = reader.getRawString(CompressedIndexes.readInt(input));
                ByteBuffer clazz = reader.getRawString(CompressedIndexes.readInt(input));
                if (input.position() > indiciesLimit) {
                    throw new RuntimeException("Missing indicies");
                }
                // 'L' (pkg '/')? clazz
                output.put((byte) 'L');
                if (pkg.hasRemaining()) {
                    output.put(pkg);
                    output.put((byte) '/');
                }
                output.put(clazz);
            }
        }
        if (runStart < desc.limit()) {
            // copy last run
            int runLength = desc.limit() - runStart;
            transfert(desc, runStart, output, runLength);
        }
        if (input.position() < indiciesLimit) {
            BasicImageReader.LOGGER.warning("StringSharingDecompressor: " + (indiciesLimit - input.position()) + " indicies bytes remain unused after reconstructing descriptor");
            input.position(indiciesLimit);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ByteBuffer decompress(StringsProvider reader, ByteBuffer content, long originalSize) {
        return normalize(reader, content, Math.toIntExact(originalSize));
    }
}
