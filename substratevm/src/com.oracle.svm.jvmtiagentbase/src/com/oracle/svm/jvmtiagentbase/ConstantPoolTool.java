/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jvmtiagentbase;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal data extractor for the Java constant pool. See Java Virtual Machine Specification 4.4.
 */
public class ConstantPoolTool {
    private static final int INVALID_LENGTH = -1;

    @SuppressWarnings("unused")
    enum ConstantKind {
        // Keep this order: ordinals must match constant pool tag values.
        UNUSED_0(INVALID_LENGTH),
        UTF8(INVALID_LENGTH), // variable length
        UNUSED_2(INVALID_LENGTH),
        INTEGER(4),
        FLOAT(4),
        LONG(8, 2), // double-entry constant
        DOUBLE(8, 2), // double-entry constant
        CLASS(2),
        STRING(2),
        FIELDREF(4),
        METHODREF(4),
        INTERFACEMETHODREF(4),
        NAMEANDTYPE(4),
        UNUSED_13(INVALID_LENGTH),
        UNUSED_14(INVALID_LENGTH),
        METHODHANDLE(3),
        METHODTYPE(2),
        DYNAMIC(4),
        INVOKEDYNAMIC(4),
        MODULE(2),
        PACKAGE(2);

        static final ConstantKind[] VALUES = values();

        final int lengthWithoutTag;
        final int tableEntries;

        ConstantKind(int lengthWithoutTag) {
            this(lengthWithoutTag, 1);
        }

        ConstantKind(int lengthWithoutTag, int tableEntries) {
            this.lengthWithoutTag = lengthWithoutTag;
            this.tableEntries = tableEntries;
        }
    }

    public static class MethodReference {
        public final CharSequence name;
        public final CharSequence descriptor;

        MethodReference(CharSequence name, CharSequence descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private final ByteBuffer buffer;

    private int cachedIndex = 1;
    private int cachedIndexOffset = 0;

    public ConstantPoolTool(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public MethodReference readMethodReference(int cpi) {
        try {
            seekEntryPastTag(cpi, ConstantKind.METHODREF);
            buffer.getShort(); // class: not needed at the moment
            int nameAndTypeIndex = Short.toUnsignedInt(buffer.getShort());

            seekEntryPastTag(nameAndTypeIndex, ConstantKind.NAMEANDTYPE);
            int nameIndex = Short.toUnsignedInt(buffer.getShort());
            int descriptorIndex = Short.toUnsignedInt(buffer.getShort());

            CharSequence name = readUTF(nameIndex);
            CharSequence descriptor = readUTF(descriptorIndex);
            return new MethodReference(name, descriptor);
        } catch (BufferUnderflowException | IllegalArgumentException | CharacterCodingException e) {
            throw new ConstantPoolException("Malformed constant pool", e);
        }
    }

    private void seekEntryPastTag(int cpi, ConstantKind expectedKind) {
        seekEntry(cpi);
        int tag = Byte.toUnsignedInt(buffer.get());
        if (tag != expectedKind.ordinal()) {
            throw new ConstantPoolException("Expected tag " + expectedKind.ordinal());
        }
    }

    private CharSequence readUTF(int cpi) throws CharacterCodingException {
        seekEntryPastTag(cpi, ConstantKind.UTF8);
        int length = Short.toUnsignedInt(buffer.getShort());
        int previousLimit = buffer.limit();
        buffer.limit(buffer.position() + length);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(buffer);
        } finally {
            buffer.limit(previousLimit);
        }
    }

    private void seekEntry(int cpi) {
        boolean resumeAtCachedIndex = (cpi >= cachedIndex);
        int index = resumeAtCachedIndex ? cachedIndex : 1;
        buffer.position(resumeAtCachedIndex ? cachedIndexOffset : 0);
        while (index < cpi) {
            int tag = Byte.toUnsignedInt(buffer.get());
            if (tag >= ConstantKind.VALUES.length) {
                throw new ConstantPoolException("Invalid constant pool entry tag: " + tag);
            }
            ConstantKind kind = ConstantKind.VALUES[tag];
            int length = kind.lengthWithoutTag;
            if (kind == ConstantKind.UTF8) {
                length = Short.toUnsignedInt(buffer.getShort()); // in bytes; advances buffer
            }
            if (length <= 0 || kind.tableEntries <= 0) {
                throw new ConstantPoolException("Invalid constant pool entry kind: " + kind);
            }
            buffer.position(buffer.position() + length);
            index += kind.tableEntries;
        }
        if (index != cpi) {
            throw new ConstantPoolException("Constant pool index is not valid or unusable: " + cpi);
        }
        cachedIndex = cpi;
        cachedIndexOffset = buffer.position();
    }

    @SuppressWarnings("serial")
    public static final class ConstantPoolException extends RuntimeException {
        ConstantPoolException(String message) {
            super(message);
        }

        ConstantPoolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
