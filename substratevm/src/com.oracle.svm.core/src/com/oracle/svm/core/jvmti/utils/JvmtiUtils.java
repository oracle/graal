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
package com.oracle.svm.core.jvmti.utils;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;

public final class JvmtiUtils {

    private JvmtiUtils() {
    }

    @Uninterruptible(reason = "jvmti unmanaged memory buffer utils")
    public static <T extends PointerBase> T allocateWordBuffer(int nbElements) {
        int size = nbElements * ConfigurationValues.getTarget().wordSize;
        T allocatedPtr = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(size));
        if (allocatedPtr.isNull()) {
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(allocatedPtr);
            return WordFactory.nullPointer();
        }
        return allocatedPtr;
    }

    @Uninterruptible(reason = "jvmti unmanaged memory buffer utils")
    public static <T extends PointerBase, P extends ComparableWord> void writeWordAtIdxInBuffer(T array, int index, P element) {
        ((Pointer) array).writeWord(index * ConfigurationValues.getTarget().wordSize, element);
    }

    @Uninterruptible(reason = "jvmti unmanaged memory buffer utils")
    public static <T extends PointerBase> T growWordBuffer(T buffer, int newNbElements) {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(buffer, WordFactory.unsigned(newNbElements * ConfigurationValues.getTarget().wordSize));
    }

    @Uninterruptible(reason = "jvmti unmanaged memory buffer utils")
    public static <T extends PointerBase> void freeWordBuffer(T buffer) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(buffer);
    }

}
