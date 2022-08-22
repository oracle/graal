/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;

public class ContinuationSupport {
    @AlwaysInline("If not inlined, this method could overwrite its own frame.")
    @Uninterruptible(reason = "Copies stack frames containing references.")
    public CodePointer copyFrames(StoredContinuation storedCont, Pointer to) {
        int totalSize = StoredContinuationAccess.getFramesSizeInBytes(storedCont);
        CodePointer storedIP = StoredContinuationAccess.getIP(storedCont);
        Pointer frameData = StoredContinuationAccess.getFramesStart(storedCont);

        /*
         * NO CALLS BEYOND THIS POINT! They would overwrite the frames we are copying.
         */

        int offset = 0;
        for (int next = offset + 32; next < totalSize; next += 32) {
            Pointer src = frameData.add(offset);
            Pointer dst = to.add(offset);
            long l0 = src.readLong(0);
            long l8 = src.readLong(8);
            long l16 = src.readLong(16);
            long l24 = src.readLong(24);
            dst.writeLong(0, l0);
            dst.writeLong(8, l8);
            dst.writeLong(16, l16);
            dst.writeLong(24, l24);
            offset = next;
        }
        for (; offset < totalSize; offset++) {
            to.writeByte(offset, frameData.readByte(offset));
        }
        return storedIP;
    }

    @Uninterruptible(reason = "Copies stack frames containing references.")
    public CodePointer copyFrames(StoredContinuation fromCont, StoredContinuation toCont) {
        return copyFrames(fromCont, StoredContinuationAccess.getFramesStart(toCont));
    }
}
