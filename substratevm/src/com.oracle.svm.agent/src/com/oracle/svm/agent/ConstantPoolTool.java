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
package com.oracle.svm.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Minimal data extractor for the Java constant pool. */
class ConstantPoolTool {
    static final int TAG_UTF8 = 1;
    static final int TAG_METHODREF = 10;
    static final int TAG_NAMEANDTYPE = 12;

    static class MethodReference {
        final CharSequence name;
        final CharSequence descriptor;

        MethodReference(CharSequence name, CharSequence descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private final ByteBuffer buffer;

    private int cachedIndex = 1;
    private int cachedIndexOffset = 0;

    ConstantPoolTool(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    MethodReference readMethodReference(int cpi) {
        seekEntryPastTag(cpi, TAG_METHODREF);
        buffer.getShort(); // class: not needed at the moment
        int nameAndTypeIndex = Short.toUnsignedInt(buffer.getShort());

        seekEntryPastTag(nameAndTypeIndex, TAG_NAMEANDTYPE);
        int nameIndex = Short.toUnsignedInt(buffer.getShort());
        int descriptorIndex = Short.toUnsignedInt(buffer.getShort());

        CharSequence name = readUTF(nameIndex);
        CharSequence descriptor = readUTF(descriptorIndex);
        return new MethodReference(name, descriptor);
    }

    private void seekEntryPastTag(int cpi, int expectedTag) {
        seekEntry(cpi);
        int tag = Byte.toUnsignedInt(buffer.get());
        assert tag == expectedTag;
    }

    private CharSequence readUTF(int cpi) {
        seekEntryPastTag(cpi, TAG_UTF8);
        int length = Short.toUnsignedInt(buffer.getShort());
        int previousLimit = buffer.limit();
        buffer.limit(buffer.position() + length);
        try {
            return StandardCharsets.UTF_8.decode(buffer);
        } finally {
            buffer.limit(previousLimit);
        }
    }

    private void seekEntry(int cpi) {
        boolean resumeAtCachedIndex = (cpi >= cachedIndex);
        int i = resumeAtCachedIndex ? cachedIndex : 1;
        buffer.position(resumeAtCachedIndex ? cachedIndexOffset : 0);
        // See Java Virtual Machine Specification section 4.4
        while (i < cpi) {
            int length;
            int tag = Byte.toUnsignedInt(buffer.get());
            if (tag == TAG_UTF8) {
                length = Short.toUnsignedInt(buffer.getShort()); // in bytes; consumes two bytes
            } else if (tag == 7 || tag == 8 || tag == 16 || tag == 19 || tag == 20) {
                length = 2;
            } else if (tag == 15) {
                length = 3;
            } else if (tag == 3 || tag == 4 || tag == 9 || tag == TAG_METHODREF || tag == 11 || tag == TAG_NAMEANDTYPE || tag == 17 || tag == 18) {
                length = 4;
            } else if (tag == 5 || tag == 6) {
                length = 8;
                i++; // takes two constant pool entries
            } else {
                return;
            }
            buffer.position(buffer.position() + length);
            i++;
        }
        if (i != cpi) {
            throw new IllegalArgumentException("Constant pool index is not valid or unusable: " + cpi);
        }
        cachedIndex = cpi;
        cachedIndexOffset = buffer.position();
    }
}
