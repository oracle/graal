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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownHashKeyException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HashTest extends AbstractPolyglotTest {

    @Test
    public void testTruffleObjectInteropMessages() throws InteropException {
        setupEnv();
        testInteropMessages(new Hash());
    }

    @Test
    public void testHostObjectInteropMessages() throws InteropException {
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        Accessor accessor = new Accessor();
        Value accessorValue = context.asValue(accessor);
        accessorValue.execute(context.asValue(new HashMap<>()));
        testInteropMessages(accessor.object);
    }

    @Test
    @Ignore
    public void testPolyglotProxyInteropMessages() throws InteropException {
        setupEnv();
        Accessor accessor = new Accessor();
        Value accessorValue = context.asValue(accessor);
        accessorValue.execute(context.asValue(ProxyHashMap.fromMap(new HashMap<>())));
        testInteropMessages(accessor.object);
    }

    private static void testInteropMessages(Object hash) throws InteropException {
        InteropLibrary interop = InteropLibrary.getUncached();
        assertTrue(interop.hasHashEntries(hash));
        for (int i = 0; i < 100; i += 2) {
            Object key = i;
            Object value = String.valueOf(i);
            assertNonExisting(hash, key, interop);
            interop.writeHashEntry(hash, key, value);
            key = String.valueOf(i);
            value = i;
            assertNonExisting(hash, key, interop);
            interop.writeHashEntry(hash, key, value);
        }
        assertEquals(100, interop.getHashSize(hash));
        for (int i = 0; i < 100; i++) {
            Object key = i;
            if ((i & 1) == 0) {
                Object expectedValue = String.valueOf(i);
                assertExisting(hash, key, interop);
                assertEquals(expectedValue, interop.readHashValue(hash, key));
                key = String.valueOf(i);
                expectedValue = i;
                assertExisting(hash, key, interop);
                assertEquals(expectedValue, interop.readHashValue(hash, key));
            } else {
                assertNonExisting(hash, key, interop);
                key = String.valueOf(i);
                assertNonExisting(hash, key, interop);
            }
        }
        Map<Object, Object> expected = new HashMap<>();
        for (int i = 0; i < 100; i += 2) {
            Object key = i;
            Object value = i + 1;
            interop.writeHashEntry(hash, key, value);
            assertExisting(hash, key, interop);
            assertEquals(value, interop.readHashValue(hash, key));
            expected.put(key, value);
            key = String.valueOf(i);
            interop.removeHashEntry(hash, key);
            assertNonExisting(hash, key, interop);
        }
        Object iterator = interop.getHashEntriesIterator(hash);
        assertTrue(interop.isIterator(iterator));
        Map<Object, Object> expected2 = new HashMap<>();
        while (interop.hasIteratorNextElement(iterator)) {
            Object entry = interop.getIteratorNextElement(iterator);
            assertTrue(interop.isHashEntry(entry));
            Object key = interop.getHashEntryKey(entry);
            Object value = interop.getHashEntryValue(entry);
            Object expectedValue = expected.remove(key);
            assertEquals(expectedValue, value);
            Object newValue = String.valueOf(value);
            interop.setHashEntryValue(entry, newValue);
            expected2.put(key, newValue);
        }
        assertTrue(expected.isEmpty());
        iterator = interop.getHashEntriesIterator(hash);
        while (interop.hasIteratorNextElement(iterator)) {
            Object entry = interop.getIteratorNextElement(iterator);
            Object key = interop.getHashEntryKey(entry);
            Object value = interop.getHashEntryValue(entry);
            Object expectedValue = expected2.remove(key);
            assertEquals(expectedValue, value);
        }
        assertTrue(expected2.isEmpty());
    }

    private static void assertNonExisting(Object hash, Object key, InteropLibrary interop) {
        assertFalse(interop.isHashEntryExisting(hash, key));
        assertFalse(interop.isHashEntryModifiable(hash, key));
        assertFalse(interop.isHashEntryRemovable(hash, key));
        assertTrue(interop.isHashEntryInsertable(hash, key));
        assertTrue(interop.isHashEntryWritable(hash, key));
    }

    private static void assertExisting(Object hash, Object key, InteropLibrary interop) {
        assertTrue(interop.isHashEntryExisting(hash, key));
        assertTrue(interop.isHashEntryModifiable(hash, key));
        assertTrue(interop.isHashEntryRemovable(hash, key));
        assertFalse(interop.isHashEntryInsertable(hash, key));
        assertTrue(interop.isHashEntryWritable(hash, key));
    }

    @Test
    public void testValues() {
        setupEnv(Context.newBuilder().allowAllAccess(true).build());
        testValueImpl(context.asValue(new Hash()));
        testValueImpl(context.asValue(new HashMap<>()));
    }

    private static void testValueImpl(Value hash) {
        assertTrue(hash.hasHashEntries());
        for (int i = 0; i < 100; i += 2) {
            Object key = i;
            Object value = String.valueOf(i);
            assertFalse(hash.hasHashEntry(key));
            hash.putHashEntry(key, value);
            key = String.valueOf(i);
            value = i;
            assertFalse(hash.hasHashEntry(key));
            hash.putHashEntry(key, value);
        }
        assertEquals(100, hash.getHashSize());
        for (int i = 0; i < 100; i++) {
            Object key = i;
            if ((i & 1) == 0) {
                Object expectedValue = String.valueOf(i);
                assertTrue(hash.hasHashEntry(key));
                assertEquals(expectedValue, hash.getHashValue(key).asString());
                key = String.valueOf(i);
                expectedValue = i;
                hash.hasHashEntry(key);
                assertEquals(expectedValue, hash.getHashValue(key).asInt());
            } else {
                assertFalse(hash.hasHashEntry(key));
                key = String.valueOf(i);
                assertFalse(hash.hasHashEntry(key));
            }
        }
        Map<Integer, Integer> expected = new HashMap<>();
        for (int i = 0; i < 100; i += 2) {
            Object key = i;
            Object value = i + 1;
            hash.putHashEntry(key, value);
            assertTrue(hash.hasHashEntry(key));
            assertEquals(value, hash.getHashValue(key).asInt());
            expected.put((Integer) key, (Integer) value);
            key = String.valueOf(i);
            hash.removeHashEntry(key);
            assertFalse(hash.hasHashEntry(key));
        }
        Value iterator = hash.getHashEntriesIterator();
        assertTrue(iterator.isIterator());
        Map<Integer, String> expected2 = new HashMap<>();
        while (iterator.hasIteratorNextElement()) {
            Value entry = iterator.getIteratorNextElement();
            assertTrue(entry.isHashEntry());
            Value key = entry.getHashEntryKey();
            Value value = entry.getHashEntryValue();
            Object expectedValue = expected.remove(key.asInt());
            assertEquals(expectedValue, value.asInt());
            String newValue = String.valueOf(value);
            entry.setHashEntryValue(newValue);
            expected2.put(key.asInt(), newValue);
        }
        assertTrue(expected.isEmpty());
        iterator = hash.getHashEntriesIterator();
        while (iterator.hasIteratorNextElement()) {
            Value entry = iterator.getIteratorNextElement();
            Value key = entry.getHashEntryKey();
            Value value = entry.getHashEntryValue();
            Object expectedValue = expected2.remove(key.asInt());
            assertEquals(expectedValue, value.asString());
        }
        assertTrue(expected2.isEmpty());
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HashEntry implements TruffleObject {
        final int hashCode;
        final Object key;
        Object value;

        HashEntry(int hashCode, Object key, Object value) {
            this.hashCode = hashCode;
            this.key = key;
            this.value = value;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isHashEntry() {
            return true;
        }

        @ExportMessage
        Object getHashEntryKey() {
            return key;
        }

        @ExportMessage
        Object getHashEntryValue() {
            return value;
        }

        @ExportMessage
        void setHashEntryValue(Object newValue) {
            this.value = newValue;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class HashEntriesIterator implements TruffleObject {

        private final HashEntry[] store;
        private final int size;
        private int emittedCount;
        private int index;

        HashEntriesIterator(HashEntry[] store, int size) {
            this.store = store;
            this.size = size;
            this.index = 0;
            this.emittedCount = 0;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isIterator() {
            return true;
        }

        @ExportMessage
        boolean hasIteratorNextElement() {
            return emittedCount < size;
        }

        @ExportMessage
        Object getIteratorNextElement() throws StopIterationException {
            while (index < store.length) {
                if (store[index] != null) {
                    emittedCount++;
                    return store[index++];
                } else {
                    index++;
                }
            }
            throw StopIterationException.create();
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class Hash implements TruffleObject {

        private static final int INITIAL_CAPACITY = 1 << 12;

        private int size;
        private HashEntry[] store;

        Hash() {
            this.size = 0;
            this.store = new HashEntry[INITIAL_CAPACITY];
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasHashEntries() {
            return true;
        }

        @ExportMessage
        long getHashSize() {
            return size;
        }

        @ExportMessage(name = "isHashValueReadable")
        @ExportMessage(name = "isHashEntryModifiable")
        @ExportMessage(name = "isHashEntryRemovable")
        boolean isHashEntryExisting(Object key) {
            return get(key) != -1;
        }

        @ExportMessage
        boolean isHashEntryInsertable(Object key) {
            return get(key) == -1;
        }

        @ExportMessage
        Object readHashValue(Object key) throws UnknownHashKeyException {
            int addr = get(key);
            if (addr == -1) {
                throw UnknownHashKeyException.create(key);
            } else {
                return store[addr].value;
            }
        }

        @ExportMessage
        void writeHashEntry(Object key, Object value) {
            int addr = get(key);
            if (addr != -1) {
                store[addr].value = value;
            } else {
                int hashCode = hashCode(key);
                HashEntry e = new HashEntry(hashCode, key, value);
                if (size >= (int) (store.length * 0.75f)) {
                    copyStore(store.length << 1);
                }
                insert(e);
            }
        }

        @ExportMessage
        void removeHashEntry(Object key) throws UnknownHashKeyException {
            int addr = get(key);
            if (addr == -1) {
                throw UnknownHashKeyException.create(key);
            } else {
                store[addr] = null;
                size--;
                copyStore(store.length);
            }
        }

        @ExportMessage
        Object getHashEntriesIterator() {
            return new HashEntriesIterator(store, size);
        }

        private int get(Object key) {
            int hash = hashCode(key);
            int addr = hash % store.length;
            int end = addr;
            do {
                HashEntry e = store[addr];
                if (e == null) {
                    return -1;
                } else if (e.hashCode == hash && equals(e.key, key)) {
                    return addr;
                }
                addr = (addr + 1) % store.length;
            } while (addr != end);
            return -1;
        }

        private void insert(HashEntry e) {
            int addr = e.hashCode % store.length;
            int end = addr;
            do {
                if (store[addr] == null) {
                    store[addr] = e;
                    size++;
                    return;
                }
                addr = (addr + 1) % store.length;
            } while (addr != end);
            throw new IllegalStateException("Full store.");
        }

        @TruffleBoundary
        private void copyStore(int newSize) {
            HashEntry[] oldStore = store;
            store = new HashEntry[newSize];
            size = 0;
            for (HashEntry e : oldStore) {
                if (e != null) {
                    insert(e);
                }
            }
        }

        @TruffleBoundary
        private static boolean equals(Object o1, Object o2) {
            return Objects.equals(o1, o2);
        }

        @TruffleBoundary
        private static int hashCode(Object obj) {
            return Objects.hashCode(obj);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Accessor implements TruffleObject {

        Object object;

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object... arguments) {
            object = arguments[0];
            return object;
        }
    }
}
