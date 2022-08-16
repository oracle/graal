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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

public class ContinuationSupport {
    private long ipOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected ContinuationSupport() {
    }

    @Fold
    public static ContinuationSupport singleton() {
        return ImageSingletons.lookup(ContinuationSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setIPOffset(long value) {
        assert ipOffset == 0;
        ipOffset = value;
    }

    public long getIPOffset() {
        assert ipOffset != 0;
        return ipOffset;
    }

    @AlwaysInline("If not inlined, this method could overwrite its own frame.")
    @Uninterruptible(reason = "Copies stack frames containing references.")
    public CodePointer copyFrames(StoredContinuation storedCont, Pointer to) {
        int wordSize = ConfigurationValues.getTarget().wordSize;
        int totalSize = StoredContinuationAccess.getFramesSizeInBytes(storedCont);
        assert totalSize % wordSize == 0;

        CodePointer storedIP = StoredContinuationAccess.getIP(storedCont);
        Pointer frameData = StoredContinuationAccess.getFramesStart(storedCont);

        /*
         * NO CALLS BEYOND THIS POINT! They would overwrite the frames we are copying.
         */

        int stepSize = 4 * wordSize;
        Pointer src = frameData;
        Pointer srcEnd = frameData.add(totalSize);
        Pointer dst = to;
        while (src.add(stepSize).belowOrEqual(srcEnd)) {
            WordBase w0 = src.readWord(0 * wordSize);
            WordBase w8 = src.readWord(1 * wordSize);
            WordBase w16 = src.readWord(2 * wordSize);
            WordBase w24 = src.readWord(3 * wordSize);
            dst.writeWord(0 * wordSize, w0);
            dst.writeWord(1 * wordSize, w8);
            dst.writeWord(2 * wordSize, w16);
            dst.writeWord(3 * wordSize, w24);

            src = src.add(stepSize);
            dst = dst.add(stepSize);
        }

        while (src.belowThan(srcEnd)) {
            dst.writeWord(WordFactory.zero(), src.readWord(WordFactory.zero()));
            src = src.add(wordSize);
            dst = dst.add(wordSize);
        }

        assert src.equal(srcEnd);
        assert dst.equal(to.add(totalSize));
        return storedIP;
    }

    @Uninterruptible(reason = "Copies stack frames containing references.")
    public CodePointer copyFrames(StoredContinuation fromCont, StoredContinuation toCont) {
        return copyFrames(fromCont, StoredContinuationAccess.getFramesStart(toCont));
    }
}
