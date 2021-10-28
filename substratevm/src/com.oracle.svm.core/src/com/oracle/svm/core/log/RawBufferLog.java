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

package com.oracle.svm.core.log;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.RestrictHeapAccess;

public class RawBufferLog extends RealLog {

    CCharPointer rawBuffer;
    int rawBufferSize;
    int rawBufferPos;

    public void setRawBuffer(CCharPointer rawBuffer, int rawBufferSize) {
        this.rawBuffer = rawBuffer;
        this.rawBufferSize = rawBufferSize;
        this.rawBufferPos = 0;
    }

    public int getRawBufferPos() {
        return rawBufferPos;
    }

    public int getRawBufferBytesLeft() {
        return rawBufferSize - rawBufferPos;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "This implementation only allocates on the stack.")
    @Override
    protected Log rawBytes(CCharPointer bytes, UnsignedWord lengthAsWord) {
        int length = (int) lengthAsWord.rawValue();

        /* Write bytes to rawBuffer until full. */
        int bytesLeft = getRawBufferBytesLeft();
        int bytesToWrite = Math.min(bytesLeft, length);

        int index = 0;
        while (bytesToWrite > 0) {
            rawBuffer.write(rawBufferPos++, bytes.read(index++));
            bytesToWrite--;
        }
        return this;
    }

    @Override
    public Log flush() {
        // noop
        return this;
    }
}
