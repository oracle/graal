/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.util.VMError.shouldNotReachHereUnexpectedInput;

import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.shared.Uninterruptible;

class GCCExceptionTable {

    static final long NO_HANDLER = 0;

    private static final int ULEB128 = 0x1;
    private static final int UDATA4 = 0x3;

    @Uninterruptible(reason = "Called from the LLVM personality function while unwinding exceptions.")
    static long getHandlerInfo(Pointer buffer, long pcOffset) {

        CIntPointer offset = UnsafeStackValue.get(Integer.BYTES);
        offset.write(0);

        int header = buffer.readByte(offset.read()) & 0xff;
        offset.write(offset.read() + Byte.BYTES);
        assert header == 255;

        buffer.readByte(offset.read());
        offset.write(offset.read() + Byte.BYTES);

        long typeBaseOffset = getULSB(buffer, offset);
        long typeEnd = typeBaseOffset + offset.read();
        assert typeEnd >= typeBaseOffset;

        int siteEncodingEncoding = buffer.readByte(offset.read()) & 0xff;
        offset.write(offset.read() + Byte.BYTES);

        long siteTableLength = getULSB(buffer, offset);

        long siteTableEnd = offset.read() + siteTableLength;
        while (offset.read() < siteTableEnd) {

            long startOffset = get(buffer, siteEncodingEncoding, offset);
            long size = get(buffer, siteEncodingEncoding, offset);
            long handlerOffset = get(buffer, siteEncodingEncoding, offset);

            long action = getULSB(buffer, offset);

            if (startOffset <= pcOffset && pcOffset < startOffset + size) {
                if (handlerOffset == 0) {
                    return NO_HANDLER;
                }
                return encodeHandlerInfo(handlerOffset, action);
            }

            assert offset.read() <= siteTableEnd;
        }

        return NO_HANDLER;
    }

    @Uninterruptible(reason = "Called from the LLVM personality function while unwinding exceptions.")
    static long getHandlerOffset(long handlerInfo) {
        return handlerInfo >>> 1;
    }

    @Uninterruptible(reason = "Called from the LLVM personality function while unwinding exceptions.")
    static boolean isCleanup(long handlerInfo) {
        return (handlerInfo & 1) == 0;
    }

    @Uninterruptible(reason = "Called from the LLVM personality function while unwinding exceptions.")
    private static long encodeHandlerInfo(long handlerOffset, long action) {
        return (handlerOffset << 1) | (action != 0 ? 1 : 0);
    }

    @Uninterruptible(reason = "Called from the LLVM personality function while unwinding exceptions.")
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

    @Uninterruptible(reason = "Called from the LLVM personality function while unwinding exceptions.")
    private static long get(Pointer buffer, int encoding, CIntPointer offset) {
        switch (encoding) {
            case ULEB128:
                return getULSB(buffer, offset);
            case UDATA4:
                int result = buffer.readInt(offset.read());
                offset.write(offset.read() + Integer.BYTES);
                return result;
            default:
                throw shouldNotReachHereUnexpectedInput(encoding); // ExcludeFromJacocoGeneratedReport
        }
    }
}
