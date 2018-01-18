/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.c;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.Utf8;

public interface CGlobalDataFactory {
    static <T extends PointerBase> CGlobalData<T> forSymbol(String symbolName) {
        return new CGlobalDataImpl<>(symbolName);
    }

    static <T extends PointerBase> CGlobalData<T> createCString(String content) {
        return createCString(content, null);
    }

    static <T extends PointerBase> CGlobalData<T> createCString(String content, String symbolName) {
        return new CGlobalDataImpl<>(symbolName, () -> Utf8.stringToUtf8(content, true));
    }

    static <T extends PointerBase> CGlobalData<T> createBytes(IntSupplier sizeSupplier) {
        return createBytes(sizeSupplier, null);
    }

    static <T extends PointerBase> CGlobalData<T> createBytes(IntSupplier sizeSupplier, String symbolName) {
        return new CGlobalDataImpl<>(symbolName, sizeSupplier);
    }

    static <T extends PointerBase> CGlobalData<T> createBytes(Supplier<byte[]> contentSupplier) {
        return createBytes(contentSupplier, null);
    }

    static <T extends PointerBase> CGlobalData<T> createBytes(Supplier<byte[]> contentSupplier, String symbolName) {
        return new CGlobalDataImpl<>(symbolName, contentSupplier);
    }

    static <T extends PointerBase> CGlobalData<T> createWord(WordBase initialValue) {
        return createWord(initialValue, null);
    }

    static <T extends PointerBase> CGlobalData<T> createWord(WordBase initialValue, String symbolName) {
        assert ConfigurationValues.getTarget().wordSize == Long.BYTES;
        Supplier<byte[]> supplier = () -> ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder()).putLong(initialValue.rawValue()).array();
        return new CGlobalDataImpl<>(symbolName, supplier);
    }

    static <T extends PointerBase> CGlobalData<T> createWord() {
        return createWord((String) null);
    }

    static <T extends PointerBase> CGlobalData<T> createWord(String symbolName) {
        return new CGlobalDataImpl<>(symbolName, () -> ConfigurationValues.getTarget().wordSize);
    }
}
