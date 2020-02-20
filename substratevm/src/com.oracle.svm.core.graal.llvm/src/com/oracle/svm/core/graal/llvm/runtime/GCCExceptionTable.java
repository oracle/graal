/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.runtime;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.Arrays;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.log.Log;

class GCCExceptionTable {

    enum Encoding {
        ULEB128((byte) 0x1),
        UDATA2((byte) 0x2),
        UDATA4((byte) 0x3),
        UDATA8((byte) 0x4),
        SLEB128((byte) 0x9),
        SDATA2((byte) 0xA),
        SDATA4((byte) 0xB),
        SDATA8((byte) 0xC);

        byte encoding;
        static Encoding[] lookupTable = new Encoding[16];

        static {
            Arrays.stream(values()).forEach(value -> lookupTable[value.encoding] = value);
        }

        Encoding(byte encoding) {
            this.encoding = encoding;
        }

        static Encoding parse(int encodingEncoding) {
            if (encodingEncoding < 0 || encodingEncoding >= lookupTable.length) {
                return null;
            }
            return lookupTable[encodingEncoding];
        }
    }

    static Long getHandlerOffset(Pointer buffer, long pcOffset) {
        Log log = Log.noopLog();

        CIntPointer offset = StackValue.get(Integer.BYTES);
        offset.write(0);

        int header = Byte.toUnsignedInt(buffer.readByte(offset.read()));
        offset.write(offset.read() + Byte.BYTES);
        log.string("header: ").hex(header).newline();
        assert header == 255;

        int typeEncodingEncoding = Byte.toUnsignedInt(buffer.readByte(offset.read()));
        offset.write(offset.read() + Byte.BYTES);
        log.string("typeEncodingEncoding: ").hex(typeEncodingEncoding).newline();

        long typeBaseOffset = getULSB(buffer, offset);
        long typeEnd = typeBaseOffset + offset.read();
        log.string("typeBaseOffset: ").unsigned(typeBaseOffset).string(", typeEnd: ").unsigned(typeEnd).newline();

        int siteEncodingEncoding = Byte.toUnsignedInt(buffer.readByte(offset.read()));
        offset.write(offset.read() + Byte.BYTES);
        log.string("siteEncodingEncoding: ").hex(siteEncodingEncoding).newline();
        Encoding siteEncoding = Encoding.parse(siteEncodingEncoding);

        long siteTableLength = getULSB(buffer, offset);
        log.string("siteTableLength: ").signed(siteTableLength).newline();

        long siteTableEnd = offset.read() + siteTableLength;
        log.string("siteTableEnd: ").signed(siteTableEnd).newline();
        while (offset.read() < siteTableEnd) {

            long startOffset = get(buffer, siteEncoding, offset);
            long size = get(buffer, siteEncoding, offset);
            long handlerOffset = get(buffer, siteEncoding, offset);
            log.string("start: ").unsigned(startOffset).string(", size: ").unsigned(size).string(", handlerOffset: ").unsigned(handlerOffset).newline();

            if (startOffset <= pcOffset && startOffset + size >= pcOffset) {
                return handlerOffset;
            }
            int action = Byte.toUnsignedInt(buffer.readByte(offset.read()));
            offset.write(offset.read() + Byte.BYTES);
            log.string("action: ").unsigned(action).newline();
            assert action == 0 || action == 1;

            assert offset.read() <= siteTableEnd;
        }

        return null;
    }

    private static long getULSB(Pointer buffer, CIntPointer offset) {
        int read;
        long result = 0;
        int shift = 0;
        do {
            read = buffer.readByte(offset.read());
            offset.write(offset.read() + Byte.BYTES);
            result |= (read & Byte.MAX_VALUE) << shift;
            shift += 7;
        } while ((read & 0x80) != 0);

        return result;
    }

    private static long get(Pointer buffer, Encoding encoding, CIntPointer offset) {
        switch (encoding) {
            case ULEB128:
                return getULSB(buffer, offset);
            case UDATA4:
                int result = buffer.readInt(offset.read());
                offset.write(offset.read() + Integer.BYTES);
                return result;
            default:
                throw shouldNotReachHere();
        }
    }
}
