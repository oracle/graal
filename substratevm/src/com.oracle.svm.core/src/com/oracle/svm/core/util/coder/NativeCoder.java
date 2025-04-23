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

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;

/** Uses the native, architecture-specific byte order to access {@link ByteStream} data. */
public class NativeCoder {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int readU1(Pointer ptr) {
        return ptr.readInt(0) & 0xFF;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int readU2(Pointer ptr) {
        return ptr.readShort(0) & 0xFFFF;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long readU4(Pointer ptr) {
        return ptr.readInt(0) & 0xFFFFFFFFL;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord readU8(Pointer ptr) {
        return Word.unsigned(ptr.readLong(0));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static byte readByte(ByteStream data) {
        Pointer position = data.getPosition();
        byte result = position.readByte(0);
        data.setPosition(position.add(Byte.BYTES));
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int readInt(ByteStream data) {
        Pointer position = data.getPosition();
        int result = position.readInt(0);
        data.setPosition(position.add(Integer.BYTES));
        return result;
    }
}
