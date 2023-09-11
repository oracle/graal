/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.utils;

import com.oracle.svm.core.config.ConfigurationValues;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.WordFactory;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

public class PointerArrayAccess {
    public static boolean initialize(PointerArray array, int initialCapacity) {
        WordPointer newData = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(initialCapacity).multiply(wordSize()));
        if (newData.isNull()) {
            return false;
        }
        array.setData(newData);
        for (int i = 0; i < initialCapacity; i++) {
            array.getData().addressOf(i).write(WordFactory.nullPointer());
        }
        array.setSize(initialCapacity);
        return true;
    }

    public static PointerBase get(PointerArray array, int i) {
        assert i >= 0 && i < array.getSize();
        return array.getData().addressOf(i).read();
    }

    public static void write(PointerArray array, int i, WordBase word) {
        assert i >= 0 && i < array.getSize();
        array.getData().addressOf(i).write(word);
    }

    public static void freeData(PointerArray array) {
        if (array.isNull() || array.getData().isNull()) {
            return;
        }
        for (int i = 0; i < array.getSize(); i++) {
            PointerBase ptr = PointerArrayAccess.get(array, i);
            if (ptr.isNonNull()) {
                ImageSingletons.lookup(UnmanagedMemorySupport.class).free(ptr);
            }
        }
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(array.getData());
        array.setData(WordFactory.nullPointer());
        array.setSize(0);
    }

    @Fold
    static int wordSize() {
        return ConfigurationValues.getTarget().wordSize;
    }
}
