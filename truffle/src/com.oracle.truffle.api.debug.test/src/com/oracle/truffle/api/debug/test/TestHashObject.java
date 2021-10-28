/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.debug.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Test implementation of hash interop.
 */
@ExportLibrary(InteropLibrary.class)
public final class TestHashObject implements TruffleObject {

    private final Map<HashElement, Object> hashMap;
    private final Map<Object, HashElement> hashKeys;
    private final boolean insertable;

    public TestHashObject(Map<HashElement, Object> hashMap, boolean insertable) {
        this.hashMap = hashMap;
        this.hashKeys = new HashMap<>();
        for (HashElement keyElement : hashMap.keySet()) {
            hashKeys.put(keyElement.getKey(), keyElement);
        }
        this.insertable = insertable;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasHashEntries() {
        return true;
    }

    @ExportMessage
    long getHashSize() {
        return hashMap.size();
    }

    @ExportMessage
    boolean isHashEntryReadable(Object key) {
        HashElement element = hashKeys.get(key);
        return element != null && element.isReadable();
    }

    @ExportMessage
    Object readHashValue(Object key) throws UnknownKeyException {
        HashElement keyElement = hashKeys.get(key);
        if (keyElement == null || !keyElement.isReadable()) {
            throw UnknownKeyException.create(key);
        }
        return hashMap.get(keyElement);
    }

    @ExportMessage
    boolean isHashEntryModifiable(Object key) {
        HashElement keyElement = hashKeys.get(key);
        if (keyElement == null) {
            return false;
        } else {
            return keyElement.isWritable();
        }
    }

    @ExportMessage
    boolean isHashEntryInsertable(Object key) {
        HashElement keyElement = hashKeys.get(key);
        if (keyElement == null) {
            return insertable;
        } else {
            return false;
        }
    }

    @ExportMessage
    void writeHashEntry(Object key, Object value) throws UnsupportedMessageException, UnknownKeyException {
        HashElement keyElement = hashKeys.get(key);
        if (keyElement == null) {
            if (insertable) {
                keyElement = new HashElement(key, true, true, true);
                hashMap.put(keyElement, value);
                hashKeys.put(key, keyElement);
            } else {
                throw UnsupportedMessageException.create();
            }
        } else {
            if (keyElement.isWritable()) {
                hashMap.put(keyElement, value);
            } else {
                throw UnknownKeyException.create(key);
            }
        }
    }

    @ExportMessage
    boolean isHashEntryRemovable(Object key) {
        HashElement keyElement = hashKeys.get(key);
        if (keyElement == null) {
            return false;
        } else {
            return keyElement.isRemovable();
        }
    }

    @ExportMessage
    void removeHashEntry(Object key) throws UnknownKeyException {
        HashElement keyElement = hashKeys.get(key);
        if (keyElement == null) {
            throw UnknownKeyException.create(key);
        } else {
            if (keyElement.isRemovable()) {
                hashMap.remove(keyElement);
                hashKeys.remove(key);
            } else {
                throw UnknownKeyException.create(key);
            }
        }
    }

    @ExportMessage
    Object getHashEntriesIterator() {
        List<HashEntryArray> entries = new ArrayList<>();
        for (Map.Entry<HashElement, Object> mapEntry : hashMap.entrySet()) {
            entries.add(new HashEntryArray(mapEntry.getKey(), mapEntry.getValue()));
        }
        return new TestIteratorObject(false, true, entries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HashEntryArray implements TruffleObject {

        private final HashElement key;
        private final Object value;

        HashEntryArray(HashElement key, Object value) {
            this.key = key;
            this.value = value;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 2L;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 == index || 1 == index && key.isReadable();
        }

        @ExportMessage
        Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            if (index == 0) {
                return key.getKey();
            } else if (index == 1) {
                if (key.isReadable()) {
                    return value;
                } else {
                    throw UnsupportedMessageException.create();
                }
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

    }

    public static final class HashElement {

        private Object key;
        private final boolean readable;
        private final boolean writable;
        private final boolean removable;

        public HashElement(Object key, boolean readable, boolean writable, boolean removable) {
            this.key = key;
            this.readable = readable;
            this.writable = writable;
            this.removable = removable;
        }

        public boolean isReadable() {
            return readable;
        }

        public Object getKey() {
            return key;
        }

        public boolean isWritable() {
            return writable;
        }

        public boolean isRemovable() {
            return removable;
        }

        public void setValue(Object value) {
            this.key = value;
        }

    }

    public static Map<HashElement, Object> createTestMap() {
        Map<HashElement, Object> map = new LinkedHashMap<>();
        map.put(new HashElement(10, true, true, true), 100);
        map.put(new HashElement(20, true, false, true), 200);
        map.put(new HashElement(30, true, true, false), 300);
        map.put(new HashElement(40, true, false, false), 400);
        map.put(new HashElement(50, false, true, true), 500);
        map.put(new HashElement(60, false, false, true), 600);
        map.put(new HashElement(70, false, true, false), 700);
        map.put(new HashElement(80, false, false, false), 800);
        return map;
    }

}
