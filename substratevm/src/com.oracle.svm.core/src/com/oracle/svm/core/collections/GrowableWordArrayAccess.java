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
package com.oracle.svm.core.collections;

import jdk.compiler.graal.api.replacements.Fold;
import jdk.compiler.graal.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;

public class GrowableWordArrayAccess {
    private static final int INITIAL_CAPACITY = 10;

    public static void initialize(GrowableWordArray array) {
        array.setSize(0);
        array.setCapacity(0);
        array.setData(WordFactory.nullPointer());
    }

    public static Word get(GrowableWordArray array, int i) {
        assert i >= 0 && i < array.getSize();
        return array.getData().addressOf(i).read();
    }

    public static boolean add(GrowableWordArray array, Word element) {
        if (array.getSize() == array.getCapacity() && !grow(array)) {
            return false;
        }

        array.getData().addressOf(array.getSize()).write(element);
        array.setSize(array.getSize() + 1);
        return true;
    }

    public static void freeData(GrowableWordArray array) {
        if (array.isNonNull()) {
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(array.getData());
            array.setData(WordFactory.nullPointer());
            array.setSize(0);
            array.setCapacity(0);
        }
    }

    private static boolean grow(GrowableWordArray array) {
        int newCapacity = computeNewCapacity(array);
        if (newCapacity < 0) {
            /* Overflow. */
            return false;
        }

        assert newCapacity >= INITIAL_CAPACITY;
        WordPointer oldData = array.getData();
        WordPointer newData = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(newCapacity).multiply(wordSize()));
        if (newData.isNull()) {
            return false;
        }

        UnmanagedMemoryUtil.copyForward((Pointer) oldData, (Pointer) newData, WordFactory.unsigned(array.getSize()).multiply(wordSize()));
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(oldData);

        array.setData(newData);
        array.setCapacity(newCapacity);
        return true;
    }

    private static int computeNewCapacity(GrowableWordArray array) {
        int oldCapacity = array.getCapacity();
        if (oldCapacity == 0) {
            return INITIAL_CAPACITY;
        } else {
            return oldCapacity * 2;
        }
    }

    @Fold
    static int wordSize() {
        return ConfigurationValues.getTarget().wordSize;
    }
}
