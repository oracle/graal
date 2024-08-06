/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util.coder;

import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;

/** Read Pack200 encoded values from a {@link ByteStream} or a raw {@link Pointer}. */
public class Pack200Coder {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long readUV(Pointer data) {
        ByteStream stream = StackValue.get(ByteStream.class);
        ByteStreamAccess.initialize(stream, data);
        return readUV(stream);
    }

    /** This code is adapted from TypeReader.getUV(). */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long readUV(ByteStream data) {
        Pointer pos = data.getPosition();
        long result = pos.readByte(0) & 0xFF;
        pos = pos.add(1);
        if (result >= UnsafeArrayTypeWriter.NUM_LOW_CODES) {
            long shift = UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
            for (int i = 2;; i++) {
                long b = pos.readByte(0) & 0xFF;
                pos = pos.add(1);
                result += b << shift;
                if (b < UnsafeArrayTypeWriter.NUM_LOW_CODES || i == UnsafeArrayTypeWriter.MAX_BYTES) {
                    break;
                }
                shift += UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
            }
        }
        data.setPosition(pos);
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int readUVAsInt(Pointer data) {
        long result = readUV(data);
        assert (int) result == result;
        return (int) result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int readUVAsInt(ByteStream data) {
        long result = readUV(data);
        assert (int) result == result;
        return (int) result;
    }
}
