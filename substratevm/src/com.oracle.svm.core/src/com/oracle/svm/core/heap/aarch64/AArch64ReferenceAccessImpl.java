/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Arm Limited. All rights reserved.
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
package com.oracle.svm.core.heap.aarch64;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceAccessImpl;

public class AArch64ReferenceAccessImpl extends ReferenceAccessImpl implements AArch64ReferenceAccess {

    static void initialize() {
        ImageSingletons.add(ReferenceAccess.class, new AArch64ReferenceAccessImpl());
    }

    @Override
    @Uninterruptible(reason = "for uninterruptible callers", mayBeInlined = true)
    public Word readSplitObjectAsUntrackedPointer(Pointer p, boolean compressed, int numPieces) {
        final int mask = 0xFFFF; // 16 bit unit size
        final int bitOffset = 5;
        long inlinedAddress = 0;
        for (int i = 0; i < numPieces; i++) {
            long value = p.readInt(i * 4);
            value = (value >> bitOffset) & mask;
            inlinedAddress = inlinedAddress | (value << (16 * i));
        }
        if (compressed) {
            UnsignedWord w = WordFactory.unsigned(inlinedAddress);
            Object obj = ReferenceAccess.singleton().uncompressReference(w);
            return Word.objectToUntrackedPointer(obj);
        }

        return WordFactory.unsigned(inlinedAddress);
    }

    @Override
    @Uninterruptible(reason = "for uninterruptible callers", mayBeInlined = true)
    public void writeSplitObjectAt(Pointer p, Object value, boolean compressed, int numPieces) {
        final int mask = 0xFFFF; // 16 bit unit size
        final int bitOffset = 5;
        long objAddress = compressed ? ReferenceAccess.singleton().getCompressedRepresentation(value).rawValue() : Word.objectToUntrackedPointer(value).rawValue();
        for (int i = 0; i < numPieces; i++) {
            int current = 0;
            current = p.readInt(i * 4);
            current = (current & ~(mask << bitOffset)) | (((int) objAddress & mask) << bitOffset);
            p.writeInt(i * 4, current);
            objAddress = objAddress >>> 16;
        }
        assert objAddress != 0 : "could not write full address at position";
    }
}

@AutomaticFeature
@Platforms(Platform.AARCH64.class)
class ReferenceAccessFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        AArch64ReferenceAccessImpl.initialize();
    }
}
